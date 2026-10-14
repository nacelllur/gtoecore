package com.gtoecore.station;

import com.gtoecore.GTNCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 骑乘期间强制关闭「自由视角」（space_gravity 零重力模式），下马后恢复原状态。
 *
 * <p><b>为什么需要这个</b>：零重力模式会清空原版移动输入、强制游泳姿势并把镜头
 * 交给推力系统 —— 骑乘（马/船/矿车/GTNA 载具）时这套输入会和载具控制打架，
 * 人物模型也会以平躺姿态插在坐骑里。所以骑乘一律先退回原版重力，
 * 下马再把玩家原本的开关还回去。</p>
 *
 * <p><b>与 {@link StationGravityManager} 的协作</b>：管理器算舱外零重力时已经把
 * {@code isPassenger()} 排除在外（见那边的 {@code zeroG = !inside && !riding}），
 * 所以两者不会互相覆盖 —— 骑乘期间管理器最多也只能算出 false，
 * 本守卫负责把它按住并记录「骑乘前的原状态」，下马时恢复。</p>
 *
 * <p><b>无核心维度也生效</b>：管理器在维度内没有任何生效核心时完全不动玩家状态，
 * 那种场景（例如普通世界里手动 {@code /spaceGravity 0}）的强制关闭与恢复由本类
 * 独立完成，不依赖核心是否存在。</p>
 *
 * <p>纯服务端逻辑；space_gravity 缺失时（{@link SpaceGravityBridge} 探测失败）
 * 整段跳过，不会有任何副作用。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RideZeroGravityGuard {

    /** 玩家 UUID → 骑乘开始前的零重力状态（进骑乘时记录，下马时恢复并移除）。 */
    private static final Map<UUID, Boolean> SUSPENDED_STATE = new HashMap<>();

    /** 玩家 UUID → 上一 tick 是否在骑乘（用于检测下马边沿）。 */
    private static final Map<UUID, Boolean> WAS_RIDING = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!SpaceGravityBridge.isAvailable()) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean riding = player.isPassenger();
            boolean wasRiding = WAS_RIDING.getOrDefault(id, false);

            if (riding) {
                if (!wasRiding) {
                    // 上马瞬间：记录骑乘前的原状态（仅此瞬间记录，中途操作不污染它）
                    SUSPENDED_STATE.putIfAbsent(id, SpaceGravityBridge.isZeroGravity(player));
                }
                // 骑乘全程按住关闭：即使被 /spaceGravity 0 之类外部手段重新打开也压回去
                if (SpaceGravityBridge.isZeroGravity(player)) {
                    SpaceGravityBridge.setZeroGravity(player, false);
                }
            } else if (wasRiding) {
                // 下马边沿：恢复骑乘前的原状态
                Boolean prev = SUSPENDED_STATE.remove(id);
                if (prev != null && prev && !SpaceGravityBridge.isZeroGravity(player)) {
                    SpaceGravityBridge.setZeroGravity(player, true);
                }
            }

            if (riding) {
                WAS_RIDING.put(id, true);
            } else {
                WAS_RIDING.remove(id);
            }
        }
    }

    /** 玩家退出：清掉两个映射，避免长连接服务器内存泄漏与跨登录状态残留。 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WAS_RIDING.remove(player.getUUID());
            SUSPENDED_STATE.remove(player.getUUID());
        }
    }

    private RideZeroGravityGuard() {}
}
