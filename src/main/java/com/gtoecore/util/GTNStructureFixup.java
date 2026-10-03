package com.gtoecore.util;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 把「用 GT 终端自动建造出来的多方块」的装饰方块朝向，还原成投影里的样子。
 *
 * <h2>为什么需要它</h2>
 * <p>GTM 终端（{@code TerminalBehavior}）右键未成形的控制器时会调用
 * {@code BlockPattern.autoBuild()}。该方法<b>只从 pattern 复现"放什么方块"，
 * 不复现"方块朝哪边"</b>，源码级原因有三条：</p>
 * <ol>
 *   <li>落地方块的状态来自
 *       {@code new BlockItem(item).place(new BlockPlaceContext(world, player, MAIN_HAND, stack,
 *       new BlockHitResult(player.getViewVector(0), Direction.UP, pos)))}——
 *       于是<b>所有</b>楼梯/按钮的 {@code facing} 都等于「玩家当时的朝向」，所有半砖都变成
 *       {@code type=bottom}；</li>
 *   <li>之后 {@code BlockPattern.resetFacing()} 只做启发式修补：从
 *       {@code [控制器朝向, DOWN,UP,NORTH,SOUTH,WEST,EAST]} 里挑第一个「邻居是空气」的方向，
 *       与投影无关；</li>
 *   <li>谓词的 {@code candidates} 只被用来取 {@code ItemStack}，<b>不参与</b>决定落地状态。</li>
 * </ol>
 * <p>⇒ pattern 天生表达不了朝向（这是特性：机器转个方向也能成形），
 * 所以「用终端摆出来的按钮/楼梯方向错」是必然的，改 pattern 无解。</p>
 *
 * <h2>做法</h2>
 * <p>机器自己的 {@code .shapeInfo(...)} 里<b>已经</b>写好了「控制器朝北」时每一格的完整
 * {@code BlockState}（本来只给 JEI 预览用）。本类在结构成形后把它当成"施工图"：</p>
 * <pre>
 *   Δp    = 该格在 shapeInfo.getBlocks() 里的下标 − 控制器格下标
 *   Δw    = cwRotate(Δp, steps)        // steps = 顺时针 NORTH→控制器实际朝向 的步数
 *   目标格 = 控制器坐标 + Δw
 *   目标态 = rotateState(shapeInfo 状态, steps)
 * </pre>
 * <p>只在该格<b>已经是同一种方块</b>时才写回，因此：</p>
 * <ul>
 *   <li>玩家放了别的等级的能量仓/输入仓 → 类型不同 → 一律不动（不会把仓室降级）；</li>
 *   <li>GTM 的机器方块（{@code IMachineBlock}）一律跳过，交给 GTM 自己的 resetFacing；</li>
 *   <li>只改"属性"，不改"方块类型" ⇒ pattern 必然仍然匹配，不会把结构弄散。</li>
 * </ul>
 *
 * <h2>坐标约定（已用投影逐格回归验证）</h2>
 * <p>{@code MultiblockShapeInfo.getBlocks()} 的轴序是 <b>[x][y][z]</b>：
 * LDLib {@code Builder.bakeArray()} 内部执行 {@code Ts[charPos][row][aisleCallIdx] = symbol}，
 * 即第 1 维是行内字符位置、第 3 维才是 {@code .aisle()} 调用序号（转置过的，别想当然）。
 * 该轴序与 {@code PatternPreviewWidget} 消费时 {@code pos = origin.offset(x, y, z)} 一致。</p>
 * <p>而 {@code setActualRelativeOffset(char,row,aisle,NORTH,UP,false) = (-char, row, -aisle)}，
 * 所以「北向帧的偏移」正是 shapeInfo 的下标本身（差一个常量原点，取差时抵消）。
 * 从北向帧到控制器实际朝向只是绕 Y 轴的纯水平旋转，顺时针步数就是
 * {@code NORTH→EAST→SOUTH→WEST} 的序号。</p>
 *
 * <p>验证：{@code tools/verify_structfix.py} 用投影当 ground truth 复现本算法，蜂群之心 300/300 格 0 差异。</p>
 */
public final class GTNStructureFixup {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("gtoecore");

