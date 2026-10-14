package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtoecore.energy.GTOEnergyType;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 低温冷凝机（CU 产能机）—— GT 电 → 冷能。
 *
 * <p><b>15 压（ULV~MAX，2026-10-05）：</b>满功率输出制冷值随电压等级 ×4/级
 * 增长（ULV 24 K → LV 96 K → MV 384 K → … → MAX 24M K）；耗电 = 本机电压 1A。
 * 支持超损超频（基类 {@link GTOEnergyConverterMachine}）：超 1 档功耗 ×4 / 产出 ×2，无上限。</p>
 *
 * <p>断电或电压不足时输出值按抽电比例回升（趋近 0 = 无冷源），
 * 配方条件 {@code energy_range} 用 min/max 区间卡温带。</p>
 */
public class CryogenicCondenserMachine extends GTOEnergyConverterMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(CryogenicCondenserMachine.class, GTOEnergyConverterMachine.MANAGED_FIELD_HOLDER);

    public CryogenicCondenserMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, GTOEnergyType.COLD, 24L << (2 * tier), (int) GTValues.V[tier]);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}