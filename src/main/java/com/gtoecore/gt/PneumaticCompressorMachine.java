package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtoecore.energy.GTOEnergyType;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 气动压缩机（KU 产能机）—— GT 电 → 动能（压强 kPa）。
 *
 * <p><b>15 压（ULV~MAX，2026-10-05）：</b>满功率输出随电压等级 ×4/级增长
 * （ULV 64 kPa → LV 256 kPa → MV 1024 kPa → …）；耗电 = 本机电压 1A。
 * 支持超损超频：超 1 档功耗 ×4 / 压强 ×2，无上限。</p>
 *
 * <p>气压随抽电比例线性，断电归零；配方条件用 {@code energy_range} 卡压强带。</p>
 */
public class PneumaticCompressorMachine extends GTOEnergyConverterMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(PneumaticCompressorMachine.class, GTOEnergyConverterMachine.MANAGED_FIELD_HOLDER);

    public PneumaticCompressorMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, GTOEnergyType.KINETIC, 64L << (2 * tier), (int) GTValues.V[tier]);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}