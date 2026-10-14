package com.gtoecore.client;

import com.gtoecore.GTNBlocks;
import com.gtoecore.GTNCore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 全周天舱壁的客户端注册（BER）。整类仅在 CLIENT 侧加载。 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HullViewportClient {

    private HullViewportClient() {}

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GTNBlocks.HULL_VIEWPORT_BE.get(), ctx -> new HullViewportRenderer());
    }
}
