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

    // ---- GT6 风味能量输入仓（单方块部件机器，复用 PartAbility.INPUT_ENERGY）----
    public static MachineDefinition GTO_ENERGY_INPUT_HATCH;

    // ---- GT6 风味产能机：燃烧引擎（GTM 单方块机器，GT 原生 FancyUI）----
    public static MachineDefinition COMBUSTION_ENGINE;

    // ---- GT6 风味电动产能机（EU → 单一 GTO 能量，全部 GT 原生 FancyUI）----
    // 15 压（ULV~MAX，索引 = GTValues.tier）：注册用 GTMachineUtils.registerTieredMachines，
    // 生成 id = "<voltage>_<name>"（如 lv_cryogenic_condenser）。
    public static MachineDefinition[] CRYOGENIC_CONDENSER;   // [0..14]
    public static MachineDefinition[] PNEUMATIC_COMPRESSOR;
    public static MachineDefinition[] MAGNETIC_COIL;
    public static MachineDefinition[] LASER_FOCUS;
    public static MachineDefinition[] QUANTUM_CONDENSER;
    /** 聚光阵列（聚变光源 → 激光能数值），光凝流链路第二环 */
    public static MachineDefinition[] SOLAR_CONCENTRATOR;

    // ---- 聚变光源核心（与 GT 聚变反应堆结构/逻辑一致，主方块换皮）----
    public static MultiblockMachineDefinition[] LUMINA_FUSION_CORE;   // [6..8]，三级 MK1/MK2/MK3

    // ---- 事件视界引擎（35³ 球面线框笼 + 程序化黑洞特效）----
    public static MultiblockMachineDefinition EVENT_HORIZON_ENGINE;

    // ---- AE 合成终端多方块主机 ----
    public static MultiblockMachineDefinition AE_CRAFTING_TERMINAL;

    // ---- 机架类机器（底板上立着一具 OBJ 塔；尺寸 / 形态各按自己脚印取，2026-10-13 起四向可配）----
    // tower_rig = 烧液体燃料发电（真 GT 多方块）；其余 = 纯结构 + 外观。
    // 底座：tower 43×23 / arch 31×19 / mast 21×17 / drum 23×21 / blade 13×23 /
    //       cms 115×77 / maimai 1×2（用户指定）/ lhc_combo 无底座（用户指定）。
    public static MultiblockMachineDefinition TOWER_RIG;
    /** 拱架（arch_rig.obj，约 24×34×15 格）。 */
    public static MultiblockMachineDefinition ARCH_RIG;
    /** 桅杆（mast_rig.obj，约 17×33×13 格）。 */
    public static MultiblockMachineDefinition MAST_RIG;
    /** 滚筒（drum_rig.obj，约 20×29×18 格）。 */
    public static MultiblockMachineDefinition DRUM_RIG;
    /** 叶片（blade_rig.obj，约 11×27×19 格）。 */
    public static MultiblockMachineDefinition BLADE_RIG;
    /**
     * 舞萌街机柜（maimai_rig.obj，约 9.5×9.8×7.4 格）——
     * <b>唯一一台底座不由「碰撞体外延 + 一圈」决定的机器</b>：用户指定底座缩到
     * <b>1 宽 × 2 深</b>（控制器自己 + 正面外侧 1 格）。详见 {@link MaimaiRigMachine}。
     */
    public static MultiblockMachineDefinition MAIMAI_RIG;
    /**
     * CMS 探测器（cms_rig.obj，约 112×84×74 格）—— 115×77 的单层实心底座。
     * 碰撞表做过「填腔 + 单层壳」优化（142,752 → 95,061 格）。
     */
    public static MultiblockMachineDefinition CMS_RIG;
    /**
     * LHC 环线（lhc_combo.obj，约 203×203×45 格，横跨 13×13 区块）——
     * <b>没有底座</b>（{@code rigPlateLayers() = 0}），放下去即成形，塔直接坐地。
     */
    public static MultiblockMachineDefinition LHC_COMBO;

    /** 产能机 15 压数组（延迟取引用：静态初始化期字段还是 null，注册后调用才有效） */
    public static MachineDefinition[][] gtoProducers() {
        return new MachineDefinition[][]{
                CRYOGENIC_CONDENSER, PNEUMATIC_COMPRESSOR, MAGNETIC_COIL, LASER_FOCUS, QUANTUM_CONDENSER,
                SOLAR_CONCENTRATOR
        };
    }
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

        // ---- GT6 风味能量输入仓（复用 INPUT_ENERGY ⇒ 所有 pattern 的能量位自动接受）----
        REGISTERED.add(GTO_ENERGY_INPUT_HATCH = registerGTOEnergyInputHatch());

        // ---- GT6 风味产能机：燃烧引擎 ----
        REGISTERED.add(COMBUSTION_ENGINE = registerCombustionEngine());

        // ---- GT6 风味电动产能机 15 压（CU/KU/MU/LU/QU × ULV~MAX = 75 台，全部 GT 原生 FancyUI）----
        CRYOGENIC_CONDENSER = registerTieredGTOProducer(
                "cryogenic_condenser", CryogenicCondenserMachine::new, "低温冷凝机");
        PNEUMATIC_COMPRESSOR = registerTieredGTOProducer(
                "pneumatic_compressor", PneumaticCompressorMachine::new, "气动压缩机");
        MAGNETIC_COIL = registerTieredGTOProducer(
                "magnetic_coil", MagneticCoilMachine::new, "电磁线圈机");
        LASER_FOCUS = registerTieredGTOProducer(
                "laser_focus", LaserFocusMachine::new, "激光聚束器");
        QUANTUM_CONDENSER = registerTieredGTOProducer(
                "quantum_condenser", QuantumCondenserMachine::new, "量子凝聚器");
        SOLAR_CONCENTRATOR = registerTieredGTOProducer(
                "solar_concentrator", SolarConcentratorMachine::new, "聚光阵列");
        for (MachineDefinition[] defs : gtoProducers()) {
            for (MachineDefinition def : defs) {
                if (def != null) REGISTERED.add(def);
            }
        }

        // ---- 聚变光源核心：与 GT 聚变反应堆结构/逻辑一致，主方块换皮（三级 MK1/MK2/MK3）----
        LUMINA_FUSION_CORE = registerLuminaFusionCore();
        for (MultiblockMachineDefinition def : LUMINA_FUSION_CORE) {
            if (def != null) REGISTERED.add(def);
        }

        // ---- 事件视界引擎（35³ 球面线框笼，中央程序化黑洞）----
        REGISTERED.add(EVENT_HORIZON_ENGINE = registerEventHorizonEngine());

        // ---- AE 合成终端多方块主机（3×3×3 脱氧钢外壳，右键开 AE 合成终端界面）----
        GTOEMultiblockTerminalLocator.register();
        REGISTERED.add(AE_CRAFTING_TERMINAL = registerAECraftingTerminal());

        // ---- 机架类机器：底板上立着一具 OBJ 塔（DynamicRender），塔的隐形碰撞体随结构铺/收 ----
        // 底座尺寸各按自己塔的脚印取（tower 43×23 / arch 31×19 / mast 21×17 / drum 23×21 /
        // blade 13×23 / cms 115×77），见 RigController 的 rigPlateHalfLr / rigPlateHalfFb；
        // 2026-10-13 起的四向可配版额外支持「深度为偶数的 1×2 底座」（maimai_rig）
        // 与「完全没有底座」（lhc_combo）。
        // tower_rig 烧液体燃料发电；其余只做结构与外观（暂不接配方逻辑）。
        REGISTERED.add(TOWER_RIG = registerTowerRig());
        REGISTERED.add(ARCH_RIG = registerDecorRig(
                "arch_rig", ArchRigMachine::new, "Arch Rig",
                "arch_rig", ArchRigDynamicRender::new,
                ArchRigMachine.PLATE_HALF_LR, ArchRigMachine.PLATE_HALF_FB));
        REGISTERED.add(MAST_RIG = registerDecorRig(
                "mast_rig", MastRigMachine::new, "Mast Rig",
                "mast_rig", MastRigDynamicRender::new,
                MastRigMachine.PLATE_HALF_LR, MastRigMachine.PLATE_HALF_FB));
        REGISTERED.add(DRUM_RIG = registerDecorRig(
                "drum_rig", DrumRigMachine::new, "Drum Rig",
                "drum_rig", DrumRigDynamicRender::new,
                DrumRigMachine.PLATE_HALF_LR, DrumRigMachine.PLATE_HALF_FB));
        REGISTERED.add(BLADE_RIG = registerDecorRig(
                "blade_rig", BladeRigMachine::new, "Blade Rig",
                "blade_rig", BladeRigDynamicRender::new,
                BladeRigMachine.PLATE_HALF_LR, BladeRigMachine.PLATE_HALF_FB));

        // 舞萌街机柜：底座 1 宽 × 2 深（控制器自己 + 正面外侧 1 格）。
        // ⚠ 这是唯一走「四向」重载的机器 —— back = 0（塔中心 = 控制器自己那一格）、
        //   front = 1（底座往正面多伸 1 格当踏脚）。深度为偶数，塔中心只能落在
        //   两排中的一排上，所以选了控制器那一排。
        REGISTERED.add(MAIMAI_RIG = registerDecorRig(
                "maimai_rig", MaimaiRigMachine::new, "Maimai Rig",
                "maimai_rig", MaimaiRigDynamicRender::new,
                MaimaiRigMachine.PLATE_HALF_LR, MaimaiRigMachine.PLATE_HALF_LR,
                0, MaimaiRigMachine.PLATE_FRONT_EXTENT, 3));
        // CMS 探测器：115×77 单层实心底座（老式「控制器贴正面边缘」的形态）。
        REGISTERED.add(CMS_RIG = registerDecorRig(
                "cms_rig", CmsRigMachine::new, "CMS Rig",
                "cms_rig", CmsRigDynamicRender::new,
                CmsRigMachine.PLATE_HALF_LR, CmsRigMachine.PLATE_HALF_FB));
        // LHC 环线：1×1×1 —— 完全没有底座，放下即成形，203 格直径的环直接坐在地面上。
        // 塔横跨 13×13 区块，所以多给一行 tooltip 提醒玩家它对性能/视野的影响。
        REGISTERED.add(LHC_COMBO = registerDecorRig(
                "lhc_combo", LhcComboRigMachine::new, "LHC Combo",
                "lhc_combo", LhcComboRigDynamicRender::new,
                0, 0, 0, 0, 4));

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
                GTNCoreGT.id("block/casing/deep_space_module"),
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

    /**
     * GT6 风味能量输入仓 —— 单方块部件机器。
     *
     * <p>复用 {@link PartAbility#INPUT_ENERGY}：现有所有多方块结构里
     * {@code Predicates.abilities(PartAbility.INPUT_ENERGY)} 的能量位都能直接接受本仓，
     * 无需改动任何现有机器 pattern。仓本身不存能量，只做"信号透传"：
     * 相邻 GTO 导体每 tick 刷新仓内值，配方条件（GTOEnergyRangeCondition）读仓内实时值。</p>
     */
    private static MachineDefinition registerGTOEnergyInputHatch() {
        return REGISTRATE
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
    private static MachineDefinition registerCombustionEngine() {
        return REGISTRATE
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
    private static MachineDefinition[] registerTieredGTOProducer(String name,
            java.util.function.BiFunction<com.gregtechceu.gtceu.api.machine.IMachineBlockEntity, Integer,
                    com.gregtechceu.gtceu.api.machine.MetaMachine> factory, String lang) {
        return GTMachineUtils.registerTieredMachines(REGISTRATE, name, factory,
                (tier, builder) -> builder
                        .langValue(GTValues.VN[tier] + " " + lang)
                        .rotationState(RotationState.NON_Y_AXIS)
                        .simpleModel(GTNCoreGT.id("block/machine/"
                                + GTValues.VN[tier].toLowerCase(java.util.Locale.ROOT) + "_" + name))
                        .register(),
                GTValues.ALL_TIERS);
    }

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
    private static MultiblockMachineDefinition[] registerLuminaFusionCore() {
        return GTMachineUtils.registerTieredMultis(REGISTRATE, "lumina_fusion_core",
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

    // ------------------------------------------------------------------
    // 事件视界引擎（Event Horizon Engine）—— 35³ 六面舱壁球壳 + 黑洞/日冕特效
    // ------------------------------------------------------------------

    /**
     * 事件视界引擎 —— 35³ 球壳里关着一个黑洞（或一团日冕）。
     *
     * <p><b>结构</b>：{@link GTNEventHorizonStructure} 直接投影自
     * {@code 144.litematic} —— 3395 格 {@code hull_panel_hex} 六面舱壁构成的球壳，
     * 内部是 GTNA 骨架（6 根轴柱 + 赤道带 casing + 16 格 annihilate_core），
     * 外加一点 AE2 装饰（纯外观，不参与成形）。网格走资源文件，
     * 由 {@code tools/gen_eh_structure.py} 生成。</p>
     *
     * <p><b>控制器位置</b>：球壳赤道面上（aisle=35/row=17/char=18），
     * 球壳几何中心在控制器 BACK 方向 17 格 —— 与旧结构完全一致，
     * 所以 {@link EventHorizonRenderer} 的 {@code CENTER_OFFSET = 17.0} 不用改。</p>
     *
     * <p><b>特效</b>：{@link EventHorizonRenderer}。UI 里有 1/2 模式按钮：
     * 1 = 烘焙黑洞（引力透镜帧集公告板），2 = 真 3D 碗口日冕（黑碗 + 白热光子环）。</p>
     *
     * <p>⚠ 2026-10-07 结构已从「球面线框笼」换成「实心舱壁球壳」⇒
     * 存档里已摆好的旧机器<b>必定不成形，必须拆掉重摆</b>。</p>
     */
    private static MultiblockMachineDefinition registerEventHorizonEngine() {
        return REGISTRATE
                .multiblock("event_horizon_engine", EventHorizonMachine::new)
                .langValue("Event Horizon Engine")
                // 控制器贴在球面外侧的水平面上 ⇒ 只用水平朝向
                .rotationState(RotationState.NON_Y_AXIS)
                // ⚠ 关掉扩展朝向（照抄 GTNA 的 eye_of_harmony）：
                //   开着的话 block 会多出 upwards_facing 属性、16 种状态，
                //   而 checkPatternAt 用的是控制器**真实的** upwards_facing
                //   —— 球面线框只关于 x↔z 互换对称、不关于 y↔z 互换，
                //   一旦玩家用扳手敲正面把 upwards 滚成东/南/西，
                //   结构映射就整体转 90°，机器会毫无提示地不成形。
                .allowExtendedFacing(false)
                .recipeType(GTNRecipeTypes.EVENT_HORIZON_RECIPES)
                .recipeModifiers(GTRecipeModifiers.OC_NON_PERFECT_SUBTICK,
                        GTRecipeModifiers.BATCH_MODE)
                .tooltips(
                        Component.translatable("gtoecore.machine.event_horizon_engine.tooltip.1"),
                        Component.translatable("gtoecore.machine.event_horizon_engine.tooltip.2"),
                        Component.translatable("gtoecore.machine.event_horizon_engine.tooltip.3"))
                // 外壳现在是六面舱壁 —— JEI 里显示它（而不是旧的 singularity 外壳）
                .appearanceBlock(GTNBlocks.HULL_PANEL_HEX)
                .pattern(GTNEventHorizonStructure::create)
                // 有 RecipeLogic ⇒ 模型必须带 recipe_logic_status 变体，否则紫黑格
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                .model(GTMachineModels.createWorkableCasingMachineModel(
                                GTNCoreGT.id("block/casing/singularity_constraint_casing"),
                                GTNCoreGT.id("block/multiblock/event_horizon_engine"))
                        .andThen(b -> b.addDynamicRenderer(EventHorizonRenderer::new)))
                .hasBER(true)
                .register();
    }

    // ------------------------------------------------------------------
    // 塔架动力塔（Tower Rig Power Tower）
    //
    // 原来的 gtoecore:tower_rig 只是 objdemo 里的「隐形占位 + BER 画 OBJ」渲染示范：
    // 摆着好看但什么也不干。现在它是真正的 GTM 多方块 —— 结构塔基 + 成形后长出的塔，
    // 烧液体燃料发电。塔的视觉走 DynamicRender（见 TowerRigDynamicRender 的类注释）。
    // ------------------------------------------------------------------

    /**
     * 塔架动力塔 —— 43 × 1 × 23 的<b>单层实心脱氧钢底座</b>，控制器嵌在正面边缘的中点；
     * 底座顶面之上由 {@code TowerRigDynamicRender} 画出 {@code tower_rig.obj} 塔。
     *
     * <p><b>结构（aisle 从背面到正面；本结构只有 1 层，所以每个 aisle 只有 1 个字符串）：</b></p>
     * <ul>
     *   <li>23 张切片全是同一行 43 个 {@code #}（脱氧钢），只有<b>正面</b>那一切片的
     *       中点换成控制器 {@code S}；</li>
     *   <li>底座共 43 × 23 = 989 格，1 格是控制器，其余 988 格全是脱氧钢 ——
     *       任意位置都可以换成仓室，也就是「边缘位置放舱室」；</li>
     *   <li>本机的配方类型是 {@code COMBUSTION_GENERATOR_FUELS}（EU 输出 + 流体输入），
     *       所以 {@code autoAbilities} 会自动开放<b>能量输出仓</b>（下限 1，必需）
     *       与<b>流体输入仓</b>；</li>
     *   <li>维护仓用「可选槽」写法（{@code setMaxGlobalLimited(1)}，不设下限）——
     *       不强制玩家放维护仓。</li>
     * </ul>
     *
     * <p><b>为什么是 43 × 23 而不是正好等于塔脚印的 41 × 21：</b>塔底是一块 1.7 格厚的
     * <b>实心台</b>（OBJ 里的 {@code foot_edge} 伸到 x ±19.97 / z ±10.51）—— 41 × 21 的
     * 底座会被它 100% 盖死，连控制器都点不到（右键不出界面）。每边多留一圈（+1 格）正好
     * 给出一条可站立的边；而落地碰撞体的坐标表本来就不含最外圈（生成时用了
     * {@code --inset 0.02}，0.01 格的发丝级溢出不会刷出整格），所以那一条边是空的。
     * 详见 {@link TowerRigMachine} 与 {@code RigCollision} 的类注释。</p>
     *
     * <p><b>三台机器的底座尺寸互不相同</b>（都是「自己那具塔的脚印 + 一圈」）：
     * tower 43 × 23 / arch 31 × 19 / mast 21 × 17 —— 尺寸定义在各自的机器类里
     * （{@code PLATE_HALF_LR / PLATE_HALF_FB}），{@link #registerTowerRig} 与
     * {@link #registerDecorRig} 显式传给下面的生成器。
     * {@code RigController} 只提供<b>默认值</b>（= tower 的 21 / 11）。</p>
     *
     * <p><b>⚠ 结构尺寸变了要同步改三处：</b>机器类里的 {@code PLATE_HALF_LR} /
     * {@code PLATE_HALF_FB} 常量<b>与</b>它覆写的 {@code rigPlateHalfLr()} /
     * {@code rigPlateHalfFb()}（渲染器和碰撞体读的是方法，pattern 读的是常量 ——
     * 两者必须一致）、该机器自己的碰撞体坐标表
     * {@code assets/gtoecore/structures/}<i>&lt;name&gt;</i>{@code _collision.txt}
     * （重烤：{@code tools/gen_tower_collision.py <obj> <out> --scale 4.45 --keep-origin
     * --clamp-x <半宽> --clamp-z <半深>}），
     * 以及 lang 里 tooltip 中写死的尺寸文案。
     * 本文件的 {@link #rigPattern} / {@link #rigShapeInfo} <b>不用改</b>（尺寸是入参）。
     * {@code blockstates/*.json} 不用动 —— {@code allowExtendedFacing(false)}
     * ⇒ 只有 4 个 facing 变体，不随结构尺寸变。</p>
     */
    private static MultiblockMachineDefinition registerTowerRig() {
        return REGISTRATE
                .multiblock("tower_rig", TowerRigMachine::new)
                .langValue("Tower Rig Power Tower")
                .rotationState(RotationState.NON_Y_AXIS)
                // 塔是细高的非对称造型 ⇒ 关掉扩展朝向（同事件视界引擎）：
                // 开着的话 block 会多出 upwards_facing 属性（16 种状态），
                // 而 checkPatternAt 用的是控制器真实的 upwards_facing ——
                // 玩家用扳手敲正面把它滚 90°，结构映射跟着整体转，机器会
                // 毫无提示地不成形。
                .allowExtendedFacing(false)
                // 复用 GTM 现成的燃料发电机配方类型：玩家灌任意可燃液体就能发电，
                // JEI 里已有全部燃料配方。EU 是「输出」（setEUIO(IO.OUT)）。
                .recipeType(GTRecipeTypes.COMBUSTION_GENERATOR_FUELS)
                .recipeModifiers(GTRecipeModifiers.OC_NON_PERFECT)
                .tooltips(
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.1"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.2"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.3"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.4"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.5"))
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                // 结构与预览由三台机架机器共用（见 rigPattern / rigShapeInfo）；
                // 只有本机是「可工作」的 ⇒ 外壳谓词额外开放配方需要的能力仓。
                // 底座尺寸显式传 tower 的 43 × 23（= RigController 的默认值）。
                .pattern(definition -> rigPattern(definition, true,
                        RigController.PLATE_HALF_LR, RigController.PLATE_HALF_FB))
                .shapeInfo(definition -> rigShapeInfo(definition, true,
                        RigController.PLATE_HALF_LR, RigController.PLATE_HALF_FB))
                // 有 RecipeLogic ⇒ 模型必须带 recipe_logic_status 变体，否则紫黑格
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                // 视觉：控制器本体还是常规 GT 外壳 + 覆层；塔由动态渲染器画在底座顶面上方
                .model(GTMachineModels.createWorkableCasingMachineModel(
                                GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                                GTCEu.id("block/multiblock/generator/large_combustion_engine"))
                        .andThen(b -> b.addDynamicRenderer(TowerRigDynamicRender::new)))
                .hasBER(true)
                .register();
    }

    /**
     * 拱架（{@code arch_rig}）/ 桅杆（{@code mast_rig}）/ 滚筒（{@code drum_rig}）/
     * 叶片（{@code blade_rig}）/ CMS（{@code cms_rig}）—— 与 {@code tower_rig}
     * <b>同一种底座</b>（单层实心脱氧钢、控制器嵌在正面边缘中点、尺寸 = 自己那具塔的
     * 脚印 + 一圈），只是把底座上的塔换成另一具 OBJ 模型，并且<b>不接配方逻辑</b>
     * （基类是 {@code MultiblockControllerMachine}，不是 {@code Workable*} 系）。
     *
     * <p><b>⚠ 无 RecipeLogic ⇒ 模型不能带 {@code recipe_logic_status} 变体</b>：
     * 那台机器的方块根本没有这个属性，写了会在模型烘焙时直接崩。
     * 所以这里用 {@code createOverlayCasingMachineModel}（全状态同一模型），
     * 手写的资源 JSON 里也只有 {@code is_formed=false / true} 两个变体。</p>
     *
     * <p>外壳谓词也不开放 {@code autoAbilities} —— 本机没有任何配方，
     * 因此不需要能量仓 / 流体仓；只保留一个<b>可选</b>维护仓槽。
     * 预览里 {@code E}/{@code F} 的位置因此摆的是普通脱氧钢（见 {@link #rigShapeInfo}），
     * 否则 JEI 预览会不成形。</p>
     *
     * <p><b>这是「控制器贴正面边缘」形态的便捷重载</b>，等价于
     * {@code (halfLr, halfLr, halfFb × 2, 0, 3)}。底座形不成这种形态的机器
     * （尺寸是偶数、或干脆不要底座）请用下面那个完整重载。</p>
     *
     * @param plateHalfLr 底座左右半宽（格）—— 传机器类自己的 {@code PLATE_HALF_LR}，
     *                    必须与该机器 {@code rigPlateHalfLr()} 的返回值一致
     * @param plateHalfFb 底座前后半深（格）—— 同上，对应 {@code rigPlateHalfFb()}
     */
    private static MultiblockMachineDefinition registerDecorRig(
            String name,
            Function<IMachineBlockEntity, ? extends MultiblockControllerMachine> factory,
            String langValue,
            String tooltipKey,
            Supplier<com.gregtechceu.gtceu.client.renderer.machine.DynamicRender<?, ?>> renderer,
            int plateHalfLr,
            int plateHalfFb) {
        return registerDecorRig(name, factory, langValue, tooltipKey, renderer,
                plateHalfLr, plateHalfLr, plateHalfFb * 2, 0, 3);
    }

    /**
     * 同上，但底座的四向延伸与 tooltip 行数都是显式的（2026-10-13 新增）。
     *
     * <p>老机器的底座一律「控制器贴正面边缘、平板整个长在背面」，用一对半宽 / 半深
     * 就够；这台重载留给两种表达不了的形态：</p>
     * <ul>
     *   <li><b>深度是偶数</b>（{@code maimai_rig} 的 1 × 2 —— {@code back = 0,
     *       front = 1}）：塔中心 = 控制器 + BACK × {@code rigPlateHalfFb()}，
     *       而碰撞体坐标表的格子是方块对齐的、没法落在半格上 ⇒ 把控制器摆在
     *       靠后那一排，原点就等于控制器自己那一格。</li>
     *   <li><b>完全没有底座</b>（{@code lhc_combo} 的 {@code 0,0,0,0}）：
     *       pattern 退化成 1 × 1 × 1，放下去即成形；塔底面贴控制器方块底面
     *       （{@code rigBaseY()} = 0），直接坐在地面上。</li>
     * </ul>
     *
     * <p>⚠ 这四个数必须与机器类覆写的
     * {@code rigPlateHalfLr()} / {@code rigPlateBackExtent()} /
     * {@code rigPlateFrontExtent()} <b>逐个对齐</b>，否则碰撞体原点会偏到底座外面。</p>
     *
     * @param left         控制器左边几格（= {@code rigPlateHalfLr()}）
     * @param right        控制器右边几格（同上，恒与 left 相等）
     * @param back         控制器背面几排（= {@code rigPlateBackExtent()}）
     * @param front        控制器正面几排（= {@code rigPlateFrontExtent()}）
     * @param tooltipLines lang 里 {@code gtoecore.machine.<tooltipKey>.tooltip.N} 的行数
     */
    private static MultiblockMachineDefinition registerDecorRig(
            String name,
            Function<IMachineBlockEntity, ? extends MultiblockControllerMachine> factory,
            String langValue,
            String tooltipKey,
            Supplier<com.gregtechceu.gtceu.client.renderer.machine.DynamicRender<?, ?>> renderer,
            int left, int right, int back, int front, int tooltipLines) {
        Component[] tooltips = new Component[tooltipLines];
        for (int i = 0; i < tooltipLines; i++) {
            tooltips[i] = Component.translatable(
                    "gtoecore.machine." + tooltipKey + ".tooltip." + (i + 1));
        }
        return REGISTRATE
                .multiblock(name, factory)
                .langValue(langValue)
                .rotationState(RotationState.NON_Y_AXIS)
                // 同 tower_rig：非对称高塔 + 关掉扩展朝向
                .allowExtendedFacing(false)
                .tooltips(tooltips)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(definition -> rigPattern(definition, false, left, right, back, front))
                .shapeInfo(definition -> rigShapeInfo(definition, false, left, right, back, front))
                // 无 RecipeLogic ⇒ 不加 modelProperty，模型 JSON 里也不出现该属性
                .model(GTMachineModels.createOverlayCasingMachineModel(
                                GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                                GTCEu.id("block/machine/template/cube_all/sided"))
                        .andThen(b -> b.addDynamicRenderer(renderer)))
                .hasBER(true)
                .register();
    }

    /**
     * 机架类机器共用的结构 pattern：{@code (2×halfLr+1)}(左右) × 1(高) ×
     * {@code (2×halfFb+1)}(前后) 的<b>单层实心脱氧钢底座</b>。
     *
     * <p>aisle 顺序 = 背面 → 正面，本结构只有 1 层 ⇒ 每个 aisle 只有 1 个字符串。
     * 正面那一切片的中点是控制器 {@code S}（左右各 {@code halfLr} 格）。</p>
     *
     * <p><b>尺寸由调用方给</b>（= 机器类自己的 {@code PLATE_HALF_*}），
     * 因为三台机器各要一块大小不同的底盘：tower 43 × 23、arch 31 × 19、mast 21 × 17。
     * 取法固定在「该塔碰撞体在本轴的最大外延 + 1 格」，多出的一圈是可站立走道
     * —— 塔脚在贴地那层是铺满的，与脚印等大的底盘会被碰撞体 100% 盖死。</p>
     *
     * @param workable    是否可工作（有 RecipeLogic）—— 决定外壳是否额外开放
     *                    {@code autoAbilities}（配方需要的能力仓：能量输出 / 流体输入）
     * @param halfLr      底座左右半宽（格）
     * @param halfFb      底座前后半深（格）
     */
    private static BlockPattern rigPattern(MultiblockMachineDefinition definition, boolean workable,
                                           int halfLr, int halfFb) {
        return rigPattern(definition, workable, halfLr, halfLr, halfFb * 2, 0);
    }

    /**
     * 机架类机器共用的结构 pattern（四向可配版，2026-10-13）。
     *
     * <p>{@code (left+right+1)}(左右) × 1(高) × {@code (back+front+1)}(前后) 的
     * <b>单层实心脱氧钢底座</b>；控制器放在「控制器行」上，它的背面还有
     * {@code back} 排、正面还有 {@code front} 排。</p>
     *
     * <p>aisle 顺序 = 背面 → 正面，所以控制器所在 aisle 的下标正好是 {@code back}。
     * 老机器沿用 {@code (halfLr, halfLr, 2×halfFb, 0)}（控制器贴正面边缘、
     * 平板整个长在背面）⇒ 逐格与旧实现完全相同。</p>
     *
     * <p>⚠ 允许 {@code back + front + 1 == 1}（{@code lhc_combo} 无底座）：
     * 那时整张 pattern 只有控制器一格，放下即成形。</p>
     *
     * <p>⚠ <b>row 内必须严格等于 width 个字符</b>，否则 GTM 会抛
     * "Aisle length does not match"。</p>
     *
     * @param workable 是否可工作（有 RecipeLogic）—— 决定外壳是否额外开放 {@code autoAbilities}
     * @param left     控制器左边几格
     * @param right    控制器右边几格
     * @param back     控制器背面几排
     * @param front    控制器正面几排
     */
    private static BlockPattern rigPattern(MultiblockMachineDefinition definition, boolean workable,
                                           int left, int right, int back, int front) {
        int width = left + right + 1;
        int depth = back + front + 1;
        // ⚠ 口径：casingCells 含控制器那一格 —— 老的 tower 是 43 × 23 = 989 取 900
        //    （≈ 91%，即约 89 格可换仓）；arch 31×19 = 589 取 536、mast 21×17 = 357 取 325。
        int casingCells = width * depth;
        FactoryBlockPattern pattern = FactoryBlockPattern.start()
                .where('S', Predicates.controller(Predicates.blocks(definition.get())));

        if (casingCells > 1) {
            String solidRow = "#".repeat(width);
            // 控制器那一排：左右各留够，控制器在中点（左右不对称时也照样摆）
            String controllerRow = "#".repeat(left) + "S" + "#".repeat(right);
            for (int z = 0; z < depth; z++) {
                pattern.aisle(z == back ? controllerRow : solidRow);
            }
            // 外壳：实心脱氧钢（下限见上），再叠一个可选维护仓；
            // 可工作的机器额外叠加「本配方类型需要的能力仓」。
            // ⚠ 小底盘要夹一下上限：1×2 只有 1 格外壳，按 91% 算会要 2 格 ⇒ 永远无法成形。
            int minCasing = Math.min(Math.max(casingCells - 1, 0),
                    (int) Math.round(casingCells * 900.0 / 989.0));
            TraceabilityPredicate shell = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                    .setMinGlobalLimited(minCasing);
            if (workable) {
                shell = shell.or(Predicates.autoAbilities(definition.getRecipeTypes()));
            }
            shell = shell.or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1));
            pattern.where('#', shell);
        } else {
            // 无底座（lhc_combo）：整张 pattern 只有控制器那一格，放下即成形。
            // ⚠ 这里<b>不能</b>顺手把 '#' 谓词也挂上 —— 一个 '#' 格都不存在时
            //   minGlobalLimited 只能取 0，虽然 GTM 不报错，但语义上没必要，
            //   而且给未来留了个「0 下限」的坑。
            pattern.aisle("S");
        }
        return pattern.build();
    }

    /**
     * 机架类机器共用的结构预览（JEI / GTM 面板）。
     *
     * <p>顺序约定：shapeInfo 的 aisle 顺序与 pattern 相反、行内字符串左右相反
     * （= {@code BlockPattern.getPreview} 的排布）。本结构左右对称，所以行内不用倒。</p>
     *
     * <p><b>⚠ 预览必须真的摆上与 pattern 匹配的方块</b>，否则结构在预览里不成形 ——
     * 那种情况下在 JEI 里点一下会 NPE 崩游戏（packcompanion 的
     * {@code PatternPreviewWidgetMixin} 只是兜底，别依赖它）。
     * 所以 {@code E}/{@code F}（能量输出仓 / 流体输入仓）只给 {@code workable} 的机器摆，
     * 纯结构的机器在同一位置摆普通脱氧钢。</p>
     *
     * <p><b>⚠ 控制器必须留在中点</b>：{@code halfLr} 至少要 ≥ 2，否则
     * {@code "#".repeat(halfLr - 2)} 会抛 {@code IllegalArgumentException}。
     * 目前最小的 mast 是 10，安全。</p>
     *
     * @param workable 与 {@link #rigPattern} 保持一致
     * @param halfLr   底座左右半宽（格）
     * @param halfFb   底座前后半深（格）
     */
    private static MultiblockShapeInfo rigShapeInfo(MultiblockMachineDefinition definition, boolean workable,
                                                    int halfLr, int halfFb) {
        return rigShapeInfo(definition, workable, halfLr, halfLr, halfFb * 2, 0);
    }

    /**
     * 机架类机器共用的结构预览（JEI / GTM 面板）—— 四向可配版。
     *
     * <p>顺序约定：shapeInfo 的 aisle 顺序与 pattern 相反（= {@code BlockPattern.getPreview}
     * 的排布），所以 <b>preview 第 0 张切片 = 正面</b>，而控制器那一排就落在下标
     * {@code front} 上（老机器 {@code front=0} ⇒ 控制器在第 0 张切片上，与旧实现一致）。</p>
     *
     * <p><b>⚠ 预览必须真的摆上与 pattern 匹配的方块</b>，否则结构在预览里不成形 ——
     * 那种情况下在 JEI 里点一下会 NPE 崩游戏（packcompanion 的
     * {@code PatternPreviewWidgetMixin} 只是兜底，别依赖它）。
     * 所以 {@code E}/{@code F}（能量输出仓 / 流体输入仓）只给 {@code workable} 的机器摆，
     * 纯结构的机器在同一位置摆普通脱氧钢。</p>
     *
     * <p><b>⚠ 小底盘不能照抄「左右各留 2 格放 EFSFE」</b>：{@code "#".repeat(-2)} 会抛
     * {@code IllegalArgumentException}。这里按宽度分档 —— 只有左右都 ≥ 2 时才摆功能仓，
     * 否则退化成「# ＊ left + S + # ＊ right」。</p>
     */
    private static MultiblockShapeInfo rigShapeInfo(MultiblockMachineDefinition definition, boolean workable,
                                                    int left, int right, int back, int front) {
        int width = left + right + 1;
        int depth = back + front + 1;
        String solidRow = "#".repeat(width);
        // preview 第 front 张切片 = 控制器那一排（可工作的机器左右各挂一个功能仓）
        String controllerRow = (workable && left >= 2 && right >= 2)
                ? "#".repeat(left - 2) + "EFSFE" + "#".repeat(right - 2)
                : "#".repeat(left) + "S" + "#".repeat(right);
        // preview 最后 1 张切片 = 背面（维护仓 M 在中点，两台都允许）
        String backRow = "#".repeat(left) + "M" + "#".repeat(right);
        MultiblockShapeInfo.ShapeInfoBuilder builder = MultiblockShapeInfo.builder();
        for (int z = 0; z < depth; z++) {
            if (z == front) {
                builder.aisle(controllerRow);
            } else if (z == depth - 1) {
                builder.aisle(backRow);
            } else {
                builder.aisle(solidRow);
            }
        }
        return builder
                .where('S', definition.get(), Direction.NORTH)
                .where('#', GTNPreview.state(GTBlocks.CASING_STEEL_SOLID.get()))
                // 索引 = GT 电压档位（GTValues.V：0=ULV, 1=LV, 2=MV, 3=HV…），
                // 本机按 HV 设计（脱氧钢外壳 + HV 合成配方），预览也给 HV 仓。
                // 注意结构本身不挑档位：外壳谓词用的是 autoAbilities（只看能力），
                // 玩家塞 LV 仓也照样成形，只是发电量受仓的电压上限限制。
                // （纯结构的机器不会走到这两行 —— 那里 E/F 位置本来就是 '#'。）
                .where('E', GTMachines.ENERGY_OUTPUT_HATCH[3], Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[3], Direction.NORTH)
                .where('M', GTMachines.MAINTENANCE_HATCH, Direction.SOUTH)
                .build();
    }

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
    private static MultiblockMachineDefinition registerAECraftingTerminal() {
        return REGISTRATE
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
}
