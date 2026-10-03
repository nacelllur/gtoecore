package com.gtoecore.station;

import com.gtoecore.GTNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 舱室重力核心（空间站舱室主方块）。
 *
 * <p>右键：启动/重新洪泛扫描所在舱室的气密空间；扫描成功（舱室密封）后，
 * 该舱室内部提供正常重力，舱室外（同维度）由 {@link StationGravityManager}
 * 自动切换为失重（space_gravity mod 的零重力模式，含自由视角）。</p>
 */
public class StationGravityCoreBlock extends BaseEntityBlock {

    public StationGravityCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationGravityCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, GTNBlocks.STATION_GRAVITY_CORE_BE.get(),
                StationGravityCoreBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof StationGravityCoreBlockEntity be) {
            be.startScan(sp);
        }
        return InteractionResult.CONSUME;
    }
}
