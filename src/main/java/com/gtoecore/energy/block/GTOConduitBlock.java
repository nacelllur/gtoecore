package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;

import com.gtoecore.energy.GTOEnergyBridge;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * GT6 风味能量导体方块 —— 通用基类，按 {@link GTOEnergyType} 区分能量形式。
 *
 * <p><b>连接体系（对齐 GTM PipeBlock 的设计，用原版 multipart blockstate 实现）：</b></p>
 * <ul>
 *   <li>六方向各一个 boolean 连接属性（= GTM 的 connections 位掩码）；</li>
 *   <li>摆放/邻居变化时自动重算（= GTM PipeBlock.onNeighborChange / canConnect）；</li>
 *   <li>外形 = 中心体 ∪ 已连接方向的管段（= GTM PipeBlock.getShapes(connections)）；</li>
 *   <li>渲染由 blockstate multipart 按连接位拼装子模型（= GTM 的 PipeModel 动态烘焙）。</li>
 * </ul>
 *
 * <p><b>能量形式（每种一个方块实例，共享同一套传播逻辑）：</b></p>
 * <ul>
 *   <li>旋转轴（RU）—— 引擎转速沿管链逐节损耗传递</li>
 *   <li>导热管（HU）—— 热源温度沿管链逐节耗散</li>
 *   <li>冷凝管（CU）—— 冷源温度沿管链逐节回升</li>
 *   <li>动能杆（KU）—— 活塞能量沿杆链逐节衰减</li>
 * </ul>
 *
 * <p>传导语义（MVP）：每 tick 沿<b>全部已连接方向</b>探测——邻居是产能机器则拉当前值；
 * 邻居是同类型导体则取“其值 × 每节损耗”；邻居是接收端则主动推送。
 * （连接全六向 ⇒ 天然支持 T 形/十字分支，与 GT6 管道一致。）</p>
 */
public class GTOConduitBlock extends BaseEntityBlock {

    /** 六方向连接状态（= GTM PipeBlockEntity 的 connections 位掩码的 blockstate 形态） */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final BooleanProperty[] CONNECTION_PROPS = {
            NORTH, SOUTH, WEST, EAST, UP, DOWN
    };
    private static final Direction[] CONNECTION_DIRS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
    };

    /** 每节损耗系数（按能量形式区分；MVP 用统一基准值） */
    private static final double LOSS_ROTATION = 0.98D;
    private static final double LOSS_HEAT = 0.97D;
    private static final double LOSS_COLD = 0.97D;
    private static final double LOSS_KINETIC = 0.98D;

    /** 外形：中心体（4x4x4）+ 每个连接方向一根管段（4x4x6），与 JSON 模型一致 */
    private static final VoxelShape CENTER_SHAPE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);
    private static final VoxelShape[] STUB_SHAPES = {
            Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 6.0D),   // north
            Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 16.0D), // south
            Block.box(0.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D),   // west
            Block.box(10.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D), // east
            Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D), // up
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 6.0D, 10.0D)    // down
    };

    /** 形状缓存：64 种连接组合 × 4 种方块实例，按 state 缓存合并结果（GTM 同款思路） */
    private static final ConcurrentHashMap<BlockState, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

    private final GTOEnergyType fixedType;

    public GTOConduitBlock(Properties props, GTOEnergyType type) {
        super(props);
        this.fixedType = type;
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(EAST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    public GTOEnergyType getEnergyType() {
        return fixedType;
    }

    public static double lossFor(GTOEnergyType type) {
        return switch (type) {
            case ROTATION -> LOSS_ROTATION;
            case HEAT -> LOSS_HEAT;
            case COLD -> LOSS_COLD;
            case KINETIC -> LOSS_KINETIC;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, SOUTH, WEST, EAST, UP, DOWN);
    }

    /**
     * 连接判定（对齐 GTM PipeBlock.canConnect，支持 GTM 机器经桥接）：
     * 邻居是同类型导体 / 该类型发射端 / 接受该类型的接收端 才连。
     */
    public boolean canConnect(BlockGetter level, BlockPos pos, Direction dir, GTOEnergyType type) {
        BlockEntity be = level.getBlockEntity(pos.relative(dir));
        if (be instanceof GTOConduitBlockEntity
                && be.getBlockState().getBlock() instanceof GTOConduitBlock conduit) {
            return conduit.getEnergyType() == type;
        }
        if (GTOEnergyBridge.isEmitter(be, type)) {
            return true;
        }
        return GTOEnergyBridge.accepts(be, type, dir.getOpposite());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockGetter level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = defaultBlockState();
        for (int i = 0; i < CONNECTION_DIRS.length; i++) {
            state = state.setValue(CONNECTION_PROPS[i], canConnect(level, pos, CONNECTION_DIRS[i], fixedType));
        }
        return state;
    }

    /** 邻居方块变化时重算该方向连接（= 原版栅栏/墙的同款钩子，摆机器/拆机器自动连断） */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(propFor(dir), canConnect(level, pos, dir, fixedType));
    }

    public static BooleanProperty propFor(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    /** 遍历某状态的已连接方向。 */
    public static void forEachConnection(BlockState state, ConnectionConsumer consumer) {
        for (int i = 0; i < CONNECTION_DIRS.length; i++) {
            if (state.getValue(CONNECTION_PROPS[i])) {
                consumer.accept(CONNECTION_DIRS[i]);
            }
        }
    }

    @FunctionalInterface
    public interface ConnectionConsumer {
        void accept(Direction dir);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE_CACHE.computeIfAbsent(state, s -> {
            VoxelShape shape = CENTER_SHAPE;
            for (int i = 0; i < CONNECTION_DIRS.length; i++) {
                if (s.getValue(CONNECTION_PROPS[i])) {
                    shape = Shapes.or(shape, STUB_SHAPES[i]);
                }
            }
            return shape;
        });
    }

    /** BaseEntityBlock 默认渲染为 INVISIBLE，必须覆盖为 MODEL。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GTNBlocks.GTO_CONDUIT_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, GTNBlocks.GTO_CONDUIT_BE.get(),
                        GTOConduitBlockEntity::serverTick);
    }

    /** 发射端可选的显式类型声明（避免用 pull 探测连接造成停机误断连） */
    public interface GTOEnergyEmitterHost {
        boolean emits(GTOEnergyType type);
    }
}
