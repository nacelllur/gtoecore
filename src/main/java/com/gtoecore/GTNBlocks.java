package com.gtoecore;

import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.block.GTOConduitBlock;
import com.gtoecore.energy.block.GTOConduitBlockEntity;
import com.gtoecore.energy.block.RotaryMillBlock;
import com.gtoecore.energy.block.RotaryMillBlockEntity;
import com.gtoecore.energy.block.SpeedDetectorBlock;
import com.gtoecore.energy.block.SpeedDetectorBlockEntity;
import com.gtoecore.station.HullViewportBlock;
import com.gtoecore.station.HullViewportBlockEntity;
import com.gtoecore.station.PanelSkin;
import com.gtoecore.station.StationGravityCoreBlock;
import com.gtoecore.station.StationGravityCoreBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * gtoecore 普通方块注册（空间站舱室设施）。
 *
 * <p>舱室重力核心（station_gravity_core）：放在空间站舱室内，右键后洪泛扫描
 * 气密空间；生效期间舱内玩家正常重力、舱外（同一维度内）自动失重。
 * 失重效果由 space_gravity mod 提供（软依赖）。</p>
 */
public final class GTNBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, GTNCore.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GTNCore.MODID);

    /** 舱室重力核心 —— 一个核心适配任意形状/尺寸的舱室 */
    public static final RegistryObject<Block> STATION_GRAVITY_CORE =
            BLOCKS.register("station_gravity_core", () -> new StationGravityCoreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<StationGravityCoreBlockEntity>> STATION_GRAVITY_CORE_BE =
            BLOCK_ENTITIES.register("station_gravity_core",
                    () -> BlockEntityType.Builder
                            .of(StationGravityCoreBlockEntity::new, STATION_GRAVITY_CORE.get())
                            .build(null));

    /**
     * 全周天舱壁 —— 单向舷窗方块（space station / 飞船观察舱壁）。
     *
     * <p>舱内一侧通透、舱外看似实心；通红石信号后整片相连舱壁由信号点向外
     * 波纹式变透明，断电反向收拢。模型省略朝内面，该面由客户端 BER 动态补绘
     * 以支持透明度动画。</p>
     *
     * <p>⚠ 必须带 {@code .dynamicShape()}：它让 {@code BlockStateBase.Cache} 不生成，
     * 方块覆写的 {@code getLightBlock}/{@code propagatesSkylightDown} 才会生效
     * （否则光照被缓存成「实心墙挡光」）。<b>切勿</b>加 {@code .noOcclusion()}：
     * 那会破坏面剔除，整面墙出现网格状细缝。</p>
     */
    public static final RegistryObject<HullViewportBlock> HULL_VIEWPORT =
            BLOCKS.register("hull_viewport", () -> new HullViewportBlock(viewportProperties()));

    // ── 面板皮肤变种：只换「关闭（无信号）态朝内面」的纹理，其余行为与 hull_viewport 完全一致 ──
    //
    // 变种随皮肤增长（现在 14 个），所以这里**遍历 PanelSkin 注册**，不再一个个写字段：
    // 新增皮肤 = PanelSkin 加一个枚举值 + 跑 tools/gen_hull_panel_skins.py 出贴图与资源，
    // 本文件 / GTNItems / 创造栏都不用动。
    // 注册名 = "hull_viewport_" + skin.id()；PanelSkin.HEX 就是基础方块 hull_viewport，跳过。

    /** 皮肤 → 变种方块。遍历顺序无关，对外顺序统一由 {@link #allHullViewports()} 给。 */
    private static final Map<PanelSkin, RegistryObject<HullViewportBlock>> HULL_VARIANTS =
            new EnumMap<>(PanelSkin.class);

    static {
        for (PanelSkin skin : PanelSkin.values()) {
            if (skin == PanelSkin.HEX) {
                continue;
            }
            HULL_VARIANTS.put(skin, BLOCKS.register("hull_viewport_" + skin.id(),
                    () -> new HullViewportBlock(viewportProperties(), skin)));
        }
    }

    /**
     * 六面蜂窝舱壁 —— 六个面<b>都</b>是蜂窝晶格面板的实心墙块（最初的全周天舱壁外观）。
     *
     * <p>与舷窗版的差别只有「外观从哪来」：舷窗版静态模型省略朝内面、只补绘那一个面
     * （舱内看穿）；本方块 <b>C 档静态化（2026-10-07）</b>——闭合态 = 静态实心蜂窝模型、
     * 通电态 = 静态半透明模型（blockstate 的 open 分流），<b>不走 BER、不创建方块实体</b>，
     * 每帧零开销。红石波纹与舷窗版一致（BFS 的 {@code instanceof HullViewportBlock}
     * 判定自动包含它）。</p>
     *
     * <p>贴图 {@code hull_panel_hex_tile} 无缝平铺、无边角线：相邻方块拼贴处零色差
     * （wrap seam delta = 0.00），观感等同无缝。旧版「空模型 + 六面 BER」因每方块
     * 2 次 draw call + 256 格全渲染而卡顿，已废弃。</p>
     *
     * <p>通电后不是「变透明消失」而是<b>变半透明</b>（残留不透明度 {@link HullViewportBlock#openAlphaFloor()}）。</p>
     */
    public static final RegistryObject<HullViewportBlock> HULL_PANEL_HEX =
            BLOCKS.register("hull_panel_hex",
                    () -> new HullViewportBlock(viewportProperties(), PanelSkin.HEX, true));

    /** 取某个皮肤的舱壁方块；{@link PanelSkin#HEX} 返回基础方块 {@link #HULL_VIEWPORT}。 */
    public static RegistryObject<HullViewportBlock> hullViewport(PanelSkin skin) {
        return skin == PanelSkin.HEX ? HULL_VIEWPORT : HULL_VARIANTS.get(skin);
    }

    /** 全部舱壁（基础 + 皮肤变种 + 六面蜂窝版）。 */
    public static List<RegistryObject<HullViewportBlock>> allHullViewports() {
        List<RegistryObject<HullViewportBlock>> out = new ArrayList<>(PanelSkin.values().length + 1);
        for (PanelSkin skin : PanelSkin.values()) {
            out.add(hullViewport(skin));
        }
        out.add(HULL_PANEL_HEX);
        return out;
    }

    /**
     * 所有舱壁（含变种）共用的方块属性。
     *
     * <p>⚠ 必须带 {@code .dynamicShape()}，且<b>切勿</b>加 {@code .noOcclusion()}，
     * 理由见 {@link #HULL_VIEWPORT} 的注释。</p>
     */
    private static BlockBehaviour.Properties viewportProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 8.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .dynamicShape();
    }

    /**
     * ⚠ 所有皮肤变种<b>共用同一个方块实体类型</b>：方块实体只持有客户端的透明度动画进度，
     * 与皮肤无关。{@code Builder.of(...)} 是 varargs，把这些方块一并登记即可。
     *
     * <p>数组在 supplier 被调用时才填（注册事件期间），所以这里读 {@link #HULL_VARIANTS}
     * 是安全的 —— 类的静态初始化早就完成了。</p>
     */
    public static final RegistryObject<BlockEntityType<HullViewportBlockEntity>> HULL_VIEWPORT_BE =
            BLOCK_ENTITIES.register("hull_viewport", () -> {
                List<RegistryObject<HullViewportBlock>> all = allHullViewports();
                HullViewportBlock[] blocks = new HullViewportBlock[all.size()];
                for (int i = 0; i < blocks.length; i++) {
                    blocks[i] = all.get(i).get();
                }
                return BlockEntityType.Builder
                        .of(HullViewportBlockEntity::new, blocks)
                        .build(null);
            });

    /**
     * 原始计算机外壳 —— 无人机蜂群之心（drone_swarm_heart）的结构方块；
     * 外观复用 gtceu 电脑外壳贴图（casings/hpca/computer_casing/front）。
     */
    public static final RegistryObject<Block> PRIMITIVE_COMPUTER_CASING =
            BLOCKS.register("primitive_computer_casing", () -> new Block(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /** 原始计算机散热口 —— 蜂群之心结构方块（GT 计算机散热口的原始变体，cube_column） */
    public static final RegistryObject<Block> PRIMITIVE_HEAT_VENT =
            BLOCKS.register("primitive_heat_vent", () -> new Block(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /** 原始空HPCA组件 —— 蜂群之心内部填充（GT 空HPCA组件的原始变体，外壳+空覆层合成贴图） */
    public static final RegistryObject<Block> PRIMITIVE_EMPTY_COMPONENT =
            BLOCKS.register("primitive_empty_component", () -> new Block(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /** 原始HPCA计算组件 —— 蜂群之心内部核心（GT HPCA计算组件的原始变体，外壳+计算覆层合成贴图） */
    public static final RegistryObject<Block> PRIMITIVE_COMPUTATION_COMPONENT =
            BLOCKS.register("primitive_computation_component", () -> new Block(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /**
     * 光伏面板 —— 太阳能发电模块（solar_array_module）结构中间层的下半砖；
     * 只有 BOTTOM 状态（且上方能看到天空）才计入发电。
     */
    public static final RegistryObject<SlabBlock> PV_PANEL =
            BLOCKS.register("pv_panel", () -> new SlabBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(1.5f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    /**
     * 奇点约束外壳 —— 「事件视界引擎」（event_horizon_engine）的唯一结构方块。
     *
     * <p>35³ 球面线框笼的骨架＋赤道总线环都由它构成（见
     * {@code GTNEventHorizonStructure}）。外观走专属贴图
     * {@code gtoecore:block/casing/singularity_constraint_casing}
     * （深色金属底 + 青紫辉光纹路），与黑洞的冷暖对比呼应。</p>
     */
    public static final RegistryObject<Block> SINGULARITY_CONSTRAINT_CASING =
            BLOCKS.register("singularity_constraint_casing", () -> new Block(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(6.0f, 12.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    // ---------------- GT6 风味能量导体（GTOEnergyType 四形式各一实例） ----------------

    /** 旋转轴（RU）—— 转速沿轴链逐节损耗传递 */
    public static final RegistryObject<GTOConduitBlock> ROTATION_AXLE =
            BLOCKS.register("rotation_axle", () -> new GTOConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL),
                    GTOEnergyType.ROTATION));

    /** 导热管（HU）—— 热源温度沿管链逐节耗散 */
    public static final RegistryObject<GTOConduitBlock> HEAT_PIPE =
            BLOCKS.register("heat_pipe", () -> new GTOConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL),
                    GTOEnergyType.HEAT));

    /** 冷凝管（CU）—— 冷源低温沿管链逐节回升 */
    public static final RegistryObject<GTOConduitBlock> COLD_PIPE =
            BLOCKS.register("cold_pipe", () -> new GTOConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL),
                    GTOEnergyType.COLD));

    /** 动能杆（KU）—— 活塞压强沿杆链逐节衰减 */
    public static final RegistryObject<GTOConduitBlock> KINETIC_ROD =
            BLOCKS.register("kinetic_rod", () -> new GTOConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL),
                    GTOEnergyType.KINETIC));

    /** 激光能管道（LU）—— 直线不能拐弯（axialOnly=true，对齐 GT 激光管道语义） */
    public static final RegistryObject<GTOConduitBlock> LASER_PIPE =
            BLOCKS.register("laser_pipe", () -> new GTOConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_CYAN)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.GLASS),
                    GTOEnergyType.LASER, true));

    /** 量子能管道（QU）—— Mek 管道风格，有能量时发荧光（active 属性） */
    public static final RegistryObject<GTOConduitBlock> QUANTUM_PIPE =
            BLOCKS.register("quantum_pipe", () -> new GTOConduitBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.AMETHYST),
                    GTOEnergyType.QUANTUM, false));

    /** 全部导体共用的 BlockEntity（一个 BE type 绑定六种导体方块） */
    public static final RegistryObject<BlockEntityType<GTOConduitBlockEntity>> GTO_CONDUIT_BE =
            BLOCK_ENTITIES.register("gto_conduit",
                    () -> BlockEntityType.Builder
                            .of(GTOConduitBlockEntity::new,
                                    ROTATION_AXLE.get(), HEAT_PIPE.get(),
                                    COLD_PIPE.get(), KINETIC_ROD.get(),
                                    LASER_PIPE.get(), QUANTUM_PIPE.get())
                            .build(null));

    // ---------------- GT6 风味能量产能机：燃烧引擎 ----------------
    // ⚠ 燃烧引擎已迁移为 GTM 单方块机器（GTNMachines.COMBUSTION_ENGINE，GT 原生 FancyUI），
    //   不再作为普通方块注册。能量链路经 GTOEnergyBridge 桥接（GTM 机器也认）。

    // ---------------- GT6 风味能量接收端：转速检测器（RU → 红石） ----------------

    public static final RegistryObject<SpeedDetectorBlock> SPEED_DETECTOR =
            BLOCKS.register("speed_detector", () -> new SpeedDetectorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_YELLOW)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL)));

    public static final RegistryObject<BlockEntityType<SpeedDetectorBlockEntity>> SPEED_DETECTOR_BE =
            BLOCK_ENTITIES.register("speed_detector",
                    () -> BlockEntityType.Builder
                            .of(SpeedDetectorBlockEntity::new, SPEED_DETECTOR.get())
                            .build(null));

    // ---------------- GT6 风味能量消费端：旋转石磨（RU → 磨矿） ----------------

    public static final RegistryObject<RotaryMillBlock> ROTARY_MILL =
            BLOCKS.register("rotary_mill", () -> new RotaryMillBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<RotaryMillBlockEntity>> ROTARY_MILL_BE =
            BLOCK_ENTITIES.register("rotary_mill",
                    () -> BlockEntityType.Builder
                            .of(RotaryMillBlockEntity::new, ROTARY_MILL.get())
                            .build(null));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    private GTNBlocks() {}
}
