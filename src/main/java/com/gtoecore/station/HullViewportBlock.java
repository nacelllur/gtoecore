package com.gtoecore.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 全周天舱壁（hull_viewport）—— 单向舷窗方块。
 *
 * <p><b>静态效果：</b>从舱内看是通的（能看见舱外世界），从舱外看是实心的金属舷窗。
 * 用来做飞船 / 空间站的观察舱壁。</p>
 *
 * <p><b>静态效果的实现（纯方块模型，零渲染代码）：</b>
 * 方块带 {@link #INNER} 朝向属性，模型<b>永久省略「朝内」那一面</b>的几何。
 * 于是从舱外看：朝外那面正对相机 → 正常渲染 → 实心壳；从舱内看：朝内那面模型里
 * 根本不存在，朝外那面背对相机被背面剔除 → 直接看穿。
 * 朝内那一面的几何由 {@code HullViewportRenderer}（BER）动态补绘，
 * 因此它可以做透明度动画（见下）。</p>
 *
 * <p><b>面板皮肤（变种）：</b>构造时可传 {@link PanelSkin}，只替换关闭态朝内面的纹理
 * （蜂窝 / 星空 / 警示 / 电路 / 装甲），其余行为完全一致。变种方块共用同一个
 * {@code HullViewportBlockEntity} 类型，也不额外占用网络或存档字段。</p>
 *
 * <p><b>红石波纹：</b>任意一块舱壁通上红石信号后，与它<b>相连的整片舱壁</b>会一起
 * 变透明；顺序按与信号源的距离逐圈推进（BFS 距离 × {@link #WAVE_STEP_TICKS} tick 错峰），
 * 因为错峰间隔等于单块淡出时长，所以表现为「上一块完全透明后下一块才开始」。
 * 断电时沿同一条路径反向收拢。</p>
 *
 * <p>两个状态位：{@link #SIGNAL} = 红石意图（BFS 时一次性置位全部舱壁）；
 * {@link #OPEN} = 动画目标（由计划刻按距离错峰翻转）。客户端只跟 {@code OPEN} 做插值动画。</p>
 *
 * <p><b>为什么不会在方块之间出现细缝：</b>本方块保持默认的 {@code canOcclude() == true}
 * 且遮挡形状是完整立方体，原版 {@code Block.shouldRenderFace} 会把邻居朝向本方块的面、
 * 以及本方块朝向邻居的面全部剔除（与普通实心方块完全一致）。<b>切勿</b>加 {@code .noOcclusion()}，
 * 否则整面墙会变成网格状细缝。</p>
 *
 * <p><b>为什么能透光：</b>覆写 {@link #getLightBlock} 与 {@link #propagatesSkylightDown}；
 * 方块属性带 {@code .dynamicShape()} 使 {@code BlockStateBase.Cache} 不生成
 * （见 {@code BlockBehaviour.initCache()}），覆写才会真正生效。</p>
 */
public class HullViewportBlock extends Block implements EntityBlock {

    /** 开口朝向（= 舱内一侧）。模型省略这一面。 */
    public static final DirectionProperty INNER = DirectionProperty.create("inner", Direction.values());

    /** 动画目标：true = 应完全透明（客户端朝这个目标插值）。 */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    /** 红石意图：true = 整片相连舱壁已收到信号。 */
    public static final BooleanProperty SIGNAL = BooleanProperty.create("signal");

    /** 相邻舱壁之间的波纹间隔（tick）。等于淡出时长 → 严格「上一块完全透明后才轮到下一块」。 */
    public static final int WAVE_STEP_TICKS = 6;
    /** 单块淡入/淡出所需 tick 数（客户端插值用）。 */
    public static final int FADE_TICKS = 6;
    /** 单次波纹最多覆盖的舱壁块数（防呆，避免巨型结构卡服）。 */
    public static final int MAX_WAVE_BLOCKS = 4096;

    /**
     * 面板皮肤：只决定<b>关闭态朝内面</b>的纹理，见 {@link PanelSkin}。
     * 单向舷窗、红石波纹、自动外框等行为对所有皮肤完全一致，
     * 因此变种之间共享同一个方块实体类型。
     */
    private final PanelSkin skin;

    /**
     * 是否「六面舱壁」：<b>纯静态方块</b>（C 档静态化，2026-10-07）。
     *
     * <p>普通舱壁（false）是<b>单向舷窗</b>：静态模型省略朝内面，只有那一个面由 BER 补绘，
     * 于是舱内看穿、舱外实心，开合有淡入淡出动画。</p>
     *
     * <p>六面舱壁（true）<b>不再走 BER</b>：闭合态 = 静态实心蜂窝模型
     * （{@code block/hull_panel_hex}），通电态 = 静态半透明模型
     * （{@code block/hull_panel_hex_open}，render_type translucent），
     * 由 blockstate 的 {@link #OPEN} 直接切换。曾经的「空模型 + 六面全靠 BER 画」
     * 在大机库下每帧每块 2 次 draw call、256 格内全渲染，卡顿严重，已废弃。</p>
     *
     * <p>红石波纹完全不变：服务端 BFS + 计划刻错峰翻转 {@link #OPEN}，
     * 客户端逐块在错峰时刻直接切换模型（波纹节奏与原来一致，
     * 只是单块从 6 tick 渐变变成瞬变）。六面版不再创建方块实体
     * （见 {@link #newBlockEntity}），也不再有外框与跨方块连续图案 ——
     * 贴图本身无缝平铺，观感上等同无缝。</p>
     */
    private final boolean sixSided;

    /** 默认皮肤（{@link PanelSkin#HEX}，蜂窝晶格），单向舷窗。 */
    public HullViewportBlock(Properties properties) {
        this(properties, PanelSkin.HEX, false);
    }

    /** 指定皮肤的变种（星空 / 警示 / 电路 / 装甲），单向舷窗。 */
    public HullViewportBlock(Properties properties, PanelSkin skin) {
        this(properties, skin, false);
    }

    /**
     * @param sixSided true = 六面舱壁（六面全画面板，静态模型为空）
     */
    public HullViewportBlock(Properties properties, PanelSkin skin, boolean sixSided) {
        super(properties);
        this.skin = skin;
        this.sixSided = sixSided;
        registerDefaultState(stateDefinition.any()
                .setValue(INNER, Direction.DOWN)
                .setValue(OPEN, false)
                .setValue(SIGNAL, false));
    }

    /** 本方块的面板皮肤（供 {@code HullViewportRenderer} 选择贴图）。 */
    public PanelSkin skin() {
        return skin;
    }

    /** 是否六面舱壁（六个面都由 BER 画面板，而非单向舷窗）。 */
    public boolean sixSided() {
        return sixSided;
    }

    /**
     * 通电（{@link #OPEN}）完全淡入后残留的不透明度。
     *
     * <p>舷窗版是 0（全透明 = 看穿），六面版留 {@code 0.30} —— 用户要的是
     * 「通电变半透明」，整块还看得见蜂窝纹理，不是整块消失。</p>
     */
    public float openAlphaFloor() {
        return sixSided ? 0.30F : 0.0F;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(INNER, OPEN, SIGNAL);
    }

    // ---------------- 放置：自动判定朝内面 ----------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(INNER, innerFor(context));
    }

    /**
     * 朝内方向 = 方块中心指向玩家眼睛的主轴方向。
     *
     * <p>比「点击面」更稳：靠墙向上叠方块时点击面恒为 UP，但玩家其实站在侧面，
     * 用眼睛位置才能得到正确的水平朝向。</p>
     */
    private static Direction innerFor(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            // 发射器 / 其它无玩家的放置上下文：退回点击面
            return context.getClickedFace();
        }
        Vec3 center = Vec3.atCenterOf(context.getClickedPos());
        Vec3 eye = player.getEyePosition();
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        if (Math.abs(dx) < 1.0e-4 && Math.abs(dy) < 1.0e-4 && Math.abs(dz) < 1.0e-4) {
            return context.getClickedFace();
        }
        return Direction.getNearest(dx, dy, dz);
    }

    /** 潜行 + 空手右键：循环翻转开口朝向（手里拿工具时不影响工具自身行为）。 */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // 六面舱壁没有「开口朝向」概念（INNER 只是沿用基类的占位属性），翻转没有意义
        if (sixSided || !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Direction next = Direction.values()[(state.getValue(INNER).ordinal() + 1) % 6];
            level.setBlock(pos, state.setValue(INNER, next), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------- 红石：整片相连舱壁的波纹 ----------------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            spread(level, pos, true);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(SIGNAL)) {
            return;   // 意图没变，无需重新起波
        }
        spread(level, pos, powered);
    }

    /**
     * 从 {@code origin} 出发，沿「相连的舱壁方块」BFS（6 邻接），
     * 把所有块的红石意图统一置为 {@code target}，并按 BFS 距离错峰安排
     * {@link #OPEN} 的翻转 —— 这就是波纹。
     */
    private static void spread(Level level, BlockPos origin, boolean target) {
        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof HullViewportBlock)) {
            return;
        }
        if (originState.getValue(SIGNAL) == target) {
            return;   // 已经被同一次波纹覆盖过
        }

        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos seed = origin.immutable();
        distance.put(seed, 0);
        queue.add(seed);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int dist = distance.get(pos);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HullViewportBlock panel)) {
                continue;
            }

            // 意图立即置位；动画目标（OPEN）按距离错峰
            if (state.getValue(SIGNAL) != target) {
                setWaveState(level, pos, state.setValue(SIGNAL, target));
            }
            level.scheduleTick(pos, panel, dist * WAVE_STEP_TICKS);

            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (distance.containsKey(next) || distance.size() >= MAX_WAVE_BLOCKS) {
                    continue;
                }
                if (!(level.getBlockState(next).getBlock() instanceof HullViewportBlock)) {
                    continue;
                }
                distance.put(next, dist + 1);
                queue.add(next);
            }
        }
    }

    /** 计划刻到点：把动画目标翻到当前红石意图（错峰由 {@link #spread} 安排）。 */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean target = state.getValue(SIGNAL);
        if (state.getValue(OPEN) != target) {
            setWaveState(level, pos, state.setValue(OPEN, target));
        }
    }

    /**
     * 波纹专用：改方块状态 + 同步客户端，但<b>绕开</b> {@link Level#setBlock}。
     *
     * <p>⛔ 为什么不能直接用 {@code level.setBlock}：GTM 在
     * {@code Level.markAndNotifyBlock} 上挂了 {@code updateChunkMultiblocks}，
     * 任何 setBlock 都会让附近的多方块机器跑一次<b>完整结构重新匹配</b>。
     * 事件视界引擎的 pattern 有 47915 格（37×35×37），单次约 100ms；
     * 而一次波纹要改 3395 块 ⇒ 3395 × 100ms ≈ 5 分钟卡死。
     * 2026-10-08 实测：玩家拆拉杆触发波纹，服务器线程落后 809 tick（40 秒）仍未结束，
     * jstack 抓到主线程停在 {@code BlockPattern.checkPatternAt}。</p>
     *
     * <p>而 pattern 只判<b>方块类型</b>，{@link #SIGNAL} / {@link #OPEN} 的变化
     * 根本不改变结构是否成形 —— 那次检查完全是白做的。</p>
     *
     * <p>所以这里直接改 chunk 里的方块状态（不发邻居更新、不触发 markAndNotify），
     * 再用 {@code sendBlockUpdated} 单独把新状态同步给客户端。
     * 光照不受影响：舱壁 {@code getLightBlock=0} 且 {@code propagatesSkylightDown=true}，
     * 这两个值与 SIGNAL/OPEN 无关。</p>
     */
    private static void setWaveState(Level level, BlockPos pos, BlockState state) {
        LevelChunk chunk = level.getChunkAt(pos);
        BlockState old = chunk.setBlockState(pos, state, false);
        if (old == null || old == state) {
            return;
        }
        chunk.setUnsaved(true);
        level.sendBlockUpdated(pos, old, state, Block.UPDATE_CLIENTS);
    }

    // ---------------- 客户端动画驱动 ----------------

    /**
     * ⚠ 1.20.1 里「有方块实体」的判定是 {@code BlockBehaviour.hasBlockEntity()}
     * ({@code this instanceof EntityBlock})，所以本方块必须 implements {@link EntityBlock}，
     * 否则方块实体根本不会被创建 —— 客户端就不会有动画，朝内面也不会被补绘。
     *
     * <p><b>六面舱壁（C 档静态化，2026-10-07）返回 {@code null}</b>：它的闭合态/通电态
     * 都是静态烘焙模型（实心 / 半透明），没有任何 BER 才能画的东西，因此不创建方块实体
     * —— 每帧零 draw call、零邻居查询，性能与普通实心方块一致。旧存档里已经写进 NBT 的
     * 六面舱壁方块实体会照常反序列化（类型注册表仍包含本方块），渲染端用
     * {@code HullViewportRenderer} 的短路兜住，不画任何东西。</p>
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (this.sixSided) {
            return null;   // 纯静态方块：模型就是全部外观，不需要 BE
        }
        return new HullViewportBlockEntity(pos, state);
    }

    /** 只在客户端挂 ticker：推进淡入淡出进度。服务端不需要逐 tick 逻辑。六面舱壁无 BE，也无需动画。 */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide || this.sixSided) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof HullViewportBlockEntity viewport) {
                viewport.clientTick();
            }
        };
    }

    // ---------------- 透光：外壳保持实心遮挡，但让光照进来 ----------------

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
