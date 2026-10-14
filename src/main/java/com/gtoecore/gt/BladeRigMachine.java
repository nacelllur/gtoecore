package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 叶片架（Blade Rig）—— 站在 <b>13 × 1 × 23</b> 单层脱氧钢底座上的「立式叶片轮」动力塔。
 *
 * <p>{@code blade_rig.obj} 的造型是一座细长立式机架 + 顶部大型叶片轮（带白色发光轮毂），
 * 立起来后约 <b>10.7 宽 × 26.8 高 × 18.8 厚</b> 格
 * （模型自身 Z-up 2.400 × 4.220 × 6.021，SCALE = 4.45）。</p>
 *
 * <p>本机是<b>纯结构 + 外观</b>：能成形、能长出塔、能站上去，但不接配方逻辑。
 * 塔的姿态换算、碰撞体的铺/收全部来自 {@link RigController}（与 {@code tower_rig}
 * 完全同一套），只有底座尺寸是自己的一套 —— 见下。</p>
 *
 * <h2>底座 13 × 23 是怎么来的</h2>
 * <p>取法 = 「塔的碰撞体在每个轴上的最大外延 + 1 格」，多出来的一圈是可站立走道
 * （塔脚在贴地那层是铺满的，底座与脚印等大就会被碰满、站不上去也点不到控制器）。</p>
 * <ul>
 *   <li>左右：碰撞表 {@code dx ∈ [-5, 5]} ⇒ 半宽 5 + 1 = <b>6</b>，宽 13。</li>
 *   <li>前后：碰撞表 {@code dz ∈ [-9, 10]} ⇒ 半深 10 + 1 = <b>11</b>，深 23。</li>
 * </ul>
 * <p>碰撞体 2917 格，最高 26 层。</p>
 */
public class BladeRigMachine extends AbstractRigMachine {

    /** 本机塔的碰撞体坐标表（2917 格；工具 {@code tools/gen_tower_collision.py}）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/blade_rig_collision.txt";

    /**
     * 底座左右半宽（格）⇒ 宽 13。
     *
     * <p>{@code GTNMachines} 注册 pattern / shapeInfo 时读这个常量，
     * 运行时渲染器与碰撞体读 {@link #rigPlateHalfLr()} —— 两者必须一致。</p>
     */
    public static final int PLATE_HALF_LR = 6;

    /** 底座前后半深（格）⇒ 深 23。 */
    public static final int PLATE_HALF_FB = 11;

    public BladeRigMachine(IMachineBlockEntity holder, Object... args) {
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
