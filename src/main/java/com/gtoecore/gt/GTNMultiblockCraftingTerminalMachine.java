package com.gtoecore.gt;

import appeng.api.config.Actionable;
import appeng.api.config.AccessRestriction;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.MEStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.IConfigManager;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.storage.NullInventory;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.util.ConfigManager;
import appeng.util.inv.AppEngInternalInventory;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.EnumSet;
import java.util.List;

/**
 * AE 合成终端多方块主机。
 *
 * <p><b>定位</b>：把 AE2 的合成终端做成 GT 多方块机器 —— 3×3×3 脱氧钢外壳，
 * 成形后右键控制器直接打开 AE2 原版合成终端界面（9 格合成格、网格物品存取、
 * 自动合成请求全可用）。</p>
 *
 * <p><b>实现要点（已 javap 实证 AE2 15.4.10）</b>：</p>
 * <ul>
 *   <li><b>菜单宿主不是本类，而是 {@link CraftingTerminalBlockEntity}</b>（本机注册的专用 BE）。
 *       AE2 的 {@code AEBaseMenu} 只接受 BlockEntity / IPart / ItemMenuHost，
 *       {@code CraftingTermMenu} 还要宿主实现 {@link ISegmentedInventory} 去取 9 格合成盘，
 *       {@code MEStorageMenu} 还要宿主实现 {@code IActionHost} 才能拿到 networkNode / powerSource。
 *       本类只负责「机器侧」的能力：网格存储（{@link #getInventory()}）、
 *       常驻设置（{@link #getConfigManager()}）、9 格合成盘（{@link #getSubInventory}）。</li>
 *   <li>右键：{@link IInteractedMachine#onUse} 里
 *       {@code MenuOpener.open(CraftingTermMenu.TYPE, player, locator)}；
 *       locator 用 {@link GTOEMultiblockTerminalLocator}（自定义），
 *       因为 {@code MenuLocators.forBlockEntity} 内部的定位器是包私有、且只认「BE 自身实现接口」。</li>
 *   <li>网络接入：本机自持 {@link IManagedGridNode}（{@link GridHelper}）并实现
 *       {@link IGridConnectedBlockEntity}，旁边拉一根 ME 线缆即接入网格。</li>
 * </ul>
 */
public class GTNMultiblockCraftingTerminalMachine extends WorkableMultiblockMachine
        implements IInteractedMachine, IGridConnectedBlockEntity, IAEPowerStorage, ITerminalHost,
        ISegmentedInventory, IDisplayUIMachine {

    private static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(GTNMultiblockCraftingTerminalMachine.class,
                    WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 设置持久化用的子 tag（ConfigManager.writeToNBT 是平铺键，必须包一层避免撞名）。 */
    private static final String CFG_TAG = "gtoe_ae_terminal_cfg";

    private final IManagedGridNode mainNode;

    /** 本机是否已接入网格并有频道（由网格状态回调更新）。 */
    private boolean online;

    /**
     * 9 格合成盘 —— AE 合成终端与普通 ME 终端的唯一差别。
     * AE2 的 {@code CraftingTermMenu} 会往宿主要 {@code getSubInventory(INV_CRAFTING)}。
     */
    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(9);

    /**
     * 常驻的终端设置（排序 / 视图 / 类型过滤 / 排序方向）。
     *
     * <p>⛔ 必须是常驻字段且必须注册这 4 项：AE2 在服务端用它读设置、客户端改设置也会回写它，
     * 而 {@code ConfigManager.putSetting} 对<b>未注册</b>的设置直接抛
     * {@code UnsupportedSettingException}（javap 实证）。</p>
     */
    private final IConfigManager configManager = new ConfigManager(() -> {});

    public GTNMultiblockCraftingTerminalMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.configManager.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.configManager.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.configManager.registerSetting(Settings.TYPE_FILTER, TypeFilter.ALL);
        this.configManager.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.mainNode = GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(1.0);
        this.mainNode.setVisualRepresentation(holder.getDefinition().getItem());
        this.mainNode.addService(IAEPowerStorage.class, this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- 网格节点（IGridConnectedBlockEntity） ----------------

    @Override
    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    @Override
    public void saveChanges() {
        onChanged();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.online = mainNode.isOnline() && mainNode.isPowered();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            // 对齐 GTM GridNodeHolder：延迟 1 tick 等世界/区块就绪再 create 节点
            if (getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new TickTask(0, () -> {
                    if (getLevel() != null && !isInValid()) {
                        mainNode.create(getLevel(), getPos());
                    }
                }));
            }
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        mainNode.destroy();
    }

    /** 是否已接入网格（AE2 的 IGridConnectedBlockEntity 没有 isOnline，故不标 @Override）。 */
    public boolean isGridOnline() {
        return online;
    }

    // ---------------- 右键：打开 AE 合成终端 ----------------

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        if (isRemote()) {
            return InteractionResult.SUCCESS;
        }
        if (!isFormed()) {
            return InteractionResult.PASS;
        }
        MenuOpener.open(CraftingTermMenu.TYPE, player, new GTOEMultiblockTerminalLocator(pos));
        return InteractionResult.SUCCESS;
    }

    // ---------------- ITerminalHost ----------------

    @Override
    public MEStorage getInventory() {
        if (!isFormed()) {
            // 未成形/客户端重建菜单时也要有值，否则 MEStorageMenu 构造解引用 NPE
            return NullInventory.of();
        }
        IGridNode node = mainNode.getNode();
        if (node == null || node.getGrid() == null) {
            return NullInventory.of();
        }
        return node.getGrid().getStorageService().getInventory();
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return UpgradeInventories.empty();
    }

    // ---------------- ISegmentedInventory：9 格合成盘 ----------------

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        // AE2 约定：未知分段返回 null（MEStorageMenu 取 CELLS 失败时自己会兜底）
        return CraftingTerminalPart.INV_CRAFTING.equals(id) ? craftingGrid : null;
    }

    // ---------------- 设置持久化（MetaMachine 的自定义数据钩子） ----------------

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forSyncing) {
        super.saveCustomPersistedData(tag, forSyncing);
        CompoundTag cfg = new CompoundTag();
        configManager.writeToNBT(cfg);
        tag.put(CFG_TAG, cfg);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains(CFG_TAG, CompoundTag.TAG_COMPOUND)) {
            configManager.readFromNBT(tag.getCompound(CFG_TAG));
        }
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.open(CraftingTermMenu.TYPE, player, new GTOEMultiblockTerminalLocator(getPos()));
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getDefinition().getItem());
    }

    @Override
    public String getCloseHotkey() {
        return null;
    }

    // ---------------- IAEPowerStorage（终端不耗电：提供无限电源让节点在线） ----------------

    @Override
    public double injectAEPower(double amt, Actionable mode) {
        return 0;
    }

    @Override
    public double getAEMaxPower() {
        return Long.MAX_VALUE / 10000.0;
    }

    @Override
    public double getAECurrentPower() {
        return Long.MAX_VALUE / 10000.0;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        return amt;
    }

    // ---------------- IDisplayUIMachine（成形后界面显示状态） ----------------

    @Override
    public void addDisplayText(List<Component> textList) {
        IDisplayUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            if (isGridOnline()) {
                textList.add(Component.translatable("gtoecore.machine.crafting_terminal.display.online"));
            } else {
                textList.add(Component.translatable("gtoecore.machine.crafting_terminal.display.offline"));
            }
        }
    }
}
