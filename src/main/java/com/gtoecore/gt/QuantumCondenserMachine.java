package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.GTOFluids;
import com.gtoecore.energy.IGTOEnergyAcceptor;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 量子凝聚器（QU 产能机）—— GT 电 + 激光能数值 → 量子能（QU）+「量子能」流体。
 *
 * <p><b>2026-10-05 晚改版：</b>激光能输入改为<b>数值通道</b>——激光管道（{@code laser_pipe}）
 * 直连本机即可注入 LU 值（W），<b>不再需要流体管道</b>。激光聚束器也不再产激光能流体。</p>
 *
 * <p><b>15 压（ULV~MAX）：</b>满功率输出 QU 随电压等级 ×4/级增长
 * （ULV 4 QU → LV 16 QU → MV 64 QU → …）。超损超频（基类）：超 1 档功耗 ×4 / 输出 ×2。</p>
 *
 * <p><b>量子能流体产出（保留）：</b>参考 GT 聚变反应堆，凝聚出可长期储运的量子能流体。
 * 罐容量 8 000 mB × 2^tier（上限 20 亿）。</p>
 */
public class QuantumCondenserMachine extends GTOEnergyConverterMachine implements IGTOEnergyAcceptor {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(QuantumCondenserMachine.class, GTOEnergyConverterMachine.MANAGED_FIELD_HOLDER);

    /** 量子能输出罐（纯罐装流体，可用流体管道/流体仓外运） */
    @Persisted
    public final NotifiableFluidTank quantumTank;
    /** 激光能数值输入（激光管道每 tick 注入，W）—— 短窗口保持，防 tick 顺序抖动 */
    protected long laserInValue;
    /** 数值输入的来源档位 */
    protected int laserInTier = 1;
    /** 数值窗口剩余 tick（注入后保持，防管道/机器 tick 顺序错位导致偶发停摆） */
    protected int laserInHold = 0;
    private static final int LASER_HOLD_TICKS = 20;   // 1 秒窗口

    public QuantumCondenserMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, GTOEnergyType.QUANTUM, 4L << (2 * tier), (int) GTValues.V[tier]);
        int cap = (int) Math.min(2_000_000_000L, 8_000L << tier);
        this.quantumTank = new NotifiableFluidTank(this, 1, cap, IO.OUT);
        attachTraits(quantumTank);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- IGTOEnergyAcceptor（激光能数值输入通道） ----------------

    /** 只接受激光能（LU）：激光管道/激光聚束器直连即可喂入，无需流体管道 */
    @Override
    public boolean acceptsGTOEnergy(GTOEnergyType type, Direction side) {
        return type == GTOEnergyType.LASER;
    }

    @Override
    public long injectGTOEnergy(GTOEnergyType type, Direction side, int tier, long amount, boolean simulate) {
        if (type != GTOEnergyType.LASER) return 0L;
        if (!simulate) {
            laserInValue = amount;
            laserInTier = Math.max(tier, 1);
            laserInHold = LASER_HOLD_TICKS;
        }
        return amount;
    }

    // ---------------- tick：EU + 激光能数值 → 量子能 + 量子能流体 ----------------

    @Override
    protected void tickConverter() {
        updateOcLevel();   // ⚠ 必须调用：基类的 oc 计算，否则超频对这台机器永远失效
        long stored = energyContainer.getEnergyStored();
        int draw = (int) Math.min((long) drawBudgetBase * (1L << (2 * ocLevel)), stored);

        // 激光供能判定：数值通道（激光管道注入的 W 值）
        // 同档激光聚束器满输出 = 128<<2tier W；注入值 ≥ 该值即全供
        long needW = 128L << (2 * nominalTier);
        boolean laserOk = laserInValue >= needW;

        if (draw > 0 && laserOk) {
            energyContainer.removeEnergy(draw);
            lastDraw = draw;
            currentValue = computeValue(draw);
            // EU+激光能 → 量子能流体：满功率 1 mB/t（耗电/64）
            quantumTank.getStorages()[0].fill(
                    new FluidStack(GTOFluids.QUANTUM_ENERGY.get(), Math.max(1, draw / 64)),
                    IFluidHandler.FluidAction.EXECUTE);
        } else {
            lastDraw = 0;
            currentValue = 0L;
        }
        // 数值输入窗口衰减：注入值保持 LASER_HOLD_TICKS 后才清，防 tick 顺序抖动
        if (laserInHold > 0) {
            laserInHold--;
            if (laserInHold == 0) {
                laserInValue = 0L;
            }
        }
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = (WidgetGroup) super.createUIWidget();
        // 量子能输出罐
        group.addWidget(new TankWidget(quantumTank.getStorages()[0], 130, 30, 18, 18, true, false)
                .setShowAmount(true)
                .setDrawHoverTips(true)
                .setBackground(GuiTextures.FLUID_SLOT));
        group.addWidget(new LabelWidget(120, 52, "量子能"));
        group.addWidget(new LabelWidget(8, 138,
                () -> "需激光: " + (128L << (2 * nominalTier)) + " W"
                        + "  产流: " + (lastDraw > 0 ? Math.max(1, lastDraw / 64) + " mB/t" : "--")
                        + "  罐: " + quantumTank.getStorages()[0].getFluidAmount()
                        + " / " + quantumTank.getStorages()[0].getCapacity()));
        return group;
    }

    // ---------------- 持久化（数值输入为运行时量，仅存罐与基类字段） ----------------

    @Override
    public void saveCustomPersistedData(@NotNull net.minecraft.nbt.CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
    }

    @Override
    public void loadCustomPersistedData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        laserInValue = 0L;
        laserInTier = 1;
    }
}
