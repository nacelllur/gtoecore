package com.gtoecore;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtoecore.item.GTNCloneItem;
import com.gtoecore.item.GTNDroneItem;
import com.gtoecore.station.StationGravityCoreItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Locale;

/**
 * gtoecore 普通物品注册（克隆体 / 生物团 / 无人机）。
 *
 * <p>用 Forge 标准 {@link DeferredRegister}（不依赖 GT registrate 时序），
 * 在 mod 构造期注册：</p>
 * <ul>
 *   <li>克隆体（clone）：{@link GTNCloneItem}，右键空气绑定玩家，生产车间复活消耗。</li>
 *   <li>生物团（bio_cluster）：制造仓产出 → 维护室消耗。</li>
 *   <li>无人机（survey/mining_drone_&lt;电压名&gt;）：LV~MAX 共 28 个，
 *       无人机蜂群之心的任务耗材。</li>
 * </ul>
 */
public final class GTNItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GTNCore.MODID);

    /** 克隆体 —— 1 格高史蒂夫小人模型，右键空气绑定玩家 */
    public static final RegistryObject<Item> CLONE =
            ITEMS.register("clone", () -> new GTNCloneItem(new Item.Properties()));

    /** 生物团 —— 制造仓每 5 分钟产出的中间产物 */
    public static final RegistryObject<Item> BIO_CLUSTER =
            ITEMS.register("bio_cluster", () -> new Item(new Item.Properties()));

    /** 舱室重力核心 —— 空间站舱室主方块（方块注册见 {@link GTNBlocks}） */
    public static final RegistryObject<Item> STATION_GRAVITY_CORE =
            ITEMS.register("station_gravity_core",
                    () -> new StationGravityCoreItem(GTNBlocks.STATION_GRAVITY_CORE.get(),
                            new Item.Properties()));

    /** 光伏面板 —— 太阳能发电模块结构中间层的下半砖 */
    public static final RegistryObject<Item> PV_PANEL =
            ITEMS.register("pv_panel",
                    () -> new BlockItem(GTNBlocks.PV_PANEL.get(), new Item.Properties()));

    /** 原始计算机外壳 —— 无人机蜂群之心结构方块（方块注册见 {@link GTNBlocks}） */
    public static final RegistryObject<Item> PRIMITIVE_COMPUTER_CASING =
            ITEMS.register("primitive_computer_casing",
                    () -> new BlockItem(GTNBlocks.PRIMITIVE_COMPUTER_CASING.get(),
                            new Item.Properties()));

    /** 原始计算机散热口 —— 蜂群之心侧壁散热鳃带 */
    public static final RegistryObject<Item> PRIMITIVE_HEAT_VENT =
            ITEMS.register("primitive_heat_vent",
                    () -> new BlockItem(GTNBlocks.PRIMITIVE_HEAT_VENT.get(),
                            new Item.Properties()));

    /** 原始空HPCA组件 —— 蜂群之心内部填充 */
    public static final RegistryObject<Item> PRIMITIVE_EMPTY_COMPONENT =
            ITEMS.register("primitive_empty_component",
                    () -> new BlockItem(GTNBlocks.PRIMITIVE_EMPTY_COMPONENT.get(),
                            new Item.Properties()));

    /** 原始HPCA计算组件 —— 蜂群之心内部核心 */
    public static final RegistryObject<Item> PRIMITIVE_COMPUTATION_COMPONENT =
            ITEMS.register("primitive_computation_component",
                    () -> new BlockItem(GTNBlocks.PRIMITIVE_COMPUTATION_COMPONENT.get(),
                            new Item.Properties()));

    /**
     * 深空燃料单元 —— 深空枢纽的运营耗材。
     * 对标通天之路的碳纳米管线轴：枢纽每 80 tick 吃 1 个，断供则全网络模块停摆。
     */
    public static final RegistryObject<Item> DEEP_SPACE_FUEL =
            ITEMS.register("deep_space_fuel", () -> new Item(new Item.Properties()));

    /**
     * 无人机物品，索引 = GTValues 电压等级（LV=1 .. MAX=14；0 号位为 null）。
     * 注册名：survey_drone_lv / mining_drone_zpm …（电压名小写）。
     */
    public static final RegistryObject<Item>[] SURVEY_DRONES = new RegistryObject[15];
    public static final RegistryObject<Item>[] MINING_DRONES = new RegistryObject[15];

    static {
        for (int tier = GTValues.LV; tier <= GTValues.MAX; tier++) {
            final int t = tier;
            String vn = GTValues.VN[t].toLowerCase(Locale.ROOT);
            SURVEY_DRONES[t] = ITEMS.register("survey_drone_" + vn,
                    () -> new GTNDroneItem(new Item.Properties(), t, GTNDroneItem.DroneType.SURVEY));
            MINING_DRONES[t] = ITEMS.register("mining_drone_" + vn,
                    () -> new GTNDroneItem(new Item.Properties(), t, GTNDroneItem.DroneType.MINING));
        }
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private GTNItems() {}
}
