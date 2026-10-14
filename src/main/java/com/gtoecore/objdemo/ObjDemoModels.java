package com.gtoecore.objdemo;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.minecraftforge.client.model.obj.ObjLoader;
import net.minecraftforge.client.model.obj.ObjModel;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OBJ 模型懒加载缓存（纯客户端）。
 *
 * <p><b>为什么懒加载而不是在模型烘焙事件里加载：</b></p>
 * <ul>
 *   <li>{@code ModelEvent.ModifyBakingResult} 明确声明「在工作线程触发，除模型注册表与
 *       ModelBakery 外的东西都不安全访问」—— 而 {@link ObjLoader#loadModel} 要读
 *       ResourceManager，属于不安全操作；</li>
 *   <li>{@code ModelEvent.BakingCompleted} 的触发时机与 {@code ObjLoader} 这个
 *       reload listener 的执行顺序在 Forge 里没有硬保证。</li>
 * </ul>
 * <p>放到「第一帧渲染时」加载，则必然处于主线程、且资源管理器早已就绪，零时序风险。
 * 失败会被记住（{@link #FAILED}），不会每帧重试刷日志。</p>
 *
 * <p>模型体量按方块空间建模（1 单位 = 1 格），由各渲染器自行缩放/旋转/位移。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ObjDemoModels {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, CompositeRenderable> CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Set<String>> PART_NAMES = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();

    /**
     * 未烘焙的原始 {@link ObjModel}（按同一个 key 缓存）。
     *
     * <p>只有 {@link RigGeometry} 用它 —— 把一具模型拆成「静态 / 逐帧动画」两份
     * {@link CompositeRenderable} 时，必须拿得到<b>按部件分组</b>的原始几何
     * （{@code bakeRenderable} 一次性全烘，不提供过滤）。</p>
     */
    private static final Map<ResourceLocation, ObjModel> RAW = new ConcurrentHashMap<>();

    /**
     * 取（或首次加载）某个 OBJ 的烘焙结果。
     *
     * @param path 相对于 {@code assets/gtoecore/models/} 的路径，例如 {@code obj/tower_rig.obj}
     * @return 加载成功返回模型，失败/尚未成功返回 {@code null}
     */
    public static CompositeRenderable get(String path) {
        ResourceLocation location = location(path);
        CompositeRenderable cached = CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(location)) {
            return null;
        }
        CompositeRenderable baked = null;
        Set<String> partNames = null;
        try {
            // automaticCulling=true, shadeQuads=true, flipV=false, emissiveAmbient=false
            ObjModel obj = ObjLoader.INSTANCE.loadModel(
                    new ObjModel.ModelSettings(location, true, true, false, false, null));
            // ⚠ 必须放在 bakeRenderable 之前：烘焙时 Forge 把 diffuseColor 乘进顶点色，
            // Ke（自发光）/ d（透明度）不解析，这里在材质对象上补丁后再烘，两者才进得去。
            ObjMaterialLift.apply(obj, location);
            baked = obj.bakeRenderable(StandaloneGeometryBakingContext.create(location));
            // 部件名（= OBJ 里的 `o` / `g` 组名）顺便记下来：Forge 的
            // CompositeRenderable.Transforms 就是按这些名字索引变换矩阵的，
            // 逐部件动画（如塔架的涡轮转子）靠它绑定，不用在 Java 里写死名字。
            partNames = Set.copyOf(obj.getRootComponentNames());
            RAW.put(location, obj);
        } catch (Throwable t) {
            FAILED.add(location);
            LOGGER.error("[gtoecore] failed to load OBJ model {}", location, t);
        }
        if (baked == null) {
            FAILED.add(location);
            return null;
        }
        PART_NAMES.put(location, partNames == null ? Set.of() : partNames);
        CACHE.put(location, baked);
        LOGGER.info("[gtoecore] OBJ model loaded: {} ({} parts)", location,
                partNames == null ? 0 : partNames.size());
        return baked;
    }

    /**
     * 取某个 OBJ 的<b>未烘焙</b>模型（按部件分组的原始几何）。
     *
     * <p>仅限本 mod 内部使用（{@link RigGeometry} 拆分静态 / 动画部件）。
     * 首次调用会顺带触发 {@link #get(String)} 的加载；加载失败返回 {@code null}。</p>
     */
    static ObjModel raw(String path) {
        get(path);
        return RAW.get(location(path));
    }

    private static ResourceLocation location(String path) {
        return new ResourceLocation(GTOEObjDemo.MODID, "models/" + path);
    }

    /**
     * 取某个 OBJ 的**部件名集合**（= Forge {@code CompositeRenderable} 里
     * {@code Transforms} 的 key）。首次调用会顺带触发模型加载。
     *
     * @return 部件名（不可变）；模型尚未加载成功时返回空集
     */
    public static Set<String> getPartNames(String path) {
        ResourceLocation location = location(path);
        Set<String> names = PART_NAMES.get(location);
        if (names != null) {
            return names;
        }
        get(path);
        return PART_NAMES.getOrDefault(location, Set.of());
    }

    /** 资源包重载后调用，丢弃缓存强制重新加载（模型/贴图改完 F3+T 即可看到）。 */
    public static void invalidate() {
        CACHE.clear();
        PART_NAMES.clear();
        RAW.clear();
        FAILED.clear();
    }

    private ObjDemoModels() {
    }
}
