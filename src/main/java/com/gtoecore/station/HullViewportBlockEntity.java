package com.gtoecore.station;

import com.gtoecore.GTNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 全周天舱壁方块实体 —— 只负责<b>客户端</b>的透明度动画进度。
 *
 * <p>服务端不持有任何状态：红石意图与动画目标都在方块状态里
 * （{@link HullViewportBlock#SIGNAL} / {@link HullViewportBlock#OPEN}），
 * 所以不需要 NBT、不需要网络包 —— 波纹完全由服务端的「错峰翻转 OPEN」驱动，
 * 客户端只对收到的 {@code OPEN} 做插值，所有玩家看到的波纹天然一致。</p>
 *
 * <p>{@link #progress}：0 = 完全不透明（闭合），1 = 完全透明（打开）。
 * 首次加载（区块刚进来 / 进度未初始化）直接取终态，不播动画，避免远处结构
 * 一堆方块同时闪一下。</p>
 */
public class HullViewportBlockEntity extends BlockEntity {

    /** 上一 tick 的进度（供 partialTick 插值） */
    private float previousProgress = 0.0F;
    /** 当前进度 */
    private float progress = -1.0F;

    public HullViewportBlockEntity(BlockPos pos, BlockState state) {
        super(GTNBlocks.HULL_VIEWPORT_BE.get(), pos, state);
    }

    /** 客户端每 tick 推进进度，朝方块状态里的目标靠拢。 */
    public void clientTick() {
        Level level = this.level;
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(this.worldPosition);
        boolean wantOpen = state.getBlock() instanceof HullViewportBlock
                && state.getValue(HullViewportBlock.OPEN);
        float target = wantOpen ? 1.0F : 0.0F;

        if (this.progress < 0.0F) {
            // 首次见到这个方块（区块刚加载）：直接到位，不补动画
            this.progress = target;
            this.previousProgress = target;
            return;
        }

        this.previousProgress = this.progress;
        float step = 1.0F / HullViewportBlock.FADE_TICKS;
        if (this.progress < target) {
            this.progress = Math.min(target, this.progress + step);
        } else if (this.progress > target) {
            this.progress = Math.max(target, this.progress - step);
        }
    }

    /** 插值后的进度，供渲染器使用。 */
    public float progress(float partialTick) {
        if (this.progress < 0.0F) {
            return 0.0F;
        }
        return Mth.lerp(partialTick, this.previousProgress, this.progress);
    }
}
