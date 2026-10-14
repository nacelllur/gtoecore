package com.gtoecore.objdemo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.renderable.ITextureRenderTypeLookup;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OBJ 渲染用的贴图查找工具（纯客户端）。
 *
 * <h2>为什么需要「缺贴图兜底」</h2>
 * <p>Blender 导出的 {@code .mtl} 只写纯色 {@code Kd}，没有 {@code map_Kd}。
 * 而 Forge 的 {@code ObjModel} 会把 {@code Kd} 写进<b>顶点色</b>
 * （{@code Vector4f colorTint = mat.diffuseColor}），最终颜色 = <b>贴图采样 × 顶点色</b>。
 * 顶点色有了，贴图却是空的 —— Forge 的 {@code UnbakedGeometryHelper.resolveDirtyMaterial}
 * 在 {@code map_Kd == null} 时返回 {@code MissingTextureAtlasSprite.getLocation()}，
 * 于是 mesh 被绑上紫黑棋盘格，整座模型被乘成紫黑，
 * {@code Kd} 根本看不出来。所以必须换一张贴图顶上。</p>
 *
 * <h2>⚠ lookup 收到的 key 是「完整资源路径」</h2>
 * <p>{@code ObjModel} 的 bake 阶段自己拼了路径（第 659 行）：</p>
 * <pre>
 * ResourceLocation texturePath = new ResourceLocation(loc.getNamespace(),
 *         "textures/" + loc.getPath() + ".png");
 * builder.addMesh(texturePath, quads);
 * </pre>
 * <p>所以 {@link ITextureRenderTypeLookup#get} 收到的形如
 * {@code gtoecore:textures/block/obj/xxx.png}；无 {@code map_Kd} 时则是
 * {@code minecraft:textures/missingno.png}。<b>别再按 {@code "missingno"} 比对了。</b>
 * 也正因此，本类递给 {@link RenderType#entitySolid} 的必须是同样格式的完整路径。</p>
 *
 * <h2>为什么在 Java 里兜底而不是改 MTL</h2>
 * <p>在 MTL 里给每个材质补一行 {@code map_Kd ...} 同样有效，但那样每次 Blender
 * 重新导出都要跑一遍后处理脚本，容易忘。放在这里则 <b>MTL 永远保持 Blender 原样导出</b>，
 * 全量丢进 {@code models/obj/} 即可。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ObjDemoTextures {

    /** 1×1 纯白贴图，完整资源路径（与 mesh key 同格式）。 */
    public static final ResourceLocation WHITE =
            new ResourceLocation(GTOEObjDemo.MODID, "textures/block/obj/obj_white.png");

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 已解析的 {@link RenderType} 缓存，避免每帧重新 {@code RenderType.create}。 */
    private static final Map<ResourceLocation, RenderType> CACHE = new ConcurrentHashMap<>();

    /** 玻璃（半透明）专用 RenderType 集合 —— 用于绘制时把 translucent 层排最后。 */
    private static final java.util.Set<RenderType> TRANSLUCENT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 某个 RenderType 是不是玻璃半透明层（{@code RigBakedMesh.draw} 排序用）。 */
    public static boolean isTranslucent(RenderType type) {
        return TRANSLUCENT.contains(type);
    }

    /**
     * 通用查找器：贴图缺失（missingno 或文件不存在）就顶白图，其余原样；
     * 玻璃（{@code ObjMaterialLift.GLASS_KEY} 注入的 key）走
     * {@code entityTranslucent} —— 只有它才开 alpha 混合，半透明才真正生效。
     */
    public static final ITextureRenderTypeLookup FLAT_COLOR_LOOKUP =
            name -> CACHE.computeIfAbsent(name, key -> {
                boolean glass = isGlass(key);
                RenderType type = glass
                        ? RenderType.entityTranslucent(isUsable(key) ? key : WHITE)
                        : RenderType.entitySolid(isUsable(key) ? key : WHITE);
                if (glass) {
                    TRANSLUCENT.add(type);
                    LOGGER.debug("[gtoecore] glass layer for {}", key);
                }
                return type;
            });

    /** 由 {@code ObjMaterialLift} 注入的半透明材质 key（渲染层选 translucent）。 */
    private static boolean isGlass(ResourceLocation texture) {
        return !isMissingno(texture)
                && texture.getNamespace().equals(GTOEObjDemo.MODID)
                && texture.getPath().endsWith("block/obj/glass.png");
    }

    private static boolean isUsable(ResourceLocation texture) {
        return !isMissingno(texture) && exists(texture);
    }

    /** Forge 无 {@code map_Kd} 时的占位贴图（{@code minecraft:textures/missingno.png}）。 */
    private static boolean isMissingno(ResourceLocation texture) {
        return texture.getPath().endsWith("missingno.png");
    }

    /**
     * 贴图文件是否真的在资源包里 —— 顺手救「MTL 指向了没打包进来的 png」这种情况，
     * 结果按 key 缓存，每个材质只查一次。
     */
    private static boolean exists(ResourceLocation texture) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return true; // 资源管理器未就绪：先别换，交给下次
            }
            return minecraft.getResourceManager().getResource(texture).isPresent();
        } catch (Exception e) {
            return true;
        }
    }

    private ObjDemoTextures() {
    }
}
