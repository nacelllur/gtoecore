package com.gtoecore.energy;

/**
 * GT6 风味能量主机 —— 携带能量接收值的多方块主机实现本接口，
 * 供 {@link com.gtoecore.energy.recipe.GTOEnergyRangeCondition} 在配方匹配时查询。
 */
public interface IGTOEnergyHost {

    /**
     * 查询本主机当前某能量形式的实时值。
     *
     * @param type 能量类型
     * @return 当前值（RU = 运行转速 rpm；HU = 当前温度 K；无接收仓/未运转 = 0）
     */
    long getGTOEnergyValue(GTOEnergyType type);
}
