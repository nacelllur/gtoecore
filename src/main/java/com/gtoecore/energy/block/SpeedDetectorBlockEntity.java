package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 转速检测器 BE：实现 {@link IGTOEnergyAcceptor}，缓存轴链推来的当前转速。
 */
public class SpeedDetectorBlockEntity extends BlockEntity implements IGTOEnergyAcceptor {

    /** 当前转速（rpm） */
    private long rpm;
    /** 最近一次收到注入的 gameTime —— 超过 20 tick 未刷新按断供归零 */
    private long lastInjectTime = -100L;

    public SpeedDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(GTNBlocks.SPEED_DETECTOR_BE.get(), pos, state);
    }

    public long getRpm() {
        return rpm;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeedDetectorBlockEntity be) {
        // 修竞态 bug：不再依赖「每 tick 清零再等推送」的时序赌运气，
        // 改用 lastInjectTime 断供检测——与旋转石磨同款语义。
        long effRpm = (level.getGameTime() - be.lastInjectTime <= 20L) ? be.rpm : 0L;
        int signal = (int) Math.min(15L, effRpm / 32L);
        if (state.getValue(SpeedDetectorBlock.POWER) != signal) {
            level.setBlock(pos, state.setValue(SpeedDetectorBlock.POWER, signal), 3);
        }
    }

    // ---------------- IGTOEnergyAcceptor ----------------

    @Override
    public boolean acceptsGTOEnergy(GTOEnergyType type, Direction side) {
        return type == GTOEnergyType.ROTATION;
    }

    @Override
    public long injectGTOEnergy(GTOEnergyType type, Direction side, int tier, long amount, boolean simulate) {
        if (type != GTOEnergyType.ROTATION) return 0L;
        if (!simulate) {
            rpm = amount;
            if (level != null) lastInjectTime = level.getGameTime();
            setChanged();
        }
        return amount;
    }

    // ---------------- NBT ----------------

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        rpm = tag.getLong("Rpm");
        lastInjectTime = tag.contains("LastInject") ? tag.getLong("LastInject") : -100L;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Rpm", rpm);
        tag.putLong("LastInject", lastInjectTime);
    }
}
