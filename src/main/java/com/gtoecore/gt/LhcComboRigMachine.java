package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * LHC 环线（Large Hadron Collider，27 km 对撞机鸟瞰模型）—— 本 mod 体积最大的占位机器。
 *
 * <p>{@code lhc_combo.obj} 新版尺寸 79.0 × 120.85 × 9.38（Blender Z-up），
 * SCALE = 4.45 ⇒ 世界里约 <b>352 × 42 × 538</b> 格，横跨 22 × 34 个区块。
 * 内容：环形隧道 + 4 个实验点（ATLAS / CMS / ALICE / LHCb）+ SPS 小环 + 转移线 +
 * 地表建筑，底下是模型自带的一整块方形地板（{@code Base_L} 材质，
 * 抬高约 4 格、由支腿撑住）。</p>
 *
 * <h2>⚠ 这台<b>没有底座</b>（用户指定，2026-10-13）</h2>
 * <p>{@code rigPlateLayers()} = 0 ⇒</p>
 * <ul>
 *   <li>pattern 退化成 <b>1 × 1 × 1</b>：机器就是控制器那一个方块，放下去即成形。</li>
 *   <li>塔底面贴<b>控制器方块的底面</b>（{@code rigBaseY()} = 0），即直接坐在地面上
 *       —— 环线的支腿和地板边缘本来就该落在地上，不是坐在钢板上。</li>
 *   <li>碰撞体坐标表的原点格 = 控制器自己那一格。</li>
 * </ul>
 * <p>玩家可以走到模型自带的地板<b>下面</b>（离地 4 格，站得进去），
 * 控制器就在那层架空层里 —— 右键它即可看到结构状态。地板顶面的走道
 * 由碰撞表提供，可以站上去绕环线走一圈。</p>
 *
 * <h2>⚠ 碰撞体是「单层壳」（334,848 格，原始体素 435,677）</h2>
 * <p>与 {@link CmsRigMachine} 同一套优化：填掉封闭内腔（隧道内孔等）后只保留
 * 外轮廓一层方块。这一台的地板本身就是 1 格厚的薄板，
 * 是碰撞体里最大的一笔开销 —— 如果实测太重，下一步可以把底板从碰撞表里
 * 整体剔除（{@code tools/drop_obj_groups.py} 删掉 {@code Base_L/Base_D} 组后重烤）。</p>
 *
 * <h2>视距</h2>
 * <p>默认 256 格的渲染视距对 203 格直径的环线太短（走远一点整座环就消失），
 * 这里放宽到 <b>512</b>（{@link #rigViewDistance()}）。</p>
 */
public class LhcComboRigMachine extends AbstractRigMachine {

    /** 本机塔的碰撞体坐标表（334,848 格，已做「填腔 + 取壳」优化）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/lhc_combo_collision.txt";

    /** 无底座 ⇒ 没有半宽。 */
    public static final int PLATE_HALF_LR = 0;

    /** 无底座 ⇒ 控制器就是塔中心。 */
    public static final int PLATE_HALF_FB = 0;

    /** 本机的渲染视距（格）—— 巨构要放宽，默认 256 对 203 格直径的环线太短。 */
    public static final int VIEW_DISTANCE = 512;

    public LhcComboRigMachine(IMachineBlockEntity holder, Object... args) {
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

    /** 无底座：pattern 退化为控制器单格，塔直接坐地。 */
    @Override
    public int rigPlateLayers() {
        return 0;
    }

    @Override
    public int rigViewDistance() {
        return VIEW_DISTANCE;
    }
}
