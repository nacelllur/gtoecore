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
 * 能源类机器 —— 太阳能阵列模块、无人机蜂群之心、GT6 风味能量输入仓、
 * 燃烧引擎、15 压电动产能机批量注册。从 {@link GTNMachines} 拆出（2026-10-14）。
 */
public final class GTNEnergyMachines {

    /**
     * 太阳能发电模块 —— 1 层高、7×7 正方形被动发电多方块。
     *
     * <p>结构：四周边框（含正面）为脱氧钢机械方块
     * （{@code gtceu:solid_machine_casing} = {@code GTBlocks.CASING_STEEL_SOLID}），
     * 边框任意位置可替换为能量输出仓；中间 5×5 放光伏面板下半砖（可留空，
     * 面板越少发电越少）。每 4 块可见天空的面板 = 1A LV（32 EU/t），
     * 满铺 25 块 = 6A LV（192 EU/t）。白天、晴天、维度有天空才发电。</p>
     */
    public static MultiblockMachineDefinition registerSolarArrayModule() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock("solar_array_module", SolarArrayMachine::new)
                .langValue("Solar Array Module")
                .rotationState(RotationState.ALL)
                // 被动发电机：无配方，DUMMY 类型 JEI 不可见
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .tooltips(
                        Component.translatable("gtoecore.machine.solar_array_module.tooltip.1"),
                        Component.translatable("gtoecore.machine.solar_array_module.tooltip.2"),
                        Component.translatable("gtoecore.machine.solar_array_module.tooltip.3"),
                        Component.translatable("gtoecore.machine.solar_array_module.tooltip.4"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 约定：aisle 从背面到正面；本机器只有 1 层（每个 aisle 1 个字符串）
                        .aisle("#######")   // 后边框
                        .aisle("#PPPPP#")   // 中间 5 排：边框 + 光伏面板区
                        .aisle("#PPPPP#")
                        .aisle("#PPPPP#")
                        .aisle("#PPPPP#")
                        .aisle("#PPPPP#")
                        .aisle("###S###")   // 前边框（控制器在中点）
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 边框：脱氧钢机械方块，任意位置可换能量输出仓（取电口）
                        .where('#', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                                .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY)))
                        // 光伏面板：只认下半砖状态；允许留空（发电量随面板数变化）
                        .where('P', Predicates.states(GTNBlocks.PV_PANEL.get()
                                        .defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM))
                                .or(Predicates.air()))
                        .build())
                .workableCasingModel(
                        GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        // 太阳能主题正面覆层（太阳+光伏面板），勿用燃气轮机贴图
                        GTNCoreGT.id("block/multiblock/solar_array"))
                .register();
        return def;
    }

    /**
     * 无人机蜂群之心 —— 结构取自 {@code D:\GT-New\schematics\蜂群之心.litematic}
     * （作者 nacelllur 导出），13 宽 × 15 高 × 9 深。
     *
     * <p>造型说明（aisle 从 z=0 背面到 z=8 正面；层内字符串从底层到顶层）：</p>
     * <ul>
     *   <li><b>主机体</b>（z=3~5，y=0~8）：原始计算机外壳为骨架，计算机散热口作侧壁"鳃带"，
     *       钢化玻璃开观察窗，控制器嵌于 z=4 切片 y=3 行；底座一角为高压外壳；</li>
     *   <li><b>外观饰面</b>（z=0~2 / z=6~8 及 y=6~14）：平滑石英柱体 + 石英台阶/楼梯收边，
     *       抛光黑石按钮作铆钉点缀，末地烛 / 青色羊毛点缀核心窗，AE2 水晶共振器嵌于核心两侧；</li>
     *   <li>原右下角磨制安山岩已按用户要求移除。</li>
     * </ul>
     * <p>外壳位（C，共 64 格）可替换能量/物品/流体仓与维护仓（下限 55 格）；
     * 散热口、玻璃、装饰位不可换仓。空格字符 = GTM 默认 {@code Predicates.any()}（任意方块）。</p>
     */
    public static MultiblockMachineDefinition registerDroneSwarmHeart() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock("drone_swarm_heart", GTNDroneSwarmHeartMachine::new)
                .langValue("Drone Swarm Heart")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeTypes(GTNRecipeTypes.SPACE_MINING_RECIPES,
                        GTNRecipeTypes.DEEP_SPACE_EXPLORATION_RECIPES)
                .recipeModifiers(GTRecipeModifiers.OC_NON_PERFECT_SUBTICK,
                        GTRecipeModifiers.BATCH_MODE)
                .appearanceBlock(GTNBlocks.PRIMITIVE_COMPUTER_CASING)
                .tooltips(
                        Component.translatable("gtoecore.machine.drone_swarm_heart.tooltip.1"),
                        Component.translatable("gtoecore.machine.drone_swarm_heart.tooltip.2"),
                        Component.translatable("gtoecore.machine.drone_swarm_heart.tooltip.3"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 结构 = 蜂群之心.litematic（13 宽 × 15 高 × 9 深），13 个 aisle 从背面到正面；
                        // 每 aisle 15 行字符串从底层到顶层、每行 9 字符从左到右；空格 = 任意方块
                        // （GTM 默认 Predicates.any()）。坐标已按 litematic 世界坐标转置镜像校正。
                        // GT aisle 0 (lit x=12 东墙)
                        .aisle("    C    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                        // GT aisle 1 (lit x=11)
                        .aisle("   CCC   ", "         ", "    V    ", "    G    ", "    G    ", "    G    ", " t  G  t ", " Q  G  Q ", " Q  V  Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " t     t ")
                        // GT aisle 2 (lit x=10)
                        .aisle("   CCC   ", "    C    ", "   VVV   ", "   GRG   ", "   GEG   ", "   GWG   ", " Q GEG Q ", "bQbGRGbQb", " Q VVV Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", "bQb   bQb", " Q     Q ")
                        // GT aisle 3 (lit x=9)
                        .aisle("   CCC   ", "    C    ", "    V    ", "   CGC   ", "   CGC   ", "   CGC   ", " QsCGCsQ ", " Q CGC Q ", " QsQVQsQ ", " Q     Q ", " s     s ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ")
                        // GT aisle 4 (lit x=8)
                        .aisle("   CCC   ", "    C    ", "   VCV   ", "   CCC   ", "   C C   ", "   C C   ", " Q Q Q Q ", " Q C C Q ", " Q V V Q ", " Q     Q ", " s     s ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ")
                        // GT aisle 5 (lit x=7)
                        .aisle("   CCC   ", "    C    ", "   sVs   ", "   sCs   ", "   s s   ", "   s s   ", " QsQ QsQ ", " Q Q Q Q ", " QsQ QsQ ", " Q     Q ", " s     s ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ")
                        // GT aisle 6 (lit x=6)
                        .aisle("   CCC   ", "    C    ", "   VCV   ", "   CCC   ", "   C C   ", "   C C   ", " Q Q Q Q ", " Q C C Q ", " Q V V Q ", " Q     Q ", " s     s ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ")
                        // GT aisle 7 (lit x=5)  ← 控制器
                        .aisle("   CCC   ", "         ", "    V    ", "    S    ", "         ", "         ", " Q     Q ", "bQb   bQb", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", "bQb   bQb", " Q     Q ")
                        // GT aisle 8 (lit x=4)
                        .aisle("   CCC   ", "         ", "         ", "         ", "         ", "         ", " t     t ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " Q     Q ", " t     t ")
                        // GT aisle 9 (lit x=3)
                        .aisle("   CHC   ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                        // GT aisle 10 (lit x=2)
                        .aisle("    C    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                        // GT aisle 11 (lit x=1)
                        .aisle("         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                        // GT aisle 12 (lit x=0)
                        // 注意：投影里 z=8 右下角有 1 格 polished_andesite，用户 2026-10-01 要求去掉，
                        // 该位固定为空格 = Predicates.any()（放不放都成形）。生成器需带 --ignore minecraft:polished_andesite。
                        .aisle("         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 原始计算机外壳：本体 + 可换能量/物品/流体/维护仓（共 64 格，留 ~9 格换仓位）
                        .where('C', Predicates.blocks(GTNBlocks.PRIMITIVE_COMPUTER_CASING.get())
                                .setMinGlobalLimited(55)
                                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                .or(Predicates.autoAbilities(true, false, true)))
                        // 计算机散热口（鳃带，不可换仓）
                        .where('V', Predicates.blocks(GTBlocks.COMPUTER_HEAT_VENT.get()))
                        // 高压外壳（底座一角）
                        .where('H', Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get()))
                        // 钢化玻璃（观察窗）
                        .where('G', Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                        // 平滑石英装饰（柱/台阶/楼梯）
                        .where('Q', Predicates.blocks(Blocks.SMOOTH_QUARTZ))
                        .where('s', Predicates.blocks(Blocks.SMOOTH_QUARTZ_SLAB))
                        .where('t', Predicates.blocks(Blocks.SMOOTH_QUARTZ_STAIRS))
                        // 抛光黑石按钮（装饰）
                        .where('b', Predicates.blocks(Blocks.POLISHED_BLACKSTONE_BUTTON))
                        // 末地烛 / 青色羊毛（装饰）
                        .where('E', Predicates.blocks(Blocks.END_ROD))
                        .where('W', Predicates.blocks(Blocks.CYAN_WOOL))
                        // AE2 水晶共振器（装饰，可选 mod）：缺失时该位退化为「任意」，而不是「必须是空气」
                        .where('R', GTNPreview.blocksOrAny("ae2:crystal_resonance_generator"))
                        .build())
                // 结构预览（JEI / GTM 面板）：pattern 只判「方块类型」，无法表达 facing / half / type；
                // 不给 shapeInfo 时预览会用方块默认状态渲染 —— 表现为按钮全挤在一面墙上（"丢了一半"）、
                // 楼梯与半砖朝向全错。这里逐符号给出完整 BlockState。
                // 顺序约定：shapeInfo 的 aisle 顺序与 pattern 相反、行内字符串左右相反（= BlockPattern
                // .getPreview 的排布）；状态已按「控制器朝北」旋转 1 步（投影里控制器朝 WEST）。
                // 该片段由 tools/litematic2gtm.py 生成（--ignore minecraft:polished_andesite + 3 个
                // --preview-part），并断言 bakeArray 与 getPreview 逐格一致。
                // ⚠ 末尾 V/W/X 三行是「预览注入的功能部件」：pattern 的 C 谓词带 autoAbilities(...)
                //   （能量仓 + 维护仓下限 1），预览里若全是外壳则结构不成形 → 在 JEI 里点它会 NPE 崩游戏。
                //   删掉它们会直接把游戏点崩；换结构重新生成时务必同样带这 3 个 --preview-part。
                .shapeInfo(definition -> MultiblockShapeInfo.builder()
                        .aisle("         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=0
                        .aisle("         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=1
                        .aisle("    K    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=2
                        .aisle("   KNK   ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=3
                        .aisle("   KKK   ", "         ", "         ", "         ", "         ", "         ", " B     B ", " D     D ", " D     D ", " D     D ", " D     D ", " D     D ", " D     D ", " D     D ", " G     G ")   // preview z=4
                        .aisle("   KKK   ", "         ", "    L    ", "    S    ", "         ", "         ", " D     D ", "ADJ   ADJ", " D     D ", " D     D ", " D     D ", " D     D ", " D     D ", "ADJ   ADJ", " D     D ")   // preview z=5
                        .aisle("   KKK   ", "    K    ", "   LKL   ", "   WVX   ", "   K K   ", "   K K   ", " D D D D ", " D K K D ", " D L L D ", " D     D ", " F     F ", " D     D ", " D     D ", " D     D ", " D     D ")   // preview z=6
                        .aisle("   KKK   ", "    K    ", "   FLF   ", "   FKF   ", "   F F   ", "   F F   ", " DID DID ", " D D D D ", " DID DID ", " D     D ", " F     F ", " D     D ", " D     D ", " D     D ", " D     D ")   // preview z=7
                        .aisle("   KKK   ", "    K    ", "   LKL   ", "   KKK   ", "   K K   ", "   K K   ", " D D D D ", " D K K D ", " D L L D ", " D     D ", " F     F ", " D     D ", " D     D ", " D     D ", " D     D ")   // preview z=8
                        .aisle("   KKK   ", "    K    ", "    L    ", "   KMK   ", "   KMK   ", "   KMK   ", " DIKMKID ", " D KMK D ", " DIDLDID ", " D     D ", " F     F ", " D     D ", " D     D ", " D     D ", " D     D ")   // preview z=9
                        .aisle("   KKK   ", "    K    ", "   LLL   ", "   MOM   ", "   MPM   ", "   MRM   ", " D MTM D ", "ADJMUMADJ", " D LLL D ", " D     D ", " D     D ", " D     D ", " D     D ", "ADJ   ADJ", " D     D ")   // preview z=10
                        .aisle("   KKK   ", "         ", "    L    ", "    M    ", "    M    ", "    M    ", " E  M  E ", " D  M  D ", " D  L  D ", " D     D ", " D     D ", " D     D ", " D     D ", " D     D ", " H     H ")   // preview z=11
                        .aisle("    K    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=12
                        .where('S', definition.get(), Direction.NORTH)
                        .where('A', GTNPreview.state(Blocks.POLISHED_BLACKSTONE_BUTTON, "face=wall", "facing=west", "powered=false"))   // minecraft:polished_blackstone_button
                        .where('B', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=south", "half=top", "shape=straight", "waterlogged=false"))   // minecraft:smooth_quartz_stairs
                        .where('D', GTNPreview.state(Blocks.SMOOTH_QUARTZ))   // minecraft:smooth_quartz
                        .where('E', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=north", "half=top", "shape=straight", "waterlogged=false"))   // minecraft:smooth_quartz_stairs
                        .where('F', GTNPreview.state(Blocks.SMOOTH_QUARTZ_SLAB, "type=bottom", "waterlogged=false"))   // minecraft:smooth_quartz_slab
                        .where('G', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=south", "half=bottom", "shape=straight", "waterlogged=false"))   // minecraft:smooth_quartz_stairs
                        .where('H', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=north", "half=bottom", "shape=straight", "waterlogged=false"))   // minecraft:smooth_quartz_stairs
                        .where('I', GTNPreview.state(Blocks.SMOOTH_QUARTZ_SLAB, "type=top", "waterlogged=false"))   // minecraft:smooth_quartz_slab
                        .where('J', GTNPreview.state(Blocks.POLISHED_BLACKSTONE_BUTTON, "face=wall", "facing=east", "powered=false"))   // minecraft:polished_blackstone_button
                        .where('K', GTNPreview.state(GTNBlocks.PRIMITIVE_COMPUTER_CASING.get()))   // gtoecore:primitive_computer_casing
                        .where('L', GTNPreview.state(GTBlocks.COMPUTER_HEAT_VENT.get()))   // gtceu:computer_heat_vent
                        .where('M', GTNPreview.state(GTBlocks.CASING_TEMPERED_GLASS.get()))   // gtceu:tempered_glass
                        .where('N', GTNPreview.state(GTBlocks.HIGH_POWER_CASING.get()))   // gtceu:high_power_casing
                        .where('O', GTNPreview.stateOrAir("ae2:crystal_resonance_generator", "facing=up", "waterlogged=false"))   // ae2:crystal_resonance_generator
                        .where('P', GTNPreview.state(Blocks.END_ROD, "facing=up"))   // minecraft:end_rod
                        .where('R', GTNPreview.state(Blocks.CYAN_WOOL))   // minecraft:cyan_wool
                        .where('T', GTNPreview.state(Blocks.END_ROD, "facing=down"))   // minecraft:end_rod
                        .where('U', GTNPreview.stateOrAir("ae2:crystal_resonance_generator", "facing=down", "waterlogged=false"))   // ae2:crystal_resonance_generator
                        .where('V', GTMachines.ENERGY_INPUT_HATCH[1], Direction.SOUTH)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                        .where('W', GTMachines.MAINTENANCE_HATCH, Direction.NORTH)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                        .where('X', GTMachines.ITEM_EXPORT_BUS[1], Direction.EAST)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                        .build())
                .workableCasingModel(
                        GTCEu.id("block/casings/hpca/computer_casing/front"),
                        GTCEu.id("block/multiblock/hpca"))
                .register();
        return def;
    }

    /**
     * GT6 风味能量输入仓 —— 单方块部件机器。
     *
     * <p>复用 {@link PartAbility#INPUT_ENERGY}：现有所有多方块结构里
     * {@code Predicates.abilities(PartAbility.INPUT_ENERGY)} 的能量位都能直接接受本仓，
     * 无需改动任何现有机器 pattern。仓本身不存能量，只做"信号透传"：
     * 相邻 GTO 导体每 tick 刷新仓内值，配方条件（GTOEnergyRangeCondition）读仓内实时值。</p>
     */
    public static MachineDefinition registerGTOEnergyInputHatch() {
        return GTNMachines.REGISTRATE
                .machine("gtoe_energy_input_hatch", GTOEnergyInputHatchPartMachine::new)
                .langValue("GTOE 能量输入仓")
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.INPUT_ENERGY)
                .simpleModel(GTNCoreGT.id("block/machine/gtoe_energy_input_hatch"))
                .register();
    }

    /**
     * 燃烧引擎 —— GT6 风味 RU 产能机（GTM 单方块机器）。
     *
     * <p>挂 IFancyUIMachine：直接使用 GTM 原生 FancyUI（左侧 tab 栏 + 橙框槽位 +
     * 深灰面板，即 GT 机器标准界面）。燃料槽自动吞燃料，燃烧产 RU（转速）+ HU（热）。</p>
     */
    public static MachineDefinition registerCombustionEngine() {
        return GTNMachines.REGISTRATE
                .machine("combustion_engine", CombustionEngineMachine::new)
                .langValue("燃烧引擎")
                .rotationState(RotationState.NON_Y_AXIS)
                // 普通 block 模型（六面 casing + 正面），不用 gtceu:machine loader：
                // 引擎没有 RecipeLogic，loader 的 recipe_logic_status 变体匹配不到会紫黑格
                .simpleModel(GTNCoreGT.id("block/machine/combustion_engine"))
                .register();
    }

    /**
     * GTOE 电动产能机 15 压批量注册（EU → CU/KU/MU/LU/QU，ULV~MAX）。
     *
     * <p>沿用 {@link GTMachineUtils#registerTieredMachines}：id = {@code <电压名小写>_<name>}
     * （如 lv_cryogenic_condenser / max_quantum_condenser），构造器收 {@code (holder, tier)}。
     * 模型走普通 block 模型（无 RecipeLogic，不用 gtceu:machine loader），
     * 每压一套独立模型资源（贴图色调随 VNF 变化，由 tools/gen_energy_producers.py 生成）。</p>
     */
    public static MachineDefinition[] registerTieredGTOProducer(String name,
            java.util.function.BiFunction<com.gregtechceu.gtceu.api.machine.IMachineBlockEntity, Integer,
                    com.gregtechceu.gtceu.api.machine.MetaMachine> factory, String lang) {
        return GTMachineUtils.registerTieredMachines(GTNMachines.REGISTRATE, name, factory,
                (tier, builder) -> builder
                        .langValue(GTValues.VN[tier] + " " + lang)
                        .rotationState(RotationState.NON_Y_AXIS)
                        .simpleModel(GTNCoreGT.id("block/machine/"
                                + GTValues.VN[tier].toLowerCase(java.util.Locale.ROOT) + "_" + name))
                        .register(),
                GTValues.ALL_TIERS);
    }
    private GTNEnergyMachines() {
    }
}
