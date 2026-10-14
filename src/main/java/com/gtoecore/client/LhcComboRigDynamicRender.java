package com.gtoecore.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.LhcComboRigMachine;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;

/**
 * LHC 环线（{@code lhc_combo}）的视觉 —— 本 mod 体积最大的 {@code DynamicRender}：
 * 58,512 面、203 × 203 × 45 格、横跨 13 × 13 个区块。
 *
 * <p>落座、姿态偏航、包围盒、视距、诊断日志全部来自
 * {@link AbstractRigDynamicRender}。本机<b>没有底座</b>
 * （{@link LhcComboRigMachine#rigPlateLayers()} = 0），所以模型原点直接贴地面；
 * 视距由 {@link LhcComboRigMachine#rigViewDistance()} 放宽到 512 格。</p>
 *
 * <p>⚠ 整个模型一次性烘成 VBO（约 5.9 万面 / 23 万顶点 ≈ 7.5 MB 显存），
 * 之后每帧只有 2 个 draw call。烘焙发生在首次进入视距时，会有一次几十毫秒的卡顿。</p>
 */
public class LhcComboRigDynamicRender
        extends AbstractRigDynamicRender<LhcComboRigMachine, LhcComboRigDynamicRender> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<LhcComboRigDynamicRender> CODEC =
            Codec.unit(LhcComboRigDynamicRender::new);
    public static final DynamicRenderType<LhcComboRigMachine, LhcComboRigDynamicRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/lhc_combo.obj";

    /**
     * 模型自身比例缩放（等比例）⇒ 约 <b>203 × 203 × 45</b> 格。
     *
     * <p>⚠ 改它必须用同样的 {@code --scale} 重烤碰撞表，然后<b>再跑一次</b>
     * {@code tools/optimize_rig_collision.py}（表是「填腔 + 单层壳」，不是原始体素）。</p>
     */
    private static final float SCALE = 4.45F;

    /** 塔相对机器朝向的额外偏航角（度）。 */
    private static final float YAW_OFFSET = 0.0F;

    public LhcComboRigDynamicRender() {
        LOGGER.info("[gtoecore] lhc_combo DynamicRender INSTANTIATED "
                + "(model json \"dynamic_renders\" was decoded successfully)");
    }

    @Override
    public DynamicRenderType<LhcComboRigMachine, LhcComboRigDynamicRender> getType() {
        return TYPE;
    }

    @Override
    protected String rigObjPath() {
        return OBJ_PATH;
    }

    @Override
    protected float rigScale() {
        return SCALE;
    }

    @Override
    protected float rigYawOffset() {
        return YAW_OFFSET;
    }
}
