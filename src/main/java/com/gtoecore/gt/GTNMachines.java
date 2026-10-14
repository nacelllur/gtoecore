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
 *   <li>把 {@link GTNMachineTemplate#registerExampleMultiblock()} 整个方法复制一份，改名
 *       （方法名 = "register" + 机器名驼峰）；按子系统放进
 *       {@link GTNCloneMachines} / {@link GTNEnergyMachines} /
 *       {@link GTNDeepSpaceMachines} / {@link GTNLuminaFusionMachines} /
 *       {@link GTNEventHorizonMachines} / {@link GTNRigMachines} /
 *       {@link GTNAeTerminalMachines} 之一（2026-10-14 起按模块拆分）。</li>
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
 *       <b>必须在 pattern lambda 内懒查</b>（{@link GTNCloneMachines} 内的 {@code modBlock}）——
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
     *
     * <p>2026-10-14 起各子系统的 {@code register*} 生成器按模块拆到
     * {@link GTNCloneMachines} / {@link GTNEnergyMachines} /
     * {@link GTNDeepSpaceMachines} / {@link GTNLuminaFusionMachines} /
     * {@link GTNEventHorizonMachines} / {@link GTNRigMachines} /
     * {@link GTNAeTerminalMachines}；<b>本方法仍是唯一的注册编排入口</b>，
     * 顺序与拆分前逐行一致（顺序会影响 GT 机器创造标签页的排列）。</p>
     */
    public static void initMachines() {
        // REGISTERED.add(GTNMachineTemplate.registerExampleMultiblock());  // ← 取消注释即启用模板机器

        // ---- 生物系三台主机（外壳统一为惰性 PTFE 外壳，覆层为各机器专属贴图）----
        REGISTERED.add(CLONE_PRODUCTION_WORKSHOP = GTNCloneMachines.registerCloneProductionWorkshop());
        REGISTERED.add(CLONE_MANUFACTURING_CHAMBER = GTNCloneMachines.registerCloneManufacturingChamber());
        REGISTERED.add(CLONE_MAINTENANCE_ROOM = GTNCloneMachines.registerCloneMaintenanceRoom());

        // ---- 能源 ----
        REGISTERED.add(SOLAR_ARRAY_MODULE = GTNEnergyMachines.registerSolarArrayModule());

        // ---- 无人机蜂群之心 ----
        REGISTERED.add(DRONE_SWARM_HEART = GTNEnergyMachines.registerDroneSwarmHeart());

        // ---- 深空枢纽体系（主机 + 6 类扩展模块，原理对标 GTO 通天之路）----
        REGISTERED.add(DEEP_SPACE_HUB = GTNDeepSpaceMachines.registerDeepSpaceHub());
        for (ModuleKind kind : ModuleKind.values()) {
            REGISTERED.add(DEEP_SPACE_MODULES[kind.ordinal()] = GTNDeepSpaceMachines.registerDeepSpaceModule(kind));
        }

        // ---- GT6 风味能量输入仓（复用 INPUT_ENERGY ⇒ 所有 pattern 的能量位自动接受）----
        REGISTERED.add(GTO_ENERGY_INPUT_HATCH = GTNEnergyMachines.registerGTOEnergyInputHatch());

        // ---- GT6 风味产能机：燃烧引擎 ----
        REGISTERED.add(COMBUSTION_ENGINE = GTNEnergyMachines.registerCombustionEngine());

        // ---- GT6 风味电动产能机 15 压（CU/KU/MU/LU/QU × ULV~MAX = 75 台，全部 GT 原生 FancyUI）----
        CRYOGENIC_CONDENSER = GTNEnergyMachines.registerTieredGTOProducer(
                "cryogenic_condenser", CryogenicCondenserMachine::new, "低温冷凝机");
        PNEUMATIC_COMPRESSOR = GTNEnergyMachines.registerTieredGTOProducer(
                "pneumatic_compressor", PneumaticCompressorMachine::new, "气动压缩机");
        MAGNETIC_COIL = GTNEnergyMachines.registerTieredGTOProducer(
                "magnetic_coil", MagneticCoilMachine::new, "电磁线圈机");
        LASER_FOCUS = GTNEnergyMachines.registerTieredGTOProducer(
                "laser_focus", LaserFocusMachine::new, "激光聚束器");
        QUANTUM_CONDENSER = GTNEnergyMachines.registerTieredGTOProducer(
                "quantum_condenser", QuantumCondenserMachine::new, "量子凝聚器");
        SOLAR_CONCENTRATOR = GTNEnergyMachines.registerTieredGTOProducer(
                "solar_concentrator", SolarConcentratorMachine::new, "聚光阵列");
        for (MachineDefinition[] defs : gtoProducers()) {
            for (MachineDefinition def : defs) {
                if (def != null) REGISTERED.add(def);
            }
        }

        // ---- 聚变光源核心：与 GT 聚变反应堆结构/逻辑一致，主方块换皮（三级 MK1/MK2/MK3）----
        LUMINA_FUSION_CORE = GTNLuminaFusionMachines.registerLuminaFusionCore();
        for (MultiblockMachineDefinition def : LUMINA_FUSION_CORE) {
            if (def != null) REGISTERED.add(def);
        }

        // ---- 事件视界引擎（35³ 球面线框笼，中央程序化黑洞）----
        REGISTERED.add(EVENT_HORIZON_ENGINE = GTNEventHorizonMachines.registerEventHorizonEngine());

        // ---- AE 合成终端多方块主机（3×3×3 脱氧钢外壳，右键开 AE 合成终端界面）----
        GTOEMultiblockTerminalLocator.register();
        REGISTERED.add(AE_CRAFTING_TERMINAL = GTNAeTerminalMachines.registerAECraftingTerminal());

        // ---- 机架类机器（八台）：底座尺寸见各机器类常量；四向可配（2026-10-13）----
        // tower_rig 烧液体燃料发电；其余只做结构与外观（暂不接配方逻辑）。
        REGISTERED.add(TOWER_RIG = GTNRigMachines.registerTowerRig());
        REGISTERED.add(ARCH_RIG = GTNRigMachines.registerDecorRig(
                "arch_rig", ArchRigMachine::new, "Arch Rig",
                "arch_rig", ArchRigDynamicRender::new,
                ArchRigMachine.PLATE_HALF_LR, ArchRigMachine.PLATE_HALF_FB));
        REGISTERED.add(MAST_RIG = GTNRigMachines.registerDecorRig(
                "mast_rig", MastRigMachine::new, "Mast Rig",
                "mast_rig", MastRigDynamicRender::new,
                MastRigMachine.PLATE_HALF_LR, MastRigMachine.PLATE_HALF_FB));
        REGISTERED.add(DRUM_RIG = GTNRigMachines.registerDecorRig(
                "drum_rig", DrumRigMachine::new, "Drum Rig",
                "drum_rig", DrumRigDynamicRender::new,
                DrumRigMachine.PLATE_HALF_LR, DrumRigMachine.PLATE_HALF_FB));
        REGISTERED.add(BLADE_RIG = GTNRigMachines.registerDecorRig(
                "blade_rig", BladeRigMachine::new, "Blade Rig",
                "blade_rig", BladeRigDynamicRender::new,
                BladeRigMachine.PLATE_HALF_LR, BladeRigMachine.PLATE_HALF_FB));

        // 舞萌街机柜：底座 1 宽 × 2 深（控制器自己 + 正面外侧 1 格）。
        REGISTERED.add(MAIMAI_RIG = GTNRigMachines.registerDecorRig(
                "maimai_rig", MaimaiRigMachine::new, "Maimai Rig",
                "maimai_rig", MaimaiRigDynamicRender::new,
                MaimaiRigMachine.PLATE_HALF_LR, MaimaiRigMachine.PLATE_HALF_LR,
                0, MaimaiRigMachine.PLATE_FRONT_EXTENT, 3));
        // CMS 探测器：115×77 单层实心底座（老式「控制器贴正面边缘」的形态）。
        REGISTERED.add(CMS_RIG = GTNRigMachines.registerDecorRig(
                "cms_rig", CmsRigMachine::new, "CMS Rig",
                "cms_rig", CmsRigDynamicRender::new,
                CmsRigMachine.PLATE_HALF_LR, CmsRigMachine.PLATE_HALF_FB));
        // LHC 环线：1×1×1 —— 完全没有底座，放下即成形。
        REGISTERED.add(LHC_COMBO = GTNRigMachines.registerDecorRig(
                "lhc_combo", LhcComboRigMachine::new, "LHC Combo",
                "lhc_combo", LhcComboRigDynamicRender::new,
                0, 0, 0, 0, 4));

        // 修改已有 GT/GCYM 多方块（结构/修饰器/配方类型），见 GTNMachineOverrides
        GTNMachineOverrides.applyAll();
    }
}
