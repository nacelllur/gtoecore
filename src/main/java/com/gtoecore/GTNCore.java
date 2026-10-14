package com.gtoecore;

import com.gtoecore.client.EventHorizonClient;
import com.gtoecore.energy.GTOFluids;
import com.gtoecore.gt.GTNCoreGT;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * GT-New 专属内容 mod 入口。
 *
 * <p>与 packcompanion（GTSW 专用）解耦：GT-New 里新增的机器/内容一律放本 mod。
 * 纯内容 mod —— 无 Mixin、无配置文件，出错面最小。</p>
 */
@Mod(GTNCore.MODID)
public class GTNCore {
    public static final String MODID = "gtoecore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GTNCore() {
        // GTM addon：机器注册入口（构造期只挂监听，注册在 RegisterEvent 窗口执行）
        GTNCoreGT.init(FMLJavaModLoadingContext.get().getModEventBus());
        // 普通方块（舱室重力核心）—— 必须先于物品注册挂载
        GTNBlocks.register(FMLJavaModLoadingContext.get().getModEventBus());
        // OBJ 示范机器（涡轮机）
        com.gtoecore.objdemo.GTOEObjDemo.register(FMLJavaModLoadingContext.get().getModEventBus());
        // 普通物品（克隆体 / 生物团 / 重力核心物品）
        GTNItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        // 高级能量流体（激光能/量子能，纯罐装，参考 GT plasma）
        GTOFluids.register(FMLJavaModLoadingContext.get().getModEventBus());
        // 客户端专用：事件视界引擎的动态渲染类型注册。
        // 必须早于任何模型烘焙（GTM 自己也是在构造期一次性注册全部类型），
        // DistExecutor 保证专用服务端不会加载 com.gtoecore.client.*
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> EventHorizonClient::registerDynamicRenders);
        // 机架类机器（tower_rig / arch_rig / mast_rig）的动态渲染类型
        //（同上，必须早于模型烘焙）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> com.gtoecore.client.RigClient::registerDynamicRenders);
        // OBJ 示范机器（涡轮机）的 BER 注册与 OBJ 缓存失效
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> com.gtoecore.objdemo.TurbineDemoClient::registerRenderer);
        LOGGER.info("GT-New Core loaded");
    }
}
