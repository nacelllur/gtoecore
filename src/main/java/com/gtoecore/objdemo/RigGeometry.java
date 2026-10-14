package com.gtoecore.objdemo;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.minecraftforge.client.model.obj.ObjModel;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把一具 OBJ 拆成两份 {@link CompositeRenderable}：<b>静态部分</b>（可以一次性烘进
 * VBO，之后每帧只发 draw call）与<b>逐帧动画部分</b>（零件要转，必须每帧重算顶点）。
 *
 * <h2>为什么必须拆</h2>
 * <p>塔架的性能瓶颈是「每帧在 CPU 上把每个面重新做一遍矩阵变换 + 顶点格式打包」
 * （{@code CompositeRenderable.Mesh.render} → {@code putBulkData}）。把它烘成 VBO
 * 就没成本了 —— 但烘进去的顶点是<b>静的</b>，转不动的涡轮会被冻住。</p>
 * <p>而 Forge 的 {@code CompositeRenderable} <b>没有任何按部件过滤的入口</b>：
 * {@link CompositeRenderable.Transforms} 只是个「部件名 → 矩阵」的 map
 * （{@code getTransform(name)} 返回 {@code null} 时该部件按原样画），
 * {@code CompositeRenderable.render} 永远遍历全部部件。所以「只重画转子」这件事
 * 只能靠<b>真的拿到一份只含转子的渲染体</b>来实现。</p>
 *
 * <h2>怎么拿到「只含某几个部件的渲染体」</h2>
 * <p>不用自己去解析 OBJ（那会引出一堆法线 / 顶点序 / 自动剔除的细微差异），
 * 而是走 Forge 自己的路：{@link ObjModel} 内部就是按 OBJ 的 {@code o} 组名分好的
 * {@code Map<String, ModelObject>}，而 {@code ModelObject.bake(PartBuilder, ctx)}
 * 是 <b>public</b> 的 —— 我们只是换个 {@link CompositeRenderable.Builder} 再烘一遍。</p>
 * <p>唯一的门槛是那个 map 字段（{@code ObjModel.parts}）是 private，
 * 所以要一次反射。拿不到就<b>整段退化</b>：不拆，返回「整个模型都是静态的」
 * —— 视觉上动画会停住，但性能优化照常生效，且只打一条警告，不会每帧重试。</p>
 *
 * <h2>代价</h2>
 * <p>拆分只做一次（按 OBJ 路径缓存）。两份渲染体各烘一遍，但每个面只会被
 * {@code makeQuad} 处理<b>一次</b> —— 总量和原来的 {@code bakeRenderable} 一样。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RigGeometry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 拆分结果。
     *
     * @param statics  静态部分（永远非 null）—— 可以烘成 VBO
     * @param animated 逐帧动画部分（{@code null} = 该模型没有需要逐帧的部件，
     *                 或者拆分不可用 ⇒ 整个模型都在 {@code statics} 里）
     */
    public record Split(CompositeRenderable statics, CompositeRenderable animated) {

        public boolean hasAnimated() {
            return this.animated != null;
        }
    }

    /** 按 {@code "obj 路径|前缀,前缀,…"} 缓存。渲染线程独占访问。 */
    private static final Map<String, Split> CACHE = new ConcurrentHashMap<>();

    /**
     * {@code ObjModel.parts}（private final Map&lt;String, ModelGroup&gt;）。
     *
     * <p>Forge 自己的类不做混淆，所以这个字段名在运行时就是字面量 {@code parts}。
     * 万一将来 Forge 改了名字 / 加了模块限制，这里会变成 {@code null}，
     * 所有调用点都会走「不拆分」的退化路径。</p>
     */
    private static final Field PARTS_FIELD = lookupParts();

    private static Field lookupParts() {
        try {
            Field field = ObjModel.class.getDeclaredField("parts");
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] ObjModel.parts is not reachable - rig models will be baked "
                    + "as a single static mesh and per-part animation will be FROZEN "
                    + "(rendering is still fast, just not animated)", t);
            return null;
        }
    }

    /**
     * 取（或首次拆分）某具 OBJ。
     *
     * @param objPath           相对 {@code assets/gtoecore/models/} 的路径
     * @param animatedPrefixes  需要逐帧更新的部件名<b>前缀</b>（空数组 = 整个模型都烘死）
     * @return 拆分结果；OBJ 加载失败时返回 {@code null}
     */
    public static Split of(String objPath, String[] animatedPrefixes) {
        String key = objPath + '|' + String.join(",", animatedPrefixes);
        Split cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Split built = build(objPath, animatedPrefixes);
        if (built != null) {
            CACHE.put(key, built);
        }
        return built;
    }

    /** 资源包重载（F3+T）后调用：模型对象会换新实例，必须重新拆分。 */
    public static void invalidate() {
        CACHE.clear();
    }

    private static Split build(String objPath, String[] animatedPrefixes) {
        CompositeRenderable full = ObjDemoModels.get(objPath);
        if (full == null) {
            return null;
        }
        if (animatedPrefixes.length == 0 || PARTS_FIELD == null) {
            return new Split(full, null);
        }
        ObjModel raw = ObjDemoModels.raw(objPath);
        if (raw == null) {
            return new Split(full, null);
        }
        try {
            Map<?, ?> parts = (Map<?, ?>) PARTS_FIELD.get(raw);
            if (parts == null || parts.isEmpty()) {
                return new Split(full, null);
            }
            IGeometryBakingContext context = StandaloneGeometryBakingContext.create(
                    new ResourceLocation(GTOEObjDemo.MODID, "models/" + objPath));
            CompositeRenderable.Builder statics = CompositeRenderable.builder();
            CompositeRenderable.Builder animated = CompositeRenderable.builder();
            int animatedCount = 0;
            for (Map.Entry<?, ?> entry : parts.entrySet()) {
                if (!(entry.getValue() instanceof ObjModel.ModelObject object)) {
                    continue;
                }
                String name = String.valueOf(entry.getKey());
                if (startsWithAny(name, animatedPrefixes)) {
                    // 注意：分量必须挂在与 TOP-LEVEL 部件名同名的 child 上 ——
                    // 逐部件变换表（CompositeRenderable.Transforms）就是按顶层名索引的。
                    object.bake(animated.child(name), context);
                    animatedCount++;
                } else {
                    object.bake(statics.child(name), context);
                }
            }
            if (animatedCount == 0) {
                LOGGER.info("[gtoecore] {} has no part matching {} - whole model is static",
                        objPath, String.join("/", animatedPrefixes));
                return new Split(full, null);
            }
            LOGGER.info("[gtoecore] {} split: {} part(s) baked into a static mesh, "
                            + "{} part(s) kept on the per-frame path",
                    objPath, parts.size() - animatedCount, animatedCount);
            return new Split(statics.get(), animated.get());
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] failed to split {} into static/animated parts - "
                            + "falling back to one static mesh (animation will be frozen)",
                    objPath, t);
            return new Split(full, null);
        }
    }

    private static boolean startsWithAny(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private RigGeometry() {
    }
}
