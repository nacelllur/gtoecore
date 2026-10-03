package com.gtoecore;

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

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    private GTNBlocks() {}
}
