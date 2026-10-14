package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 桅杆（Mast Rig）—— 站在 <b>21 × 1 × 17</b> 单层脱氧钢底座上的「桅杆式」动力塔。
 *
 * <p>{@code mast_rig.obj} 的造型是一座方柜基座 + 中间桁架平台 + 细高桅杆
 * （顶部带天线），立起来后约 <b>17.3 宽 × 32.9 高</b> 格
 * （模型自身 Z-up 3.877 × 2.915 × 7.3925，SCALE = 4.45）。</p>
 *
 * <p>本机是<b>纯结构 + 外观</b>：能成形、能长出塔、能站上去，但不接配方逻辑。
 * 塔的姿态换算、碰撞体的铺/收全部来自 {@link RigController}（与 {@code tower_rig}
 * 完全同一套），只有底座尺寸是自己的一套 —— 见下。</p>
 *
 * <h2>底座 21 × 17 是怎么来的</h2>
 * <p>取法 = 「塔的碰撞体在每个轴上的最大外延 + 1 格」，多出来的一圈是可站立走道
 * （塔脚在贴地那层是铺满的，底座与脚印等大就会被碰满、站不上去也点不到控制器）。</p>
 * <ul>
 *   <li>左右：碰撞表 {@code dx ∈ [-9, 8]} ⇒ 半宽 9 + 1 = <b>10</b>，宽 21。</li>
 *   <li>前后：碰撞表 {@code dz ∈ [-7, 5]} ⇒ 半深 7 + 1 = <b>8</b>，深 17。</li>
 * </ul>
 *
 * <h2>2026-10-12 外形修正：删掉平台外侧那圈悬空栏杆</h2>
 * <p>原 {@code mast_rig.obj} 里有一组 {@code mcw_re_*}（6 根立柱 + 2 根横杆，48 个面）
 * 被摆到了桁架平台<b>外面约 1.9 格</b>的地方 —— 平台地板 {@code mcw_plate} 的 z 只到
 * −5.37，而它悬在 z = −7.31，下方完全没有几何，在游戏里就是一截凭空飘着的护栏。
 * 它的碰撞体也因此成了整机里唯一一块<b>不接地的独立连通体</b>（38 格）。</p>
 * <p>用 {@code tools/drop_obj_groups.py} 整块移除后：碰撞表从 2235 格降到
 * <b>2197 格</b>、连通块数从 2 回到 <b>1</b>，整机 z 外延由 8 格收到 7 格
 * ⇒ 底座跟着从 21 × 19 收到 <b>21 × 17</b>（规则不变，仍然每边留一圈走道）。</p>
 */
public class MastRigMachine extends AbstractRigMachine {

    /** 本机塔的碰撞体坐标表（2197 格；工具 {@code tools/gen_tower_collision.py}）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/mast_rig_collision.txt";

    /**
     * 底座左右半宽（格）⇒ 宽 21。
     *
     * <p>{@code GTNMachines} 注册 pattern / shapeInfo 时读这个常量，
     * 运行时渲染器与碰撞体读 {@link #rigPlateHalfLr()} —— 两者必须一致。</p>
     */
    public static final int PLATE_HALF_LR = 10;

    /** 底座前后半深（格）⇒ 深 17。 */
    public static final int PLATE_HALF_FB = 8;

    public MastRigMachine(IMachineBlockEntity holder, Object... args) {
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
