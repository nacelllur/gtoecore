package com.gtoecore;

import com.gtoecore.gt.GTNCoreGT;
import com.mojang.logging.LogUtils;
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
        // 普通物品（克隆体 / 生物团 / 重力核心物品）
        GTNItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("GT-New Core loaded");
    }
}
