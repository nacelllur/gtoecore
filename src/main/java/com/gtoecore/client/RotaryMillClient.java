package com.gtoecore.client;

import com.gtoecore.GTNBlocks;
import com.gtoecore.GTNCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 旋转石磨客户端注册：额外模型（旋转磨盘）+ 方块实体渲染器（BER）。
 *
 * <p>gtoecore 目前唯一的自定义渲染：磨盘由 BER 叠加绘制并绕 Y 轴旋转，
 * 底座仍走 blockstate 普通模型。整类仅在 CLIENT 侧加载。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RotaryMillClient {

    /** 磨盘独立模型（非 blockstate 引用，须经 ModelEvent.RegisterAdditional 注册才会被加载） */
    public static final ResourceLocation WHEEL_MODEL =
            new ResourceLocation(GTNCore.MODID, "rotary_mill_wheel");

    private RotaryMillClient() {}

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(WHEEL_MODEL);
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GTNBlocks.ROTARY_MILL_BE.get(), RotaryMillRenderer::new);
    }
}