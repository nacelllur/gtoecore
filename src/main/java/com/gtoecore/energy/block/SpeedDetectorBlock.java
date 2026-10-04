package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 转速检测器 —— GT6 风味能量接收端示范。
 *
 * <p>从相邻旋转轴链接收 RU 转速，转换为红石信号：
 * {@code signal = clamp(rpm / 32, 0, 15)}（128 rpm 满档 → 信号 4；后续配方条件
 * 会直接读取 BE 的实际转速而非红石）。</p>
 */
public class SpeedDetectorBlock extends BaseEntityBlock {

    /** 红石信号强度 0~15 */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public SpeedDetectorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWER);
    }

    /** BaseEntityBlock 默认渲染为 INVISIBLE，必须覆盖为 MODEL。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GTNBlocks.SPEED_DETECTOR_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, GTNBlocks.SPEED_DETECTOR_BE.get(),
                        SpeedDetectorBlockEntity::serverTick);
    }

    /** 六面都输出红石（简化；GT6 是面朝向输出） */
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWER);
    }
}
