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
 * 深空枢纽体系（主机 + 可无限叠加的扩展模块）——
 * 从 {@link GTNMachines} 拆出（2026-10-14），逻辑与注册顺序完全不变。
 */
public final class GTNDeepSpaceMachines {

    // ------------------------------------------------------------------
    // 深空枢纽体系（原理对标 GTO 通天之路 road_of_heaven；外观照抄 EARTH-STAR 调色板）
    //
    // EARTH-STAR 主色统计（D:\GT-New\schematics\earth-star-modules\manifest.txt）：
    //   smooth_quartz 平滑石英 / polished_blackstone 抛光黑石 / cyan_terracotta 青色陶瓦
    //   light_gray_concrete 浅灰混凝土 / deepslate_tiles 深板岩砖 / tinted_glass 染色玻璃
    //   sea_lantern 海晶灯（照明）
    // 本组机器全部用上述原版方块搭建，零新增自创方块/贴图。
    // ------------------------------------------------------------------

    /** 枢纽塔：7 宽 × 11 高 × 7 深 */
    private static final int HUB_W = 7;
    private static final int HUB_H = 11;
    private static final int HUB_D = 7;

    /**
     * 生成枢纽塔某一层（z 轴切片）的 11 行字符串。
     *
     * <p>行序 0 = 最底层（GT 的 aisle 内要求从底到顶）。结构要素：
     * ① 地板深板岩砖；② 天花板平滑石英 + 海晶灯阵列；
     * ③ 侧壁中部浅灰染色玻璃带（空间站落地窗）；④ 中心 3×3×3 青色陶瓦能量核心；
     * ⑤ 正面墙放控制器。</p>
     */
    private static String[] hubSlice(int z, boolean front) {
        String[] rows = new String[HUB_H];
        for (int y = 0; y < HUB_H; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < HUB_W; x++) {
                boolean edgeX = x == 0 || x == HUB_W - 1;
                boolean edgeZ = z == 0 || z == HUB_D - 1;
                boolean floor = y == 0;
                boolean ceil = y == HUB_H - 1;
                boolean shell = floor || ceil || edgeX || edgeZ;
                boolean core = x >= 2 && x <= 4 && y >= 4 && y <= 6 && z >= 2 && z <= 4;
                boolean window = y >= 4 && y <= 7 && (edgeX || edgeZ);

                char c;
                if (front && x == HUB_W / 2 && y == 1) {
                    c = 'S';                                  // 控制器：正面墙第二层中央
                } else if (!shell) {
                    c = core ? 'C' : 'A';                     // 核心 / 空腔
                } else if (core) {
                    c = 'C';                                  // 中心能量核心
                } else if (floor) {
                    c = 'D';                                  // 地板：深板岩砖
                } else if (ceil) {
                    c = (x % 2 == 1 && z % 2 == 1) ? 'L' : '#'; // 天花板：平滑石英 + 海晶灯
                } else if (window) {
                    c = 'G';                                  // 侧壁窗带：浅灰染色玻璃
                } else {
                    c = '#';                                  // 主壳体：平滑石英
                }
                sb.append(c);
            }
            rows[y] = sb.toString();
        }
        return rows;
    }

    /**
     * 深空枢纽主机 —— 7×7×11 塔式供能中枢。
     *
     * <p><b>原理对标 GTO 通天之路：</b>持续供能（每 tick 1A 本机等级）+ 每 80 tick 吃 1
     * 个深空燃料单元；任一断供则全网络模块停摆。附近 16 格内的扩展模块自动挂载，
     * 枢纽把聚合加成推送给网络内每台模块。</p>
     *
     * <p>bk外观照抄 EARTH-STAR：平滑石英主壳 + 深板岩砖地板 + 浅灰染色玻璃窗带 +
     * 青色陶瓦核心 + 海晶灯照明。</p>
     */
    public static MultiblockMachineDefinition registerDeepSpaceHub() {
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock("deep_space_hub", DeepSpaceHubMachine::new)
                .langValue("Deep Space Hub")
                .rotationState(RotationState.NON_Y_AXIS)
                // 主机不跑配方：只做供能 + 耗材 + 网络加成聚合
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(() -> Blocks.SMOOTH_QUARTZ)
                .tooltips(
                        Component.translatable("gtoecore.machine.deep_space_hub.tooltip.1"),
                        Component.translatable("gtoecore.machine.deep_space_hub.tooltip.2"),
                        Component.translatable("gtoecore.machine.deep_space_hub.tooltip.3"),
                        Component.translatable("gtoecore.machine.deep_space_hub.tooltip.4"))
                .pattern(definition -> {
                    FactoryBlockPattern pat = FactoryBlockPattern.start();
                    for (int z = 0; z < HUB_D; z++) {
                        pat.aisle(hubSlice(z, z == HUB_D - 1));
                    }
                    return pat
                            .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                            // 主壳体：可换能量输入仓（供能）、物品输入仓（投燃料）、维护仓
                            .where('#', Predicates.blocks(Blocks.SMOOTH_QUARTZ)
                                    .setMinGlobalLimited(120)
                                    .or(Predicates.abilities(PartAbility.INPUT_ENERGY))
                                    .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                                    .or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1)))
                            .where('D', Predicates.blocks(Blocks.DEEPSLATE_TILES))
                            .where('G', Predicates.blocks(Blocks.LIGHT_GRAY_STAINED_GLASS))
                            .where('C', Predicates.blocks(Blocks.CYAN_TERRACOTTA))
                            .where('L', Predicates.blocks(Blocks.SEA_LANTERN))
                            .where('A', Predicates.air())
                            .build();
                })
                .workableCasingModel(
                GTNCoreGT.id("block/casing/deep_space_hub"),
                GTNCoreGT.id("block/multiblock/deep_space_hub"))
                .register();
        return def;
    }

    /**
     * 深空枢纽扩展模块 —— 3×3×3 独立多方块，放在枢纽 16 格内自动挂载。
     *
     * <p><b>无限叠加：</b>每种模块可造任意多台；枢纽按「同类模块数量」聚合后推送给
     * 网络内每台模块，模块的配方修饰器据此乘算 —— 并行线性累加，耗时/EU/产出/耗材
     * 指数累乘（均有下限保护）。</p>
     *
     * <p>外观照抄 EARTH-STAR：浅灰混凝土壳体 + 抛光黑石立柱 + 青色陶瓦核心。</p>
     */
    public static MultiblockMachineDefinition registerDeepSpaceModule(ModuleKind kind) {
        String id = kind.blockId();
        MultiblockMachineDefinition def = GTNMachines.REGISTRATE
                .multiblock(id, DeepSpaceModuleMachine::new)
                .langValue(kind.en())
                .rotationState(RotationState.ALL)
                // 模块跑两类深空配方（与蜂群之心同类型）
                .recipeTypes(GTNRecipeTypes.SPACE_MINING_RECIPES,
                        GTNRecipeTypes.DEEP_SPACE_EXPLORATION_RECIPES)
                .recipeModifiers(ModuleRecipeModifiers.MODULE_BONUS,
                        GTRecipeModifiers.OC_NON_PERFECT_SUBTICK,
                        GTRecipeModifiers.BATCH_MODE)
                .appearanceBlock(() -> Blocks.LIGHT_GRAY_CONCRETE)
                .tooltips(
                        Component.translatable(kind.tooltipKey()),
                        Component.translatable(kind.bonusKey()),
                        Component.translatable("gtoecore.machine.deep_space_module.tooltip"))
                .pattern(definition -> FactoryBlockPattern.start()
                        // 3×3×3：地板/天花板/立柱全外壳，背墙中央嵌本类核心；正面墙放控制器
                        .aisle("###", "#O#", "###")   // 背面
                        .aisle("###", "#A#", "###")   // 中段
                        .aisle("#S#", "###", "###")   // 正面（控制器在底排中央）
                        .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                        // 壳体：可换能量/物品/流体仓 + 维护仓
                        .where('#', Predicates.blocks(Blocks.LIGHT_GRAY_CONCRETE)
                                .setMinGlobalLimited(10)
                                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                .or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1)))
                        // 立柱用抛光黑石点缀（照抄 EARTH-STAR 的深色骨架）
                        .where('O', Predicates.blocks(Blocks.CYAN_TERRACOTTA)
                                .or(Predicates.blocks(Blocks.POLISHED_BLACKSTONE)))
                        .where('A', Predicates.air())
                        .build())
                .workableCasingModel(
                GTNCoreGT.id("block/casing/deep_space_module"),
                GTNCoreGT.id("block/multiblock/deep_space_module"))
                .register();
        return def;
    }
    private GTNDeepSpaceMachines() {
    }
}
