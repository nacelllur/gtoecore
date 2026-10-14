package com.gtoecore.client;

import com.gtoecore.GTNCore;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方 mod（ad_astra）实体的渲染接管 —— 目前只有一级火箭。
 *
 * <h2>为什么要「注册 + 强制替换」双保险（2026-10-14 实测）</h2>
 * <p>第一版只在 {@code EntityRenderersEvent.RegisterRenderers} 里后注册覆盖，
 * 结果自检日志显示 <b>{@code override LOST}</b>：我们 put 成功了，但最终
 * {@code EntityRenderers.createEntityRenderers} 建出来的表里仍是原版
 * {@code RocketRenderer} —— 说明 ad_astra 那边的注册（经
 * resourcefullib / architectury 的 {@code EntityRendererRegistryImpl}，
 * 以及 FMLClientSetupEvent 的 {@code enqueueWork} 延迟任务）发生在<b>我们之后</b>，
 * 把我们的条目盖回去了。</p>
 *
 * <p>所以这里再加一道<b>确定性</b>保险：{@code AddLayers} 事件在
 * {@code createEntityRenderers} <b>建完表之后</b>触发，携带的
 * {@code renderers} 就是 {@link EntityRenderDispatcher} 正在用的那张表。
 * 我们反射把表里 {@code tier_1_rocket} 的条目<b>直接换成自己的渲染器</b>
 * —— 与注册顺序无关，必然生效。</p>
 *
 * <p>字段名不写死：遍历 {@link EntityRenderDispatcher} 的 {@code Map} 字段，
 * 用「表里有没有 {@code ad_astra:tier_1_rocket} 这个 key」认准目标
 * （SRG 运行时字段名是 {@code d}，写死名字太脆）。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketRigClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation TIER_1_ROCKET_ID = new ResourceLocation("ad_astra", "tier_1_rocket");

    private RocketRigClient() {
    }

    /** 第一道：常规后注册覆盖（若加载顺序对我们有利，这一步就够了）。 */
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RocketRigRenderer.registerIfLoaded(event);
    }

    /**
     * 第二道：建表之后强制替换（确定性）。
     *
     * <p>同时兼作自检 —— 打出的就是运行时真正在用的渲染器类名。</p>
     */
    @SubscribeEvent
    public static void forceReplaceAndVerify(EntityRenderersEvent.AddLayers event) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(TIER_1_ROCKET_ID).orElse(null);
        if (type == null) {
            return; // 没装 ad_astra
        }
        try {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            // ⚠ 1.20.1 里 EntityRenderDispatcher.renderers 是 public 非 final 字段
            //    （`public Map<EntityType<?>, EntityRenderer<?>> renderers`），直接换掉整张表即可
            //    —— 不依赖任何注册顺序，也不依赖字段名反射。
            Map<EntityType<?>, EntityRenderer<?>> copy = new HashMap<>(dispatcher.renderers);
            EntityRenderer<?> ours = new RocketRigRenderer(event.getContext());
            copy.put(type, ours);
            dispatcher.renderers = copy;
            LOGGER.info("[gtoecore] tier_1_rocket renderer FORCE-REPLACED after table build: now {}",
                    ours.getClass().getName());
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] failed to force-replace tier_1_rocket renderer", t);
        }
    }
}
