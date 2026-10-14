package com.gtoecore.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.MastRigMachine;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;

/**
 * 桅杆（{@code mast_rig}）的视觉 —— 把 {@code mast_rig.obj} 画在
 * 21 × 1 × 19 单层脱氧钢底座之上。
 *
 * <p>落座、姿态偏航、包围盒、视距、诊断日志全部来自
 * {@link AbstractRigDynamicRender}；本类只提供「用哪个模型、放大多少倍」。
 * 模型里没有需要独立旋转的部件，所以<b>整座塔是静态绘制的</b>。</p>
 */
public class MastRigDynamicRender
        extends AbstractRigDynamicRender<MastRigMachine, MastRigDynamicRender> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<MastRigDynamicRender> CODEC = Codec.unit(MastRigDynamicRender::new);
    public static final DynamicRenderType<MastRigMachine, MastRigDynamicRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/mast_rig.obj";

    /**
     * 模型自身比例缩放（等比例）⇒ 约 <b>17.3 宽 × 32.9 高 × 13.0 厚</b>格。
     *
     * <p>⚠ 改它必须用同样的 {@code --scale} 重烤碰撞表
     * （{@code assets/gtoecore/structures/mast_rig_collision.txt}）。</p>
     */
    private static final float SCALE = 4.45F;

    /**
     * 塔相对机器朝向的<b>额外</b>偏航角（度）。
     *
     * <p>发现塔「正面朝里」时改成 180 即可转半圈，<b>碰撞体会跟着一起转</b>。</p>
     */
    private static final float YAW_OFFSET = 0.0F;

    /**
     * 构造器日志 —— 模型 JSON 里的
     * {@code "dynamic_renders": [{"type": "gtoecore:mast_rig"}]}
     * 一旦被解析就会走这里；看到它就说明 JSON 那条声明生效了。
     */
    public MastRigDynamicRender() {
        LOGGER.info("[gtoecore] mast_rig DynamicRender INSTANTIATED "
                + "(model json \"dynamic_renders\" was decoded successfully)");
    }

    @Override
    public DynamicRenderType<MastRigMachine, MastRigDynamicRender> getType() {
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
