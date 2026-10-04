package com.gtoecore.energy;

import net.minecraft.core.Direction;

/**
 * GT6 风味能量发射端。产能机器实现本接口，由轴/接收端逐 tick 拉取。
 */
public interface IGTOEnergyEmitter {

    /**
     * 拉取当前可提供的能量值。
     *
     * @param type     能量类型
     * @param side     拉取方向（从接收方看的面）
     * @param tier     请求档位
     * @return 当前该类型能量值（RU = 标称转速 rpm；HU = 当前温度 K）；不产则为 0
     */
    long pullGTOEnergy(GTOEnergyType type, Direction side, int tier);
}
