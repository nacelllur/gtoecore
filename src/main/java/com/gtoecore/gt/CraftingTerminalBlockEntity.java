package com.gtoecore.gt;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.IConfigManager;
import appeng.me.storage.NullInventory;
import appeng.menu.ISubMenu;
import appeng.util.ConfigManager;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE 合成终端机器专用的方块实体（{@code gtoecore:ae_crafting_terminal}）。
 *
 * <p><b>为什么需要它</b>：AE2 的 {@code AEBaseMenu} 只接受
 * <b>BlockEntity / IPart / ItemMenuHost</b> 当菜单宿主（javap 实证：<i>Must have a valid host</i>），
 * 而 GT 机器的 {@code MetaMachine} 不是 BlockEntity；{@code CraftingTermMenu} 还要把宿主强转
 * {@link ISegmentedInventory} 取 9 格合成盘，{@code MEStorageMenu} 则需要宿主是
 * {@link IActionHost} 才能从网格取 {@code networkNode} / {@code powerSource}。
 * 这些接口都长在「BE」这个层级上，所以本机单独注册了这个 BE 子类，
 * 把它实现齐并**全部转交机器本体**（{@link GTNMultiblockCraftingTerminalMachine}）。</p>
 *
 * <p><b>为何不用 mixin</b>：{@code IMachineBlock} 的 {@code newBlockEntity} 默认实现 =
 * {@code definition.getBlockEntityType().create(pos, state)}，而 BE 类型由
 * {@code MachineBuilder} 构造器注入的 {@code blockEntityFactory} 注册；
 * {@code GTRegistrate.multiblock} 有带该工厂的重载。⇒ 只给这一台机器换 BE 类即可，
 * 无需改动 GTCEu 核心类。</p>
 *
 * <p><b>双端可用</b>：AE2 服务端 {@code open()} 与客户端 {@code fromNetwork()}
 * 都会调 {@code MenuLocator.locate(player, ITerminalHost.class)}，本类在双端都存在，
 * 所以两边的菜单都能构造成功。</p>
 */
public class CraftingTerminalBlockEntity extends MetaMachineBlockEntity
        implements ITerminalHost, ISegmentedInventory, IActionHost {

    public CraftingTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 本机机器本体；类型不符时返回 {@code null}（正常情况不会发生）。 */
    private GTNMultiblockCraftingTerminalMachine terminal() {
        return metaMachine instanceof GTNMultiblockCraftingTerminalMachine m ? m : null;
    }

    // ---------------- ITerminalHost ----------------

    @Override
    public MEStorage getInventory() {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getInventory() : NullInventory.of();
    }

    @Override
    public IConfigManager getConfigManager() {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getConfigManager() : new ConfigManager(() -> {});
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getUpgrades() : UpgradeInventories.empty();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        if (t != null) {
            t.returnToMainMenu(player, subMenu);
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getMainMenuIcon() : ItemStack.EMPTY;
    }

    @Override
    public String getCloseHotkey() {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getCloseHotkey() : null;
    }

    // ---------------- ISegmentedInventory（9 格合成盘） ----------------

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getSubInventory(id) : null;
    }

    // ---------------- IActionHost（MEStorageMenu 由此取 networkNode / powerSource） ----------------

    @Override
    public IGridNode getActionableNode() {
        GTNMultiblockCraftingTerminalMachine t = terminal();
        return t != null ? t.getActionableNode() : null;
    }
}
