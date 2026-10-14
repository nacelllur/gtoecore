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
 * AE 合成终端多方块主机 —— 3×3×3 脱氧钢外壳，右键开 AE2 合成终端界面。
 * 从 {@link GTNMachines} 拆出（2026-10-14）。
 */
public final class GTNAeTerminalMachines {

    /**
     * AE 合成终端多方块主机 —— 3×3×3 脱氧钢外壳。
     *
     * <p><b>定位</b>：把 AE2 的合成终端（9 格合成格 + 网格存取 + 自动合成请求）
     * 做成 GT 多方块机器。结构为一个 3×3×3 的实心脱氧钢立方体：
     * 正面正中放控制器，其余 25 格全部为 {@code GTBlocks.CASING_STEEL_SOLID}
     * （脱氧钢机械方块，可替换为任意 ME 输入/输出仓或维护舱）。</p>
     *
     * <p><b>UI</b>：成形后右键控制器 → {@code MenuOpener.open(CraftingTermMenu.TYPE,
     * player, new GTOEMultiblockTerminalLocator(pos))}，直接打开 AE2 原版合成终端。
     * 机器本体自持 AE 网格节点（{@code IGridConnectedBlockEntity}），
     * 在旁边接一根 ME 线缆即接入网格。</p>
     */
    public static MultiblockMachineDefinition registerAECraftingTerminal() {
        return GTNMachines.REGISTRATE
                // ⛔ 必须用 5 参重载：最后一个参数是方块实体工厂。
                // AE2 的菜单宿主只认 BlockEntity/IPart/ItemMenuHost，且 CraftingTermMenu 还要
                // ISegmentedInventory、MEStorageMenu 还要 IActionHost —— 这些只能长在 BE 上，
                // 所以本机注册专用的 CraftingTerminalBlockEntity（全部转交机器本体）。
                .multiblock("ae_crafting_terminal",
                        GTNMultiblockCraftingTerminalMachine::new,
                        MetaMachineBlock::new,
                        MetaMachineItem::new,
                        CraftingTerminalBlockEntity::new)
                .langValue("AE Crafting Terminal")
                .rotationState(RotationState.ALL)
                // 无配方：DUMMY 类型 JEI 不可见
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .tooltips(
                        Component.translatable("gtoecore.machine.ae_crafting_terminal.tooltip.1"),
                        Component.translatable("gtoecore.machine.ae_crafting_terminal.tooltip.2"),
                        Component.translatable("gtoecore.machine.ae_crafting_terminal.tooltip.3"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 约定：aisle 从背面到正面；层内字符串从底层到顶层；字符 0 = 正面视角左手侧
                        .aisle("CCC", "CCC", "CCC")
                        .aisle("CCC", "CCC", "CCC")
                        .aisle("CCC", "CSC", "CCC")
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 脱氧钢外壳；任意位置可换 ME 仓 / 物品仓 / 维护舱
                        .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()).setMinGlobalLimited(12)
                                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                                .or(Predicates.abilities(PartAbility.MAINTENANCE)))
                        .build())
                .workableCasingModel(
                        GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTNCoreGT.id("block/multiblock/ae_crafting_terminal"))
                .register();
    }
    private GTNAeTerminalMachines() {
    }
}
