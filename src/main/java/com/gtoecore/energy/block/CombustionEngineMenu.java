package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import com.gtoecore.GTNCore;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * 燃烧引擎 GUI 容器 —— 1 个燃料槽 + 玩家背包 + 燃烧/转速数据同步。
 *
 * <p>数值同步走 {@link DataSlot}（burn = 剩余燃烧 tick，speed = 标称 rpm），
 * 服务端每 tick 经 {@link #broadcastChanges()} 刷新，客户端屏幕直接读。
 * 燃料放入槽内后由 broadcastChanges 吞掉一格并注入 BE（燃余量 +2400 tick、转速按品质）。</p>
 */
public class CombustionEngineMenu extends AbstractContainerMenu {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, GTNCore.MODID);
    public static final RegistryObject<MenuType<CombustionEngineMenu>> TYPE =
            MENUS.register("combustion_engine", () -> new MenuType<CombustionEngineMenu>(
                    (id, inv) -> new CombustionEngineMenu(id, inv),
                    FeatureFlags.VANILLA_SET));

    private final CombustionEngineBlockEntity be;   // 服务端持有；客户端网络路径为 null
    private final Container fuelSlot = new SimpleContainer(1);
    private final ContainerLevelAccess access;

    /** 剩余燃烧 tick（0=停机） */
    public final DataSlot burnData = DataSlot.standalone();
    /** 标称转速 rpm */
    public final DataSlot speedData = DataSlot.standalone();

    /** 客户端网络构造 */
    public CombustionEngineMenu(int containerId, Inventory inv) {
        this(containerId, inv, null);
    }

    /** 服务端构造 */
    public CombustionEngineMenu(int containerId, Inventory inv, CombustionEngineBlockEntity be) {
        super(TYPE.get(), containerId);
        this.be = be;
        this.access = be == null ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        addSlot(new Slot(fuelSlot, 0, 80, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return ForgeHooks.getBurnTime(stack, null) > 0;
            }
        });
        // 玩家背包 3x9 + 快捷栏 9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
        addDataSlot(burnData);
        addDataSlot(speedData);
        if (be != null) {
            burnData.set(be.getBurnRemaining());
            speedData.set((int) be.getNominalSpeed());
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide) return;
        // 燃料槽：吞掉一格燃料注入 BE（燃余量 +2400，转速按品质提升）
        ItemStack f = fuelSlot.getItem(0);
        if (!f.isEmpty() && ForgeHooks.getBurnTime(f, null) > 0) {
            be.addFuel(f);
            f.shrink(1);
            fuelSlot.setItem(0, f);
        }
        burnData.set(be.getBurnRemaining());
        speedData.set((int) be.getNominalSpeed());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack copy = slot.getItem().copy();
        if (index == 0) {
            if (!moveItemStackTo(slot.getItem(), 1, slots.size(), false)) return ItemStack.EMPTY;
        } else {
            if (ForgeHooks.getBurnTime(slot.getItem(), null) <= 0) return ItemStack.EMPTY;
            if (!moveItemStackTo(slot.getItem(), 0, 1, false)) return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return be != null
                ? stillValid(access, player, GTNBlocks.COMBUSTION_ENGINE.get())
                : true;
    }

    /** 供 GUIN 注册挂载（mod 构造期调用） */
    public static void registerMenuTypes(IEventBus bus) {
        MENUS.register(bus);
    }
}