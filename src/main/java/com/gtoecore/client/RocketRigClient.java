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
 * ad_astra 火箭实体渲染接管 —— tier 1 + tier 2。
 *
 * <p>每台火箭在 {@code AddLayers}（建表之后）强制替换
 * {@link EntityRenderDispatcher#renderers} 里的条目，
 * 与注册顺序无关（2026-10-14 实测：常规后注册会被 ad_astra 侧更晚的注册盖回去）。</p>
 *
 * <table>
 *   <tr><th>tier</th><th>实体 id</th><th>OBJ</th><th>SCALE</th></tr>
 *   <tr><td>1</td><td>ad_astra:tier_1_rocket</td><td>rocket_rig.obj</td><td>0.8587</td></tr>
 *   <tr><td>2</td><td>ad_astra:tier_2_rocket</td><td>falcon_rig.obj</td><td>0.5676</td></tr>
 * </table>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketRigClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation TIER_1_ID = new ResourceLocation("ad_astra", "tier_1_rocket");
    private static final ResourceLocation TIER_2_ID = new ResourceLocation("ad_astra", "tier_2_rocket");

    private RocketRigClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        BuiltInRegistries.ENTITY_TYPE.getOptional(TIER_1_ID).ifPresent(type -> {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                EntityType t = (EntityType) type;
                event.registerEntityRenderer(t, RocketRigRenderer::tier1);
                LOGGER.info("[gtoecore] tier_1_rocket renderer registered (rocket_rig.obj)");
            } catch (Throwable ex) {
                LOGGER.warn("[gtoecore] failed to register tier_1_rocket renderer", ex);
            }
        });
        BuiltInRegistries.ENTITY_TYPE.getOptional(TIER_2_ID).ifPresent(type -> {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                EntityType t = (EntityType) type;
                event.registerEntityRenderer(t, RocketRigRenderer::tier2);
                LOGGER.info("[gtoecore] tier_2_rocket renderer registered (falcon_rig.obj)");
            } catch (Throwable ex) {
                LOGGER.warn("[gtoecore] failed to register tier_2_rocket renderer", ex);
            }
        });
    }

    /**
     * 建表之后强制替换（确定性）—— 与注册顺序无关。
     */
    @SubscribeEvent
    public static void forceReplaceAndVerify(EntityRenderersEvent.AddLayers event) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Map<EntityType<?>, EntityRenderer<?>> copy = new HashMap<>(dispatcher.renderers);
        boolean changed = false;

        // tier 1
        EntityType<?> t1 = BuiltInRegistries.ENTITY_TYPE.getOptional(TIER_1_ID).orElse(null);
        if (t1 != null && copy.containsKey(t1)) {
            EntityRenderer<?> r = RocketRigRenderer.tier1(event.getContext());
            copy.put(t1, r);
            LOGGER.info("[gtoecore] tier_1_rocket FORCE-REPLACED: now {}", r.getClass().getName());
            changed = true;
        }

        // tier 2
        EntityType<?> t2 = BuiltInRegistries.ENTITY_TYPE.getOptional(TIER_2_ID).orElse(null);
        if (t2 != null && copy.containsKey(t2)) {
            EntityRenderer<?> r = RocketRigRenderer.tier2(event.getContext());
            copy.put(t2, r);
            LOGGER.info("[gtoecore] tier_2_rocket FORCE-REPLACED: now {}", r.getClass().getName());
            changed = true;
        }

        if (changed) {
            dispatcher.renderers = copy;
        }
    }
}
