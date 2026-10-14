package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtoecore.energy.GTOEnergyType;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 激光聚束器（LU 产能机）—— GT 电 → 激光能数值（W），由激光管道/能量接收仓读取。
 *
 * <p><b>2026-10-05 晚改版：</b>不再产出「激光能」流体，只输出 LU 数值。
 * 激光管道（{@code laser_pipe}）直接连接本机即可把 W 值传给量子凝聚器或能量接收仓，
 * 无需流体管道。超损超频（基类）：超 1 档功耗 ×4 / 输出 ×2。</p>
 *
 * <p><b>15 压（ULV~MAX）：</b>满功率输出 W 随电压等级 ×4/级增长
 * （ULV 128 W → LV 512 W → MV 2048 W → …）。</p>
 */
public class LaserFocusMachine extends GTOEnergyConverterMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(LaserFocusMachine.class, GTOEnergyConverterMachine.MANAGED_FIELD_HOLDER);

    public LaserFocusMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, GTOEnergyType.LASER, 128L << (2 * tier), (int) GTValues.V[tier]);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = (WidgetGroup) super.createUIWidget();
        group.addWidget(new LabelWidget(8, 138,
                () -> "输出: " + (currentValue > 0
                        ? currentValue + " W (激光能)"
                        : "--（无电力输入）")));
        return group;
    }
}
