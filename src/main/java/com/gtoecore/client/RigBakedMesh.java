package com.gtoecore.client;

import com.gtoecore.objdemo.ObjDemoTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一份 {@link CompositeRenderable} 几何<b>一次性烘进 GPU</b>（{@link VertexBuffer}），
 * 之后每帧只发几个 draw call —— 这是「堆四台塔就掉到 20 帧」的正解。
 *
 * <h2>瓶颈在哪</h2>
 * <p>塔走的是 {@code DynamicRender} —— GTM 的 {@code MachineModel.m_6922_}（方块实体渲染）
 * 每帧调一次，最后落到 Forge {@code CompositeRenderable.Mesh.render()}：</p>
 * <pre>
 * for (BakedQuad quad : quads) consumer.putBulkData(pose.last(), quad, ...);
 * </pre>
 * <p>也就是说<b>每一帧、每一台塔</b>都要把每个面的 4 个顶点在 CPU 上重新做一遍
 * 矩阵变换 + 法线变换 + 顶点格式打包（{@code putBulkData} 里每个顶点还要
 * {@code new Vector4f(...)} + {@code MemoryStack} 分配）。实测规模：{@code tower_rig}
 * 8044 个面（三角化展开后 ≈10800 个方块面）、{@code arch_rig} 8448、{@code mast_rig} 7228
 * ⇒ 四台塔每帧 ≈ 4.2 万个面、17 万个顶点，全是纯 CPU 单线程。几十毫秒就这么没了。</p>
 *
 * <h2>烘成 VBO 之后</h2>
 * <p>顶点只在<b>第一次</b>过一遍 CPU（顺便直接上传显存），之后每帧的成本是
 * 「少数几个 draw call」，<b>与模型面数无关</b>。四台同型号同朝向的塔共用同一份 VBO
 * （缓存键命中），所以塔越多越划算。</p>
 *
 * <h2>怎么做到与逐帧路径「逐顶点一致」</h2>
 * <p>不去自己解析 OBJ（那会引出一堆法线 / 顶点序 / 自动剔除的细微差异），而是把
 * {@link CompositeRenderable#render} <b>渲染进我们自己的 {@link BufferBuilder}</b>：
 * 给它一个「假」的 {@link MultiBufferSource}，它照样按 {@code FLAT_COLOR_LOOKUP} 取
 * {@link RenderType}、照样调 {@code putBulkData}，只是顶点进了我们的缓冲而不是屏幕。
 * 换句话说<b>顶点数据完全是 Forge 自己烘的</b>，和逐帧画时字节级相同。</p>
 *
 * <h2>⚠ 姿态（含偏航和缩放）是烘进去的</h2>
 * <p>烘焙那一刻的 {@code PoseStack}（偏航 → Z-up 立成 Y-up → 等比缩放，<b>不含</b>平移）
 * 会被写进顶点，所以绘制时外部只需要把「塔底面中心」那一格平移压上去
 * （见 {@code AbstractRigDynamicRender#render}）。</p>
 * <p><b>偏航必须烘进去</b>，不能留到绘制时用 {@code ModelViewMat} 转：
 * {@code putBulkData} 会把法线按 pose 的法线矩阵变换后写死进顶点，
 * 而 {@code rendertype_entity_solid.vsh} 是拿顶点里的 {@code Normal} <b>原样</b>和
 * {@code Light0_Direction / Light1_Direction}（世界方向常量）点乘的 ——
 * 顶点里的法线必须是「世界方向」。四种朝向各一份 VBO，就是这个原因。</p>
 *
 * <h2>⚠ 顶点里的光照（{@code UV2}）</h2>
 * <p>{@code putBulkData} 会把传入的 {@code packedLight} 写进每个顶点的 {@code UV2}。
 * 这个值编码的是「方块光 / 天空光<b>等级</b>」，<b>不</b>包含日夜变化
 * —— 日夜由 lightmap 贴图本身承担（{@code draw} 里 {@link RenderType} 的
 * lightmap 状态会把<b>实时</b>光照贴图绑到 {@code Sampler2}）。
 * 所以把光照烘进顶点是<b>正确</b>的：白天/黑夜、火把颜色全都自动跟着变；
 * 代价只是「玩家把塔罩进屋里导致光照等级变了」时要重烘一次 —— 这很罕见
 * （缓存键里的 {@code light} 负责这件事）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RigBakedMesh implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 初始顶点缓冲容量。
     *
     * <p>⚠ {@code BufferBuilder(int)} 的参数不是字节数 —— 1.20.1 的实现是
     * {@code MemoryTracker.create(capacity * 6)}，也就是「以 6 字节为单位」。
     * 这里取 2^18 ⇒ 1.5 MiB，正好罩住最大那具塔（{@code tower_rig} 8044 个 QUADS 面
     * × 4 顶点 × 36 字节 ≈ 1.16 MiB）；真不够时 {@code BufferBuilder} 会自己扩容。</p>
     */
    private static final int INITIAL_CAPACITY = 1 << 18;

    /**
     * 缓存上限 = 同时保留的「渲染器 + 模型 + 朝向 + 光照」组合数。
     *
     * <p>典型占用：3 台机器 × 4 个水平朝向 = 12 条（同一个基地里光照等级通常一致）。
     * 每条 ≈ 1.2 MB 显存，所以上限给 24 足够宽裕。</p>
     */
    private static final int MAX_CACHE = 24;

    /** 「重烘风暴」判据：5 秒内重烘超过这个次数就警告一次（说明缓存在被反复冲掉）。 */
    private static final int STORM_BAKES = 16;
    private static final long STORM_WINDOW_NANOS = 5_000_000_000L;

    /** 缓存键：姿态只通过 {@code poseHash}（偏航角的位模式）区分，缩放是常量。 */
    private record Key(Class<?> renderer, String objPath, int poseHash, int light, int overlay) {
    }

    /**
     * 缓存。访问<b>全部发生在渲染线程</b>（只在 {@code DynamicRender#render} 里调），
     * 所以用普通 {@link LinkedHashMap} 就够了；{@code accessOrder=true} 顺手做 LRU。
     */
    private static final Map<Key, RigBakedMesh> CACHE =
            new LinkedHashMap<>(32, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, RigBakedMesh> eldest) {
                    if (size() > MAX_CACHE) {
                        closeQuietly(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };

    /** 资源包重载后置位；下一帧在渲染线程上真正清空（释放 GL 对象必须在渲染线程）。 */
    private static volatile boolean dirty;

    private static long stormWindowStart;
    private static int stormBakes;
    private static boolean stormWarned;

    /** 一次上传到 GPU 的分组：一个 {@link RenderType} 一个 VBO。 */
    public record Layer(RenderType type, VertexBuffer buffer) {
    }

    private final List<Layer> layers;
    private final int quads;

    private RigBakedMesh(List<Layer> layers, int quads) {
        this.layers = layers;
        this.quads = quads;
    }

    /**
     * 取（或首次烘焙）一份几何。
     *
     * @param renderer 渲染器类（缓存键的一部分，避免不同渲染器共用同一 OBJ 时串味）
     * @param objPath  OBJ 路径（日志与缓存键用）
     * @param pose     <b>不含平移</b>的局部姿态（偏航 → Z-up 立成 Y-up → 等比缩放）
     * @param poseHash 姿态的哈希（本 mod 里就是偏航角的位模式）
     * @param light    烘焙时的打包光照（{@code LevelRenderer.getLightColor} 那一路）
     * @param overlay  烘焙时的 overlay（通常是 {@code OverlayTexture.NO_OVERLAY}）
     * @param model    要烘的渲染体（可以是 {@code RigGeometry.Split#statics()} 那份子集）
     */
    public static RigBakedMesh get(Class<?> renderer, String objPath, Matrix4f pose, int poseHash,
                                  int light, int overlay, CompositeRenderable model) {
        if (dirty) {
            dirty = false;
            closeAll();
        }
        Key key = new Key(renderer, objPath, poseHash, light, overlay);
        RigBakedMesh cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        RigBakedMesh built = bake(model, pose, light, overlay);
        CACHE.put(key, built);
        noteBake(objPath, poseHash, light, built);
        return built;
    }

    /**
     * 资源包重载（F3+T）后调用 —— 模型对象会换新实例，旧的 VBO 必须重烘。
     *
     * <p>不在这里直接释放 GL 对象（可能在别的线程被调到），只置位；
     * 下一次 {@link #get} 在渲染线程上真正清空。</p>
     */
    public static void invalidate() {
        dirty = true;
    }

    /** 把一个渲染体烘成 VBO（按 {@link RenderType} 分组，通常只有一组）。 */
    private static RigBakedMesh bake(CompositeRenderable model, Matrix4f pose, int light, int overlay) {
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        // 假的顶点接收方：Forge 照常走它自己的烘焙逻辑，顶点落进我们的 BufferBuilder。
        MultiBufferSource capture = type -> builders.computeIfAbsent(type, t -> {
            BufferBuilder builder = new BufferBuilder(INITIAL_CAPACITY);
            builder.begin(t.mode(), t.format());
            return builder;
        });

        PoseStack poseStack = new PoseStack();
        poseStack.mulPoseMatrix(pose);
        model.render(poseStack, capture, ObjDemoTextures.FLAT_COLOR_LOOKUP, light, overlay, 0.0F,
                CompositeRenderable.Transforms.EMPTY);

        List<Layer> layers = new ArrayList<>(builders.size());
        int quads = 0;
        for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet()) {
            BufferBuilder.RenderedBuffer rendered = entry.getValue().end();
            if (rendered.isEmpty()) {
                rendered.release();
                continue;
            }
            quads += rendered.drawState().vertexCount() / 4;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(rendered);
            VertexBuffer.unbind();
            layers.add(new Layer(entry.getKey(), buffer));
        }
        // ⚠ 半透明层（玻璃）必须排最后：alpha 混合是「后画的叠在前面的上面」，
        // 玻璃身后的不透明面若画在它后面，深度测试会让玻璃白做。
        layers.sort(java.util.Comparator.comparingInt(l ->
                com.gtoecore.objdemo.ObjDemoTextures.isTranslucent(l.type()) ? 1 : 0));
        return new RigBakedMesh(layers, quads);
    }

    /**
     * 在当前 PoseStack 下把这份几何画出去。
     *
     * <p>用的是原版区块 VBO 的同一套写法（{@code LevelRenderer.renderChunkLayer} /
     * 天空盒 {@code *Buffer.drawWithShader}）：先让 {@link RenderType} 铺好状态
     * （着色器、贴图、lightmap、剔除、混合…），再把矩阵喂给 {@code drawWithShader} ——
     * 参数顺序是 {@code (模型视图, 投影, 着色器)}。</p>
     *
     * <p>注意着色器要<b>在 {@code setupRenderState()} 之后</b>取：这一步才是
     * 把该 {@link RenderType} 的着色器装上去的地方。</p>
     */
    public void draw(PoseStack poseStack) {
        if (this.layers.isEmpty()) {
            return;
        }
        Matrix4f modelView = poseStack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        for (Layer layer : this.layers) {
            layer.type().setupRenderState();
            layer.buffer().bind();
            layer.buffer().drawWithShader(modelView, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
            layer.type().clearRenderState();
        }
    }

    public int quadCount() {
        return this.quads;
    }

    @Override
    public void close() {
        for (Layer layer : this.layers) {
            layer.buffer().close();
        }
        this.layers.clear();
    }

    private static void noteBake(String objPath, int poseHash, int light, RigBakedMesh mesh) {
        long now = System.nanoTime();
        if (now - stormWindowStart > STORM_WINDOW_NANOS) {
            stormWindowStart = now;
            stormBakes = 0;
        }
        stormBakes++;
        LOGGER.info("[gtoecore] {} baked into {} VBO(s) ({} quads, pose {}, light {}, cache {}/{})"
                        + " - per-frame vertex processing for it is now ~zero",
                objPath, mesh.layers.size(), mesh.quads, poseHash, light, CACHE.size(), MAX_CACHE);
        if (stormBakes > STORM_BAKES && !stormWarned) {
            stormWarned = true;
            LOGGER.warn("[gtoecore] rig VBO cache is thrashing ({} re-bakes in {}s, cache size {})"
                            + " - machines probably sit at many different light levels;"
                            + " consider raising RigBakedMesh.MAX_CACHE",
                    stormBakes, STORM_WINDOW_NANOS / 1_000_000_000L, CACHE.size());
        }
    }

    private static void closeQuietly(RigBakedMesh mesh) {
        try {
            mesh.close();
        } catch (Throwable ignored) {
            // 关不掉就算了，别让清理流程再把渲染线程带崩
        }
    }

    private static void closeAll() {
        for (RigBakedMesh mesh : CACHE.values()) {
            closeQuietly(mesh);
        }
        CACHE.clear();
    }
}
