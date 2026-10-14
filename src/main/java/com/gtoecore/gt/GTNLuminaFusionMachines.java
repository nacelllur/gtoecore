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
 * 聚变光源核心（三级 MK1/MK2/MK3）—— 与 GT 聚变反应堆结构/逻辑一致，主方块换皮。
 * 从 {@link GTNMachines} 拆出（2026-10-14）。
 */
public final class GTNLuminaFusionMachines {

    // ------------------------------------------------------------------
    // 聚变光源核心（Lumina Fusion Core）—— 与 GT 聚变反应堆各种方面一致，主方块换皮
    // ------------------------------------------------------------------

    /**
     * 聚变光源核心 —— 三级（LuV/ZPM/UV，tier 6/7/8），结构/配方/超频逻辑与 GT 聚变
     * 反应堆 {@code FUSION_REACTOR} 完全一致（15 aisle 球型环、eu_to_start 预热、
     * FUSION_OC 超频、能量仓 E 位、流体进出口 O/A/I、线圈 K、玻璃 G）。
     *
     * <p>唯一区别：主方块（控制器）是 GTOE 自己的 {@code lumina_fusion_core}，
     * 外观用 GTOE 专属贴图；结构内的外壳 C 位仍用 GT fusion casing 方块
     * （{@link FusionReactorMachine#getCasingState}），保证「各种方面都一模一样」。</p>
     *
     * <p>产出：聚变光源 —— 光凝流（Luminofluid）生产链路的光子源。</p>
     */
    public static MultiblockMachineDefinition[] registerLuminaFusionCore() {
        return GTMachineUtils.registerTieredMultis(GTNMachines.REGISTRATE, "lumina_fusion_core",
                (holder, tier) -> new LuminaFusionCoreMachine(holder, tier),
                (tier, builder) -> builder
                        .langValue("聚变光源核心 MK" + (tier - 5))
                        .rotationState(RotationState.ALL)
                        .recipeType(GTRecipeTypes.FUSION_RECIPES)
                        .recipeModifiers(FusionReactorMachine::recipeModifier,
                                GTRecipeModifiers.BATCH_MODE)
                        .tooltips(Component.translatable("gtoecore.machine.lumina_fusion_core.tooltip.1"),
                                Component.translatable("gtoecore.machine.lumina_fusion_core.tooltip.2"))
                        .appearanceBlock(() -> FusionReactorMachine.getCasingState(tier))
                        .pattern(definition -> {
                            Block casingBlock = FusionReactorMachine.getCasingState(tier);
                            TraceabilityPredicate casing = Predicates.blocks(casingBlock);
                            return FactoryBlockPattern.start()
                                    .aisle("###############", "######OGO######", "###############")
                                    .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                                    .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                                    .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                                    .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                                    .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                                    .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                                    .aisle("#C###########C#", "GAG#########GAG", "#C###########C#")
                                    .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                                    .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                                    .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                                    .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                                    .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                                    .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                                    .aisle("###############", "######OSO######", "###############")
                                    .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                                    .where('G', Predicates.blocks(GTBlocks.FUSION_GLASS.get()).or(casing))
                                    .where('E', casing.or(Predicates.blocks(
                                                    PartAbility.INPUT_ENERGY.getBlockRange(tier, 8).toArray(Block[]::new))
                                            .setMinGlobalLimited(1).setPreviewCount(16)))
                                    .where('C', casing)
                                    .where('K', Predicates.blocks(FusionReactorMachine.getCoilState(tier)))
                                    .where('O', casing.or(Predicates.abilities(PartAbility.EXPORT_FLUIDS)))
                                    .where('A', Predicates.air())
                                    .where('I', casing.or(Predicates.abilities(PartAbility.IMPORT_FLUIDS)
                                            .setMinGlobalLimited(2)))
                                    .where('#', Predicates.any())
                                    .build();
                        })
                        .shapeInfos(definition -> {
                            var casing = FusionReactorMachine.getCasingState(tier);
                            List<MultiblockShapeInfo> shapeInfos = new ArrayList<>();
                            MultiblockShapeInfo.ShapeInfoBuilder base = MultiblockShapeInfo.builder()
                                    .aisle("###############", "######NMN######", "###############")
                                    .aisle("######DCD######", "####GG###GG####", "######UCU######")
                                    .aisle("####CC###CC####", "###w##SGS##e###", "####CC###CC####")
                                    .aisle("###C#######C###", "##nKsG###GsKn##", "###C#######C###")
                                    .aisle("##C#########C##", "#G#e#######w#G#", "##C#########C##")
                                    .aisle("##C#########C##", "#G#G#######G#G#", "##C#########C##")
                                    .aisle("#D###########D#", "W#E#########W#E", "#U###########U#")
                                    .aisle("#C###########C#", "G#G#########G#G", "#C###########C#")
                                    .aisle("#D###########D#", "W#E#########W#E", "#U###########U#")
                                    .aisle("##C#########C##", "#G#G#######G#G#", "##C#########C##")
                                    .aisle("##C#########C##", "#G#e#######w#G#", "##C#########C##")
                                    .aisle("###C#######C###", "##sKnG###GnKs##", "###C#######C###")
                                    .aisle("####CC###CC####", "###w##NGN##e###", "####CC###CC####")
                                    .aisle("######DCD######", "####GG###GG####", "######UCU######")
                                    .aisle("###############", "######SGS######", "###############")
                                    .where('M', definition.get(), Direction.NORTH)
                                    .where('C', casing)
                                    .where('G', GTBlocks.FUSION_GLASS.get())
                                    .where('K', FusionReactorMachine.getCoilState(tier))
                                    .where('W', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.WEST)
                                    .where('E', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.EAST)
                                    .where('S', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.SOUTH)
                                    .where('N', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.NORTH)
                                    .where('w', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.WEST)
                                    .where('e', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.EAST)
                                    .where('s', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.SOUTH)
                                    .where('n', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.NORTH)
                                    .where('U', GTMachines.FLUID_IMPORT_HATCH[tier], Direction.UP)
                                    .where('D', GTMachines.FLUID_IMPORT_HATCH[tier], Direction.DOWN)
                                    .where('#', Blocks.AIR);
                            shapeInfos.add(base.shallowCopy().where('G', casing).build());
                            return shapeInfos;
                        })
                        .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS,
                                RecipeLogic.Status.IDLE)
                        .model(GTMachineModels.createWorkableCasingMachineModel(
                                        FusionReactorMachine.getCasingType(tier).getTexture(),
                                        GTNCoreGT.id("block/multiblock/lumina_fusion_core"))
                                .andThen(b -> b.addDynamicRenderer(
                                        DynamicRenderHelper::createFusionRingRender)))
                        .hasBER(true)
                        .register(),
                6, 7, 8);
    }
    private GTNLuminaFusionMachines() {
    }
}
