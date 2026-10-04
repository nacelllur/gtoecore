package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gtoecore.GTNBlocks;
import com.gtoecore.GTNCore;
import com.gtoecore.GTNItems;
import com.gtoecore.energy.recipe.GTOEnergyRangeCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * GTM addon 初始化入口（与 packcompanion 同一套已验证时序）。
 *
 * <p>注册时序（踩坑结论，勿改）：</p>
 * <ul>
 *   <li>mod 构造期：只做 {@code GTNMachines.register(modEventBus)}（registrate 挂载）。</li>
 *   <li>{@code GTCEuAPI.RegisterEvent}（GTM 在 freeze 注册表前派发）：真正注册机器。
 *       必须用 {@code addGenericListener} 监听【基类】RegisterEvent ——
 *       EventBus 按精确类型分派，GTM 派发的是基类实例，监听 RL 子类永远收不到。</li>
 *   <li>{@code initializeAddon()}（GTAddon 回调）：此时注册表已冻结，什么都不注册。</li>
 * </ul>
 */
public class GTNCoreGT {

    public static final String MODID = GTNCore.MODID;

    /** 本 mod 命名空间的 ResourceLocation 快捷方法（对应 GTCEu.id） */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    private static final ResourceKey<CreativeModeTab> GT_MACHINE_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("gtceu", "machine"));

    /** gtoecore 专属创造标签页（DeferredRegister 注册，完全自控，不依赖 GTM tab 时序） */
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> GTO_TAB =
            CREATIVE_TABS.register("gto_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gtoecore"))
                    .icon(() -> new ItemStack(GTNBlocks.ROTARY_MILL.get()))
                    .displayItems((params, output) -> {
                        // GTM 机器
                        for (MachineDefinition def : GTNMachines.REGISTERED) {
                            if (def != null) output.accept(def.asStack());
                        }
                        // 方块物品
                        output.accept(GTNItems.CLONE.get());
                        output.accept(GTNItems.BIO_CLUSTER.get());
                        output.accept(GTNItems.STATION_GRAVITY_CORE.get());
                        output.accept(GTNItems.PV_PANEL.get());
                        output.accept(GTNItems.PRIMITIVE_COMPUTER_CASING.get());
                        output.accept(GTNItems.PRIMITIVE_HEAT_VENT.get());
                        output.accept(GTNItems.PRIMITIVE_EMPTY_COMPONENT.get());
                        output.accept(GTNItems.PRIMITIVE_COMPUTATION_COMPONENT.get());
                        output.accept(GTNItems.DEEP_SPACE_FUEL.get());
                        // GT6 风味能量系统：导体四件套 + 产能机 + 接收端 + 消费端
                        output.accept(GTNItems.ROTATION_AXLE.get());
                        output.accept(GTNItems.HEAT_PIPE.get());
                        output.accept(GTNItems.COLD_PIPE.get());
                        output.accept(GTNItems.KINETIC_ROD.get());
                        output.accept(GTNItems.COMBUSTION_ENGINE.get());
                        output.accept(GTNItems.SPEED_DETECTOR.get());
                        output.accept(GTNItems.ROTARY_MILL.get());
                        // 无人机：探测/开采 × LV~MAX（按等级成对排列）
                        for (int tier = com.gregtechceu.gtceu.api.GTValues.LV;
                             tier <= com.gregtechceu.gtceu.api.GTValues.MAX; tier++) {
                            if (GTNItems.SURVEY_DRONES[tier] != null) {
                                output.accept(GTNItems.SURVEY_DRONES[tier].get());
                            }
                            if (GTNItems.MINING_DRONES[tier] != null) {
                                output.accept(GTNItems.MINING_DRONES[tier].get());
                            }
                        }
                    })
                    .build());

    public static void init(IEventBus modEventBus) {
        // 0. gtoecore 专属创造标签页（构造期注册）
        CREATIVE_TABS.register(modEventBus);

        // 1. registrate 挂载（构造期安全）
        GTNMachines.register(modEventBus);

        // 1.45 自定义配方条件注册窗口：RECIPE_CONDITIONS 的 RegisterEvent。
        //     GTCEu 顺序：GTRecipeConditions.init()（派发并冻结条件表）→
        //     GTRecipeTypes.init()（派发并冻结配方类型）→ GTMachines.init()（机器）。
        //     因此本窗口一定先于配方类型与机器注册执行。
        modEventBus.addGenericListener(RecipeConditionType.class,
                (GTCEuAPI.RegisterEvent<String, RecipeConditionType<?>> e) ->
                        e.register("energy_range", GTOEnergyRangeCondition.TYPE));

        // 1.5 自定义配方类型注册窗口：RECIPE_TYPES 的 RegisterEvent。
        //     GTCEu 启动顺序：GTRecipeTypes.init()（派发并冻结）→ ... → GTMachines.init()，
        //     因此这里一定先于下面的机器注册窗口执行。
        modEventBus.addGenericListener(GTRecipeType.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> e) -> GTNRecipeTypes.init());

        // 2. 机器注册窗口：RegisterEvent（freeze 前）
        modEventBus.addGenericListener(MachineDefinition.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> e) -> GTNMachines.initMachines());

        // 3. 兼容兜底：仍往 GTM machine tab 注入（两份重复由游戏去重，无害）
        modEventBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (!e.getTabKey().equals(GT_MACHINE_TAB)) return;
            for (MachineDefinition def : GTNMachines.REGISTERED) {
                if (def != null) e.accept(def.asStack());
            }
            e.accept(GTNItems.CLONE.get());
            e.accept(GTNItems.BIO_CLUSTER.get());
            e.accept(GTNItems.STATION_GRAVITY_CORE.get());
            e.accept(GTNItems.PV_PANEL.get());
            e.accept(GTNItems.PRIMITIVE_COMPUTER_CASING.get());
            e.accept(GTNItems.PRIMITIVE_HEAT_VENT.get());
            e.accept(GTNItems.PRIMITIVE_EMPTY_COMPONENT.get());
            e.accept(GTNItems.PRIMITIVE_COMPUTATION_COMPONENT.get());
            e.accept(GTNItems.DEEP_SPACE_FUEL.get());
            e.accept(GTNItems.ROTATION_AXLE.get());
            e.accept(GTNItems.HEAT_PIPE.get());
            e.accept(GTNItems.COLD_PIPE.get());
            e.accept(GTNItems.KINETIC_ROD.get());
            e.accept(GTNItems.COMBUSTION_ENGINE.get());
            e.accept(GTNItems.SPEED_DETECTOR.get());
            e.accept(GTNItems.ROTARY_MILL.get());
            for (int tier = com.gregtechceu.gtceu.api.GTValues.LV;
                 tier <= com.gregtechceu.gtceu.api.GTValues.MAX; tier++) {
                if (GTNItems.SURVEY_DRONES[tier] != null) {
                    e.accept(GTNItems.SURVEY_DRONES[tier].get());
                }
                if (GTNItems.MINING_DRONES[tier] != null) {
                    e.accept(GTNItems.MINING_DRONES[tier].get());
                }
            }
        });
    }
}
