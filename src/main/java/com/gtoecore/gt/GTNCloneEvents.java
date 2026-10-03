package com.gtoecore.gt;

import com.gtoecore.GTNCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 克隆体生产车间的硬核死亡复活监听。
 *
 * <p>{@link LivingDeathEvent}：硬核（极限）模式下玩家死亡 → 遍历所有已形成的
 * 生产车间，找到输入总线内有<b>可用于该玩家</b>的克隆体（绑定该玩家或未绑定）
 * 的车间 → 消耗 1 个克隆体、取消死亡、在床上满血复活并重设出生点。</p>
 *
 * <p><b>绑定不在此处处理：</b>绑定走 {@code GTNCloneItem.use()}（原版右键物品，
 * 服务端必执行）。<b>不要</b>用 {@code PlayerInteractEvent.RightClickEmpty} ——
 * 它只在客户端触发，服务端永远收不到（旧绑定逻辑因此完全没生效，已删除）。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GTNCloneEvents {

    private GTNCloneEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 仅硬核（极限）世界生效
        if (!player.level().getLevelData().isHardcore()) return;

        boolean anyFormed = false;
        for (CloneProductionWorkshopMachine machine : CloneProductionWorkshopMachine.formedMachines()) {
            if (!machine.isFormed()) continue;
            anyFormed = true;
            // 消耗 1 个可用于该玩家的克隆体（绑定该玩家 / 未绑定通用）
            if (!machine.consumeCloneFor(player.getUUID())) continue;
            // 有克隆体：取消死亡 + 床上复活
            event.setCanceled(true);
            machine.resurrectPlayer(player);
            // 事件取消后原版 Player.die 里的 awardStat(DEATHS) 被跳过，手动补计，
            // 保证「第 N 次」跨会话连续（第一次克隆复活 = 第 1 次）
            player.awardStat(Stats.DEATHS);
            int deathCount = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
            player.displayClientMessage(
                    Component.translatable("gtoecore.clone.welcome",
                            deathCount, player.getGameProfile().getName()), false);
            return;
        }
        // 有车间但没有可用克隆体 → 提示（不拦截死亡）
        if (anyFormed) {
            player.displayClientMessage(
                    Component.translatable("gtoecore.clone.no_clone"), false);
        }
    }
}
