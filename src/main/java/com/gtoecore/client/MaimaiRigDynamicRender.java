package com.gtoecore.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.MaimaiRigMachine;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;

/**
 * 舞萌街机柜（{@code maimai_rig}）的视觉 —— 把 {@code maimai_rig.obj} 画在
 * <b>1 × 1 × 2</b> 的小底座之上（底座尺寸见 {@link MaimaiRigMachine}）。
 *
 * <p>落座、姿态偏航、包围盒、视距、诊断日志全部来自
 * {@link AbstractRigDynamicRender}；本类只提供「用哪个模型、放大多少倍」。
 * 模型里没有可动部件，整机静态绘制（走 VBO）。</p>
 */
public class MaimaiRigDynamicRender
        extends AbstractRigDynamicRender<MaimaiRigMachine, MaimaiRigDynamicRender> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<MaimaiRigDynamicRender> CODEC = Codec.unit(MaimaiRigDynamicRender::new);
    public static final DynamicRenderType<MaimaiRigMachine, MaimaiRigDynamicRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/maimai_rig.obj";

    /**
     * 模型自身比例缩放（等比例）⇒ 约 <b>9.5 宽 × 9.8 高 × 7.4 厚</b>格。
     *
     * <p>⚠ 改它必须用同样的 {@code --scale} 重烤碰撞表
     * （{@code assets/gtoecore/structures/maimai_rig_collision.txt}）。</p>
     */
    private static final float SCALE = 4.45F;

    /** 塔相对机器朝向的额外偏航角（度）；发现柜子背面朝外时改成 180 即可，碰撞体会跟着转。 */
    private static final float YAW_OFFSET = 0.0F;

    public MaimaiRigDynamicRender() {
        LOGGER.info("[gtoecore] maimai_rig DynamicRender INSTANTIATED "
                + "(model json \"dynamic_renders\" was decoded successfully)");
    }

    @Override
    public DynamicRenderType<MaimaiRigMachine, MaimaiRigDynamicRender> getType() {
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
