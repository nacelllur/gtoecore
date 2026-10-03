package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtoecore.GTNCore;
import com.gtoecore.GTNItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

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

    public static void init(IEventBus modEventBus) {
        // 1. registrate 挂载（构造期安全）
        GTNMachines.register(modEventBus);

        // 1.5 自定义配方类型注册窗口：RECIPE_TYPES 的 RegisterEvent。
        //     GTCEu 启动顺序：GTRecipeTypes.init()（派发并冻结）→ ... → GTMachines.init()，
        //     因此这里一定先于下面的机器注册窗口执行。
        modEventBus.addGenericListener(GTRecipeType.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> e) -> GTNRecipeTypes.init());

        // 2. 机器注册窗口：RegisterEvent（freeze 前）
        modEventBus.addGenericListener(MachineDefinition.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> e) -> GTNMachines.initMachines());

        // 3. 机器 + 物品进 GT 机器创造标签页（注册失败时自动跳过，不崩游戏）
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
            // 无人机：探测/开采 × LV~MAX（按等级成对排列）
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
