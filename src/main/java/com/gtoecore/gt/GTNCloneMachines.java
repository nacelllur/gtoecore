package com.gtoecore.gt;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gtoecore.GTNBlocks;
import com.gtoecore.client.ArchRigDynamicRender;
import com.gtoecore.client.BladeRigDynamicRender;
import com.gtoecore.client.CmsRigDynamicRender;
import com.gtoecore.client.DrumRigDynamicRender;
import com.gtoecore.client.EventHorizonRenderer;
import com.gtoecore.client.LhcComboRigDynamicRender;
import com.gtoecore.client.MaimaiRigDynamicRender;
import com.gtoecore.client.MastRigDynamicRender;
import com.gtoecore.client.TowerRigDynamicRender;
import com.gtoecore.util.GTNPreview;
import com.gtoecore.deepspace.DeepSpaceHubMachine;
import com.gtoecore.deepspace.DeepSpaceModuleMachine;
import com.gtoecore.deepspace.ModuleKind;
import com.gtoecore.deepspace.ModuleRecipeModifiers;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 生物系多方块主机 ×3（克隆体生产车间 / 制造仓 / 维护室）——
 * 从 {@link GTNMachines} 拆出（2026-10-14），逻辑与注册顺序完全不变。
 */
public final class GTNCloneMachines {

    // ------------------------------------------------------------------
    // 生物系多方块主机 ×3（结构按玩家投影 dump_full_out.txt 逐格核对）
    // 共同点：不耗电（GTNWorkableMultiblockMachine 非电带 GUI 基类）、无线程仓、
    //         无维护舱；结构严格匹配投影（含第三方装饰方块）。
    // ------------------------------------------------------------------

