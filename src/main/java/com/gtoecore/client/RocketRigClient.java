package com.gtoecore.client;

import com.gtoecore.GTNCore;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 第三方 mod（ad_astra）实体的渲染接管 —— 目前只有一级火箭。
 *
 * <p>挂在 {@code EntityRenderersEvent.RegisterRenderers} 上，靠 mods.toml 里对
 * ad_astra 的 {@code ordering="AFTER"} 保证「后注册覆盖先注册」。
 * 具体替换逻辑见 {@link RocketRigRenderer#registerIfLoaded}。</p>
 *
 * <p>另外挂一个 {@code AddLayers} 诊断：那个事件在 {@code EntityRenderers}
 * <b>已经建表之后</b>触发，反射读出最终表里 {@code tier_1_rocket} 用的是哪个渲染器类
 * —— 一行日志就能判定覆盖到底成没成（免得靠猜）。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketRigClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RocketRigClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RocketRigRenderer.registerIfLoaded(event);
    }

    /**
     * 覆盖结果自检（只在有 ad_astra 时打一行）。
     *
     * <p>{@code AddLayers} 携带的 {@code renderers} 是 {@code createEntityRenderers}
     * 建好的最终表 ⇒ 读出来的类名就是运行时真正用的那个。</p>
     */
    @SubscribeEvent
    public static void verifyOverride(EntityRenderersEvent.AddLayers event) {
        BuiltInRegistries.ENTITY_TYPE
                .getOptional(new ResourceLocation("ad_astra", "tier_1_rocket"))
                .ifPresent(type -> {
                    try {
                        Field f = EntityRenderersEvent.AddLayers.class.getDeclaredField("renderers");
                        f.setAccessible(true);
                        Object v = f.get(event);
                        if (v instanceof Map<?, ?> map) {
                            Object renderer = map.get(type);
                            String name = renderer == null ? "null" : renderer.getClass().getName();
                            boolean ok = name.startsWith("com.gtoecore");
                            LOGGER.info("[gtoecore] tier_1_rocket FINAL renderer = {} ({})",
                                    name, ok ? "override OK" : "!!! override LOST !!!");
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("[gtoecore] cannot verify tier_1_rocket renderer", t);
                    }
                });
    }
}
