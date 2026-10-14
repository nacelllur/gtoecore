package com.gtoecore.client;

import com.gtoecore.GTNCore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第三方 mod（ad_astra）实体的渲染接管 —— 目前只有一级火箭。
 *
 * <p>挂在 {@code EntityRenderersEvent.RegisterRenderers} 上，靠 mods.toml 里对
 * ad_astra 的 {@code ordering="AFTER"} 保证「后注册覆盖先注册」。
 * 具体替换逻辑见 {@link RocketRigRenderer#registerIfLoaded}。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketRigClient {

    private RocketRigClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RocketRigRenderer.registerIfLoaded(event);
    }
}
