package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;
import com.gtoecore.GTNBlocks;
import com.gtoecore.util.GTNPreview;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 修改【已有的】GT/GCYM 多方块机器（不新增机器）。
 *
 * <p>原理：GTM 机器注册后、注册表 freeze 前（即 {@link GTNMachines#initMachines()}
 * 所在的 RegisterEvent 窗口），{@code MultiblockMachineDefinition} 的以下入口
 * 仍然公开可改：</p>
 * <ul>
 *   <li>{@code setPatternFactory(Supplier<BlockPattern>)} —— 换结构（最常用）</li>
 *   <li>{@code setRecipeModifier(RecipeModifier)} —— 换/追加配方修饰器</li>
 *   <li>{@code setRecipeTypes(GTRecipeType[])} —— 换配方类型</li>
 *   <li>{@code setTier(int)} / {@code setAppearance(...)} / {@code setShapes(...)} 等</li>
 * </ul>
 *
 * <p>pattern 是<b>懒构建</b>的（首次结构检查才 build），此窗口替换工厂后，
 * {@code PartAbility.getAllBlocks()} 快照反而是完整的 —— 比依赖快照时序更稳。</p>
 *
 * <p>用法：每改一台机器写一个方法（照抄 {@link #exampleOverrideVacuumFreezer()}
 * 模板），然后在 {@link #applyAll()} 里加一行。target 字段查
 * {@code GTMultiMachines} / {@code GCYMMachines} 的 public static 字段。</p>
 *
 * <p><b>注意：</b></p>
 * <ul>
 *   <li>改结构会让<b>已建成的结构失效</b>（需拆掉重摆）；JEI 里的形状预览是另一份数据，
 *       结构改了要同步 {@code setShapes(...)}，否则预览与实际不符。</li>
 *   <li>换 pattern 时把官方源码的 pattern <b>原样搬来再改</b>（位置：gtceu_src 的
 *       GTMultiMachines.java / GCYMMachines.java，或在游戏里查多方块的形状），
 *       不要凭记忆手写。</li>
 *   <li>配方内容的增删改不走这里 —— 那是数据包层（data/gtceu/recipes/ 覆盖），
 *       见文档《GT-New-多方块机器添加标准流程.md》第 7 章。</li>
 * </ul>
 */
public final class GTNMachineOverrides {

    private GTNMachineOverrides() {}

    /** 由 GTNMachines.initMachines() 调用（RegisterEvent 窗口，GTM 机器已注册、未 freeze） */
    public static void applyAll() {
        // 每改一台机器在这里加一行：
        overrideAlloyBlastSmelter();
        // exampleOverrideVacuumFreezer();
    }

    /**
     * ★ 标准模板（默认不调用 → 不改变游戏内容）：以真空冷冻机为例。
     *
     * <p>示例做两件事：① 给外壳谓词追加并行槽（原版只有维护舱）；
     * ② 修饰器链头部追加 PARALLEL_HATCH（原版只有 OC + BATCH，没有并行修饰器，
     * 只开槽不放修饰器 = 放进去也没用）。pattern 结构本身照抄官方源码。</p>
     */
    public static void exampleOverrideVacuumFreezer() {
        MultiblockMachineDefinition def = GTMultiMachines.VACUUM_FREEZER;

        // ---- ① 换结构：官方 pattern 原样搬来，只改需要改的谓词 ----
        def.setPatternFactory(() -> FactoryBlockPattern.start()
                .aisle("XXX", "XXX", "XXX")
                .aisle("XXX", "X#X", "XXX")
                .aisle("XXX", "XSX", "XXX")
                .where('S', Predicates.controller(Predicates.blocks(def.getBlock())))
                .where('X', Predicates.blocks(GTBlocks.CASING_ALUMINIUM_FROSTPROOF.get()).setMinGlobalLimited(14)
                        .or(Predicates.autoAbilities(def.getRecipeTypes()))
                        // 原版是 (true, false, false)：维护舱(必需) + 无消音 + 无并行
                        // 第 3 参改 true = 外壳上可放并行仓（开线程仓同理自动兼容）
                        .or(Predicates.autoAbilities(true, false, true)))
                .where('#', Predicates.air())
                .build());

        // ---- ② 追加修饰器：setRecipeModifier 是整体替换，取原链 + 新元素重新组包 ----
        // RecipeModifierList 按数组顺序依次执行（与 builder 的 recipeModifiers(...) 语义一致），
        // PARALLEL_HATCH 必须放最前：让它看到未经 OC 压缩的原始配方
        RecipeModifier original = def.getRecipeModifier();
        def.setRecipeModifier(new RecipeModifierList(GTRecipeModifiers.PARALLEL_HATCH, original));

        // ---- ③（可选）同步 JEI 形状预览：结构没变形可以不写 ----
        // def.setShapes(() -> List.of(MultiblockShapeInfo.builder()....build()));
    }

    // ====================================================================================
    // 合金高炉（gtceu:alloy_blast_smelter）—— 结构替换为投影《合金冶炼炉》
    // ====================================================================================
    //
    // 结构来源：D:\GT-New\schematics\合金冶炼炉.litematic（13 aisle(前后) x 13 row(上下) x 9 char(左右)）
    // 由 tools/litematic2gtm.py 生成，并已断言 bakeArray(输出) == getPreview(输出) 逐格一致。
    //
    // 与原版（5x5x5）的功能对应关系：
    //   · 外壳 high_temperature_smelting_casing x92  -> 沿用原版外壳谓词（可放仓室，维护仓必需）
    //   · 线圈 cupronickel_coil_block x20            -> Predicates.heatingCoils()
    //     ★ 必须用能力谓词：CoilWorkableElectricMultiblockMachine 从 matchContext 的 "CoilType"
    //       读温度等级；若写死方块 ID，线圈等级永远锁死在默认值（换线圈也会不成形）。
    //   · 消音仓 hv_muffler_hatch x1                 -> Predicates.abilities(PartAbility.MUFFLER)
    //   · 其余（石英/玻璃/按钮/墙/散热口/AE2/计算外壳）-> 纯装饰
    //
    // 注意：结构已变形 -> 存档里已建成的旧合金高炉会不成形，需拆掉按新形状重摆。
    public static void overrideAlloyBlastSmelter() {
        MultiblockMachineDefinition def = GCYMMachines.BLAST_ALLOY_SMELTER;

        // ---- (1) 结构 ----
        def.setPatternFactory(() -> FactoryBlockPattern.start()
                .aisle("    E    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // aisle 0
                .aisle("   EEE   ", "    F    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // aisle 1
                .aisle("   EEE   ", "    D    ", "    F    ", "         ", "         ", "         ", "         ", " F     F ", "         ", " F     F ", "         ", "         ", "         ")   // aisle 2
                .aisle("   EEE   ", "    F    ", "    D    ", "    F    ", "         ", "   A A   ", "   A A   ", " D AAA D ", "JD AAA DJ", " D AAA D ", "         ", "  BBBBB  ", "         ")   // aisle 3
                .aisle("    E    ", "         ", "    F    ", "    D    ", "   EEE   ", "   A A   ", "         ", " D III D ", "JDLIIILDJ", " D III D ", "    L    ", "  BDBDB  ", "         ")   // aisle 4
                .aisle("    E    ", "         ", "         ", "   FFF   ", "   EEE   ", "   A A   ", "         ", " D AAA D ", "JD MAM DJ", " D AAA D ", "    L    ", "  BDBDB  ", "         ")   // aisle 5
                .aisle("    E    ", "         ", "   F F   ", "   D D   ", "   EEE   ", "   A A   ", " B     B ", " F AAA F ", "   A A   ", " F AAA F ", " B     B ", "  B   B  ", "         ")   // aisle 6
                .aisle("   EEE   ", "   F F   ", "   D D   ", "   B F   ", "    B    ", "   A A   ", " B AAA B ", "  AHHHA  ", "  AH HA  ", "  AHHHA  ", " B AAA B ", "  B   B  ", "         ")   // aisle 7
                .aisle("   EEE   ", "   D D   ", "   F F   ", "         ", "    B    ", "         ", " B AAA B ", "  A A A  ", "  AA AA  ", "  A A A  ", " B AAA B ", "  B   B  ", "         ")   // aisle 8
                .aisle("   EOE   ", "   F F   ", "         ", "         ", "    B    ", "         ", " B GGG B ", "  G H G  ", "  GH HG  ", "  G H G  ", " B GGG B ", "  B   B  ", "         ")   // aisle 9
                .aisle("   AAA   ", "   ASA   ", "   AAA   ", "         ", "    B    ", "         ", " B GGG B ", "  G A G  ", "  GA AG  ", "  G A G  ", " B GGG B ", "  B   B  ", "         ")   // aisle 10
                .aisle("         ", "         ", "         ", "         ", "         ", "         ", " B AAA B ", "  AHHHA  ", "  AH HA  ", "  AHHHA  ", " B AAA B ", "  B   B  ", "         ")   // aisle 11
                .aisle("         ", "         ", "         ", "         ", "         ", "         ", " B     B ", "   KAK   ", "   ANA   ", "   KAK   ", " B     B ", "         ", "         ")   // aisle 12
                .where('S', Predicates.controller(Predicates.blocks(def.getBlock())))
                // 外壳：可换能量/物品/流体/维护仓（沿用原版语义：维护仓必需、无并行槽）
                .where('A', Predicates.blocks(GCYMBlocks.CASING_HIGH_TEMPERATURE_SMELTING.get())
                        .setMinGlobalLimited(30)
                        .or(Predicates.autoAbilities(def.getRecipeTypes()))
                        .or(Predicates.autoAbilities(true, false, false)))
                // 线圈：必须走 heatingCoils() —— CoilWorkableElectricMultiblockMachine
                // 从 matchContext 的 "CoilType" 读温度等级，写死方块会让等级永远锁死在默认值
                .where('H', Predicates.heatingCoils())
                // 消音仓：能力槽，任意等级消音仓都能放（与原版一致）
                .where('N', Predicates.abilities(PartAbility.MUFFLER))
                // 装饰（纯外观，无功能）
                .where('B', Predicates.blocks(Blocks.SMOOTH_QUARTZ_SLAB))
                .where('D', Predicates.blocks(Blocks.SMOOTH_QUARTZ))
                .where('E', Predicates.blocks(GTNBlocks.PRIMITIVE_COMPUTER_CASING.get()))
                .where('F', Predicates.blocks(Blocks.SMOOTH_QUARTZ_STAIRS))
                .where('G', Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                .where('I', Predicates.blocks(GTBlocks.CASING_ENGINE_INTAKE.get()))
                .where('J', Predicates.blocks(Blocks.POLISHED_BLACKSTONE_BUTTON))
                .where('L', Predicates.blocks(Blocks.ANDESITE_WALL))
                .where('O', Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get()))
                // 可选 mod 方块：缺失时退化为「任意」，而不是「必须是空气」
                .where('K', GTNPreview.blocksOrAny("ad_astra:vent"))
                .where('M', GTNPreview.blocksOrAny("ae2:crystal_resonance_generator"))
                .build());

        // ---- (2) JEI 形状预览：每种加热线圈一套（与原版行为一致），否则预览与实际不符 ----
        def.setShapes(() -> {
            List<MultiblockShapeInfo> shapes = new ArrayList<>();
            for (Map.Entry<ICoilType, Supplier<CoilBlock>> entry : GTCEuAPI.HEATING_COILS.entrySet()) {
                shapes.add(alloyBlastSmelterPreview(entry.getValue().get().defaultBlockState()));
            }
            return shapes;
        });
    }

    /** shapeInfo 片段由 tools/litematic2gtm.py 生成：aisle 倒序 + 行内左右倒序，状态按「控制器朝北」旋转 1 步 */
    private static MultiblockShapeInfo alloyBlastSmelterPreview(BlockState coilState) {
        return MultiblockShapeInfo.builder()
                .aisle("         ", "         ", "         ", "         ", "         ", "         ", " B     B ", "   NJN   ", "   JUJ   ", "   NJN   ", " I     I ", "         ", "         ")   // preview z=0
                .aisle("         ", "         ", "         ", "         ", "         ", "         ", " B JJJ B ", "  JOOOJ  ", "  JO OJ  ", "  JOOOJ  ", " I JJJ I ", "  B   B  ", "         ")   // preview z=1
                .aisle("   JZJ   ", "   aSb   ", "   dYJ   ", "         ", "    B    ", "         ", " B KKK B ", "  K J K  ", "  KJ JK  ", "  K J K  ", " I KKK I ", "  B   B  ", "         ")   // preview z=2
                .aisle("   MTM   ", "   D D   ", "         ", "         ", "    B    ", "         ", " B KKK B ", "  K O K  ", "  KO OK  ", "  K O K  ", " I KKK I ", "  B   B  ", "         ")   // preview z=3
                .aisle("   MMM   ", "   E E   ", "   D D   ", "         ", "    B    ", "         ", " B JJJ B ", "  J J J  ", "  JJ JJ  ", "  J J J  ", " I JJJ I ", "  B   B  ", "         ")   // preview z=4
                .aisle("   MMM   ", "   H H   ", "   E E   ", "   D I   ", "    B    ", "   J J   ", " B JJJ B ", "  JOOOJ  ", "  JO OJ  ", "  JOOOJ  ", " I JJJ I ", "  B   B  ", "         ")   // preview z=5
                .aisle("    M    ", "         ", "   H H   ", "   E E   ", "   MMM   ", "   J J   ", " B     B ", " D JJJ D ", "   J J   ", " G JJJ G ", " I     I ", "  B   B  ", "         ")   // preview z=6
                .aisle("    M    ", "         ", "         ", "   HGH   ", "   MMM   ", "   J J   ", "         ", " E JJJ E ", "AE RJR EX", " E JJJ E ", "    V    ", "  BEIEB  ", "         ")   // preview z=7
                .aisle("    M    ", "         ", "    G    ", "    E    ", "   MMM   ", "   J J   ", "         ", " E PPP E ", "AELPPPLEX", " E PPP E ", "    W    ", "  BEIEB  ", "         ")   // preview z=8
                .aisle("   MMM   ", "    G    ", "    E    ", "    F    ", "         ", "   J J   ", "   J J   ", " E JJJ E ", "AE JJJ EX", " E JJJ E ", "         ", "  BBBBB  ", "         ")   // preview z=9
                .aisle("   MMM   ", "    E    ", "    F    ", "         ", "         ", "         ", "         ", " F     F ", "         ", " H     H ", "         ", "         ", "         ")   // preview z=10
                .aisle("   MMM   ", "    F    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=11
                .aisle("    M    ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")   // preview z=12
                .where('S', GCYMMachines.BLAST_ALLOY_SMELTER.get(), Direction.NORTH)
                .where('J', GTNPreview.state(GCYMBlocks.CASING_HIGH_TEMPERATURE_SMELTING.get()))
                .where('K', GTNPreview.state(GTBlocks.CASING_TEMPERED_GLASS.get()))
                .where('M', GTNPreview.state(GTNBlocks.PRIMITIVE_COMPUTER_CASING.get()))
                .where('O', coilState)
                .where('P', GTNPreview.state(GTBlocks.CASING_ENGINE_INTAKE.get(), "active=false"))
                .where('T', GTNPreview.state(GTBlocks.HIGH_POWER_CASING.get()))
                .where('U', GTNPreview.stateOrAir("gtceu:hv_muffler_hatch", "facing=north"))
                .where('N', GTNPreview.stateOrAir("ad_astra:vent"))
                .where('R', GTNPreview.stateOrAir("ae2:crystal_resonance_generator", "facing=south", "waterlogged=false"))
                .where('A', GTNPreview.state(Blocks.POLISHED_BLACKSTONE_BUTTON, "face=wall", "facing=west", "powered=false"))
                .where('X', GTNPreview.state(Blocks.POLISHED_BLACKSTONE_BUTTON, "face=wall", "facing=east", "powered=false"))
                .where('B', GTNPreview.state(Blocks.SMOOTH_QUARTZ_SLAB, "type=top", "waterlogged=false"))
                .where('I', GTNPreview.state(Blocks.SMOOTH_QUARTZ_SLAB, "type=bottom", "waterlogged=false"))
                .where('D', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=south", "half=bottom", "shape=straight", "waterlogged=false"))
                .where('F', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=north", "half=bottom", "shape=straight", "waterlogged=false"))
                .where('G', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=south", "half=top", "shape=straight", "waterlogged=false"))
                .where('H', GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=north", "half=top", "shape=straight", "waterlogged=false"))
                .where('E', GTNPreview.state(Blocks.SMOOTH_QUARTZ))
                .where('L', GTNPreview.state(Blocks.ANDESITE_WALL, "east=none", "north=low", "south=low", "up=false", "waterlogged=false", "west=none"))
                .where('V', GTNPreview.state(Blocks.ANDESITE_WALL, "east=tall", "north=none", "south=none", "up=true", "waterlogged=false", "west=none"))
                .where('W', GTNPreview.state(Blocks.ANDESITE_WALL, "east=none", "north=none", "south=none", "up=true", "waterlogged=false", "west=tall"))
                .where('Y', GTMachines.ENERGY_INPUT_HATCH[1], Direction.SOUTH)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                .where('Z', GTMachines.MAINTENANCE_HATCH, Direction.NORTH)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                .where('a', GTMachines.ITEM_IMPORT_BUS[1], Direction.WEST)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                .where('b', GTMachines.FLUID_IMPORT_HATCH[1], Direction.WEST)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                .where('d', GTMachines.FLUID_EXPORT_HATCH[1], Direction.EAST)   // 预览注入：功能部件（pattern 用能力谓词接受，占外壳位）
                .build();
    }
}
