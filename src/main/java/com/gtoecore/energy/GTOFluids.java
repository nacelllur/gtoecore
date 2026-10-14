package com.gtoecore.energy;

import com.gtoecore.GTNCore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/**
 * GTOE 高级能量流体 —— 激光能（LU）/ 量子能（QU）注册为 Forge 流体。
 *
 * <p><b>参考 GT 聚变反应堆的等离子体生产方式：</b>激光聚束器持续耗电把 EU 转成
 * 「激光能」流体注入内部罐（可用流体输出总线/管道外运），量子凝聚器再吃激光能流体
 * + EU 凝聚出「量子能」流体 —— 即高级能量以流体形式生产、储运、消耗。</p>
 *
 * <p>与 GT plasma 一致：<b>纯罐装流体</b>（不注册 LiquidBlock / 桶），只能经由
 * 罐、管道、流体仓搬运，不会出现在世界里。</p>
 *
 * <p>贴图注册方式照抄 GTM {@code GTFluidBuilder.defaultFluidType}：重写
 * {@link FluidType#initializeClient(Consumer)}，不依赖 Forge 事件（1.20.1
 * 的 {@code RegisterClientExtensionsEvent} 不存在于公开 API 里）。</p>
 */
public final class GTOFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, GTNCore.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.Keys.FLUIDS, GTNCore.MODID);

    // ---------------- 激光能（LU）：青色发光流体 ----------------

    public static final RegistryObject<FluidType> LASER_ENERGY_TYPE =
            FLUID_TYPES.register("laser_energy", () -> makeFluidType(
                    "fluid_type.gtoecore.laser_energy",
                    new ResourceLocation(GTNCore.MODID, "block/fluid/laser_energy_still"),
                    new ResourceLocation(GTNCore.MODID, "block/fluid/laser_energy_flow"),
                    0xFF30E8E8,
                    FluidType.Properties.create()
                            .descriptionId("fluid_type.gtoecore.laser_energy")
                            .density(100)
                            .viscosity(500)
                            .lightLevel(12)
                            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final RegistryObject<Fluid> LASER_ENERGY =
            FLUIDS.register("laser_energy",
                    () -> new ForgeFlowingFluid.Source(laserProps()));
    public static final RegistryObject<Fluid> LASER_ENERGY_FLOWING =
            FLUIDS.register("laser_energy_flowing",
                    () -> new ForgeFlowingFluid.Flowing(laserProps()));

    private static ForgeFlowingFluid.Properties laserPropsHolder;
    private static ForgeFlowingFluid.Properties laserProps() {
        if (laserPropsHolder == null) {
            laserPropsHolder = new ForgeFlowingFluid.Properties(
                    LASER_ENERGY_TYPE, LASER_ENERGY, LASER_ENERGY_FLOWING);
        }
        return laserPropsHolder;
    }

    // ---------------- 量子能（QU）：紫色发光流体 ----------------

    public static final RegistryObject<FluidType> QUANTUM_ENERGY_TYPE =
            FLUID_TYPES.register("quantum_energy", () -> makeFluidType(
                    "fluid_type.gtoecore.quantum_energy",
                    new ResourceLocation(GTNCore.MODID, "block/fluid/quantum_energy_still"),
                    new ResourceLocation(GTNCore.MODID, "block/fluid/quantum_energy_flow"),
                    0xFFB44CE8,
                    FluidType.Properties.create()
                            .descriptionId("fluid_type.gtoecore.quantum_energy")
                            .density(2000)
                            .viscosity(3000)
                            .lightLevel(7)
                            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final RegistryObject<Fluid> QUANTUM_ENERGY =
            FLUIDS.register("quantum_energy",
                    () -> new ForgeFlowingFluid.Source(quantumProps()));
    public static final RegistryObject<Fluid> QUANTUM_ENERGY_FLOWING =
            FLUIDS.register("quantum_energy_flowing",
                    () -> new ForgeFlowingFluid.Flowing(quantumProps()));

    private static ForgeFlowingFluid.Properties quantumPropsHolder;
    private static ForgeFlowingFluid.Properties quantumProps() {
        if (quantumPropsHolder == null) {
            quantumPropsHolder = new ForgeFlowingFluid.Properties(
                    QUANTUM_ENERGY_TYPE, QUANTUM_ENERGY, QUANTUM_ENERGY_FLOWING);
        }
        return quantumPropsHolder;
    }

    // ---------------- 聚变光源（FUSION LIGHT）：金白发光流体（光凝流的光子源） ----------------

    public static final RegistryObject<FluidType> FUSION_LIGHT_TYPE =
            FLUID_TYPES.register("fusion_light", () -> makeFluidType(
                    "fluid_type.gtoecore.fusion_light",
                    new ResourceLocation(GTNCore.MODID, "block/fluid/fusion_light_still"),
                    new ResourceLocation(GTNCore.MODID, "block/fluid/fusion_light_flow"),
                    0xFFF5E08C,
                    FluidType.Properties.create()
                            .descriptionId("fluid_type.gtoecore.fusion_light")
                            .density(50)
                            .viscosity(10)
                            .lightLevel(15)
                            .temperature(1000000)
                            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final RegistryObject<Fluid> FUSION_LIGHT =
            FLUIDS.register("fusion_light",
                    () -> new ForgeFlowingFluid.Source(fusionLightProps()));
    public static final RegistryObject<Fluid> FUSION_LIGHT_FLOWING =
            FLUIDS.register("fusion_light_flowing",
                    () -> new ForgeFlowingFluid.Flowing(fusionLightProps()));

    private static ForgeFlowingFluid.Properties fusionLightPropsHolder;
    private static ForgeFlowingFluid.Properties fusionLightProps() {
        if (fusionLightPropsHolder == null) {
            fusionLightPropsHolder = new ForgeFlowingFluid.Properties(
                    FUSION_LIGHT_TYPE, FUSION_LIGHT, FUSION_LIGHT_FLOWING);
        }
        return fusionLightPropsHolder;
    }

    // ---------------- 挂载（mod 构造期调用） ----------------

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    /** 照抄 GTM GTFluidBuilder.defaultFluidType：重写 initializeClient 指定贴图 */
    private static FluidType makeFluidType(String langKey,
                                           ResourceLocation stillTex,
                                           ResourceLocation flowTex,
                                           int tint,
                                           FluidType.Properties props) {
        return new FluidType(props) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return stillTex;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flowTex;
                    }

                    @Override
                    public int getTintColor() {
                        return tint;
                    }
                });
            }

            @Override
            public String getDescriptionId() {
                return langKey;
            }

            @Override
            public Component getDescription() {
                return Component.translatable(langKey);
            }
        };
    }

    private GTOFluids() {}
}
