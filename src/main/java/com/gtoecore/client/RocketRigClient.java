package com.gtoecore.client;

import com.gtoecore.GTNCore;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * ad_astra 火箭实体渲染接管 —— 目前 tier 1 / 2 / 3，共用 {@link RocketRigRenderer}。
 *
 * <h2>为什么必须「建表之后强制替换」</h2>
 * <p>2026-10-14 实测：只在 {@code RegisterRenderers} 里后注册会被 ad_astra 侧更晚的注册
 * （resourcefullib → architectury，以及 FMLClientSetupEvent 的 enqueueWork）盖回去，
 * 自检日志显示 {@code override LOST}。所以真正的落实点是 {@code AddLayers}
 * —— 它在 {@code createEntityRenderers} 建完表之后触发，那时直接换掉
 * {@link EntityRenderDispatcher#renderers} 里的条目，与注册顺序无关。</p>
 *
 * <h2>新增一台火箭只需加一行</h2>
 * <p>往 {@link #OVERLAYS} 里追加一项即可（id + 工厂方法）。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketRigClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 接管清单：ad_astra 实体 id → 我们的渲染器工厂。 */
    private static final List<Overlay> OVERLAYS = List.of(
            new Overlay("tier_1_rocket", RocketRigRenderer::tier1),
            new Overlay("tier_2_rocket", RocketRigRenderer::tier2),
            new Overlay("tier_3_rocket", RocketRigRenderer::tier3));

    private record Overlay(String path, Function<EntityRendererProvider.Context, RocketRigRenderer> factory) {
        ResourceLocation id() {
            return new ResourceLocation("ad_astra", path);
        }
    }

    private RocketRigClient() {
    }

    /** 第一道：常规后注册（加载顺序有利时这一步就够）。 */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (Overlay overlay : OVERLAYS) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(overlay.id()).ifPresent(type -> {
                try {
                    Function<EntityRendererProvider.Context, RocketRigRenderer> factory = overlay.factory();
                    EntityRendererProvider provider = factory::apply;
                    event.registerEntityRenderer((EntityType) type, provider);
                    LOGGER.info("[gtoecore] {} renderer registered post-hoc", overlay.path());
                } catch (Throwable ex) {
                    LOGGER.warn("[gtoecore] failed to register {} renderer", overlay.path(), ex);
                }
            });
        }
    }

    /** 第二道：建表之后强制替换（确定性）。 */
    @SubscribeEvent
    public static void forceReplaceAndVerify(EntityRenderersEvent.AddLayers event) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Map<EntityType<?>, EntityRenderer<?>> copy = new HashMap<>(dispatcher.renderers);
        int replaced = 0;

        for (Overlay overlay : OVERLAYS) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(overlay.id()).orElse(null);
            if (type == null || !copy.containsKey(type)) {
                continue;
            }
            EntityRenderer<?> renderer = overlay.factory().apply(event.getContext());
            copy.put(type, renderer);
            LOGGER.info("[gtoecore] {} FORCE-REPLACED after table build: now {}",
                    overlay.path(), renderer.getClass().getName());
            replaced++;
        }

        if (replaced > 0) {
            // ⚠ 1.20.1 里 EntityRenderDispatcher.renderers 是 public 非 final 字段
            //    （`public Map<EntityType<?>, EntityRenderer<?>> renderers`），
            //    直接换掉整张表即可 —— 不依赖任何注册顺序，也不依赖字段名反射。
            dispatcher.renderers = copy;
            LOGGER.info("[gtoecore] {} rocket renderer(s) take over ad_astra models", replaced);
        }
    }
}
