package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyEmitter;
import com.gtoecore.energy.block.GTOConduitBlock;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 燃烧引擎（GTM 单方块机器版）—— GT6 风味 RU 产能机。
 *
 * <p>从普通方块迁移为 GTM 机器，UI 使用 GTM 原生 FancyUI 框架
 * （左侧 tab 栏 + 橙框槽位 + 深灰面板，即 GT 机器标准界面）。</p>
 *
 * <ul>
 *   <li>燃料槽（1 格，只收可燃物）：每 tick 自动吞入槽内燃料，一格 = 2400 tick（2 分钟）；</li>
 *   <li>转速档位按燃料品质：burnTime ≥ 2400 → 128 rpm，≥ 800 → 64 rpm，否则 32 rpm；</li>
 *   <li>燃烧时同时产 RU（转速）与 HU（热，转速×3+300 ≈ 400~700 K）；</li>
 *   <li>烧完自动归零，轴链自然归零。</li>
 * </ul>
 *
 * <p>能量链路兼容：{@link com.gtoecore.energy.GTOEnergyBridge} 双分支桥接
 * （普通 BE 与 GTM MetaMachine 都认），导体可直接把本机器当发射端。</p>
 */
public class CombustionEngineMachine extends TieredMachine
        implements IGTOEnergyEmitter, GTOConduitBlock.GTOEnergyEmitterHost, IFancyUIMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(CombustionEngineMachine.class, TieredMachine.MANAGED_FIELD_HOLDER);

    /** 剩余燃烧时间（tick） */
    private int burnRemaining;
    /** 当前档位标称转速（rpm） */
    private long nominalSpeed;
    /** 燃料槽（1 格，仅可燃物可放入）—— @Persisted：存档重载保留槽内燃料 */
    @Persisted
    public final NotifiableItemStackHandler importItems;
    /** 每 tick 订阅（燃料吞入 + 计时） */
    private TickableSubscription tickSub;

    public CombustionEngineMachine(IMachineBlockEntity holder) {
        super(holder, 1);
        importItems = new NotifiableItemStackHandler(this, 1, IO.IN)
                .setFilter(stack -> ForgeHooks.getBurnTime(stack, null) > 0);
        attachTraits(importItems);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- tick ----------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && tickSub == null) {
            tickSub = subscribeServerTick(this::tickWatcher);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    private void tickWatcher() {
        // 1) 从燃料槽自动吞入可燃物（一格 = 固定 2400 tick）
        ItemStack fuel = importItems.storage.getStackInSlot(0);
        if (!fuel.isEmpty()) {
            int burn = ForgeHooks.getBurnTime(fuel, null);
            if (burn > 0) {
                fuel.shrink(1);
                burnRemaining += 2400;
                nominalSpeed = Math.max(nominalSpeed, nominalForBurn(burn));
                markDirty();
            }
        }
        // 2) 计时：烧完归零
        if (burnRemaining > 0) {
            burnRemaining--;
            if (burnRemaining == 0) {
                nominalSpeed = 0L;
                markDirty();
            }
        }
    }

    // ---------------- 读数（UI 用） ----------------

    public boolean isBurning() {
        return burnRemaining > 0;
    }

    public int getBurnRemaining() {
        return burnRemaining;
    }

    public long getNominalSpeed() {
        return nominalSpeed;
    }

    private static long nominalForBurn(int burnTime) {
        if (burnTime >= 2400) return 128L;
        if (burnTime >= 800) return 64L;
        return 32L;
    }

    // ---------------- IGTOEnergyEmitter（能量产出端） ----------------

    @Override
    public long pullGTOEnergy(GTOEnergyType type, Direction side, int tier) {
        if (burnRemaining <= 0) return 0L;
        return switch (type) {
            case ROTATION -> nominalSpeed;
            case HEAT -> nominalSpeed * 3L + 300L;   // 燃烧 → 同时产热（约 400~700 K）
            default -> 0L;
        };
    }

    @Override
    public boolean emits(GTOEnergyType type) {
        return type == GTOEnergyType.ROTATION || type == GTOEnergyType.HEAT;
    }

    // ---------------- UI（GTM FancyUI，GT 机器标准界面） ----------------

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 166);
        group.setBackground(new IGuiTexture[]{GuiTextures.BACKGROUND_INVERSE});
        // 燃料槽（GT 橙框槽位）—— 用 GTM 版 SlotWidget（支持 Forge IItemHandlerModifiable）
        group.addWidget(new SlotWidget(importItems.storage, 0, 79, 30, true, true)
                .setBackgroundTexture(GuiTextures.SLOT));
        group.addWidget(new ProgressWidget(
                () -> burnRemaining > 0 ? 1.0 : 0.0, 103, 30, 16, 16,
                new ResourceTexture("gtceu:textures/gui/progress_bar/progress_bar_boiler_fuel_bronze.png")));
        // 转速 / 燃料余量读数
        group.addWidget(new LabelWidget(8, 64,
                () -> "转速: " + (burnRemaining > 0 ? nominalSpeed + " rpm" : "停机")));
        group.addWidget(new LabelWidget(8, 78,
                () -> burnRemaining > 0
                        ? "燃料余量: " + (burnRemaining / 20) + " 秒"
                        : "无燃料 — 请放入易燃物"));
        return group;
    }

    // ---------------- 持久化（LDLib @Persisted 不支持 Map；标量直接字段 + 手动 NBT） ----------------

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        CompoundTag sub = new CompoundTag();
        sub.putInt("Burn", burnRemaining);
        sub.putLong("Speed", nominalSpeed);
        tag.put("Engine", sub);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("Engine")) {
            CompoundTag sub = tag.getCompound("Engine");
            burnRemaining = sub.getInt("Burn");
            nominalSpeed = sub.getLong("Speed");
        }
    }
}
