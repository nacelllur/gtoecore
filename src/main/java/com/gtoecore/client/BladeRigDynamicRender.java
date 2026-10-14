package com.gtoecore.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.BladeRigMachine;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;

/**
 * 叶片架（{@code blade_rig}）的视觉 —— 把 {@code blade_rig.obj} 画在
 * 13 × 1 × 23 单层脱氧钢底座之上。
 *
 * <p>落座、姿态偏航、包围盒、视距、诊断日志全部来自
 * {@link AbstractRigDynamicRender}；本类只提供「用哪个模型、放大多少倍」。
 * 模型里没有需要独立旋转的部件，所以<b>整座塔是静态绘制的</b>
 * （{@code tower_rig} 有 4 级涡轮要转，本机没有）。</p>
 */
public class BladeRigDynamicRender
        extends AbstractRigDynamicRender<BladeRigMachine, BladeRigDynamicRender> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<BladeRigDynamicRender> CODEC = Codec.unit(BladeRigDynamicRender::new);
    public static final DynamicRenderType<BladeRigMachine, BladeRigDynamicRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/blade_rig.obj";

    /**
     * 模型自身比例缩放（等比例）⇒ 约 <b>10.7 宽 × 26.8 高 × 18.8 厚</b>格。
     *
     * <p>⚠ 改它必须用同样的 {@code --scale} 重烤碰撞表
     * （{@code assets/gtoecore/structures/blade_rig_collision.txt}）。</p>
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
     * {@code "dynamic_renders": [{"type": "gtoecore:blade_rig"}]}
     * 一旦被解析就会走这里；看到它就说明 JSON 那条声明生效了。
     */
    public BladeRigDynamicRender() {
        LOGGER.info("[gtoecore] blade_rig DynamicRender INSTANTIATED "
                + "(model json \"dynamic_renders\" was decoded successfully)");
    }

    @Override
    public DynamicRenderType<BladeRigMachine, BladeRigDynamicRender> getType() {
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
