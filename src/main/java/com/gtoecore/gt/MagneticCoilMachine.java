package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtoecore.energy.GTOEnergyType;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 电磁线圈机（MU 产能机）—— GT 电 → 磁场能（磁感应强度 T）。
 *
 * <p><b>15 压（ULV~MAX，2026-10-05）：</b>满功率输出随电压等级 ×4/级增长
 * （ULV 12 T → LV 48 T → MV 192 T → …）；耗电 = 本机电压 1A。
 * 支持超损超频：超 1 档功耗 ×4 / 磁场 ×2，无上限。</p>
 *
 * <p>磁场随抽电比例线性，断电归零。</p>
 */
public class MagneticCoilMachine extends GTOEnergyConverterMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MagneticCoilMachine.class, GTOEnergyConverterMachine.MANAGED_FIELD_HOLDER);

    public MagneticCoilMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, GTOEnergyType.MAGNETIC, 12L << (2 * tier), (int) GTValues.V[tier]);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}