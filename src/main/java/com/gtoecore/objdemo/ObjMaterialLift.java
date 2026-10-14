package com.gtoecore.objdemo;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.obj.ObjModel;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 OBJ 烘焙<b>之前</b>对 Forge 已解析的材质对象打补丁，找回 Blender MTL 里
 * 丢失的三个语义：
 *
 * <ol>
 *   <li><b>{@code Ke} 自发光</b> —— Forge 的 {@code ObjMaterialLibrary} 解析 MTL 时
 *       switch 里没有 {@code Ke} 分支，发光材质在游戏里只剩 {@code Kd} 底色
 *       （亮处不亮）。这里自己解析同一份 MTL 的 {@code Ke} 亮度表，折算成增益
 *       乘以 {@code diffuseColor}，让霓虹条重新亮起来。</li>
 *   <li><b>暗部抬升</b> —— Blender 导出的近黑材质（如 {@code C_Blk} Kd≈0.015）
 *       乘上 {@code entity_solid} 最低 0.4 的环境光后 = 死黑一团。把低亮度 Kd
 *       抬到最小可见亮度，恢复层次（暗的仍是暗的，但不至于黑成一坨）。</li>
 *   <li><b>半透明</b> —— Forge 把 {@code d} 存进 {@code Material.dissolve} 但从不
 *       参与顶点 alpha（{@code ObjModel.makeQuad} 的顶点 alpha = 颜色 ×
 *       {@code diffuseColor.w}，而 {@code d} 不写 w）。把 {@code dissolve} 写进
 *       {@code diffuseColor.w}，原版 {@code entity_solid} 自带的 alpha 混合随即
 *       生效 —— lhc_combo 的玻璃外壳（d=0.2）真正变透明。</li>
 * </ol>
 *
 * <p>实现：反射取 {@code ObjModel.parts}（已有先例）→ 逐 {@code ModelObject} 的
 * {@code meshes} → {@code ModelMesh.mat}（类型 {@code ObjMaterialLibrary.Material}），
 * 该材质的 {@code name/diffuseColor/dissolve} 全是 public，直接改字段。
 * 在 {@link ObjDemoModels} 加载后、{@code bakeRenderable} 之前调用一次；
 * 之后 {@link RigGeometry} 的逐部件烘焙复用<b>同一批 Material 实例</b>，
 * 两条烘焙路径（全量 + 拆分）效果天然一致。</p>
 *
 * <p>拿不到字段时静默失败（返回 -1）—— 只丢增强，不崩。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ObjMaterialLift {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 低亮度 Kd 抬升到的目标平均亮度（死黑消失，但仍保持是「暗」）。 */
    private static final float MIN_LUMINANCE = 0.14F;

    /** {@code Ke} 折算进 {@code Kd} 的封顶增益（发光条亮而不刺眼）。 */
    private static final float EMISSIVE_MAX_GAIN = 0.75F;

    /**
     * 注入给「透明材质」的伪贴图 key（带命名空间，解析成
     * {@code gtoecore:textures/block/obj/glass.png}，文件不存在也没关系 ——
     * {@link ObjDemoTextures} 靠这个 key 的后缀认出「这是玻璃」，改用
     * {@code entityTranslucent} 渲染层，顶点 alpha 才会做混合）。
     */
    static final ResourceLocation GLASS_KEY =
            new ResourceLocation(GTOEObjDemo.MODID, "block/obj/glass");

    // ---- Forge 类引用（全部按 1.20.1 真实结构）----
    private static final Field PARTS = field(ObjModel.class, "parts");
    private static final Field MESHES = field("net.minecraftforge.client.model.obj.ObjModel$ModelObject", "meshes");
    private static final Field MAT = field("net.minecraftforge.client.model.obj.ObjModel$ModelMesh", "mat");
    private static final Field MAT_NAME = field("net.minecraftforge.client.model.obj.ObjMaterialLibrary$Material", "name");
    private static final Field MAT_DIFFUSE = field("net.minecraftforge.client.model.obj.ObjMaterialLibrary$Material", "diffuseColor");
    private static final Field MAT_DISSOLVE = field("net.minecraftforge.client.model.obj.ObjMaterialLibrary$Material", "dissolve");
    private static final Field MAT_DIFFUSE_MAP = field("net.minecraftforge.client.model.obj.ObjMaterialLibrary$Material", "diffuseColorMap");

    private ObjMaterialLift() {
    }

    /**
     * 对一具 OBJ 的全部材质执行三连补丁（Ke 增益 / 暗部抬升 / d→alpha）。
     *
     * @param obj 未烘焙的 {@link ObjModel}
     * @param loc OBJ 的资源位置（{@code gtoecore:models/obj/xxx.obj}）；同名的
     *            {@code .mtl} 用于读取 {@code Ke} 亮度表
     * @return 补丁成功的材质数；不可用 / 异常返回 -1
     */
    public static int apply(ObjModel obj, ResourceLocation loc) {
        if (PARTS == null || MESHES == null || MAT == null || MAT_NAME == null
                || MAT_DIFFUSE == null || MAT_DISSOLVE == null) {
            return -1;
        }
        Map<String, Float> keTable = parseKe(loc);
        int count = 0;
        try {
            Object parts = PARTS.get(obj);
            if (parts instanceof Map<?, ?> map) {
                for (Object object : map.values()) {
                    count += patchObject(object, keTable);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] material lift failed for {} - enhancement skipped", loc, t);
            return -1;
        }
        if (count > 0) {
            LOGGER.info("[gtoecore] material lift: patched {} material(s) of {} "
                    + "(Ke glow + dark lift + dissolve alpha)", count, loc);
        }
        return count;
    }

    /** 递归：对象自己的 {@code meshes} + {@code ModelGroup} 的子 {@code parts}。 */
    private static int patchObject(Object object, Map<String, Float> keTable) throws Throwable {
        int count = 0;
        List<?> meshes = (List<?>) MESHES.get(object);
        if (meshes != null) {
            for (Object mesh : meshes) {
                Object mat = MAT.get(mesh);
                if (mat != null) {
                    count += patchMaterial(mat, keTable) ? 1 : 0;
                }
            }
        }
        // 只有 ModelGroup 有 `parts` 子表；叶子 ModelObject 没有 ⇒ 用字段探测兜底
        Field partsField = null;
        try {
            partsField = object.getClass().getDeclaredField("parts");
        } catch (NoSuchFieldException ignored) {
            // 叶子对象，没有子组
        }
        if (partsField != null) {
            partsField.setAccessible(true);
            Object inner = partsField.get(object);
            if (inner instanceof Map<?, ?> map) {
                for (Object child : map.values()) {
                    count += patchObject(child, keTable);
                }
            }
        }
        return count;
    }

    private static boolean patchMaterial(Object mat, Map<String, Float> keTable) throws Throwable {
        String name = String.valueOf(MAT_NAME.get(mat));
        Vector4f diffuse = (Vector4f) MAT_DIFFUSE.get(mat);
        float dissolve = MAT_DISSOLVE.getFloat(mat);

        float r = diffuse.x();
        float g = diffuse.y();
        float b = diffuse.z();

        // ① 暗部抬升：平均亮度 < 阈值时整体乘到阈值（保色相）
        float lum = (r + g + b) / 3.0F;
        if (lum > 0.001F && lum < MIN_LUMINANCE) {
            float lift = MIN_LUMINANCE / lum;
            r *= lift;
            g *= lift;
            b *= lift;
        }

        // ② Ke 自发光增益：亮度 clamp 后乘（>1 的部分由显示器自然限幅）
        float ke = keTable.getOrDefault(name, 0.0F);
        if (ke > 0.001F) {
            float gain = 1.0F + EMISSIVE_MAX_GAIN * Math.min(ke, 1.0F);
            r *= gain;
            g *= gain;
            b *= gain;
        }

        // ③ d → alpha：dissolve 写进 diffuseColor.w（makeQuad 顶点 alpha 用它）
        float alpha = Math.max(1.0e-4F, Math.min(1.0F, dissolve));
        diffuse.set(r, g, b, alpha);
        // ④ 半透明材质 → 换玻璃渲染层 key：entitySolid 的混合状态是「不透明」
        //    （NO_TRANSPARENCY），alpha 写了也不混合；给 diffuseColorMap 注入
        //    玻璃 key，ObjDemoTextures 会为它选 entityTranslucent 层。
        boolean glass = dissolve < 0.999F;
        if (glass && MAT_DIFFUSE_MAP != null) {
            MAT_DIFFUSE_MAP.set(mat, GLASS_KEY.toString());
        }

        boolean changed = ke > 0.001F || dissolve < 0.999F || lum < MIN_LUMINANCE;
        if (changed) {
            LOGGER.debug("[gtoecore]   lift {}: Kd=({:.3f}, {:.3f}, {:.3f}) d={} ke={} glass={}",
                    name, r, g, b, dissolve, ke, glass);
        }
        return changed;
    }

    /** 解析与 OBJ 同名的 MTL，仅取 {@code newmtl ...} + {@code Ke r g b} → 亮度表。 */
    private static Map<String, Float> parseKe(ResourceLocation loc) {
        Map<String, Float> table = new HashMap<>();
        try {
            String path = loc.getPath().replace(".obj", ".mtl");
            ResourceLocation mtlLoc = new ResourceLocation(loc.getNamespace(), path);
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null) {
                return table;
            }
            String current = null;
            try (InputStream in = mc.getResourceManager().getResource(mtlLoc).orElseThrow().open();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String raw; (raw = reader.readLine()) != null; ) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.split("\\s+");
                    if (p[0].equals("newmtl") && p.length > 1) {
                        current = line.substring(line.indexOf(' ') + 1).trim();
                    } else if (p[0].equals("Ke") && p.length >= 4 && current != null) {
                        float ke = (parseFloat(p[1]) + parseFloat(p[2]) + parseFloat(p[3])) / 3.0F;
                        table.put(current, Math.max(0.0F, ke));
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[gtoecore] no Ke table for {} (glow enhancement off)", loc);
        }
        return table;
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0.0F;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] cannot reflect {}.{} - material lift DISABLED",
                    owner.getSimpleName(), name, t);
            return null;
        }
    }

    private static Field field(String className, String fieldName) {
        try {
            Class<?> cls = Class.forName(className);
            Field f = cls.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] cannot reflect {}.{} - material lift DISABLED",
                    className, fieldName, t);
            return null;
        }
    }
}