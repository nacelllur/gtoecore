package com.gtoecore.energy;

import net.minecraft.core.Direction;

/**
 * GT6 风味能量接收端（区别于 GTM 的 EU 体系）。
 *
 * <p>实现方：旋转轴链末端机器、温度舱等。产能机器（蒸汽引擎/燃烧室）
 * 每 tick 向相邻接收端调用 {@link #injectGTOEnergy}。</p>
 *
 * <p>语义借鉴 GT6 的 {@code ITileEntityEnergy.doEnergyInjection}——
 * 注入方传"本次想要注入的量与档位"，接收方返回实际吸收量。</p>
 */
public interface IGTOEnergyAcceptor {

    /**
     * 注入能量。
     *
     * @param type     能量类型
     * @param side     注入方向（从接收方看的面）
     * @param tier     电压/规格档（1=LV … 与 GTM 电压档对齐；决定转速标称/温度上限）
     * @param amount   本次注入的能量值（rpm 语义 = 标称转速；K 语义 = 传递温度）
     * @param simulate true = 只查询能吸收多少，不实际写入
     * @return 实际吸收的量
     */
    long injectGTOEnergy(GTOEnergyType type, Direction side, int tier, long amount, boolean simulate);

    /** 是否接受该类型能量（默认全收，可覆写过滤） */
    default boolean acceptsGTOEnergy(GTOEnergyType type, Direction side) {
        return true;
    }
}
