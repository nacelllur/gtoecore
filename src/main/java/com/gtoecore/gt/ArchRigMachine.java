package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 拱架（Arch Rig）—— 站在 <b>31 × 1 × 19</b> 单层脱氧钢底座上的「拱形门架式」动力塔。
 *
 * <p>{@code arch_rig.obj} 的造型是一座方柜基座 + 带大量散热鳍片的柱身 +
 * 顶部青色方箱，立起来后约 <b>24.2 宽 × 33.6 高 × 14.8 厚</b> 格
 * （模型自身 Z-up 5.44 × 3.315 × 7.56，SCALE = 4.45）。</p>
 *
 * <p>本机是<b>纯结构 + 外观</b>：能成形、能长出塔、能站上去，但不接配方逻辑。
 * 塔的姿态换算、碰撞体的铺/收全部来自 {@link RigController}（与 {@code tower_rig}
 * 完全同一套），只有底座尺寸是自己的一套 —— 见下。</p>
 *
 * <h2>底座 31 × 19 是怎么来的</h2>
 * <p>取法 = 「塔的碰撞体在每个轴上的最大外延 + 1 格」，多出来的一圈是可站立走道
 * （塔脚在贴地那层是铺满的，底座与脚印等大就会被碰满、站不上去也点不到控制器）。</p>
 * <ul>
 *   <li>左右：碰撞表 {@code dx ∈ [-14, 10]}（上部悬挑到 -14）⇒ 半宽 14 + 1 = <b>15</b>，宽 31。</li>
 *   <li>前后：碰撞表 {@code dz ∈ [-7, 8]} ⇒ 半深 8 + 1 = <b>9</b>，深 19。</li>
 * </ul>
 * <p>⚠ 不要按「贴地两层」取（那只有 21 × 14）—— 上方悬挑会探出底盘边缘，
 * 看着像塔浮在盘外。用户 2026-10-12 拍板取「含悬挑 + 一圈」。</p>
 */
public class ArchRigMachine extends AbstractRigMachine {

    /** 本机塔的碰撞体坐标表（2872 格；工具 {@code tools/gen_tower_collision.py}）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/arch_rig_collision.txt";

    /**
     * 底座左右半宽（格）⇒ 宽 31。
     *
     * <p>{@code GTNMachines} 注册 pattern / shapeInfo 时读这个常量，
     * 运行时渲染器与碰撞体读 {@link #rigPlateHalfLr()} —— 两者必须一致。</p>
     */
    public static final int PLATE_HALF_LR = 15;

    /** 底座前后半深（格）⇒ 深 19。 */
    public static final int PLATE_HALF_FB = 9;

    public ArchRigMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
    }

    @Override
    public String rigCollisionTable() {
        return COLLISION_TABLE;
    }

    @Override
    public int rigPlateHalfLr() {
        return PLATE_HALF_LR;
    }

    @Override
    public int rigPlateHalfFb() {
        return PLATE_HALF_FB;
    }
}
