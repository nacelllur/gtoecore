package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.utils.GTUtil;
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
import org.jetbrains.annotations.NotNull;

/**
 * GTOE 电动产能机基类 —— GT 电 → 单一 GTO 能量（GTM 单方块机器，GT 原生 FancyUI）。
 *
 * <p><b>15 压批量注册 + 超损超频（2026-10-05 改造）：</b></p>
 * <ul>
 *   <li>机器有标称电压（构造器 tier，ULV~MAX 共 15 级）；EU 容器按标称缩放
 *       （容量 {@code V[tier]×256}、放行电压 {@code V[14]=MAX}、2A 进电）。
 *       MAX 级放行 = 喂任何电压都不炸机，超频档由注入电压自行决定。</li>
 *   <li><b>超损超频</b>：每 tick 用「真实注入电压」估算输入档（不靠水位），
 *       输入档 - 标称档 = 超频档 oc（<b>无上限</b>）。超频时抽电上限 ×4^oc、
 *       输出基准 ×2^oc —— 每超 1 档功耗翻 4 倍、产出只翻 2 倍
 *       （GT 原版 OC 公式，效率 50%/档，天然有损；喂多高电压就超多少档）。</li>
 *   <li>低电压喂高档机 = 输出按抽电比例打折（不超频），高电压喂低档机
 *       = 超损超频多产出——玩家需要权衡「电压匹配」还是「超频抢产」。</li>
 * </ul>
 *
 * <p>LU/QU 两台高级机器额外带流体罐（激光能/量子能注册为流体，参考 GT 聚变堆
 * 等离子体的生产方式），罐容量随电压提升增加，见 {@link LaserFocusMachine} /
 * {@link QuantumCondenserMachine}。</p>
 */
