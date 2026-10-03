package com.gtoecore.station;

import com.gtoecore.GTNCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 舱室重力管理器：舱内正常重力，舱外自动失重。
 *
 * <p>生效中的重力核心每 20 tick 报到一次（{@link #touch}），报到超时（100 tick，
 * 即区块卸载/核心被破坏）自动剔除。管理器每 10 tick 检查一次每个玩家：
 * 维度内存在生效核心时——位于任一核心舱室区域内 → 关闭零重力；否则 → 开启零重力。
 * 维度内没有核心时完全不动玩家状态（不影响普通世界与其它玩法）。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StationGravityManager {

    /** 核心报到有效期（tick）；核心每 20 tick 报到一次 */
    private static final int TOUCH_TTL = 100;

    private static final Map<ResourceKey<Level>, Map<StationGravityCoreBlockEntity, Integer>> CORES =
            new HashMap<>();
    private static int tickCounter;

    /** 由核心方块实体调用（仅服务端、active 状态） */
    static void touch(StationGravityCoreBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        CORES.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(be, tickCounter);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 10 != 0) return;

        // 剔除超时核心与空维度
        for (Iterator<Map.Entry<ResourceKey<Level>, Map<StationGravityCoreBlockEntity, Integer>>> it =
             CORES.entrySet().iterator(); it.hasNext(); ) {
            Map<StationGravityCoreBlockEntity, Integer> dim = it.next().getValue();
            dim.values().removeIf(t -> tickCounter - t > TOUCH_TTL);
            if (dim.isEmpty()) it.remove();
        }
        if (CORES.isEmpty() || !SpaceGravityBridge.isAvailable()) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Map<StationGravityCoreBlockEntity, Integer> dim = CORES.get(player.level().dimension());
            if (dim == null) continue;
            BlockPos pp = player.blockPosition();
            boolean inside = false;
            for (StationGravityCoreBlockEntity be : dim.keySet()) {
                if (be.contains(pp)) {
                    inside = true;
                    break;
                }
            }
            boolean zeroG = !inside;
            if (SpaceGravityBridge.isZeroGravity(player) != zeroG) {
                SpaceGravityBridge.setZeroGravity(player, zeroG);
            }
        }
    }

    private StationGravityManager() {}
}
