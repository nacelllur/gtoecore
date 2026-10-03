package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 克隆体维护室 —— 不耗电的 workable 多方块（无额外逻辑）。
 *
 * <p>运行逻辑由 bio 配方（1000mB 水 + 1 生物团 → 1 克隆体，6000t）驱动，
 * 无 EU 配方 → 不耗电；pattern 开物品/流体输入 + 物品输出仓。</p>
 *
 * <p>继承 {@link GTNWorkableMultiblockMachine}（带 GUI 的非电基类）：
 * 直接继承 {@code WorkableMultiblockMachine} 没有实现 {@code IUIMachine}，
 * 右键打不开 GUI。</p>
 */
public class CloneMaintenanceRoomMachine extends GTNWorkableMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(CloneMaintenanceRoomMachine.class,
                    GTNWorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    public CloneMaintenanceRoomMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
