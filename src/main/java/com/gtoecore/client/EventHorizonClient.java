package com.gtoecore.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gtoecore.gt.GTNCoreGT;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 事件视界引擎的客户端注册。
 *
 * <p><b>为什么必须显式注册：</b>GTM 7.5.3 的 {@code DynamicRenderType} 是个只有
 * {@code Codec} 的 record，它的"id"来自 {@code DynamicRenderManager} 的
 * {@code BiMap} 反查；没注册的话 {@code getId()} 返回 null，
 * 于是 {@code DynamicRender.compareTo}（动态渲染器列表排序时会调用）直接 NPE；
 * 模型 JSON 里那句 {@code "type": "gtoecore:event_horizon"} 也会解析失败。</p>
 *
 * <p><b>时机：</b>必须早于任何模型烘焙。GTM 自己是在
 * {@code ClientProxy.init()}（mod 构造期）里一次性注册全部类型的，
 * 我们照做 —— 由 {@code GTNCore} 构造器经 {@code DistExecutor} 只在客户端调用
 * {@link #registerDynamicRenders()}，服务端不会加载本类。</p>
 *
 * <p><b>⚠ 注册成功 ≠ 有画面。</b>渲染器还要在<b>模型 JSON</b> 的
 * {@code dynamic_renders} 数组里被点名，GTM 才真的会实例化它（见
 * {@link EventHorizonRenderer} 的类注释）。注册这行日志只证明类型表里有它。</p>
 */
public final class EventHorizonClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EventHorizonClient() {}

    public static void registerDynamicRenders() {
        ResourceLocation id = GTNCoreGT.id("event_horizon");
        DynamicRenderManager.register(id, EventHorizonRenderer.TYPE);
        LOGGER.info("[gtoecore] dynamic render type registered: {} (still needs a "
                + "matching entry in the machine model's \"dynamic_renders\" array)", id);
    }
}
