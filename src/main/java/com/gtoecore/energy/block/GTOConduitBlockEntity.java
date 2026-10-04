package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import com.gtoecore.energy.GTOEnergyBridge;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyAcceptor;
import com.gtoecore.energy.IGTOEnergyEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 能量导体 BlockEntity —— 全部导体方块（轴/热管/冷管/动能杆）共用。
 *
 * <p>每 tick 沿全部已连接方向探测并传播当前值（含 GTM 机器桥接）：</p>
 * <ul>
 *   <li>邻居为产能机器（{@link IGTOEnergyEmitter}，含 GTM MetaMachine）→ 拉当前值（无损耗）；</li>
 *   <li>邻居为同类型导体 → 取“其值 × 每节损耗”（链式自然递减）；</li>
 *   <li>邻居为接收端（{@link IGTOEnergyAcceptor}，含 GTM 仓）→ 主动推送当前值。</li>
 * </ul>
 */
public class GTOConduitBlockEntity extends BlockEntity {

    /** 当前能量值（RU = 标称转速 rpm；HU/CU = 当前温度 K；KU = 压强 kPa） */
    private long value;
    /** 源头档位（透传给接收端；MVP 恒 LV=1） */
    private int tier = 1;
    private long lastSynced = -1L;

    public GTOConduitBlockEntity(BlockPos pos, BlockState state) {
        super(GTNBlocks.GTO_CONDUIT_BE.get(), pos, state);
    }

    public long getValue() {
        return value;
    }

    public int getTier() {
        return tier;
    }

    /** Block 侧 ticker 调用（仅服务端）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, GTOConduitBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof GTOConduitBlock conduit)) return;
        GTOEnergyType type = conduit.getEnergyType();
        double loss = GTOConduitBlock.lossFor(type);

        long best = 0L;
        int bestTier = 1;

        // 沿全部已连接方向探测（连接位由 blockstate 维护，天然支持分支）
        final long[] bestLocal = {0L};
        final int[] bestTierLocal = {1};
        GTOConduitBlock.forEachConnection(state, d -> {
            BlockPos np = pos.relative(d);
            BlockEntity nbe = level.getBlockEntity(np);
            if (GTOEnergyBridge.isEmitter(nbe, type)) {
                // 直接邻居是产能机器（含 GTM 桥接）：拉当前值（无损耗）
                long s = pullFrom(nbe, type, d.getOpposite(), tier);
                bestLocal[0] = Math.max(bestLocal[0], s);
            } else if (nbe instanceof GTOConduitBlockEntity other
                    && other.getBlockState().getBlock() instanceof GTOConduitBlock oc
                    && oc.getEnergyType() == type) {
                // 同类型导体：取其值 × 每节损耗（链式自然递减）
                long s = (long) (other.value * loss);
                if (s > bestLocal[0]) {
                    bestLocal[0] = s;
                    bestTierLocal[0] = other.tier;
                }
            } else if (GTOEnergyBridge.accepts(nbe, type, d.getOpposite())) {
                // 接收端（含 GTM 桥接）：无条件推送当前值（含 0）
                GTOEnergyBridge.inject(nbe, type, d.getOpposite(), tier, value, false);
            }
        });

        long newBest = Math.max(best, bestLocal[0]);
        int newBestTier = Math.max(bestTier, bestTierLocal[0]);
        value = newBest;
        tier = Math.max(newBestTier, 1);
        if (value != lastSynced) {
            lastSynced = value;
            setChanged();
        }
    }

    /** 从产能机器（含 GTM 桥接）拉取当前值。 */
    private static long pullFrom(BlockEntity be, GTOEnergyType type, Direction side, int tier) {
        if (be instanceof IGTOEnergyEmitter e) {
            return e.pullGTOEnergy(type, side, tier);
        }
        if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity mbe
                && mbe.getMetaMachine() instanceof IGTOEnergyEmitter e) {
            return e.pullGTOEnergy(type, side, tier);
        }
        return 0L;
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        value = tag.getLong("GtoValue");
        tier = tag.contains("GtoTier") ? tag.getInt("GtoTier") : 1;
        lastSynced = value;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("GtoValue", value);
        tag.putInt("GtoTier", tier);
    }
}
