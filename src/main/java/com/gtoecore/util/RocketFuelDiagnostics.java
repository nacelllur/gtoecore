package com.gtoecore.util;

import com.gtoecore.GTNCore;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 火箭燃料标签自检（一次性诊断日志）。
 *
 * <p>背景：ad_astra 火箭的燃料槽只接受「流体标签里认的流体」——
 * {@code Rocket.TIER_N_PROPERTIES.fuel} = {@code ad_astra:tier_N_rocket_fuel}，
 * 而该标签原内容 = {@code #ad_astra:fuel}。我们用内置数据包把它改成
 * {@code gtceu:high_octane_gasoline}（见 {@code data/ad_astra/tags/fluids/}）。</p>
 *
 * <p>标签在多个数据包之间是<b>并集</b>（除非写 {@code "replace": true}），
 * 而 mod 数据包的优先级受加载顺序影响 —— 光看文件在不在 jar 里<b>无法</b>确定最终内容。
 * 所以这里在服务器启动后把<b>实际解析结果</b>打进日志，一眼就能判定替换是否生效。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RocketFuelDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation GT_FUEL = new ResourceLocation("gtceu", "high_octane_gasoline");

    private RocketFuelDiagnostics() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            LOGGER.info("[gtoecore][fuel] fluid {} registered = {}",
                    GT_FUEL, ForgeRegistries.FLUIDS.containsKey(GT_FUEL));

            var registry = event.getServer().registryAccess().registryOrThrow(Registries.FLUID);
            for (String path : List.of("tier_1_rocket_fuel", "tier_2_rocket_fuel",
                    "tier_3_rocket_fuel", "tier_4_rocket_fuel", "fuel")) {
                TagKey<Fluid> tag = TagKey.create(Registries.FLUID,
                        new ResourceLocation("ad_astra", path));
                registry.getTag(tag).ifPresentOrElse(
                        set -> LOGGER.info("[gtoecore][fuel] #ad_astra:{} = {}", path,
                                set.stream()
                                        .map(h -> String.valueOf(ForgeRegistries.FLUIDS.getKey(h.value())))
                                        .collect(Collectors.toList())),
                        () -> LOGGER.warn("[gtoecore][fuel] #ad_astra:{} NOT PRESENT", path));
            }
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore][fuel] diagnostics failed", t);
        }
    }
}
