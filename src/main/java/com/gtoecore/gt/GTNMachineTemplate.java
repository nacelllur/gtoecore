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
 * ★ 标准多方块模板（默认不调用 ⇒ 不注册进游戏，仅保证可编译）。
 * 新机器从复制本方法开始；从 {@link GTNMachines} 拆出（2026-10-14）。
 */
public final class GTNMachineTemplate {

    // ------------------------------------------------------------------
    // ★ 标准模板（默认不调用 → 不会注册进游戏）。
    // 保证可编译：API 变动后跑一次 build 即可发现模板过期。
    // 新机器从复制本方法开始；各步骤注意点见行内注释。
    // ------------------------------------------------------------------
    public static MultiblockMachineDefinition registerExampleMultiblock() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                // 机器 id：gtoecore:example_multiblock（全小写下划线）
                .multiblock("example_multiblock", WorkableElectricMultiblockMachine::new)
                // 英文名（中文名走 lang 文件里的 block.gtoecore.<name>）
                .langValue("Example Multiblock")
                // ALL=六面可朝向；NON_Y_AXIS=常见塔类。
                // 资源脚本按 ALL（large_extractor 式 blockstate）生成，
                // 换其它 RotationState 时同步换 --source 参数
                .rotationState(RotationState.ALL)
                // 配方类型：直接复用 GTM 的（GTRecipeTypes.XXX_RECIPES）。
                // 自定义配方类型见文档第 6 步（注册进的是 gtceu: 命名空间）
                .recipeType(GTRecipeTypes.ASSEMBLER_RECIPES)
                // 修饰器顺序固定：PARALLEL_HATCH 必须放首位
                //（OC 先压时长会让 BATCH_MODE 合并放大 <100t 的配方）
                .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.OC_NON_PERFECT_SUBTICK,
                        GTRecipeModifiers.BATCH_MODE)
                // 外壳方块：决定结构外观与 JEI 预览底色，用 GTM/GCYM 现成外壳即可
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                // 结构：aisle 从背面到正面；层内字符串从底层到顶层；字符 0 = 正面视角左手侧。
                // 'S'=控制器（固定写法）'A'=纯空气
                .pattern(definition -> FactoryBlockPattern.start()
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "XAX", "XXX")
                        .aisle("XXX", "XSX", "XXX")
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 外壳谓词三件套（GTM 官方写法，照抄）：
                        // ① 外壳本体 + 最少数量（防"1 格壳"钻空子）
                        // ② autoAbilities(recipeTypes) = 能量/物品/流体仓
                        // ③ autoAbilities(true,false,true) = 维护舱(必需)+并行槽
                        //    不想要"维护舱必需"就换成 abilities(MAINTENANCE)
                        //    .setMaxGlobalLimited(1)（可选槽写法）
                        .where('X', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()).setMinGlobalLimited(10)
                                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                .or(Predicates.autoAbilities(true, false, true)))
                        .where('A', Predicates.air())
                        .build())
                // 模型：底壳贴图 + 覆层目录（运行时元数据/datagen 留档）。
                // 实际渲染走资源 JSON（tools/add_multiblock.py 生成），
                // 覆层可先引用 GTM 现成目录占位，想要专属贴图再替换
                .workableCasingModel(
                        GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/multiblock/gcym/large_packer"))
                // JEI 结构预览（可选，塔类/多层结构强烈建议写）：
                // .shapeInfos(definition -> List.of(MultiblockShapeInfo.builder()
                //         .aisle(...).where('S', definition, Direction.NORTH)....build()))
                .register();
        GTNMachines.REGISTERED.add(def);
        return def;
    }
    private GTNMachineTemplate() {
    }
}
