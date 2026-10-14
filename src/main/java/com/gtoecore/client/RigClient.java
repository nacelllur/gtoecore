package com.gtoecore.client;

import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.GTNCoreGT;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 「机架（rig）类机器」的动态渲染类型注册 —— {@code tower_rig} / {@code arch_rig} /
 * {@code mast_rig} 三台机器各一个类型。
 *
 * <p><b>为什么必须显式注册：</b>GTM 7.5.3 的 {@code DynamicRenderType} 是个只有
 * {@code Codec} 的 record，它的 "id" 来自 {@code DynamicRenderManager} 的 BiMap 反查；
 * 没注册的话 {@code getId()} 返回 null，于是 {@code DynamicRender.compareTo}
 * （动态渲染器列表排序时会调用）直接 NPE；模型 JSON 里那句
 * {@code "type": "gtoecore:arch_rig"} 也会解析失败。</p>
 *
 * <p><b>时机：</b>必须早于任何模型烘焙。由 {@code GTNCore} 构造器经
 * {@code DistExecutor} 只在客户端调用。</p>
 *
 * <p><b>⚠ 注册成功 ≠ 有画面：</b>渲染器还要在各自模型 JSON 的 {@code dynamic_renders}
 * 数组里被点名，GTM 才真的会实例化它。</p>
 */
public final class RigClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RigClient() {}

    public static void registerDynamicRenders() {
        register("tower_rig", TowerRigDynamicRender.TYPE);
        register("arch_rig", ArchRigDynamicRender.TYPE);
        register("mast_rig", MastRigDynamicRender.TYPE);
        register("drum_rig", DrumRigDynamicRender.TYPE);
        register("blade_rig", BladeRigDynamicRender.TYPE);
        register("maimai_rig", MaimaiRigDynamicRender.TYPE);
        register("cms_rig", CmsRigDynamicRender.TYPE);
        register("lhc_combo", LhcComboRigDynamicRender.TYPE);
    }

    private static <T extends IMachineFeature, S extends DynamicRender<T, S>> void register(
            String name, DynamicRenderType<T, S> type) {
        ResourceLocation id = GTNCoreGT.id(name);
        DynamicRenderManager.register(id, type);
        LOGGER.info("[gtoecore] dynamic render type registered: {} (still needs a "
                + "matching entry in the machine model's \"dynamic_renders\" array)", id);
    }
}