    /** 六个面布尔属性名（墙 / 多面方块用） */
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    private GTNStructureFixup() {}

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    /**
     * 在结构成形后安排一次朝向修复（<b>下一个服务端 tick</b> 执行）。
     *
     * <p>不能当场改：{@code onStructureFormed()} 是在 {@code checkPattern()} 持有
     * {@code patternLock} 的检查过程中被调用的，此时改方块会引起重入/数据竞争。</p>
     */
    public static void scheduleRepair(IMultiController controller) {
        MetaMachine self = controller.self();
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos pos = self.getPos();
        serverLevel.getServer().execute(() -> {
            MetaMachine again = MetaMachine.getMachine(serverLevel, pos);
            if (again instanceof IMultiController c && c.isFormed()) {
                repair(c);
            }
        });
    }

    /**
     * 立即按 shapeInfo 还原装饰方块朝向。服务端调用。
     *
     * @return 实际改动的格子数；0 表示无需修复（或该机器没有 shapeInfo）
     */
    public static int repair(IMultiController controller) {
        MetaMachine self = controller.self();
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return 0;
        if (!(self.getDefinition() instanceof MultiblockMachineDefinition def)) return 0;

        Supplier<List<MultiblockShapeInfo>> shapesSupplier = def.getShapes();
        if (shapesSupplier == null) return 0;
        List<MultiblockShapeInfo> shapes;
        try {
            shapes = shapesSupplier.get();
        } catch (Exception e) {
            return 0;
        }
        if (shapes == null || shapes.isEmpty()) return 0;

        BlockInfo[][][] blocks = shapes.get(0).getBlocks();
        if (blocks == null || blocks.length == 0) return 0;

        // 控制器在 shapeInfo 里的下标：找那格"方块类型等于控制器自己"的
        Block ctrlBlock = self.getBlockState().getBlock();
        int cx = -1, cy = -1, cz = -1;
        outer:
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            if (aisle == null) continue;
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                if (column == null) continue;
                for (int z = 0; z < column.length; z++) {
                    BlockInfo info = column[z];
                    if (info == null) continue;
                    if (info.getBlockState().getBlock() == ctrlBlock) {
                        cx = x; cy = y; cz = z;
                        break outer;
                    }
                }
            }
        }
        if (cx < 0) return 0;

        Direction facing = self.getFrontFacing();
        if (facing == null || facing.getAxis() == Direction.Axis.Y) return 0;
        int steps = clockwiseSteps(Direction.NORTH, facing);

        BlockPos center = self.getPos();
        int changed = 0;

        // 结构若被上下翻转（isFlipped），shapeInfo 的「北向帧」映射不再是纯水平旋转，本修复不适用。
        // 终端 autoBuild 摆出来的一定是未翻转的，所以正常路径不会命中这里。
        if (controller.self().isFlipped()) {
            LOGGER.debug("[gtoecore] {} 结构处于翻转态，跳过装饰方块朝向修复", self.getDefinition().getId());
            return 0;
        }

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            if (aisle == null) continue;
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                if (column == null) continue;
                for (int z = 0; z < column.length; z++) {
                    BlockInfo info = column[z];
                    if (info == null) continue;
                    BlockState want = info.getBlockState();
                    if (want == null || want.isAir()) continue;
                    Block wantBlock = want.getBlock();
                    if (wantBlock == ctrlBlock) continue;
                    // 机器方块（仓室/总线等）交给 GTM 自己处理，避免覆盖玩家放的不同等级部件
                    if (wantBlock instanceof IMachineBlock) continue;

                    int[] d = rotateOffset(x - cx, y - cy, z - cz, steps);
                    BlockPos pos = center.offset(d[0], d[1], d[2]);
                    if (!level.isLoaded(pos)) continue;

                    BlockState cur = level.getBlockState(pos);
                    if (cur.getBlock() != wantBlock) continue;   // 类型不同 → 不动

                    BlockState target = rotateState(want, steps);
                    if (target == cur) continue;

                    level.setBlock(pos, target, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                    changed++;
                }
            }
        }
        if (changed > 0) {
            LOGGER.info("[gtoecore] 已按投影还原 {} 的装饰方块朝向：{} 格（facing={}, steps={}）",
                    self.getDefinition().getId(), changed, facing, steps);
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // 旋转
    // ------------------------------------------------------------------

    /** 顺时针 90° 的步数，使 {@code from} 转到 {@code to}（水平方向）。 */
    public static int clockwiseSteps(Direction from, Direction to) {
        Direction d = from;
        for (int i = 0; i < 4; i++) {
            if (d == to) return i;
            d = d.getClockWise();
        }
        return 0;
    }

    /**
     * 偏移量顺时针水平旋转。俯视图上 (x,z) → (-z, x)：
     * NORTH(0,-1) → EAST(1,0) ✓
     */
    private static int[] rotateOffset(int dx, int dy, int dz, int steps) {
        for (int i = 0; i < (steps & 3); i++) {
            int nx = -dz;
            int nz = dx;
            dx = nx;
            dz = nz;
        }
        return new int[]{dx, dy, dz};
    }

    /**
     * 把一个「控制器朝北」坐标系下的方块状态，顺时针旋转 {@code steps} 步。
     *
     * <p>只动"方位型"属性，相对朝向/旋转不变的属性原样保留：</p>
     * <ul>
     *   <li>所有 {@link DirectionProperty}（{@code facing} / {@code vertical_direction} …）：
     *       水平方向转圈，{@code UP}/{@code DOWN} 不变；</li>
     *   <li>{@code axis}：X ↔ Z（奇数次旋转）；</li>
     *   <li>{@code rotation}（0..15）：+4·steps；</li>
     *   <li>墙 / 多面方块的六个面布尔：值跟随方向搬家；</li>
     *   <li>{@code half} / {@code type}（半砖上下）/ {@code shape}（楼梯形状，相对 facing）/
     *       {@code face}（按钮贴面）/ {@code hinge} / {@code powered} / {@code waterlogged} 等不动。</li>
     * </ul>
     */
    public static BlockState rotateState(BlockState state, int steps) {
        int s = steps & 3;
        if (s == 0) return state;
        BlockState out = state;

        // 1) 方向型属性
        for (Property<?> p : state.getProperties()) {
            if (!(p instanceof DirectionProperty dp)) continue;
            Direction v = state.getValue(dp);
            Direction nv = rotateDirection(v, s);
            if (nv != v) out = set(out, dp, nv);
        }

        // 2) axis: X <-> Z
        if ((s & 1) == 1 && state.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis a = state.getValue(BlockStateProperties.AXIS);
            Direction.Axis na = a == Direction.Axis.X ? Direction.Axis.Z
                    : a == Direction.Axis.Z ? Direction.Axis.X : a;
            if (na != a) out = set(out, BlockStateProperties.AXIS, na);
        }

        // 3) rotation 0..15（告示牌/旗帜/南瓜灯一类）
        for (Property<?> p : state.getProperties()) {
            if (!"rotation".equals(p.getName()) || p.getValueClass() != Integer.class) continue;
            int v = (Integer) state.getValue(p);
            int nv = (v + 4 * s) & 15;
            if (nv != v && p.getPossibleValues().contains(nv)) out = set(out, p, nv);
        }

        // 4) 六面布尔（墙 / 多面方块）：值跟着方向搬家
        for (String name : FACE_NAMES) {
            Property<?> src = findProperty(state, name);
            if (src == null || src.getValueClass() != Boolean.class) continue;
            Boolean val = (Boolean) state.getValue(src);
            Direction to = rotateDirection(dirByName(name), s);
            Property<?> dst = findProperty(state, to.getName().toLowerCase(Locale.ROOT));
            if (dst != null && dst.getValueClass() == Boolean.class) {
                out = set(out, dst, val);
            }
        }
        return out;
    }

    /** 水平方向顺时针转 {@code steps} 步；竖直方向原样返回。 */
    public static Direction rotateDirection(Direction d, int steps) {
        if (d.getAxis() == Direction.Axis.Y) return d;
        Direction r = d;
        for (int i = 0; i < (steps & 3); i++) {
            r = r.getClockWise();
        }
        return r;
    }

    private static Direction dirByName(String name) {
        switch (name) {
            case "north": return Direction.NORTH;
            case "south": return Direction.SOUTH;
            case "west": return Direction.WEST;
            case "east": return Direction.EAST;
            case "up": return Direction.UP;
            default: return Direction.DOWN;
        }
    }

    private static Property<?> findProperty(BlockState state, String name) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState set(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }
}
