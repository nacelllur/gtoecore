package com.gtoecore.objdemo;

import com.gtoecore.GTNCore;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * OBJ 示范机器的服务端事件接线。
 *
 * <p>只有一个职责：每 tick 推进 {@link RigCollision} 的铺/拆任务队列
 * （以及顺带的定期体检）。必须有这么一个<b>独立于控制器</b>的驱动源 ——
 * 因为拆除控制器时它的方块实体已经不存在了，没法靠自己的 ticker 把碰撞体收回去。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ObjDemoEvents {

    private ObjDemoEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RigCollision.tickAll();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RigCollision.reset();
    }
}
