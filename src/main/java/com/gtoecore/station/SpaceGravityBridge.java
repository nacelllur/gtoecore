package com.gtoecore.station;

import com.spacegravity.spacegravity.api.SpaceGravityApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * space_gravity mod 的软依赖桥接。
 *
 * <p>首次使用时探测 API 类是否存在；任何调用失败（mod 缺失/版本不兼容）
 * 都会把桥接标记为不可用并静默降级（舱内外不再自动切换失重），绝不让游戏崩溃。</p>
 */
final class SpaceGravityBridge {

    private static Boolean available;

    static boolean isAvailable() {
        if (available == null) {
            boolean ok;
            try {
                Class.forName("com.spacegravity.spacegravity.api.SpaceGravityApi");
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
            available = ok;
        }
        return available;
    }

    static boolean isZeroGravity(Player player) {
        try {
            return SpaceGravityApi.isZeroGravityEnabled(player);
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    static void setZeroGravity(ServerPlayer player, boolean enabled) {
        try {
            SpaceGravityApi.setZeroGravityEnabled(player, enabled);
        } catch (Throwable t) {
            available = false;
        }
    }

    private SpaceGravityBridge() {}
}
