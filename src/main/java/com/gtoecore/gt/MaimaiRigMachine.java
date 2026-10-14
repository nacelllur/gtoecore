package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 舞萌（maimai）街机柜 —— 家用机尺寸的「立式街机」占位机器。
 *
 * <p>{@code maimai_rig.obj} 整机 2.13 × 1.66 × 2.21（Blender Z-up），
 * SCALE = 4.45 ⇒ 世界里约 <b>9.5 宽 × 7.4 厚 × 9.8 高</b> 格。</p>
 *
 * <h2>底座 1 × 2（用户指定，2026-10-13）</h2>
 * <p>这台是唯一<b>不按「碰撞体外延 + 一圈」</b>取底座的机器 —— 用户要求底座缩到
 * <b>1 宽 × 2 深</b>，也就是「控制器本身 + 正面外侧 1 格」。理由很直接：
 * 街机柜应该是贴着地放的一件家具，而不是踩在一大片脱氧钢平台上。</p>
 *
 * <p>深度 2 是<b>偶数</b>，所以塔中心落在两格之间 —— 而碰撞体坐标表的格子是方块对齐的、
 * 没法落在半格上。解法：把控制器摆在底座<b>靠后</b>那一排
 * （{@code rigPlateBackExtent() = 0}，即塔中心 = 控制器自己那一格），
 * 底座往 <b>正面</b> 多伸 1 格当踏脚。</p>
 *
 * <h2>碰撞体仍然是整机尺寸</h2>
 * <p>底座只有 2 格，但碰撞表是照 9.5 × 7.4 × 9.8 的整机体素化的（385 格）：
 * 柜体会一直铺到控制器左右各 5 格、前后 4/3 格、高 10 格。所以
 * <b>底座小 ≠ 碰撞小</b> —— 柜子该挡人的地方照样挡，
 * 只是脚下那块「平台」没了，站着往里看只有柜体本身。</p>
 */
public class MaimaiRigMachine extends AbstractRigMachine {

    /** 本机塔的碰撞体坐标表（385 格；工具 {@code tools/gen_tower_collision.py}）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/maimai_rig_collision.txt";

    /**
     * 底座左右半宽（格）⇒ 宽 1（只有控制器自己那一列）。
     *
     * <p>⚠ 与 {@code rigPlateBackExtent()} / {@code rigPlateFrontExtent()} 配合使用；
     * 见类注释「底座 1 × 2」。</p>
     */
    public static final int PLATE_HALF_LR = 0;

    /**
     * 控制器到塔中心的距离（格）= 0 ⇒ 塔中心就是控制器那一格。
     *
     * <p>因为底座深度是偶数 2（后半深 0 + 前半深 1 + 控制器 1），
     * 塔中心只能落在其中一排上；这里选「控制器自己那一排」，
     * 于是渲染器和碰撞体都不需要半格偏移。</p>
     */
    public static final int PLATE_HALF_FB = 0;

    /** 底座往正面（控制器外侧）伸出的格数 ⇒ 总深 = 0 + 1 + 1 = 2。 */
    public static final int PLATE_FRONT_EXTENT = 1;

    public MaimaiRigMachine(IMachineBlockEntity holder, Object... args) {
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

    @Override
    public int rigPlateBackExtent() {
        return 0;
    }

    @Override
    public int rigPlateFrontExtent() {
        return PLATE_FRONT_EXTENT;
    }
}
