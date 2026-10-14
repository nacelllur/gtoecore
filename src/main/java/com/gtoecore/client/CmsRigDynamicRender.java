package com.gtoecore.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.CmsRigMachine;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;

/**
 * CMS 探测器（{@code cms_rig}）的视觉 —— 把 {@code cms_rig.obj} 画在
 * 115 × 1 × 77 单层脱氧钢底座之上。
 *
 * <p>落座、姿态偏航、包围盒、视距、诊断日志全部来自
 * {@link AbstractRigDynamicRender}；本类只提供「用哪个模型、放大多少倍」。
 * 模型 22,410 面、无独立可动部件 ⇒ 整机静态烘 VBO（一次烘焙，之后只有 2 个 draw call）。</p>
 */
public class CmsRigDynamicRender
        extends AbstractRigDynamicRender<CmsRigMachine, CmsRigDynamicRender> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<CmsRigDynamicRender> CODEC = Codec.unit(CmsRigDynamicRender::new);
    public static final DynamicRenderType<CmsRigMachine, CmsRigDynamicRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/cms_rig.obj";

    /**
     * 模型自身比例缩放（等比例）⇒ 约 <b>112 宽 × 84 高 × 74 厚</b>格。
     *
     * <p>⚠ 改它必须用同样的 {@code --scale} 重烤碰撞表，然后<b>再跑一次</b>
     * {@code tools/optimize_rig_collision.py}（表是「填腔 + 单层壳」，不是原始体素）。</p>
     */
    private static final float SCALE = 4.45F;

    /** 塔相对机器朝向的额外偏航角（度）。 */
    private static final float YAW_OFFSET = 0.0F;

    public CmsRigDynamicRender() {
        LOGGER.info("[gtoecore] cms_rig DynamicRender INSTANTIATED "
                + "(model json \"dynamic_renders\" was decoded successfully)");
    }

    @Override
    public DynamicRenderType<CmsRigMachine, CmsRigDynamicRender> getType() {
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
