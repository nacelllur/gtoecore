package com.gtoecore.objdemo;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * OBJ 示范涡轮机方块实体。
 *
 * <p>仅维护服务端同步的「模式」（0=停, 1=慢转, 2=快转, 3=反向快转）。
 * 旋转角纯客户端自增，不需要同步浮点数。</p>
 */
public class TurbineDemoBlockEntity extends BlockEntity {

    /** 0=停, 1=慢转, 2=快转, 3=反向快转 */
    private int mode = 1;

    public TurbineDemoBlockEntity(BlockPos pos, BlockState state) {
        super(GTOEObjDemo.TURBINE_DEMO_BE.get(), pos, state);
    }

    public int getMode() {
        return mode;
    }

    public void toggleMode() {
        mode = (mode + 1) % 4;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mode", mode);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = tag.getInt("Mode");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Mode", mode);
        return tag;
    }
}
