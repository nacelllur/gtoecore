package com.gtoecore.deepspace;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtoecore.GTNItems;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 深空枢纽主机 —— 对标 GTO 通天之路（road_of_heaven）的供能中枢。
 *
 * <p><b>与通天之路的对应关系：</b></p>
 * <ul>
 *   <li>太空电梯主体供能（{@code EUt=VA[tier]}）→ 主机每 tick 扣 1A 本机电压的 EU</li>
 *   <li>每 80 tick 吃 1 碳纳米管线轴 → 每 {@link #FUEL_INTERVAL} tick 吃 1 深空燃料单元</li>
 *   <li>断粮 → 模块计数归零、全模块停摆 → 断燃料/断 EU 立即 {@code offline} 并通知全网</li>
 *   <li>动力模块 MK1-5 决定可跑配方等级 → 主机等级 + 深空传感模块数 = 网络等级</li>
 *   <li>模块基座 ≤64，各自跑配方 → 16 格内任意数量模块，各自跑配方</li>
 * </ul>
 *
 * <p><b>加成聚合：</b>主机每 {@link DeepSpaceHubManager#SCAN_INTERVAL} tick 扫一次周围
 * 模块，把聚合后的 {@link DeepSpaceHubManager.Bonus} 推给每个模块；模块在自己的配方
 * 修饰器里读取，实现「同类模块越多、数值越强」的无限叠加。</p>
 */
public class DeepSpaceHubMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(DeepSpaceHubMachine.class,
                    WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 燃料消耗周期（tick）—— 对标通天之路每 80 tick 吃 1 个线轴 */
    public static final int FUEL_INTERVAL = 80;

    /** 在线状态（成形 && 有电 && 有燃料） */
    @Persisted
    @DescSynced
    private boolean online;

    /** 当前挂载的模块总数 */
    @Persisted
    @DescSynced
    private int moduleCount;

    /** 累计消耗的燃料（GUI 展示） */
    @Persisted
    @DescSynced
    private int fuelUsed;

    /** 网络等级 = 主机能量仓等级 + 深空传感模块数 */
    @Persisted
    @DescSynced
    private int networkTier;

    /** 聚合加成缓存（不持久化，每次扫描重建） */
    private DeepSpaceHubManager.Bonus cachedBonus = new DeepSpaceHubManager.Bonus();

    /** 已推送模块列表（下线时逐个复位） */
    private final List<DeepSpaceModuleMachine> knownModules = new ArrayList<>();

    private TickableSubscription tickSub;

    public DeepSpaceHubMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- 生命周期 ----------------

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (tickSub == null) {
            tickSub = subscribeServerTick(this::hubTick);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        unsubscribe();
        online = false;
        moduleCount = 0;
        cachedBonus = new DeepSpaceHubManager.Bonus();
        notifyModulesOffline();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        unsubscribe();
        notifyModulesOffline();
    }

    private void unsubscribe() {
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    // ---------------- 主循环 ----------------

    private void hubTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide || !isFormed()) return;

        // ① 供能：每 tick 扣 1A 本机电压的 EU（对标通天之路的 EUt = VA[tier]）
        int tier = Math.min(Math.max(getTier(), GTValues.LV), GTValues.MAX);
        long need = GTValues.VA[tier];
        IEnergyContainer container = getEnergyContainer();
        if (container == null || container.getEnergyStored() < need) {
            setOffline();
            return;
        }
        container.removeEnergy(need);

        // ② 耗材：每 80 tick 吃 1 个深空燃料单元，断供立即离线
        if (getOffsetTimer() % FUEL_INTERVAL == 0 && !consumeFuel()) {
            setOffline();
            return;
        }

        // ③ 在线：定期刷新网络并推送加成
        online = true;
        if (getOffsetTimer() % DeepSpaceHubManager.SCAN_INTERVAL == 0) {
            refreshNetwork(level);
        }
    }

    /**
     * 从物品输入仓扣 1 个深空燃料单元；无燃料返回 false。
     *
     * <p>踩坑：{@code NotifiableItemStackHandler} 的对外 {@code extractItem} 有能力门控
     * （按 HandlerIO/CapabilityIO 判定，不匹配时静默返回空堆），机器内部逻辑必须直接
     * 操作 {@code storage}。</p>
     */
    private boolean consumeFuel() {
        for (IMultiPart part : getParts()) {
            if (!(part instanceof ItemBusPartMachine bus)) continue;
            NotifiableItemStackHandler inv = bus.getInventory();
            for (int slot = 0; slot < inv.storage.getSlots(); slot++) {
                ItemStack stack = inv.storage.getStackInSlot(slot);
                if (stack.is(GTNItems.DEEP_SPACE_FUEL.get())) {
                    ItemStack got = inv.storage.extractItem(slot, 1, false);
                    if (!got.isEmpty()) {
                        fuelUsed++;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void setOffline() {
        if (online) {
            online = false;
            // 断供：立刻通知全网模块停摆（对标通天之路断线轴 → 全模块失效）
            notifyModulesOffline();
        }
    }

    /** 扫描周围模块 → 聚合加成 → 推送给每台模块 */
    private void refreshNetwork(Level level) {
        DeepSpaceHubManager.Bonus bonus = new DeepSpaceHubManager.Bonus();
        List<DeepSpaceModuleMachine> found = new ArrayList<>();

        DeepSpaceHubManager.forEachModule(level, getPos(), module -> {
            bonus.add(module.getModuleKind());
            found.add(module);
        });

        cachedBonus = bonus;
        moduleCount = bonus.totalModules();
        networkTier = Math.min(Math.max(getTier(), GTValues.LV), GTValues.MAX) + bonus.bonusTier();

        // 先复位掉线的旧模块，再推送当前网络
        for (DeepSpaceModuleMachine old : knownModules) {
            if (!found.contains(old)) {
                old.onNetworkUpdate(null, new DeepSpaceHubManager.Bonus(), false);
            }
        }
        for (DeepSpaceModuleMachine module : found) {
            module.onNetworkUpdate(this, bonus, true);
        }
        knownModules.clear();
        knownModules.addAll(found);
    }

    private void notifyModulesOffline() {
        for (DeepSpaceModuleMachine module : knownModules) {
            module.onNetworkUpdate(null, new DeepSpaceHubManager.Bonus(), false);
        }
        knownModules.clear();
        moduleCount = 0;
    }

    // ---------------- 对外查询 ----------------

    public boolean isOnline() {
        return online && isFormed();
    }

    public int getNetworkTier() {
        return networkTier;
    }

    public int getModuleCount() {
        return moduleCount;
    }

    public DeepSpaceHubManager.Bonus getCachedBonus() {
        return cachedBonus;
    }

    public BlockPos hubPos() {
        return getPos();
    }

    // ---------------- GUI ----------------

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), online)
                .addWorkingStatusLine()
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(getTier());
        // 注意：WorkableElectricMultiblockMachine 自身已实现 addDisplayText，
        // 走 super 即可；IDisplayUIMachine.super 只对「直接实现该接口」的类合法。
        super.addDisplayText(textList);

        if (!isFormed()) return;
        textList.add(Component.translatable(online
                ? "gtoecore.deepspace.hub.online" : "gtoecore.deepspace.hub.offline"));
        textList.add(Component.translatable("gtoecore.deepspace.hub.fuel", fuelUsed));
        textList.add(Component.translatable("gtoecore.deepspace.hub.modules", moduleCount));
        textList.add(Component.translatable("gtoecore.deepspace.hub.tier", networkTier));

        if (!cachedBonus.isEmpty()) {
            textList.add(Component.translatable("gtoecore.deepspace.hub.bonus_header"));
            for (ModuleKind kind : ModuleKind.values()) {
                int n = cachedBonus.count(kind);
                if (n > 0) {
                    textList.add(Component.translatable("gtoecore.deepspace.module_line",
                            n, Component.translatable(kind.cn())));
                }
            }
        }
    }
}