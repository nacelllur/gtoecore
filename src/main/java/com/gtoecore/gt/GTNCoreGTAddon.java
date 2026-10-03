package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtoecore.GTNCore;

/**
 * GTM 官方 addon 入口：让 GTM 认领本 mod 的 registrate（材质/模型数据生成等）。
 * 机器注册不在这里做 —— 调用此回调时 gtceu:machine 注册表已冻结，
 * 真正注册在 RegisterEvent 窗口（见 {@link GTNCoreGT}）。
 */
@GTAddon
public class GTNCoreGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTNMachines.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        // 有「材质注册完成后才能做」的初始化放这里；机器注册不放这里。
    }

    @Override
    public String addonModId() {
        return GTNCore.MODID;
    }
}
