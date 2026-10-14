package com.gtoecore.objdemo;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * OBJ 示范机器的方块/方块实体/物品注册入口。
 *
 * <p>与 {@link com.gtoecore.GTNBlocks} 平行，独立注册，不污染现有 GTM 机器体系。</p>
 *
 * <p>方块：</p>
 * <ul>
 *   <li>{@code turbine_demo} —— 自建 OBJ（0~1 方块空间，带 rotor/shaft 旋转）演示逐 part 动画；</li>
 *   <li>{@code tower_rig_collision} —— 机架类机器共用的隐形碰撞方块（无物品、无外观），
 *       由各机器按需铺设，玩家不可见。</li>
 * </ul>
 *
 * <p><b>三台机架机器（{@code tower_rig} / {@code arch_rig} / {@code mast_rig}）
 * 已经不在这里了（2026-10-12）</b>：{@code tower_rig} 原本是个
 * 「隐形占位 + BER 画 OBJ」的渲染示范，摆着好看但什么也不干。现在它们都是真正的
 * GTM 多方块机器，注册在 {@code GTNMachines}：底座是单层实心脱氧钢平板
 * （尺寸按各自塔的脚印 + 一圈：tower 43 × 23 / arch 31 × 19 / mast 21 × 17），
 * 塔由各自的 DynamicRender 画在底座顶面上
 * （{@code com.gtoecore.client.TowerRigDynamicRender} 等）。</p>
 */
public final class GTOEObjDemo {

    public static final String MODID = "gtoecore";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    /** OBJ 示范涡轮机方块 */
    public static final RegistryObject<TurbineDemoBlock> TURBINE_DEMO =
            BLOCKS.register("turbine_demo", TurbineDemoBlock::new);

    public static final RegistryObject<BlockEntityType<TurbineDemoBlockEntity>> TURBINE_DEMO_BE =
            BLOCK_ENTITIES.register("turbine_demo",
                    () -> BlockEntityType.Builder
                            .of(TurbineDemoBlockEntity::new, TURBINE_DEMO.get())
                            .build(null));

    public static final RegistryObject<Item> TURBINE_DEMO_ITEM =
            ITEMS.register("turbine_demo",
                    () -> new BlockItem(TURBINE_DEMO.get(), new Item.Properties()));

    /**
     * 机架类机器的「隐形碰撞方块」—— 由所有「底座是平板、塔是叠加视觉」的机器共用
     * （{@code tower_rig} / {@code arch_rig} / {@code mast_rig}）。
     *
     * <p>这些塔各占几十格见方、几十格高，而机器本体只有底座那 1 层，所以需要按
     * 离线体素化出来的坐标表铺几千格这种方块，让玩家撞得到、站得上。</p>
     *
     * <p>坐标表（每台机器一张）：{@code assets/gtoecore/structures/}<i>&lt;name&gt;</i>{@code _collision.txt}
     * （工具 {@code tools/gen_tower_collision.py}）。执行器 {@link RigCollision}。</p>
     *
     * <p><b>故意不给物品、不进创造栏</b>：它没有任何外观，只是一个碰撞代理。</p>
     *
     * <p>⚠ <b>注册名 {@code tower_rig_collision} 是历史遗留</b>（本方块最早只服务
     * {@code tower_rig}）。它现在是三台机器共用的，但改名会让旧存档里已经铺出去的
     * 碰撞块变成「未知方块」而为空 —— 所以名字保留，只用字段名说明它是通用的。</p>
     */
    public static final RegistryObject<TowerRigCollisionBlock> RIG_COLLISION =
            BLOCKS.register("tower_rig_collision", TowerRigCollisionBlock::new);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
    }

    private GTOEObjDemo() {
    }
}
