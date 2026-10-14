package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.GTOFluids;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 聚光阵列（Solar Concentrator Array）—— 光凝流链路的第二环。
 *
 * <p><b>定位：</b>聚变光源核心（{@link LuminaFusionCoreMachine}）产出「聚变光源」
 * 流体 → 本机把<b>聚变光源凝聚成定向强光</b>（激光能 LU 数值）→ 激光管道
 * （{@code laser_pipe}）直连送给量子凝聚器或能量接收仓。</p>
 *
 * <p><b>为什么需要它：</b>聚变光源是「光子原料」，光子还不能直接用；必须经光学
 * 阵列聚焦、准直成<b>定向激光束</b>才具备做功能力 —— 即本机是「聚变光源（原料）
 * → 激光能（可用能量）」的转换器。</p>
 *
 * <p><b>输入 = 聚变光源流体 + 维持 EU。</b>EU 用于维持光学阵列的聚焦/准直场
 * （断电即停摆、产出归零），聚变光源按 tick 消耗。超损超频（基类）：超 1 档
 * 功耗 ×4 / 输出 ×2，聚变光源消耗同步 ×4（效率 50%/档，不凭空增产）。</p>
 *
 * <p><b>15 压（ULV~MAX）：</b>满功率输出 W 随电压等级 ×4/级增长
 * （ULV 512 W → LV 2048 W → MV 8192 W → …），是<b>同档激光聚束器（128&lt;&lt;2tier W）
 * 的 4 倍</b> —— 高级聚光阵列能把同样的聚变光源榨出更多能量，形成升级动力。</p>
 *
 * <p><b>输入罐容量：</b>8 000 mB × 2^tier（上限 20 亿），随电压提升增加。</p>
 */
public class SolarConcentratorMachine extends GTOEnergyConverterMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(SolarConcentratorMachine.class,
                    GTOEnergyConverterMachine.MANAGED_FIELD_HOLDER);

    /** 满功率时每 tick 消耗的聚变光源（mB）—— 超频时按 4^oc 同步放大 */
    private static final int MB_PER_TICK_BASE = 4;

    /** 聚变光源输入罐（只收聚变光源；纯罐装流体，可用流体管道/流体仓送入） */
    @Persisted
    public final NotifiableFluidTank lightInputTank;

    public SolarConcentratorMachine(IMachineBlockEntity holder, int tier) {
        // 输出 LU 数值（W）；满功率 = 同档激光聚束器 ×4
        super(holder, tier, GTOEnergyType.LASER, 512L << (2 * tier), (int) GTValues.V[tier]);
        int cap = (int) Math.min(2_000_000_000L, 8_000L << tier);
        this.lightInputTank = new NotifiableFluidTank(this, 1, cap, IO.IN);
        this.lightInputTank.setFilter(stack -> stack.getFluid() == GTOFluids.FUSION_LIGHT.get());
        attachTraits(lightInputTank);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- tick：EU + 聚变光源 → 激光能数值（W） ----------------

    @Override
    protected void tickConverter() {
        updateOcLevel();   // ⚠ 必须调用：基类的 oc 计算，否则超频对本机永远失效
        long stored = energyContainer.getEnergyStored();
        long targetDraw = (long) drawBudgetBase * (1L << (2 * ocLevel));
        int draw = (int) Math.min(targetDraw, stored);

        // 满功率聚变光源消耗（超频 ×4^oc），再按实际抽电比例折算（至少 1 mB 才够一次聚焦）
        int needMb = (int) Math.max(1L,
                (long) MB_PER_TICK_BASE * (1L << (2 * ocLevel)) * Math.max(draw, 0) / Math.max(1L, targetDraw));

        FluidStack available = lightInputTank.getStorages()[0].drain(
                new FluidStack(GTOFluids.FUSION_LIGHT.get(), needMb),
                IFluidHandler.FluidAction.SIMULATE);
        boolean lightOk = available != null && available.getAmount() >= needMb;

        if (draw > 0 && lightOk) {
            lightInputTank.getStorages()[0].drain(
                    new FluidStack(GTOFluids.FUSION_LIGHT.get(), needMb),
                    IFluidHandler.FluidAction.EXECUTE);
            energyContainer.removeEnergy(draw);
            lastDraw = draw;
            currentValue = computeValue(draw);
        } else {
            lastDraw = 0;
            currentValue = 0L;
        }
    }

    /** 当前每 tick 聚变光源消耗（UI / Jade 读数用） */
    public int getLightConsumption() {
        if (lastDraw <= 0) return 0;
        long targetDraw = (long) drawBudgetBase * (1L << (2 * ocLevel));
        return (int) Math.max(1L,
                (long) MB_PER_TICK_BASE * (1L << (2 * ocLevel)) * lastDraw / Math.max(1L, targetDraw));
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = (WidgetGroup) super.createUIWidget();
        // 聚变光源输入罐（橙框流体槽）
        group.addWidget(new TankWidget(lightInputTank.getStorages()[0], 130, 30, 18, 18, true, true)
                .setShowAmount(true)
                .setDrawHoverTips(true)
                .setBackground(GuiTextures.FLUID_SLOT));
        group.addWidget(new LabelWidget(120, 52, "聚变光源"));
        group.addWidget(new LabelWidget(8, 138,
                () -> "耗光源: " + (getLightConsumption() > 0 ? getLightConsumption() + " mB/t" : "--")
                        + "  罐: " + lightInputTank.getStorages()[0].getFluidAmount()
                        + " / " + lightInputTank.getStorages()[0].getCapacity()));
        return group;
    }

    // ---------------- 持久化（罐由 @Persisted trait 托管，其余走基类） ----------------

    @Override
    public void saveCustomPersistedData(@NotNull net.minecraft.nbt.CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
    }

    @Override
    public void loadCustomPersistedData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.loadCustomPersistedData(tag);
    }
}