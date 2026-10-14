package com.gtoecore.objdemo;

import com.gtoecore.client.RigBakedMesh;
import com.gtoecore.client.RigModelBounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * OBJ 示范机器客户端入口（纯客户端类，由 {@code GTNCore} 经 DistExecutor 调用）。
 *
 * <p>只做两件事：</p>
 * <ol>
 *   <li>在 {@link EntityRenderersEvent.RegisterRenderers} 窗口注册涡轮机的 BER；</li>
 *   <li>在模型烘焙完成后清空 {@link ObjDemoModels} 的 OBJ 缓存 —— 改了
 *       {@code .obj}/{@code .mtl} 后 F3+T 就能看到新样子，不用重启游戏
 *       （塔架现在走 {@code DynamicRender}，同样吃这份缓存）。</li>
 * </ol>
 * <p>OBJ 本体由 {@link ObjDemoModels} 在首帧懒加载，这里不参与加载逻辑。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TurbineDemoClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TurbineDemoClient() {
    }

    /** 由 {@code GTNCore} 在 DistExecutor 的 CLIENT 分支里调用（mod 构造期）。 */
    public static void registerRenderer() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();

        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(GTOEObjDemo.TURBINE_DEMO_BE.get(),
                    TurbineDemoRenderer::new);
            LOGGER.info("[gtoecore] OBJ demo BER registered (turbine_demo)");
        });

        // 烘焙结束即失效缓存：清空是线程安全的，真正重载发生在下一帧主线程。
        // ⚠ 三类缓存必须一起清，否则 F3+T 之后会留下一份「按旧模型实例烘出来的顶点」：
        //   · ObjDemoModels —— OBJ / MTL 本体（会换成新的模型实例）
        //   · RigGeometry   —— 静态/动画拆分结果（引用旧实例）
        //   · RigBakedMesh  —— 已经上传到显存的 VBO（顶点是旧模型的）
        //   · RigModelBounds—— 碰撞表外延（开发期改表即生效）
        bus.addListener((ModelEvent.BakingCompleted event) -> {
            ObjDemoModels.invalidate();
            RigGeometry.invalidate();
            RigBakedMesh.invalidate();
            RigModelBounds.invalidate();
        });
    }
}
