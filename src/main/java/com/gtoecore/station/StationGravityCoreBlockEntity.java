package com.gtoecore.station;

import com.gtoecore.GTNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 舱室重力核心方块实体：洪泛扫描气密舱室 + 持有重力区域。
 *
 * <p>扫描语义：从核心 6 邻接的空气格出发 BFS（只穿过空气），
 * 任何非空气方块（含玻璃、开着的门）都视为密封。触及距离上限或容积上限
 * 判定为「漏气/过大」，扫描失败。</p>
 *
 * <p>生效（active）后每 20 tick 向 {@link StationGravityManager} 报到一次；
 * 区块卸载即自动失效（报到超时剔除）。区域持久化在方块实体 NBT。</p>
 */
public class StationGravityCoreBlockEntity extends BlockEntity {

    /** 舱室容积上限（空气格数） */
    private static final int MAX_CELLS = 400_000;
    /** 距核心的最大切比雪夫距离，超出判漏气 */
    private static final int MAX_RANGE = 192;
    /** 每 tick 处理的 BFS 格数（分片防卡顿） */
    private static final int CELLS_PER_TICK = 8192;

    private boolean active;
    private Set<Long> region = Set.of();

    // ---- 扫描状态机（null = 空闲） ----
    private @Nullable ArrayDeque<Long> queue;
    private @Nullable Set<Long> scanned;
    private @Nullable UUID scanInitiator;

    private int tickCounter;

    public StationGravityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(GTNBlocks.STATION_GRAVITY_CORE_BE.get(), pos, state);
    }

    // ---------------- 对外 ----------------

    public boolean isActive() {
        return active;
    }

    public boolean contains(BlockPos pos) {
        return active && region.contains(pos.asLong());
    }

    /** 右键：开始（重新）扫描 */
    public void startScan(ServerPlayer player) {
        if (level == null || level.isClientSide) return;
        active = false;
        region = Set.of();
        queue = new ArrayDeque<>();
        scanned = new HashSet<>();
        scanInitiator = player.getUUID();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockState(p).isAir()) {
                long l = p.asLong();
                queue.add(l);
                scanned.add(l);
            }
        }
        if (queue.isEmpty()) {
            queue = null;
            scanned = null;
            player.displayClientMessage(Component.translatable("gtoecore.gravity_core.no_air"), false);
        } else {
            player.displayClientMessage(Component.translatable("gtoecore.gravity_core.scan_start"), false);
        }
    }

    // ---------------- tick ----------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  StationGravityCoreBlockEntity be) {
        be.tick();
    }

    private void tick() {
        if (level == null || level.isClientSide) return;
        tickCounter++;
        if (active && tickCounter % 20 == 0) {
            StationGravityManager.touch(this);
        }
        if (queue == null || scanned == null) return;

        ArrayDeque<Long> q = queue;
        Set<Long> seen = scanned;
        int budget = CELLS_PER_TICK;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        while (budget-- > 0 && !q.isEmpty()) {
            long cur = q.poll();
            int cx = BlockPos.getX(cur), cy = BlockPos.getY(cur), cz = BlockPos.getZ(cur);
            if (seen.size() > MAX_CELLS
                    || Math.max(Math.max(Math.abs(cx - worldPosition.getX()),
                                         Math.abs(cy - worldPosition.getY())),
                                Math.abs(cz - worldPosition.getZ())) > MAX_RANGE) {
                fail("leak");
                return;
            }
            for (Direction d : Direction.values()) {
                mp.set(cx + d.getStepX(), cy + d.getStepY(), cz + d.getStepZ());
                long l = mp.asLong();
                if (seen.contains(l)) continue;
                if (!level.getBlockState(mp).isAir()) continue;
                seen.add(l);
                q.add(l);
            }
        }
        if (q.isEmpty()) {
            region = seen;
            active = true;
            queue = null;
            scanned = null;
            setChanged();
            StationGravityManager.touch(this);
            notifyInitiator(Component.translatable("gtoecore.gravity_core.active", region.size()));
        }
    }

    private void fail(String key) {
        queue = null;
        scanned = null;
        active = false;
        region = Set.of();
        setChanged();
        notifyInitiator(Component.translatable("gtoecore.gravity_core." + key, MAX_CELLS));
    }

    private void notifyInitiator(Component msg) {
        if (level == null || level.getServer() == null || scanInitiator == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(scanInitiator);
        if (p != null) p.displayClientMessage(msg, false);
    }

    // ---------------- NBT ----------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Active", active);
        if (active) {
            long[] arr = new long[region.size()];
            int i = 0;
            for (long l : region) arr[i++] = l;
            tag.putLongArray("Region", arr);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        active = tag.getBoolean("Active");
        if (active) {
            Set<Long> set = new HashSet<>();
            for (long l : tag.getLongArray("Region")) set.add(l);
            region = set;
        } else {
            region = Set.of();
        }
    }
}
