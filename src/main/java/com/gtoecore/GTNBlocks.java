package com.gtoecore;

import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.block.CombustionEngineBlock;
import com.gtoecore.energy.block.CombustionEngineBlockEntity;
import com.gtoecore.energy.block.GTOConduitBlock;
import com.gtoecore.energy.block.GTOConduitBlockEntity;
import com.gtoecore.energy.block.RotaryMillBlock;
import com.gtoecore.energy.block.RotaryMillBlockEntity;
import com.gtoecore.energy.block.SpeedDetectorBlock;
import com.gtoecore.energy.block.SpeedDetectorBlockEntity;
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

    /** 全部导体共用的 BlockEntity（一个 BE type 绑定四种导体方块） */
    public static final RegistryObject<BlockEntityType<GTOConduitBlockEntity>> GTO_CONDUIT_BE =
            BLOCK_ENTITIES.register("gto_conduit",
                    () -> BlockEntityType.Builder
                            .of(GTOConduitBlockEntity::new,
                                    ROTATION_AXLE.get(), HEAT_PIPE.get(),
                                    COLD_PIPE.get(), KINETIC_ROD.get())
                            .build(null));

    // ---------------- GT6 风味能量产能机：燃烧引擎（RU 来源） ----------------

    public static final RegistryObject<CombustionEngineBlock> COMBUSTION_ENGINE =
            BLOCKS.register("combustion_engine", () -> new CombustionEngineBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BROWN)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<CombustionEngineBlockEntity>> COMBUSTION_ENGINE_BE =
            BLOCK_ENTITIES.register("combustion_engine",
                    () -> BlockEntityType.Builder
                            .of(CombustionEngineBlockEntity::new, COMBUSTION_ENGINE.get())
                            .build(null));

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
