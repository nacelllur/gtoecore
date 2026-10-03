package com.gtoecore.gt;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtoecore.GTNBlocks;
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

/**
 * GT-New 多方块机器注册中心（标准流程的代码侧入口）。
 *
 * <p><b>以后每次新增多方块机器，照下面的步骤走：</b></p>
 * <ol>
 *   <li>把 {@link #registerExampleMultiblock()} 整个方法复制一份，改名
 *       （方法名 = "register" + 机器名驼峰）。</li>
 *   <li>改 5 个关键参数：机器 id、语言名、配方类型、外壳方块、pattern。
 *       需要自定义逻辑（如线圈机/蒸馏塔）时才换成自己的机器类，
 *       普通电动多方块直接用 {@link WorkableElectricMultiblockMachine}。</li>
 *   <li>在 {@link #initMachines()} 末尾调用该方法，返回值自动进
 *       {@link #REGISTERED}（创造模式标签页随之生效）。</li>
 *   <li>运行工作区脚本 {@code tools/add_multiblock.py} 自动生成 6 个资源文件
 *       （blockstate / 模型 / 物品模型 / 双语 lang / 合成配方）。</li>
 *   <li>{@code gradle build --offline} → 拷 jar 到 GT-New/mods → 重启游戏验证。</li>
 * </ol>
 *
 * <p><b>生物系三机（2026-09-30 按投影 dump 重写）：</b></p>
 * <ul>
 *   <li>pattern 坐标约定（FactoryBlockPattern）：<b>第一个 aisle = 背面</b>
 *       （控制器朝向的反方向），<b>最后一个 aisle = 正面</b>（控制器所在面）；
 *       aisle 内<b>第一个字符串 = 最底层</b>；字符 index 0 = 站在正面看控制器时的<b>左手侧</b>。</li>
 *   <li>第三方 mod 方块（ad_astra 气闸/通风口/NASA 工作台、ae2 天空石罐）
 *       <b>必须在 pattern lambda 内懒查</b>（{@link #modBlock}）——
 *       写成 static final 字段会在 mod 构造期过早求值得到 air 并永久缓存，
 *       导致结构里这些位置变成"要求空气"（实测踩坑）。</li>
 * </ul>
 */
public final class GTNMachines {

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GTNCoreGT.MODID);

    /** 本 mod 注册的全部机器（自动进 GT 机器创造标签页） */
    public static final List<MachineDefinition> REGISTERED = new ArrayList<>();

    // ---- 生物系多方块主机（配方类型：GTNRecipeTypes.BIO_RECIPES = gtceu:bio「生物」）----
    public static MultiblockMachineDefinition CLONE_PRODUCTION_WORKSHOP;
    public static MultiblockMachineDefinition CLONE_MANUFACTURING_CHAMBER;
    public static MultiblockMachineDefinition CLONE_MAINTENANCE_ROOM;

    // ---- 能源多方块 ----
    public static MultiblockMachineDefinition SOLAR_ARRAY_MODULE;

    // ---- 无人机多方块（配方类型：GTNRecipeTypes.DRONE_SWARM_RECIPES = gtceu:drone_swarm）----
    public static MultiblockMachineDefinition DRONE_SWARM_HEART;

    // ---- 深空枢纽体系（主机 + 6 类可无限叠加的扩展模块）----
    public static MultiblockMachineDefinition DEEP_SPACE_HUB;
    /** 扩展模块定义，索引 = ModuleKind.ordinal() */
    public static final MultiblockMachineDefinition[] DEEP_SPACE_MODULES =
            new MultiblockMachineDefinition[ModuleKind.values().length];

    public static List<MachineDefinition> allMachines() {
        return Collections.unmodifiableList(REGISTERED);
    }

    /** 在 mod 构造期调用：挂载 registrate（此时不能注册机器，见类注释时序表） */
    public static void register(IEventBus modEventBus) {
        REGISTRATE.registerRegistrate();
    }

    /**
     * 在 GTCEuAPI.RegisterEvent 窗口调用（由 GTNCoreGT 挂载的监听触发）。
     * 每新增一台机器在末尾加一行。
     */
    public static void initMachines() {
        // REGISTERED.add(registerExampleMultiblock());  // ← 取消注释即启用模板机器

        // ---- 生物系三台主机（外壳统一为惰性 PTFE 外壳，覆层为各机器专属贴图）----
        REGISTERED.add(CLONE_PRODUCTION_WORKSHOP = registerCloneProductionWorkshop());
        REGISTERED.add(CLONE_MANUFACTURING_CHAMBER = registerCloneManufacturingChamber());
        REGISTERED.add(CLONE_MAINTENANCE_ROOM = registerCloneMaintenanceRoom());

        // ---- 能源 ----
        REGISTERED.add(SOLAR_ARRAY_MODULE = registerSolarArrayModule());

        // ---- 无人机蜂群之心 ----
        REGISTERED.add(DRONE_SWARM_HEART = registerDroneSwarmHeart());

        // ---- 深空枢纽体系（主机 + 6 类扩展模块，原理对标 GTO 通天之路）----
        REGISTERED.add(DEEP_SPACE_HUB = registerDeepSpaceHub());
        for (ModuleKind kind : ModuleKind.values()) {
            REGISTERED.add(DEEP_SPACE_MODULES[kind.ordinal()] = registerDeepSpaceModule(kind));
        }

        // 修改已有 GT/GCYM 多方块（结构/修饰器/配方类型），见 GTNMachineOverrides
        GTNMachineOverrides.applyAll();
    }

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
        MultiblockMachineDefinition def = REGISTRATE
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
        MultiblockMachineDefinition def = REGISTRATE
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
        MultiblockMachineDefinition def = REGISTRATE
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
        MultiblockMachineDefinition def = REGISTRATE
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
        MultiblockMachineDefinition def = REGISTRATE
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
        MultiblockMachineDefinition def = REGISTRATE
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
                        new ResourceLocation("minecraft", "block/smooth_quartz"),
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
        MultiblockMachineDefinition def = REGISTRATE
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
                        new ResourceLocation("minecraft", "block/light_gray_concrete"),
                        GTNCoreGT.id("block/multiblock/deep_space_module"))
                .register();
        return def;
    }

    // ------------------------------------------------------------------
    // ★ 标准模板（默认不调用 → 不会注册进游戏）。
    // 保证可编译：API 变动后跑一次 build 即可发现模板过期。
    // 新机器从复制本方法开始；各步骤注意点见行内注释。
    // ------------------------------------------------------------------
    public static MultiblockMachineDefinition registerExampleMultiblock() {
        MultiblockMachineDefinition def = REGISTRATE
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
        REGISTERED.add(def);
        return def;
    }
}