    /**
     * 取第三方 mod 方块（如 ad_astra 的气闸/通风口、ae2 的天空石罐）；缺失时返回 air。
     *
     * <p><b>只能在 pattern lambda（GTMemoizer 懒构建）里调用</b>，此时全部方块已进
     * {@link BuiltInRegistries}。提前到类加载期求值会拿到 air 并被永久缓存。</p>
     */
    private static Block modBlock(String modid, String name) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(modid, name));
        return b == null ? net.minecraft.world.level.block.Blocks.AIR : b;
    }

    /**
     * 克隆体生产车间 —— 复活专用：不耗电、无配方检测、只开输入仓；
     * 克隆体（物品 NBT 绑定玩家）放输入总线，硬核死亡时消耗 1 个在床上复活。
     *
     * <p>投影 ground truth：控制器在正面底排中央；床仅 1 张（head+foot 两格，
     * 位于房间中轴 y=1）；正面 3×3 气闸门；两侧墙下部通风口。</p>
     */
    public static MultiblockMachineDefinition registerCloneProductionWorkshop() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock("clone_production_workshop", CloneProductionWorkshopMachine::new)
                .langValue("Clone Production Workshop")
                .rotationState(RotationState.ALL)
                .recipeType(GTNRecipeTypes.BIO_RECIPES)
                .appearanceBlock(GTBlocks.CASING_PTFE_INERT)
                .tooltips(
                        Component.translatable("gtoecore.machine.clone_production_workshop.tooltip.1"),
                        Component.translatable("gtoecore.machine.clone_production_workshop.tooltip.2"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 约定：aisle 从背面到正面；字符串从底层到顶层；字符 0 = 正面视角左手侧
                        .aisle("#####", "#CCC#", "#####", "#CCC#", "#####")
                        .aisle("#CCC#", "CABAC", "#AAA#", "CAAAC", "#CCC#")
                        .aisle("#CCC#", "CABAC", "#AAA#", "CAAAC", "#C#C#")
                        .aisle("#CCC#", "VAAAV", "#AAA#", "VAAAV", "#CCC#")
                        .aisle("##S##", "#LLL#", "#LLL#", "#LLL#", "#####")
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 外壳（inert / clean 两种）都允许放输入总线（只允许输入仓，无能源/流体/输出）
                        .where('#', Predicates.blocks(GTBlocks.CASING_PTFE_INERT.get())
                                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS)))
                        .where('C', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get())
                                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS)))
                        .where('L', Predicates.blocks(modBlock("ad_astra", "airlock")))
                        .where('V', Predicates.blocks(modBlock("ad_astra", "vent")))
                        // 床：标记 slotName=bed，形成后供复活定位（head+foot 两格都会记录）
                        .where('B', Predicates.blocks(net.minecraft.world.level.block.Blocks.WHITE_BED).setSlotName("bed"))
                        .where('A', Predicates.air())
                        .build())
                .workableCasingModel(
                        GTCEu.id("block/casings/solid/machine_casing_inert_ptfe"),
                        GTNCoreGT.id("block/multiblock/clone_production_workshop"))
                .register();
        return def;
    }

    /**
     * 克隆体制造仓 —— 不耗电、只开输出仓；成形后每 5 分钟自动产 1 生物团
     * （机器内定时器实现，空输入配方在 GTRecipeLookup 机制下不可达）。
     *
     * <p>投影 ground truth：3 深 × 3 高 × 6 宽；玻璃管沿左右方向（非朝向纵深），
     * 管顶通风口、管底石英楼梯；控制器在正面右端。</p>
     */
    public static MultiblockMachineDefinition registerCloneManufacturingChamber() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock("clone_manufacturing_chamber", CloneManufacturingChamberMachine::new)
                .langValue("Clone Manufacturing Chamber")
                .rotationState(RotationState.ALL)
                .recipeType(GTNRecipeTypes.BIO_RECIPES)
                .appearanceBlock(GTBlocks.CASING_PTFE_INERT)
                .tooltips(
                        Component.translatable("gtoecore.machine.clone_manufacturing_chamber.tooltip.1"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 约定：aisle 从背面到正面；字符串从底层到顶层；字符 0 = 正面视角左手侧
                        .aisle("######", "######", "#$$$$#")   // 背板
                        .aisle("######", "$AAAA#", "#VVVV#")   // 中段（玻璃管内空 + 顶部通风口）
                        .aisle("#TTTT#", "SGGGG#", "#$$$$#")   // 正面（控制器 + 玻璃管 + 底部楼梯）
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 外壳（inert / solid）允许放输出总线（只允许输出仓）
                        .where('#', Predicates.blocks(GTBlocks.CASING_PTFE_INERT.get())
                                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS)))
                        .where('$', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS)))
                        .where('V', Predicates.blocks(modBlock("ad_astra", "vent")))
                        .where('G', Predicates.blocks(net.minecraft.world.level.block.Blocks.GLASS))
                        .where('T', Predicates.blocks(net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ_STAIRS))
                        .where('A', Predicates.air())
                        .build())
                .workableCasingModel(
                        GTCEu.id("block/casings/solid/machine_casing_inert_ptfe"),
                        GTNCoreGT.id("block/multiblock/clone_manufacturing_chamber"))
                .register();
        return def;
    }

    /**
     * 克隆体维护室 —— 不耗电、物品+流体输入 + 物品输出；
     * 配方 1000mB 水 + 1 生物团 → 1 克隆体（6000t）。
     *
     * <p>投影 ground truth：开放式框架 —— y0 一层 3×3 外壳地板，控制器在正面地板边中点；
     * 背面中央立柱（y1~y3）；NASA 工作台在立柱前 y1，天空石罐在 y3。</p>
     */
    public static MultiblockMachineDefinition registerCloneMaintenanceRoom() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock("clone_maintenance_room", CloneMaintenanceRoomMachine::new)
                .langValue("Clone Maintenance Room")
                .rotationState(RotationState.ALL)
                .recipeType(GTNRecipeTypes.BIO_RECIPES)
                .appearanceBlock(GTBlocks.CASING_PTFE_INERT)
                .tooltips(
                        Component.translatable("gtoecore.machine.clone_maintenance_room.tooltip.1"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 约定：aisle 从背面到正面；字符串从底层到顶层；字符 0 = 正面视角左手侧
                        .aisle("###", "A#A", "A#A", "A#A")   // 背面立柱
                        .aisle("###", "AWA", "AAA", "AKA")   // 中段（NASA 工作台 y1 / 天空石罐 y3）
                        .aisle("#S#", "AAA", "AAA", "AAA")   // 正面（控制器在地板中点）
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 外壳允许物品/流体输入 + 物品输出仓
                        .where('#', Predicates.blocks(GTBlocks.CASING_PTFE_INERT.get())
                                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS,
                                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS)))
                        .where('W', Predicates.blocks(modBlock("ad_astra", "nasa_workbench")))
                        .where('K', Predicates.blocks(modBlock("ae2", "sky_stone_tank")))
                        .where('A', Predicates.air())
                        .build())
                .workableCasingModel(
                        GTCEu.id("block/casings/solid/machine_casing_inert_ptfe"),
                        GTNCoreGT.id("block/multiblock/clone_maintenance_room"))
                .register();
        return def;
    }
    private GTNCloneMachines() {
    }
}
