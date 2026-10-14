package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gtoecore.objdemo.RigCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * 「机架（rig）类多方块机器」的共享契约 —— 底座常量、塔的姿态换算、以及
 * 「结构成形铺碰撞体 / 散架收回碰撞体」的生命周期。
 *
 * <p>目前有三台机器实现它：{@code tower_rig}（塔架动力塔）、
 * {@code arch_rig}（拱架）、{@code mast_rig}（桅杆）。三台的底座是<b>同一种东西</b>
 * —— 单层实心脱氧钢平板、控制器嵌在正面边缘的中点、尺寸 = 自己那具塔的脚印 + 一圈
 * —— 但<b>尺寸各不同</b>：tower 43 × 23、arch 31 × 19、mast 21 × 17。
 * 差别只在「站在底座顶面上的那座塔」是哪一具 OBJ 模型，以及各自占多大地方。</p>
 *
 * <h2>底座尺寸为什么是「每台一套 + 接口给默认值」</h2>
 * <p>底座必须比塔的脚印各边<b>大一圈</b>：塔脚的碰撞体在贴地那层是铺满的
 * （tower 的 j=0 层就是整片 41 × 21），底座若与脚印等大，那一圈会被碰撞体 100% 盖死
 * ⇒ 玩家站不上去、控制器和仓室都点不到。多出的一圈正好是可站立的走道。</p>
 * <p>所以尺寸不能三台共用：tower 的脚印（41 × 21）比 arch / mast 大得多，
 * 统一按最大的取会让另外两台的底盘凭空大一倍。接口给出<b>默认值 =
 * tower 的 43 × 23</b>，其余机器覆写 {@link #rigPlateHalfLr()} /
 * {@link #rigPlateHalfFb()} 即可，pattern / shapeInfo 的生成器直接问机器要。</p>
 *
 * <h2>底座几何是四向可配的（2026-10-13 扩展）</h2>
 * <p>老机器的底座一律「控制器贴正面边缘、平板整个长在背面」，用一对半宽 / 半深
 * 就能表达。加了 {@code maimai_rig}（<b>1 × 2</b>，深度是偶数）与
 * {@code lhc_combo}（<b>完全不要底座</b>）之后这套表达不够用了，于是拆成：</p>
 * <ul>
 *   <li>{@link #rigPlateHalfLr()} —— 左右半宽（控制器的左右各几格，恒对称）。</li>
 *   <li>{@link #rigPlateBackExtent()} / {@link #rigPlateFrontExtent()} ——
 *       控制器往 BACK / FRONT 各延伸几格（总深 = 两者 + 1，<b>可以是偶数</b>）。</li>
 *   <li>{@link #rigPlateLayers()} —— 底座层数，<b>0 = 无底座</b>（{@code lhc_combo}）。</li>
 * </ul>
 * <p>向后兼容：{@code rigPlateBackExtent()} 默认 {@code 2 × rigPlateHalfFb()}、
 * {@code rigPlateFrontExtent()} 默认 0、{@code rigPlateLayers()} 默认 1
 * ⇒ 老机器一行都不用改，行为逐格不变。</p>
 * <p>⚠ <b>塔中心 = 控制器 + BACK × rigPlateHalfFb()</b>：也就是说
 * {@code rigPlateHalfFb()} 现在的准确含义是「控制器到塔中心的距离」，
 * 必须等于 {@code rigPlateBackExtent() ÷ 2}（所以后者取偶数）——
 * 底座往前伸出多少格都不影响塔的位置，只影响那块平板本身。</p>
 *
 * <h2>⚠ 实现类必须同时是 {@link MultiblockControllerMachine}</h2>
 * <p>{@link #rigMachine()} 靠一个 unchecked cast 把 {@code this} 转成
 * {@link MultiblockControllerMachine} —— 本接口的全部默认方法都建立在
 * 「宿主是一台多方块控制器」这个前提上（要访问 {@code getPos()} /
 * {@code getFrontFacing()} / {@code isFlipped()} 等）。本 mod 里三台机器都
 * 满足（一台直接继承，一台经由 {@code WorkableElectricMultiblockMachine}），
 * 但将来若有人拿它去套别的机器，会在第一次调用时抛 ClassCastException。</p>
 *
 * <h2>为什么姿态换算只有这一处</h2>
 * <p>模型 JSON 顶层的 {@code "dynamic_renders"} 是<b>所有朝向共享</b>的，
 * 方块 {@code facing} 的旋转只烘焙进静态模型的顶点、<b>不会</b>作用到动态渲染器的
 * PoseStack 上。底座是<b>矩形</b>（宽 ≠ 深）、塔自身也不对称 ⇒ 必须自己按朝向加偏航。
 * 那套轴的定义就收在本接口的 {@link #rigAxisX()} / {@link #rigAxisZ()} /
 * {@link #rigYawDegrees()}，<b>渲染器和碰撞体都从这里取</b>
 * （{@code AbstractRigDynamicRender} 与 {@link RigCollision}），
 * 所以塔的视觉和它的碰撞箱永远是同步的。</p>
 *
 * <h2>轴是怎么推出来的（GTM 源码级）</h2>
 * <p>{@code FactoryBlockPattern.start()} 的三个轴固定是
 * {@code (char → LEFT, string → UP, aisle → FRONT)}；
 * {@code BlockPattern.setActualRelativeOffset} 里水平朝向时
 * {@code LEFT → facing.getCounterClockWise()}、{@code FRONT → facing}。
 * 底座的宽边在 char 轴上、深边在 aisle 轴上，所以塔的长轴取 {@code LEFT}、
 * 短轴取 {@code FRONT}。</p>
 * <p>{@code isFlipped} 在本 mod 的机器上恒为 false
 * （{@code checkPatternAt} 固定传 false），{@code allowExtendedFacing(false)}
 * 下 {@code getUpwardsFacing()} 恒为 {@code NORTH} —— 但这里仍然把这两个量
 * 原样传进 {@code getRelative}，不硬编码简化，免得将来定义一变就静默出错。</p>
 */
public interface RigController extends IMachineLife {

    // ------------------------------------------------------------------
    // 底座尺寸 —— 每台机器各自一套，默认值 = tower_rig 的 43 × 1 × 23
    //
    // 底座一律是「单层实心脱氧钢平板」：宽 = 左右半宽 × 2 + 1、深 = 前后半深 × 2 + 1，
    // 控制器嵌在正面边缘那张切片的中点。三台机器各自按自己的塔脚印取尺寸 ——
    // 取法固定为「塔的碰撞体在该轴上的最大外延 + 1 格」，那多出来的一圈既是
    // 可站立的走道，也是能点到控制器的通道（塔脚会把内圈铺满）。
    // ------------------------------------------------------------------

    /** 默认底座左右半宽（格）= tower_rig 的 43 格宽 ⇒ 半宽 21。 */
    int PLATE_HALF_LR = 21;

    /** 默认底座前后半深（格）= tower_rig 的 23 格深 ⇒ 半深 11。 */
    int PLATE_HALF_FB = 11;

    /** 默认底座边长（宽 / 深），仅用于文档与默认机器；实际以 {@link #rigPlateWidth()} 为准。 */
    int PLATE_WIDTH = PLATE_HALF_LR * 2 + 1;
    int PLATE_DEPTH = PLATE_HALF_FB * 2 + 1;

    /**
     * 本机底座的左右半宽（格）—— 宽 = 该值 × 2 + 1。
     *
     * <p>默认沿用 <b>tower_rig</b> 的 43 × 23；{@code arch_rig} / {@code mast_rig}
     * 各自覆写成自己塔脚印的尺寸（31 × 19 / 21 × 17）。</p>
     *
     * <p>⚠ 这个值与 pattern / shapeInfo 的生成器（{@code GTNMachines.rigPattern}）
     * 必须一致，否则结构成形时算出来的碰撞体原点会偏到底座外面。</p>
     */
    default int rigPlateHalfLr() {
        return PLATE_HALF_LR;
    }

    /** 本机底座的前后半深（格）—— 深 = 该值 × 2 + 1。 */
    default int rigPlateHalfFb() {
        return PLATE_HALF_FB;
    }

    /** 本机底座宽度（格）= 左右半宽 × 2 + 1。 */
    default int rigPlateWidth() {
        return rigPlateHalfLr() * 2 + 1;
    }

    /**
     * 本机底座深度（格）= 后方延伸 + 前方延伸 + 1。
     *
     * <p>⚠ 深度<b>不必是奇数</b>：深度是偶数时塔的模型原点落在两层之间，
     * 而碰撞体坐标表的格子是方块对齐的、没法落在半格上 ⇒ 那种情况下把控制器
     * 摆在底座靠后的那一排（{@code rigPlateBackExtent() = 0}），
     * 原点就等于控制器自己那一格（见 {@code maimai_rig} 的 1 × 2）。</p>
     */
    default int rigPlateDepth() {
        return rigPlateBackExtent() + rigPlateFrontExtent() + 1;
    }

    /**
     * 底座从控制器往 <b>BACK</b> 方向（底座内部）延伸的格数。
     *
     * <p>默认 {@code 2 × rigPlateHalfFb()} —— 与老行为完全一致（tower 22 / arch 18 /
     * mast 16 / drum 18 / blade 20），即整块平板都长在控制器背面，
     * 控制器本身就在底座的正面边缘上。</p>
     *
     * <p>⚠ 这个值必须是<b>偶数</b>：塔中心 = 控制器 + BACK × (该值 ÷ 2)，
     * 奇数会让塔中心落在半格上、与方块对齐的碰撞体错位。</p>
     */
    default int rigPlateBackExtent() {
        return rigPlateHalfFb() * 2;
    }

    /** 底座从控制器往 <b>FRONT</b> 方向（正面外侧）延伸的格数；默认 0（控制器贴正面边缘）。 */
    default int rigPlateFrontExtent() {
        return 0;
    }

    /** 底座占几层（老机器都是 1）。 */
    int PLATE_LAYERS = 1;

    /**
     * 本机的底座层数 —— <b>0 表示「无底座」</b>：机器只由控制器那一个方块构成，
     * 塔直接坐在方块下方的地面上（{@code lhc_combo} 用这个）。
     *
     * <p>无底座时：pattern 退化成 1 × 1 × 1 的控制器本身、塔底面贴控制器方块
     * <b>底面</b>（= 地面）、碰撞体坐标表的原点格就是控制器那一格。</p>
     */
    default int rigPlateLayers() {
        return PLATE_LAYERS;
    }

    /**
     * 塔底面（= 底座顶面）相对控制器方块锚点的局部高度（格）。
     *
     * <p>控制器在底座第 0 层 ⇒ 底座占满方块局部 y 0..1，所以塔底面贴 y=1
     * （正好坐在底座顶面上，不会陷进去也不会浮空）；
     * 无底座（{@link #rigPlateLayers()} = 0）时贴 y=0，即坐在控制器方块下面的地面上。</p>
     */
    default double rigBaseY() {
        return rigPlateLayers();
    }

    /**
     * 碰撞体坐标表原点格相对控制器的竖直偏移（格）。
     *
     * <p>坐标表的 (0,0,0) 是「模型原点正下方那一格」= 底座顶面上方那一格
     * （无底座时 = 控制器自己那一格）。</p>
     */
    default int rigCollisionOriginYOffset() {
        return rigPlateLayers();
    }

    /**
     * 塔的渲染视距（格）—— 超过这个距离整座塔不再绘制。
     *
     * <p>默认 256（塔最高约 66 格）。{@code lhc_combo} 那种直径 200 格的巨型结构
     * 要覆写成更大的值，否则站远一点整座环线就消失了。</p>
     */
    default int rigViewDistance() {
        return 256;
    }

    // ------------------------------------------------------------------
    // 宿主与资源
    // ------------------------------------------------------------------

    /**
     * 宿主机器本体。
     *
     * <p>⚠ 实现类必须同时是 {@link MultiblockControllerMachine}（见接口文档）。</p>
     */
    default MultiblockControllerMachine rigMachine() {
        return (MultiblockControllerMachine) (Object) this;
    }

    /**
     * 本机碰撞体的坐标表路径（classpath 绝对路径），
     * 例如 {@code /assets/gtoecore/structures/arch_rig_collision.txt}。
     *
     * <p>由 {@code tools/gen_tower_collision.py} 离线体素化生成，参数必须与
     * 渲染器的 {@code SCALE} 一致。</p>
     */
    String rigCollisionTable();

    // ------------------------------------------------------------------
    // 塔的姿态 —— 渲染器与碰撞体唯一的换算来源
    // ------------------------------------------------------------------

    /**
     * 塔局部 <b>+x 轴</b>（长轴，对应底座宽边那条）。
     *
     * @param frontFacing   机器正面朝向
     * @param upwardsFacing 机器上朝向（本 mod 恒为 NORTH）
     * @param flipped       是否翻转（本 mod 恒为 false）
     */
    static Direction rigAxisX(Direction frontFacing, Direction upwardsFacing, boolean flipped) {
        return RelativeDirection.LEFT.getRelative(frontFacing, upwardsFacing, flipped);
    }

    /** 塔局部 <b>+z 轴</b>（短轴，对应底座深边那条）。 */
    static Direction rigAxisZ(Direction frontFacing, Direction upwardsFacing, boolean flipped) {
        return RelativeDirection.FRONT.getRelative(frontFacing, upwardsFacing, flipped);
    }

    /**
     * 塔的姿态偏航角（度）—— 让模型的局部 +x 轴指向 {@code axisX}。
     *
     * <p>{@code Axis.YP.rotationDegrees(yaw)} 作用在 (1,0,0) 上得到
     * {@code (cos yaw, 0, -sin yaw)}，令它等于 {@code axisX} 的水平分量即可，
     * 所以 {@code yaw = atan2(-axisX.z, axisX.x)}。四个水平朝向分别得到
     * 0° / 90° / 180° / -90°，四种情况都是精确值。</p>
     */
    static float rigYawDegrees(Direction axisX) {
        return (float) Math.toDegrees(Math.atan2(-axisX.getStepZ(), axisX.getStepX()));
    }

    /** 本机当前的塔 +x 轴方向。 */
    default Direction rigAxisX() {
        MultiblockControllerMachine m = rigMachine();
        return rigAxisX(m.getFrontFacing(), m.getUpwardsFacing(), m.isFlipped());
    }

    /** 本机当前的塔 +z 轴方向。 */
    default Direction rigAxisZ() {
        MultiblockControllerMachine m = rigMachine();
        return rigAxisZ(m.getFrontFacing(), m.getUpwardsFacing(), m.isFlipped());
    }

    /** 本机当前的姿态偏航角（度）。 */
    default float rigYawDegrees() {
        return rigYawDegrees(rigAxisX());
    }

    /**
     * 碰撞体坐标表的原点格 = 控制器 + BACK 方向 × {@link #rigPlateHalfFb()}
     * + 竖直 {@link #COLLISION_ORIGIN_Y_OFFSET} 格。
     *
     * <p>就是「底座中心正上方那一格」—— 模型原点正好落在它的底面中心
     * ({@code 0.5, 0, 0.5})。渲染器 translate 到的也是同一个点，
     * 所以塔的视觉和碰撞箱共享同一个锚。</p>
     */
    default BlockPos rigCollisionOrigin() {
        MultiblockControllerMachine m = rigMachine();
        Direction back = RelativeDirection.BACK.getRelative(
                m.getFrontFacing(), m.getUpwardsFacing(), m.isFlipped());
        int centerBackOffset = rigPlateHalfFb();
        return m.getPos().offset(
                back.getStepX() * centerBackOffset,
                rigCollisionOriginYOffset(),
                back.getStepZ() * centerBackOffset);
    }

    // ------------------------------------------------------------------
    // 碰撞体生命周期
    //
    // 机器的覆写体只需两行转发：
    //   onStructureFormed → super.onStructureFormed(); rigOnFormed();
    //   onStructureInvalid → super.onStructureInvalid(); rigOnInvalid();
    //
    // onMachineRemoved 由本接口给默认实现（收回碰撞体），一般不用再覆写。
    // 定期补块不在这里 —— 那由 RigCollision 自己的活跃表驱动（见其类注释）。
    // ------------------------------------------------------------------

    /** 结构成形：把整片碰撞体铺出来（分帧，约 1 秒铺完）。 */
    default void rigOnFormed() {
        MultiblockControllerMachine m = rigMachine();
        if (m.getLevel() instanceof ServerLevel level) {
            RigCollision.start(level, rigCollisionOrigin(), rigAxisX(), rigAxisZ(), rigCollisionTable());
        }
    }

    /** 结构散架 / 换朝向：立刻把碰撞体收回（只清本 mod 的方块，别的都不动）。 */
    default void rigOnInvalid() {
        MultiblockControllerMachine m = rigMachine();
        if (m.getLevel() instanceof ServerLevel level) {
            RigCollision.clear(level, rigCollisionOrigin(), rigAxisX(), rigAxisZ(), rigCollisionTable());
        }
    }

    /**
     * 控制器方块本身被拆掉时收回碰撞体。
     *
     * <p>必须单独挂这个钩子：控制器没了，它的方块实体也就没了，
     * {@code onStructureInvalid} 未必还会被调到 —— 而那时整片隐形碰撞体
     * （几千格）会永久留在世界里，玩家既挖不动又看不见。
     * {@code MetaMachineBlock.onRemove} 会在方块真被移除时调到这里。</p>
     */
    @Override
    default void onMachineRemoved() {
        rigOnInvalid();
    }
}