public abstract class GTOEnergyConverterMachine extends TieredMachine
        implements IFancyUIMachine, IGTOEnergyEmitter, GTOConduitBlock.GTOEnergyEmitterHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(GTOEnergyConverterMachine.class, TieredMachine.MANAGED_FIELD_HOLDER);

    /** EU 缓存（四面进电；容量/电压随标称等级缩放，放行到 +4 档不炸机；记录真实注入电压） */
    @Persisted
    public final NotifiableEnergyContainer energyContainer;
    /** 最近一次注入电压（tick 内平滑，供超频判定，不靠水位猜） */
    protected long lastVoltage;
    /** 输出能量类型（构造期由子类定死） */
    protected final GTOEnergyType outputType;
    /** 标称电压等级（0=ULV ~ 14=MAX） */
    protected final int nominalTier;
    /** 标称满功率输出值（≤1 档输入时的满值），超频在此基础上 ×2^oc */
    protected final long fullValueBase;
    /** 标称每 tick 最大抽电量（EU/t）= V[nominalTier]（1A） */
    protected final int drawBudgetBase;
    /** 当前超损超频档（0 起步，无上限：喂多高电压就超多少档） */
    protected int ocLevel;
    /** 当前实际抽电量（上个 tick，EU/t）——UI/条件读数用 */
    protected int lastDraw;
    /** 当前输出值（导体逐 tick 拉取） */
    protected long currentValue;
    /** 服务端 tick 订阅 */
    protected TickableSubscription tickSub;

    protected GTOEnergyConverterMachine(IMachineBlockEntity holder, int tier,
                                        GTOEnergyType outputType, long fullValue, int drawBudget) {
        super(holder, tier);
        this.nominalTier = tier;
        this.outputType = outputType;
        this.fullValueBase = fullValue;
        this.drawBudgetBase = drawBudget;
        this.lastVoltage = GTValues.V[tier];
        this.ocLevel = 0;
        // 容器：放行电压 = MAX 级（喂任何电压都不炸机，超频档由注入电压自行决定），
        // 2A 进电；容量 = V[tier]×256（容纳最坏超频抽电 + 进电窗口）；
        // 子类容器记录真实注入电压供 oc 判定
        this.energyContainer = new NotifiableEnergyContainer(
                this, GTValues.V[tier] * 256L, GTValues.V[14], 2, 0L, 0L) {
            @Override
            public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
                long accepted = super.acceptEnergyFromNetwork(side, voltage, amperage);
                if (accepted > 0L) {
                    lastVoltage = voltage;
                }
                return accepted;
            }
        };
        attachTraits(energyContainer);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- tick：估算输入档 → 有损超频折算 → 输出 ----------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && tickSub == null) {
            tickSub = subscribeServerTick(this::tickConverter);
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

    protected void tickConverter() {
        updateOcLevel();
        long stored = energyContainer.getEnergyStored();

        // 超频抽电上限 = 标称 1A × 4^oc；从缓存抽（不足则按余量折算）
        long targetDraw = (long) drawBudgetBase * (1L << (2 * ocLevel));
        int draw = (int) Math.min(targetDraw, stored);
        if (draw > 0) {
            energyContainer.removeEnergy(draw);
        }
        lastDraw = draw;
        currentValue = computeValue(draw);
    }

    /** 超频档 = 实际注入电压档 - 标称档（无上限：喂多高就超多少档）。断供时
     *  lastVoltage 复位为标称，抽空即降级不超频。注入电压由容器
     *  acceptEnergyFromNetwork 记录，不靠水位猜。子类覆写 tickConverter 时也要先调它。 */
    protected void updateOcLevel() {
        int inputTier = GTUtil.getTierByVoltage(Math.max(lastVoltage, GTValues.V[nominalTier]));
        ocLevel = Math.max(0, inputTier - nominalTier);
    }

    /** 输出值折算：oc=0 线性（draw=0→0；draw=budget→fullValueBase）；oc>0 时基准 ×2^oc */
    protected long computeValue(int draw) {
        if (draw <= 0 || fullValueBase <= 0) return 0L;
        long target = (long) drawBudgetBase * (1L << (2 * ocLevel));
        if (target <= 0) return 0L;
        long mult = 1L << ocLevel;
        return fullValueBase * mult * draw / target;
    }

    // ---------------- IGTOEnergyEmitter（导体拉取入口） ----------------

    @Override
    public long pullGTOEnergy(GTOEnergyType type, Direction side, int tier) {
        if (type != outputType) return 0L;
        return currentValue;
    }

    @Override
    public boolean emits(GTOEnergyType type) {
        return type == outputType;
    }

    // ---------------- 读数（UI / Jade / 配方条件用） ----------------

    public GTOEnergyType getOutputType() {
        return outputType;
    }

    public int getLastDraw() {
        return lastDraw;
    }

    /** 当前有效抽电上限（含超频放大） */
    public int getDrawBudget() {
        return (int) Math.min(Integer.MAX_VALUE,
                (long) drawBudgetBase * (1L << (2 * ocLevel)));
    }
    public int getDrawBudgetBase() {
        return drawBudgetBase;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    /** 标称电压等级（0=ULV ~ 14=MAX） */
    public int getNominalTier() {
        return nominalTier;
    }

    /** 当前超损超频档（0 起步，无上限） */
    public int getOcLevel() {
        return ocLevel;
    }

    // ---------------- UI（GTM FancyUI：EU 电量条 + 超频档 + 输出读数） ----------------

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 166);
        group.setBackground(new IGuiTexture[]{GuiTextures.BACKGROUND_INVERSE});
        // EU 电量条（GT 锅炉式竖条）+ 缓存读数
        group.addWidget(new ProgressWidget(
                () -> (double) energyContainer.getEnergyStored() / Math.max(1L, energyContainer.getEnergyCapacity()),
                12, 28, 10, 54,
                new ResourceTexture("gtceu:textures/gui/progress_bar/progress_bar_boiler_heat.png")));
        group.addWidget(new LabelWidget(8, 6,
                () -> "标称: " + GTValues.VN[nominalTier] + "  耗电: " + lastDraw + " / " + getDrawBudget() + " EU/t"));
        group.addWidget(new LabelWidget(8, 90,
                () -> "EU 缓存: " + energyContainer.getEnergyStored() + " / " + energyContainer.getEnergyCapacity()));
        // 超频档（红色警告样式）
        group.addWidget(new LabelWidget(8, 106,
                () -> ocLevel > 0
                        ? "§c超损超频 ×" + (1 << ocLevel) + "（+"
                        + (energyContainer.getInputVoltage() >= GTValues.V[14] ? "MAX 供" : "oc" + ocLevel)
                        + "）"
                        : "§7未超频（电压匹配）"));
        // 输出读数
        group.addWidget(new LabelWidget(8, 122,
                () -> "输出: " + (currentValue > 0
                        ? currentValue + " " + outputType.getUnit()
                        : "--（无电力输入）")));
        return group;
    }

    // ---------------- 持久化（trait 字段走 @Persisted；标量走手动 NBT） ----------------

    @Override
    public void saveCustomPersistedData(@NotNull net.minecraft.nbt.CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putInt("LastDraw", lastDraw);
        tag.putLong("CurrentValue", currentValue);
        tag.putInt("OcLevel", ocLevel);
    }

    @Override
    public void loadCustomPersistedData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        lastDraw = tag.getInt("LastDraw");
        currentValue = tag.getLong("CurrentValue");
        ocLevel = tag.getInt("OcLevel");
        // 注入电压为运行时量，不持久化：重载后按标称起步，下一轮进电自动更新
        lastVoltage = GTValues.V[nominalTier];
    }
}