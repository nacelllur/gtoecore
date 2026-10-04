package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 燃烧引擎（GT6 风味 RU 产能机）—— 烧任意熔炉燃料，向相邻旋转轴输出标称转速。
 *
 * <p>交互（MVP）：手持燃料物品右键填充一单位燃料；燃料余量决定剩余运转时间，
 * 燃料品质决定转速档位（见 BE 标称表）。LIT 状态用于渲染燃烧动画。</p>
 */
public class CombustionEngineBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CombustionEngineBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // HORIZONTAL_FACING 只允许 4 个水平朝向 —— 用 getHorizontalDirection()，
        // 绝不能 getNearestLookingDirection()（玩家抬头/低头会返回 UP/DOWN → setValue 抛异常崩游戏）
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** BaseEntityBlock 默认渲染为 INVISIBLE，必须覆盖为 MODEL。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GTNBlocks.COMBUSTION_ENGINE_BE.get().create(pos, state);
    }

    /** 右击打开 GUI（燃料槽 + 燃烧余量 + 转速读数）。 */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CombustionEngineBlockEntity be) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new CombustionEngineMenu(id, inv, be),
                    net.minecraft.network.chat.Component.translatable(
                            "block.gtoecore.combustion_engine")));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, GTNBlocks.COMBUSTION_ENGINE_BE.get(),
                        CombustionEngineBlockEntity::serverTick);
    }
}
