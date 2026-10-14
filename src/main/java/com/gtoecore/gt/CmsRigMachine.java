package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * CMS 探测器（Compact Muon Solenoid）—— LHC 四大实验点里最大的那台通用探测器。
 *
 * <p>{@code cms_rig.obj} 整机 25.24 × 16.70 × 18.85（Blender Z-up），
 * SCALE = 4.45 ⇒ 世界里约 <b>112 宽 × 74 深 × 84 高</b> 格。
 * 造型从内到外：束流管 → 内部径迹探测器 → 电磁量能器 → 超导螺线管（蓝色）→
 * 13,000 t 的 μ 子桶（红色端部花冠）→ 两侧 cradle 支架与线缆。</p>
 *
 * <h2>底座 115 × 77（「老样子」，2026-10-13 用户确认）</h2>
 * <p>取法与其他机架一致 = 「碰撞体在该轴的最大外延 + 1 格」：
 * dx ∈ [-56, 56] ⇒ 半宽 57；dz ∈ [-37, 37] ⇒ 半深 38。
 * 走道那一圈是唯一能站到控制器跟前的位置 —— 检测器本体的贴地层几乎铺满。</p>
 *
 * <h2>⚠ 碰撞体是「单层壳」（95,061 格，原始体素 142,752）</h2>
 * <p>探测器内部（量能器环、螺线管内腔、线缆管路）玩家永远进不去，所以坐标表
 * 已经用 {@code tools/optimize_rig_collision.py} 做过「填腔 + 取一层壳」：
 * 只保留外轮廓那层方块，内部 4 万格实心与 8.8 万格内层细节全部丢弃。
 * 游戏手感不变（撞得到、站得上、进不去），但世界里的隐形方块少掉 1/3。</p>
 *
 * <p>⚠ 因此<b>别再用 {@code --cell 1} 原始表覆盖这张表</b>；重新体素化后要
 * 再跑一次优化工具，否则会退回 14 万格。</p>
 */
public class CmsRigMachine extends AbstractRigMachine {

    /** 本机塔的碰撞体坐标表（95,061 格，已做「填腔 + 取壳」优化）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/cms_rig_collision.txt";

    /** 底座左右半宽（格）⇒ 宽 115。 */
    public static final int PLATE_HALF_LR = 57;

    /** 底座前后半深（格）⇒ 深 77。 */
    public static final int PLATE_HALF_FB = 38;

    public CmsRigMachine(IMachineBlockEntity holder, Object... args) {
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
