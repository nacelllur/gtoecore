package com.gtoecore.objdemo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * OBJ 示范涡轮机 —— 方块本体。
 *
 * <p>纯客户端外形由 {@link TurbineDemoRenderer}（BER + Forge ObjLoader）渲染，
 * 方块本身不参与任何成形逻辑：放下去就有模型。</p>
 */
public class TurbineDemoBlock extends Block implements EntityBlock {

    public TurbineDemoBlock() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 8.0f)
                .sound(SoundType.METAL)
                // 本体是隐形占位（模型 JSON 为空），不遮挡光线
                .noOcclusion()
                .requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineDemoBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurbineDemoBlockEntity be) {
            // 右键切换旋转方向 / 加速，方便验证动画
            be.toggleMode();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
