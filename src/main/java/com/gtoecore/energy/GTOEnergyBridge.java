package com.gtoecore.energy;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtoecore.energy.block.GTOConduitBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * GTO 能量跨体系桥接工具。
 *
 * <p>GTM 机器的 BlockEntity 是 GTM 自己的 {@code MachineBlockEntity}（我们无法给它加接口），
 * 因此所有"导体 ↔ GTM 机器"的交互都必须经 {@code IMachineBlockEntity.getMetaMachine()} 桥接。</p>
 */
public final class GTOEnergyBridge {

    /** 判断方块实体对应的"能量宿主"是否发射该类型（含 GTM MetaMachine 桥接）。 */
    public static boolean isEmitter(BlockEntity be, GTOEnergyType type) {
        if (be instanceof IGTOEnergyEmitter e) {
            return !(e instanceof GTOConduitBlock.GTOEnergyEmitterHost host) || host.emits(type);
        }
        if (be instanceof IMachineBlockEntity mbe
                && mbe.getMetaMachine() instanceof IGTOEnergyEmitter e) {
            return !(e instanceof GTOConduitBlock.GTOEnergyEmitterHost host) || host.emits(type);
        }
        return false;
    }

    /** 判断方块实体对应的"能量宿主"是否接受该类型（含 GTM MetaMachine 桥接）。 */
    public static boolean accepts(BlockEntity be, GTOEnergyType type, Direction side) {
        if (be instanceof IGTOEnergyAcceptor acc) {
            return acc.acceptsGTOEnergy(type, side);
        }
        if (be instanceof IMachineBlockEntity mbe
                && mbe.getMetaMachine() instanceof IGTOEnergyAcceptor acc) {
            return acc.acceptsGTOEnergy(type, side);
        }
        return false;
    }

    /** 向方块实体对应的"能量宿主"注入能量（含 GTM MetaMachine 桥接）。 */
    public static long inject(BlockEntity be, GTOEnergyType type, Direction side, int tier,
                              long amount, boolean simulate) {
        if (be instanceof IGTOEnergyAcceptor acc) {
            return acc.injectGTOEnergy(type, side, tier, amount, simulate);
        }
        if (be instanceof IMachineBlockEntity mbe
                && mbe.getMetaMachine() instanceof IGTOEnergyAcceptor acc) {
            return acc.injectGTOEnergy(type, side, tier, amount, simulate);
        }
        return 0L;
    }

    /**
     * 从多方块主机（或单方块机器）查询当前 GTO 能量值 —— 供配方条件使用。
     *
     * <p>多方块：扫描 {@link IMultiController#getParts()} 里的仓（实现 {@link IGTOEnergyHost}），
     * 取最大有效值。普通 BE 宿主直接 instanceof。</p>
     */
    public static long queryValue(Object host, GTOEnergyType type) {
        if (host instanceof IGTOEnergyHost h) {
            return h.getGTOEnergyValue(type);
        }
        if (host instanceof IMultiController controller) {
            List<IMultiPart> parts = controller.getParts();
            if (parts != null) {
                long best = 0L;
                for (IMultiPart part : parts) {
                    if (part instanceof IGTOEnergyHost h) {
                        best = Math.max(best, h.getGTOEnergyValue(type));
                    }
                }
                return best;
            }
        }
        return 0L;
    }

    private GTOEnergyBridge() {}
}
