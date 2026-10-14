package com.gtoecore.client;

import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.EventHorizonMachine;
import com.gtoecore.gt.GTNCoreGT;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * 「事件视界引擎」黑洞特效渲染器 —— <b>烘焙透镜 + 倾角帧集</b>方案 v3。
 *
 * <p><b>为什么是烘焙：</b>引力透镜（远侧盘弯到阴影上方 + 下方镜像弧 +
 * 多普勒亮弧）是测地线效应，拼几何画不出来。离线逐射线积分史瓦西零测地线
 * （{@code tools/blackhole_lens.py}，俘获阈值自检通过）烘成 9 张倾角帧
 * （0°~64° 每 8°），运行时画一个<b>永远面向相机的公告板</b>按相机仰角
 * 相邻两帧交叉淡化 —— 史瓦西透镜轴对称，帧集就是全空间外观。</p>
 *
 * <p><b>v3（2026-10-07，用户反馈四连）改了什么：</b></p>
 * <ol>
 *   <li><b>删喷流</b> —— 几何空心锥加法混合，在截图里就是「上下两根紫色光柱」，
 *       《星际穿越》的 Gargantua 根本没有喷流，烘焙图里也没有，纯出戏；</li>
 *   <li><b>星空只画给舱内</b> —— 750 片星星壳从结构外面看就是满屏「外圈光点」；
 *       现在只有相机进入结构半径内才渲染（舱内仰头才是满天星），外面干干净净；</li>
 *   <li><b>换原版渲染类型</b> —— 自定义 {@code RenderType.create(...)} 用的是
 *       {@code position_color_tex} 核心着色器，<b>Oculus/Iris 光影对这类
 *       自定义类型支持差，经常整个不画</b>。公告板改用
 *       {@link RenderType#entityTranslucentEmissive}、星星用
 *       {@link RenderType#lightning()}，都是原版类型，光影必经的翻译层全都认识；</li>
 *   <li><b>动画只剩「呼吸」</b> —— 原来每帧 1200+ 个几何 quad + 喷流脉冲是
 *       「卡顿感」的来源。现在黑洞本体是静态贴图，只做 ±3% 的缓慢亮度呼吸。</li>
 * </ol>
 *
 * <p><b>⚠ 必须同时在模型 JSON 里声明 {@code dynamic_renders}：</b>
 * 运行时只有模型 JSON 生效（{@code MachineModelLoader} 直接读这个数组），
 * {@code GTNMachines} 里的 {@code .addDynamicRenderer(...)} 只影响 datagen。
 * 漏了它渲染器根本不会被实例化，而且<b>不报任何错</b>。</p>
 *
 * <p><b>⚠ 一趟只持有一个 VertexConsumer：</b>
 * {@code MultiBufferSource.BufferSource} 把非 fixed 类型全部映射到同一个
 * {@code BufferBuilder}，交叉持有两种类型会静默合并错色。
 * 提交顺序 = 绘制顺序：黑洞帧0 → 黑洞帧1 →（舱内）星空。</p>
 *
 * <p><b>定位：</b>结构 35³、控制器嵌在赤道面 +Z 极的球面上。
 * 由 {@code BlockPattern.checkPatternAt} 的 {@code setActualRelativeOffset} 可算出
 * 结构内某格相对控制器的世界偏移 = {@code (17-char, row-17, 34-aisle)}，
 * 结构中心沿控制器 <b>BACK</b> 方向 17 格（心距 17.0，不是 17.5）。</p>
 */
public class EventHorizonRenderer extends DynamicRender<EventHorizonMachine, EventHorizonRenderer> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<EventHorizonRenderer> CODEC = Codec.unit(EventHorizonRenderer::new);
    public static final DynamicRenderType<EventHorizonMachine, EventHorizonRenderer> TYPE =
            new DynamicRenderType<>(CODEC);

    // ---- 烘焙帧集参数（与 tools/bake_blackhole_view.py 一致）----
    /** 帧覆盖的倾角步长（度） */
    private static final float FRAME_INCL_STEP = 8.0f;
    /** 最大倾角（度） */
    private static final float FRAME_MAX_INCL = 64.0f;
    private static final int FRAME_COUNT = 9;
    private static final ResourceLocation[] FRAME_TEX = new ResourceLocation[FRAME_COUNT];

    static {
        for (int i = 0; i < FRAME_COUNT; i++) {
            FRAME_TEX[i] = GTNCoreGT.id(String.format(
                    "textures/block/multiblock/event_horizon_engine/blackhole_i%02d.png",
                    Math.round(i * FRAME_INCL_STEP)));
        }
    }

    /**
     * 公告板半宽（格）—— <b>固定值，不随距离缩放</b>。
     * 烘焙是 30 格外观测（FOV 60°，帧宽覆盖 2·tan30°·30 = 34.6 格）。
     * 盘外缘缩到 11.5 后实测内容半宽 13.86 格（数值 bbox），取 14.2 留少量边。
     * 固定世界尺寸 = 无论玩家多远，阴影 / 盘的<b>世界半径</b>都 1:1 重现
     * （若按视角缩放，黑洞在任何距离都占同样视角，假得很）。
     */
    private static final float BILLBOARD_HALF = 14.2f;

    // ---- 几何尺度（格）----
    /** 星空壳半径。必须小于结构内表面半径 15.49，否则星星会插进笼子格子里 */
    private static final float R_SKY = 15.0f;
    /** 视界球相对控制器方块原点的偏移（沿 BACK 方向，单位：格） */
    private static final double CENTER_OFFSET = 17.0;

    private static final int STAR_COUNT = 750;

    // ---- 日冕（模式 2）参数 ----
    /** 纯白贴图（黑球只靠顶点色染色，用一张 1×1 白图即可） */
    private static final ResourceLocation WHITE_TEX =
            GTNCoreGT.id("textures/block/multiblock/event_horizon_engine/white.png");
    /** 黑球半径（格）。
     *
     * <p>v2（2026-10-07）放大：整体（黑球 + 最外层光晕）半径 ≈ 内环大小 ——
     * 最外层光晕 = 6.25 × 2.36 = 14.75 格，约为结构内表面半径 15.49 的 95%，
     * 既能填满内腔又不插进笼子格子（星空壳 15.0 也恰好在外缘之外）。</p> */
    private static final float CORONA_BALL_R = 6.25f;
    private static final float[] CORONA_HALO_RF = {1.40f, 1.82f, 2.36f};
    /** 各燃烧云层的亮度权重（由内到外衰减）。
     * v3（2026-10-07）：之前 {0.42,0.26,0.15} 再叠噪声(均值~0.4)×菲涅尔×径向稀薄，
     * 实际到屏只有 0.03~0.09 —— 亮背景上肉眼不可见（用户截图实锤）。
     * 加法混合要「抢肉眼」就得敢给大数，最亮处截到白没关系（火就该白热）。 */
    private static final float[] CORONA_HALO_ALPHA = {0.90f, 0.55f, 0.32f};
    /** 光柱 / 光晕外缘的硬上限（格）：结构内表面半径 15.49，留 0.5 余量不插笼子 */
    private static final float CORONA_OUTER_MAX = 15.0f;
    /**
     * 网格密度 —— 刻意压低（见类注释「性能」）。
     * 顶点数 = rings·sectors·4，噪声按四边形级评估、菲涅尔按顶点级评估，
     * 所以每帧的 sin 总量 ≈ rings·sectors·4（噪声）+ rings·sectors·4（菲涅尔去重后）。
     * v2：RINGS 提到 20（半球 10 环）—— 球放大后 12 环会让菲涅尔亮带只有 2~3 环，
     * 呈明显多边形锯齿；配合 haloShell 的「菲涅尔过小就跳过」剔除，
     * 实际提交的 quad 仍集中在可见环带内，总量与 v3 星空相当。
     */
    private static final int CORONA_RINGS = 20;
    private static final int CORONA_SECTORS = 48;

    /** 一次性诊断：渲染器确实被实例化并跑到了第一帧（静默失败排查用） */
    private boolean loggedFirstFrame = false;

    @Override
    public DynamicRenderType<EventHorizonMachine, EventHorizonRenderer> getType() {
        return TYPE;
    }

    @Override
    public void render(EventHorizonMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!machine.isFormed()) {
            return;
        }
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOGGER.info("[gtoecore] event horizon renderer is live at {}", machine.getPos());
        }
        float tick = machine.getOffsetTimer() + partialTick;
        boolean working = machine.getRecipeLogic().isWorking();
        // 工作 = 缓慢「呼吸」（±3%）；未工作 = 压暗到 42%（仍可见，确认机器成形了）
        float bright = working
                ? 0.97f + 0.03f * Mth.sin(tick * 0.05f)
                : 0.42f;

        Direction back = RelativeDirection.BACK.getRelative(
                machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());

        poseStack.pushPose();
        poseStack.translate(0.5 + back.getStepX() * CENTER_OFFSET, 0.5,
                0.5 + back.getStepZ() * CENTER_OFFSET);
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nrm = poseStack.last().normal();

        Vec3 center = Vec3.atCenterOf(machine.getPos())
                .add(back.getStepX() * CENTER_OFFSET, 0, back.getStepZ() * CENTER_OFFSET);
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 toCam = camPos.subtract(center);
        double dist = toCam.length();
        Vec3 f = dist < 1.0E-4 ? new Vec3(0, 0, 1) : toCam.scale(1.0 / dist);

        // ⚠ 提交顺序 = 绘制顺序：星空必须先画、主体后画 —— 星星全在 r=15 的壳上，
        // 相机在壳内时它们全部位于球体**背后**，先画才能被正确盖住
        // （反过来画 = 白色方块星星压在主体上，视频截图里满屏白点就是这个）。
        if (dist < R_SKY * 0.98) {
            renderStarfield(mat, buffer, tick, bright);
        }
        if (machine.getRenderMode() == EventHorizonMachine.MODE_CORONA) {
            renderCorona(mat, nrm, buffer, f, tick, bright);
        } else {
            renderBakedBlackhole(mat, nrm, buffer, f, bright);
        }

        poseStack.popPose();
    }

    // ------------------------------------------------------------------
    // 1. 烘焙黑洞：面向相机的公告板 + 倾角交叉淡化
    // ------------------------------------------------------------------
    private void renderBakedBlackhole(Matrix4f mat, Matrix3f nrm, MultiBufferSource buffer,
                                      Vec3 f, float bright) {
        // 相机仰角（相对盘轴 = 世界 Y）：贴帧集用它的绝对值
        float inclDeg = Math.abs((float) Math.toDegrees(Math.asin(Mth.clamp(f.y, -1.0f, 1.0f))));
        inclDeg = Math.min(inclDeg, FRAME_MAX_INCL - 0.01f);
        float fIdx = inclDeg / FRAME_INCL_STEP;
        int i0 = (int) fIdx;
        float frac = fIdx - i0;
        int i1 = Math.min(i0 + 1, FRAME_COUNT - 1);

        // 公告板正交基：right ⊥ 视线、尽量贴近世界水平；up2 = right × f 保持"图顶朝上"
        Vec3 worldUp = Math.abs(f.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = f.cross(worldUp).normalize();
        Vec3 up2 = right.cross(f).normalize();

        float half = BILLBOARD_HALF;
        float rx = (float) (right.x * half), ry = (float) (right.y * half), rz = (float) (right.z * half);
        float ux = (float) (up2.x * half), uy = (float) (up2.y * half), uz = (float) (up2.z * half);

        // 帧 i0 不透明打底；帧 i1 按交叉淡化系数叠加（半透明混合下恰好是线性插值）
        drawBakedQuad(mat, nrm, buffer, FRAME_TEX[i0], rx, ry, rz, ux, uy, uz, bright, 1.0f);
        if (frac > 0.01f && i1 != i0) {
            drawBakedQuad(mat, nrm, buffer, FRAME_TEX[i1], rx, ry, rz, ux, uy, uz, bright, frac);
        }
    }

    private void drawBakedQuad(Matrix4f mat, Matrix3f nrm, MultiBufferSource buffer,
                               ResourceLocation tex,
                               float rx, float ry, float rz, float ux, float uy, float uz,
                               float bright, float alpha) {
        // 原版 entityTranslucentEmissive：光影（Oculus/Iris）认识的原版类型，
        // 自发光 = 不受光照/雾颜色调制，顶点色即最终颜色
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(tex));
        putVertex(vc, mat, nrm, -rx + ux, -ry + uy, -rz + uz, bright, alpha, 0, 0);
        putVertex(vc, mat, nrm, rx + ux, ry + uy, rz + uz, bright, alpha, 1, 0);
        putVertex(vc, mat, nrm, rx - ux, ry - uy, rz - uz, bright, alpha, 1, 1);
        putVertex(vc, mat, nrm, -rx - ux, -ry - uy, -rz - uz, bright, alpha, 0, 1);
    }

    private static void putVertex(VertexConsumer vc, Matrix4f mat, Matrix3f nrm,
                                  float x, float y, float z,
                                  float bright, float alpha, int u, int v) {
        vc.vertex(mat, x, y, z)
                .color(bright, bright, bright, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(nrm, 0.0f, 1.0f, 0.0f)
                .endVertex();
    }

    // ------------------------------------------------------------------
    // 2. 日冕（模式 2）：真 3D 球体 —— 黑球 + 菲涅尔边缘光 + 湍流燃烧云 + 下缘光柱
    // ------------------------------------------------------------------

    /**
     * 纯 <b>3D 球面几何</b>的日食观感：不透明黑球打底（写深度）→ 紧贴边缘的
     * 橙白亮环 → 三层向外扩散的湍流燃烧云 → 下缘一道光柱。
     *
     * <p><b>为什么不用公告板：</b>用户明确「不要搞一个面对着玩家的」。
     * 球面几何从任何角度看都是球，且云层用透视投影天然生成径向丝缕，
     * 缩放也物理正确。</p>
     *
     * <p><b>绘制顺序（关键）：</b>黑球用
     * {@link RenderType#entityCutoutNoCull} —— 不透明、<b>写深度</b>、双面，
     * 所以先把球体画完，后续所有加法混合的光晕都会在深度测试里被球体正确遮挡，
     * 自然形成「黑球前的光晕被吃掉」的日食效果。光晕全部用
     * {@link RenderType#lightning()}（加法混合、<b>不写深度</b>），
     * 因此云层之间不会互相剔除、叠加出更亮的炽热感。</p>
     *
     * <p><b>不需要手动 endBatch：</b>{@code entity*} 是 fixed 渲染类型
     * （缓冲被缓存、不随切换冲刷），{@code lightning} 不是 ——
     * {@code getBuffer} 换类型时帧缓冲会<b>自动 endBatch 上一个类型</b>，
     * 于是「同类型连续写 = 合并成一批、换类型 = 立刻提交」，
     * 提交顺序就是绘制顺序。</p>
     */
    private void renderCorona(Matrix4f mat, Matrix3f nrm, MultiBufferSource buffer,
                              Vec3 f, float tick, float bright) {
        // 视线基（正交归一）：rx = 水平右向，uy = 图上方；辐条方向基于它们
        Vec3 worldUp = Math.abs(f.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 rx3 = f.cross(worldUp).normalize();
        Vec3 uy3 = rx3.cross(f).normalize();

        // 缓慢自转（周期 ~110 秒）+ 两档抖动，让"燃烧"是活的但不躁
        float spin = tick * 0.057f;
        float flick = 0.88f + 0.12f * Mth.sin(tick * 0.031f)
                + 0.06f * Mth.sin(tick * 0.117f + 1.7f);

        // 光晕专用的亮度补偿：未工作（bright=0.42）时黑洞公告板压到 0.42 没问题，
        // 但加法光晕要在亮背景上可见，0.77 的旧下限实测仍太暗 —— 抬到 0.91。
        // 只影响日冕，黑洞模式的明暗语义保持不变。
        float glowBright = 0.85f + 0.15f * bright;

        // ── 2a) 黑碗：只画玩家看不到的背面半球（用户点名的新实现）────────
        // 面向相机的正面被剔除，任何角度看都是朝里凹的碗口；
        // 碗内壁写深度 ⇒ 后续光晕在深度测试里被正确遮挡。
        VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEX));
        bowl(mat, nrm, solid, CORONA_BALL_R, f);

        // ── 2b) 碗口光子环 + 三层燃烧云（加法混合、不写深度）─────────────
        VertexConsumer glow = buffer.getBuffer(RenderType.lightning());

        // 碗口亮环：⟂ 视线平面的同心环带（白热核心 → 橙 → 暗红外晕）。
        // 光子环物理上就长在阴影轮廓上 = 从任何方向看都正对相机 ——
        // 画在这里永远清晰贴边，不再依赖菲涅尔在球面上"蹭"出细亮带。
        rimRing(mat, glow, CORONA_BALL_R, rx3, uy3, spin, glowBright, flick);
        // 燃烧云：三层由内到外、逐渐变暗变红、湍流更强
        for (int k = 0; k < CORONA_HALO_RF.length; k++) {
            float a = CORONA_HALO_ALPHA[k] * glowBright * flick;
            // 越外层越红（散热更彻底）；内层偏黄白贴近亮环
            float cr = 1.00f;
            float cg = Math.max(0.14f, 0.66f - 0.18f * k);
            float cb = Math.max(0.05f, 0.20f - 0.06f * k);
            haloShell(mat, glow, CORONA_BALL_R * CORONA_HALO_RF[k], rx3, uy3,
                    spin + k * 1.13f, a, cr, cg, cb, k, f);
        }

        // ── 2c) 下缘光柱（世界 -Y 方向，跟日食照片底部那道柱一致）─────────
        // ⚠ 光柱是"世界方向"的，不随相机仰角旋转 ⇒ 灭掉弹幕式各向辐射观感
        // ⚠ 柱底封顶在 CORONA_OUTER_MAX：球放大后若仍按 R×4.2 延伸会插进笼子格子
        float pillarAlpha = 0.32f * glowBright * flick;
        float pR = CORONA_BALL_R * 0.30f;
        float y0 = -CORONA_BALL_R * 1.04f, y1 = -CORONA_OUTER_MAX;
        quad(glow, mat, -pR, y0, 0.0f, pR, y0, 0.0f, pR * 0.5f, y1, 0.0f, -pR * 0.5f, y1, 0.0f,
                1.00f, 0.44f, 0.15f, pillarAlpha);
        quad(glow, mat, 0.0f, y0, -pR, 0.0f, y0, pR, 0.0f, y1, pR * 0.5f, 0.0f, y1, -pR * 0.5f,
                1.00f, 0.44f, 0.15f, pillarAlpha);
        // 光柱与球交接处的余晖（避免硬接）
        quad(glow, mat,
                -CORONA_BALL_R * 0.95f, -CORONA_BALL_R * 0.86f, 0.0f,
                CORONA_BALL_R * 0.95f, -CORONA_BALL_R * 0.86f, 0.0f,
                CORONA_BALL_R * 0.5f, -CORONA_OUTER_MAX * 0.92f, 0.0f,
                -CORONA_BALL_R * 0.5f, -CORONA_OUTER_MAX * 0.92f, 0.0f,
                1.00f, 0.50f, 0.18f, pillarAlpha * 0.45f);
    }

    /**
     * 碗口光子环 —— ⟂ 视线平面上的同心环带（参考星际穿越式光环）。
     *
     * <p><b>为什么永远正对相机不是"公告板偷懒"：</b>光子环长在黑洞阴影的
     * 轮廓上，物理事实就是从任何方向看都是一个正对相机的圆。环平面取
     * ⟂ 视线、圆心在球心，与碗口的几何轮廓（{@link #bowl}）完全重合。</p>
     *
     * <p>四段径向渐变（内→外）：渗入碗内的柔光 → 白热核心 → 橙 → 暗红外晕。
     * 每段两端顶点 alpha 不同，靠 GPU 插值免费得到径向渐变；角向湍流按
     * 角点评估一次、四段复用（相邻 quad 共享顶点 ⇒ 丝缕对齐、无缝），
     * 随 {@code spin} 缓慢流动。</p>
     */
    private static void rimRing(Matrix4f mat, VertexConsumer vc, float R,
                                Vec3 right3, Vec3 up3, float spin, float bright, float flick) {
        float rx = (float) right3.x, ry = (float) right3.y, rz = (float) right3.z;
        float ux = (float) up3.x, uy = (float) up3.y, uz = (float) up3.z;
        // 环是全画面最被盯着看的边缘，角向分辨率加倍（96 段），避免内缘呈多边形
        final int sect = CORONA_SECTORS * 2;
        // 角向湍流：每个角点评估一次，所有环带共享（径向"丝缕"对齐）
        float[] w = new float[sect + 1];
        for (int s = 0; s <= sect; s++) {
            float th = (float) (2.0 * Math.PI) * s / sect;
            w[s] = (0.74f + 0.16f * Mth.sin(th * 6.0f + spin * 2.1f)
                         + 0.10f * Mth.sin(th * 13.0f - spin * 3.7f)) * bright * flick;
        }
        // {内半径系数, 外半径系数, 内端 alpha, 外端 alpha, r, g, b}
        float[][] bands = {
                {0.94f, 1.00f, 0.04f, 0.45f, 1.00f, 0.62f, 0.30f},   // 渗入碗内的柔光
                {1.00f, 1.13f, 1.00f, 0.55f, 1.00f, 0.86f, 0.58f},   // 白热核心
                {1.13f, 1.28f, 0.55f, 0.20f, 1.00f, 0.48f, 0.18f},   // 橙
                {1.28f, 1.48f, 0.20f, 0.00f, 0.85f, 0.30f, 0.10f},   // 暗红外晕
        };
        for (float[] b : bands) {
            float r0 = R * b[0], r1 = R * b[1], a0 = b[2], a1 = b[3];
            float cr = b[4], cg = b[5], cb = b[6];
            for (int s = 0; s < sect; s++) {
                float p0 = (float) (2.0 * Math.PI) * s / sect;
                float p1 = (float) (2.0 * Math.PI) * (s + 1) / sect;
                // ⚠ lightning() 开背面剔除（反编译字节码确认：CompositeStateBuilder
                //   默认 CULL 且 lightning 未覆盖）—— 而 MC 的 BER 管线绕序约定与
                //   直觉相反，「右手法线朝向相机」的 quad 会被整批剔掉（v4~v6 的
                //   环和云隐形三重根因之一）。正反两个绕序各发一遍：
                //   剔除开着时恰好画一份，亮度不变，且对绕序约定免疫。
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r0, p0, a0 * w[s], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r0, p1, a0 * w[s + 1], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r1, p1, a1 * w[s + 1], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r1, p0, a1 * w[s], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r1, p0, a1 * w[s], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r1, p1, a1 * w[s + 1], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r0, p1, a0 * w[s + 1], cr, cg, cb);
                ringVert(vc, mat, rx, ry, rz, ux, uy, uz, r0, p0, a0 * w[s], cr, cg, cb);
            }
        }
    }

    /** 环带顶点：位置 =（rx·cosθ + uy·sinθ）× r，位于 ⟂ 视线、过球心的平面内。 */
    private static void ringVert(VertexConsumer vc, Matrix4f mat,
                                 float rx, float ry, float rz, float ux, float uy, float uz,
                                 float r, float ang, float a, float cr, float cg, float cb) {
        float ca = Mth.cos(ang), sa = Mth.sin(ang);
        vc.vertex(mat, (rx * ca + ux * sa) * r, (ry * ca + uy * sa) * r, (rz * ca + uz * sa) * r)
                .color(cr, cg, cb, Mth.clamp(a, 0.0f, 1.0f))
                .endVertex();
    }

    /** 法线是否朝向相机（背面剔除）。{@code f} 是"球→相机"的单位方向。 */
    private static boolean facingCamera(float[] dir, Vec3 f) {
        return dir[0] * f.x + dir[1] * f.y + dir[2] * f.z > 0.0;
    }

    /**
     * 一层向外扩散的湍流燃烧云（球壳 + 菲涅尔 × 多频噪声）。
     *
     * <p>噪声用「法线投影到视线基」的两维坐标 + 三个错开的角频率与层相位，
     * 生成<b>径向丝缕</b>的燃烧纹理；同一层内四个顶点复用一次噪声采样
     * （四边形级），既省 CPU、格状感又会被加法混合与模糊吃掉。</p>
     */
    private static void haloShell(Matrix4f mat, VertexConsumer vc, float R,
                                  Vec3 right3, Vec3 up3, float spin,
                                  float alpha, float cr, float cg, float cb, int layer, Vec3 f) {
        float rx = (float) right3.x, ry = (float) right3.y, rz = (float) right3.z;
        float ux = (float) up3.x, uy = (float) up3.y, uz = (float) up3.z;
        int rn = CORONA_RINGS / 2;
        for (int ri = 0; ri < rn; ri++) {
            float t0 = (float) Math.PI * ri / rn;
            float t1 = (float) Math.PI * (ri + 1) / rn;
            for (int si = 0; si < CORONA_SECTORS; si++) {
                float p0 = (float) (2.0 * Math.PI) * si / CORONA_SECTORS;
                float p1 = (float) (2.0 * Math.PI) * (si + 1) / CORONA_SECTORS;
                float[] v00 = onSphere(t0, p0);
                float[] v01 = onSphere(t0, p1);
                float[] v11 = onSphere(t1, p1);
                float[] v10 = onSphere(t1, p0);
                // 背面剔除（同亮环；背面的菲涅尔无意义且会画出"第二个环"）
                if (!facingCamera(v00, f)) {
                    continue;
                }
                // 菲涅尔按顶点算（它决定"贴着轮廓"的宽度，顶点级才平滑）；
                // 四个顶点全都在暗区（球冠中部）时整个 quad 不可见，直接跳过 ——
                // 既省 4 次端点写入，也省下面的噪声采样（4 次 sin）。
                // 菲涅尔按顶点算（它决定"贴着轮廓"的宽度，顶点级才平滑）。
                // ⚠ 幂是「宽↔可见」的旋钮：5=窄到看不见（吃过亏），1.5=宽到把
                //   黑球脸都盖黄。2.2 = 燃烧带贴着轮廓又有厚度，黑球仍是主体。
                float a00 = fresnel(v00, rx, ry, rz, ux, uy, uz, 2.2f);
                float a01 = fresnel(v01, rx, ry, rz, ux, uy, uz, 2.2f);
                float a11 = fresnel(v11, rx, ry, rz, ux, uy, uz, 2.2f);
                float a10 = fresnel(v10, rx, ry, rz, ux, uy, uz, 2.2f);
                float aMax = Math.max(Math.max(a00, a01), Math.max(a11, a10));
                if (aMax * alpha < 0.004f) {
                    continue;
                }
                // 四边形级噪声：取四顶点方向平均后评估一次（省 4x 三角函数）
                float[] mid = {(v00[0] + v01[0] + v11[0] + v10[0]) * 0.25f,
                               (v00[1] + v01[1] + v11[1] + v10[1]) * 0.25f,
                               (v00[2] + v01[2] + v11[2] + v10[2]) * 0.25f};
                float n = turb(mid, rx, ry, rz, ux, uy, uz, spin, layer);
                if (n < 0.01f) {
                    continue;
                }
                float k = n * alpha;
                // 正反两个绕序各发一遍（lightning 开背面剔除，见 rimRing 注释）
                glowVert(vc, mat, R, v00, k * a00, cr, cg, cb);
                glowVert(vc, mat, R, v01, k * a01, cr, cg, cb);
                glowVert(vc, mat, R, v11, k * a11, cr, cg, cb);
                glowVert(vc, mat, R, v10, k * a10, cr, cg, cb);
                glowVert(vc, mat, R, v00, k * a00, cr, cg, cb);
                glowVert(vc, mat, R, v10, k * a10, cr, cg, cb);
                glowVert(vc, mat, R, v11, k * a11, cr, cg, cb);
                glowVert(vc, mat, R, v01, k * a01, cr, cg, cb);
            }
        }
    }

    /** 球面参数 → 单位方向（t ∈ [0,π] 极角，p ∈ [0,2π] 方位角）。 */
    private static float[] onSphere(float t, float p) {
        float st = Mth.sin(t);
        return new float[]{st * Mth.cos(p), Mth.cos(t), st * Mth.sin(p)};
    }

    /**
     * 菲涅尔遮罩：{@code (1-|n·v|)^power}。
     * 法线正对相机（球心方向）→ 0；法线垂直于视线（轮廓那一圈）→ 1。
     * 这是"只有边缘发光"的物理本质，也是晕圈看起来"贴着球"的原因。
     */
    private static float fresnel(float[] dir, float rx, float ry, float rz,
                                 float ux, float uy, float uz, float power) {
        float sx = dir[0] * rx + dir[1] * ry + dir[2] * rz;
        float sy = dir[0] * ux + dir[1] * uy + dir[2] * uz;
        float nz = Mth.sqrt(Mth.clamp(1.0f - (sx * sx + sy * sy), 0.0f, 1.0f));
        float f = 1.0f - nz;
        // 小指数手算即可，避免 Math.pow
        int p = (int) power;
        float out = 1.0f;
        for (int i = 0; i < p; i++) {
            out *= f;
        }
        float frac = power - p;
        if (frac > 0.001f) {
            out *= (float) Math.pow(f, frac);
        }
        return out;
    }

    /**
     * 湍流强度（不含菲涅尔）—— 多频正弦叠加模拟 FBM。
     *
     * <p>三个频率的<b>角频率互质</b>（5/11/19 与层号的偏移），叠加后不会
     * 周期对齐成规则花纹；{@code spin} 让云绕视线缓慢自转。</p>
     */
    private static float turb(float[] dir, float rx, float ry, float rz,
                              float ux, float uy, float uz, float spin, int layer) {
        float sx = dir[0] * rx + dir[1] * ry + dir[2] * rz;
        float sy = dir[0] * ux + dir[1] * uy + dir[2] * uz;
        float th = (float) Math.atan2(sy, sx) + spin;
        float rad = Mth.sqrt(sx * sx + sy * sy);
        float n = 0.50f
                + 0.34f * Mth.sin(th * (5.0f + layer * 3.0f) + layer * 2.1f)
                + 0.22f * Mth.sin(th * (11.0f + layer * 5.0f) - spin * 2.7f + rad * 6.0f)
                + 0.14f * Mth.sin(th * 19.0f + rad * 11.0f + layer * 0.7f);
        n = Mth.clamp(n, 0.0f, 1.0f);
        // 径向稀薄：越靠外越淡（云有"边界"而不是无限扩散）。
        // v3：旧下限 0.20 把轮廓处（云最该亮的地方）压到 0.3 以下，是"几乎看不见"
        // 的主力帮凶；抬到 0.45 并放缓斜率。
        float radial = Mth.clamp(1.0f - rad * 0.60f, 0.35f, 1.0f);
        return n * radial;
    }

    /** 光晕顶点：位置 = 球壳点 × R；顶点色 = 颜色 × 强度（加法混合下即亮度）。
     *
     * <p>⚠ <b>绝对不要在这里预乘 alpha</b>（2026-10-07 修的隐藏 bug）：
     * {@code lightning} 的混合公式是 {@code (SRC_ALPHA, ONE)} —— 最终贡献 =
     * 顶点色 × 顶点 alpha。旧写法 {@code color(cr*a, cg*a, cb*a, a)} 会把亮度
     * 衰减成 {@code a²}：a=0.2 时只剩 4%，在草地/天空这种亮背景上整个光晕
     * 隐形（离线预演的背景是纯黑太空，把这个问题完全掩盖了）。
     * alpha 只进 alpha 通道，由混合公式负责乘。</p>
     */
    private static void glowVert(VertexConsumer vc, Matrix4f mat,
                                 float R, float[] dir, float k, float cr, float cg, float cb) {
        float a = Mth.clamp(k, 0.0f, 1.0f);
        vc.vertex(mat, dir[0] * R, dir[1] * R, dir[2] * R)
                .color(cr, cg, cb, a)
                .endVertex();
    }

    /**
     * 黑碗（用户点名的实现）：<b>只渲染玩家原本看不到的背面半球</b> ——
     * 面向相机的正面被剔除，玩家从任何角度看都是朝里凹的碗口。
     *
     * <p>相比实心黑球：碗口内边缘 = 真实的几何轮廓，光环（{@link #rimRing}）
     * 画在这里永远清晰贴边；内壁近黑、碗缘留一丝暖色反光（环的光"渗"进
     * 内壁），让"凹进去"有体积感而不是一个平面圆片。</p>
     *
     * <p>⚠ 只剔除「4 顶点<b>全</b>朝前」的 quad —— 跨越轮廓的 quad 保留，
     * 否则碗缘会出现漏出背景的针脚缝隙。</p>
     *
     * <p>顶点色接近纯黑但<b>不是 0</b> —— 全 0 会在深度边缘出现硬锯齿。</p>
     */
    private static void bowl(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, float R, Vec3 f) {
        int rings = 26, sectors = 52;
        for (int ri = 0; ri < rings; ri++) {
            float t0 = (float) Math.PI * ri / rings;
            float t1 = (float) Math.PI * (ri + 1) / rings;
            for (int si = 0; si < sectors; si++) {
                float p0 = (float) (2.0 * Math.PI) * si / sectors;
                float p1 = (float) (2.0 * Math.PI) * (si + 1) / sectors;
                float[] v00 = onSphere(t0, p0);
                float[] v01 = onSphere(t0, p1);
                float[] v11 = onSphere(t1, p1);
                float[] v10 = onSphere(t1, p0);
                if (facingCamera(v00, f) && facingCamera(v01, f)
                        && facingCamera(v11, f) && facingCamera(v10, f)) {
                    continue;   // 整片正对相机的正面：剔除 —— 玩家看进碗内
                }
                // 碗缘反光：中点在背面、且越贴近轮廓（|n·f|→0）越暖越亮。
                // ⚠ 只给「中点在背面」的 quad：跨轮廓的正面切片若也染色，
                //   近距离看会变成一大片棕色月牙伪影（2026-10-07 截图实锤）。
                float[] mid = {(v00[0] + v01[0] + v11[0] + v10[0]) * 0.25f,
                               (v00[1] + v01[1] + v11[1] + v10[1]) * 0.25f,
                               (v00[2] + v01[2] + v11[2] + v10[2]) * 0.25f};
                float midf = mid[0] * (float) f.x + mid[1] * (float) f.y + mid[2] * (float) f.z;
                float rim = midf < 0.0f
                        ? (float) Math.pow(Mth.clamp(1.0f + midf, 0.0f, 1.0f), 5.0f)
                        : 0.0f;
                float cr = 0.02f + 0.060f * rim;
                float cg = 0.02f + 0.028f * rim;
                float cb = 0.028f + 0.010f * rim;
                solidVert(mat, nrm, vc, R, v00, cr, cg, cb);
                solidVert(mat, nrm, vc, R, v01, cr, cg, cb);
                solidVert(mat, nrm, vc, R, v11, cr, cg, cb);
                solidVert(mat, nrm, vc, R, v10, cr, cg, cb);
            }
        }
    }

    private static void solidVert(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, float R, float[] dir,
                                  float cr, float cg, float cb) {
        // entityCutoutNoCull 的顶点链：pos → color → uv → overlay → light → normal
        vc.vertex(mat, dir[0] * R, dir[1] * R, dir[2] * R)
                .color(cr, cg, cb, 1.0f)
                .uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(nrm, dir[0], dir[1], dir[2])
                .endVertex();
    }

    // ------------------------------------------------------------------
    // 3. 星空壳（只在相机进入结构内部时渲染）
    // ------------------------------------------------------------------
    private void renderStarfield(Matrix4f mat, MultiBufferSource buffer, float tick, float bright) {
        // 原版 lightning：加法混合、POSITION_COLOR，光影同样认识
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        RandomSource rnd = RandomSource.create(0x5EED10B7L);
        for (int i = 0; i < STAR_COUNT; i++) {
            double u = rnd.nextDouble() * 2.0 - 1.0;
            double phi = rnd.nextDouble() * Math.PI * 2.0;
            double s = Math.sqrt(Math.max(0.0, 1.0 - u * u));
            float nx = (float) (s * Math.cos(phi));
            float ny = (float) u;
            float nz = (float) (s * Math.sin(phi));
            // 切向正交基
            float ux = 0.0f, uy = 1.0f, uz = 0.0f;
            if (Math.abs(ny) > 0.9f) {
                ux = 1.0f;
                uy = 0.0f;
            }
            float rx = uy * nz - uz * ny;
            float ry = uz * nx - ux * nz;
            float rz = ux * ny - uy * nx;
            float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl < 1.0E-5f) {
                continue;
            }
            rx /= rl;
            ry /= rl;
            rz /= rl;
            // up2 = n × right；不除掉 |up2| 的话极点附近星星会被压扁成细条
            float wx = ny * rz - nz * ry;
            float wy = nz * rx - nx * rz;
            float wz = nx * ry - ny * rx;
            wx /= rl;
            wy /= rl;
            wz /= rl;

            float size = 0.05f + rnd.nextFloat() * 0.055f;   // 0.10~0.17 在近景是大白方块
            float starBright = 0.30f + rnd.nextFloat() * 0.45f;
            // 轻微闪烁（固定相位，避免每帧乱跳）
            float twinkle = 0.78f + 0.22f * Mth.sin(tick * 0.035f + i * 1.71f);
            float alpha = starBright * twinkle * bright * 0.65f;
            // 偏色：多数冷白，少数暖黄
            float tint = rnd.nextFloat();
            float cr = tint > 0.85f ? 1.0f : 0.84f;
            float cg = tint > 0.85f ? 0.92f : 0.88f;
            float cb = tint > 0.85f ? 0.78f : 1.0f;

            float cx = nx * R_SKY, cy = ny * R_SKY, cz = nz * R_SKY;
            quad(vc, mat,
                    cx - rx * size + wx * size, cy - ry * size + wy * size, cz - rz * size + wz * size,
                    cx + rx * size + wx * size, cy + ry * size + wy * size, cz + rz * size + wz * size,
                    cx + rx * size - wx * size, cy + ry * size - wy * size, cz + rz * size - wz * size,
                    cx - rx * size - wx * size, cy - ry * size - wy * size, cz - rz * size - wz * size,
                    cr, cg, cb, alpha);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------
    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        // 正反两个绕序各发一遍（lightning 开背面剔除，见 rimRing 注释）——
        // 剔除开着时恰好画一份，从任何一侧看都可见
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).endVertex();
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean shouldRender(EventHorizonMachine machine, Vec3 cameraPos) {
        return machine.isFormed()
                && cameraPos.distanceToSqr(Vec3.atCenterOf(machine.getPos())) < 16384.0;
    }

    @Override
    public boolean shouldRenderOffScreen(EventHorizonMachine machine) {
        return machine.isFormed();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(EventHorizonMachine machine) {
        // 特效以控制器为中心向 BACK 方向铺开 17 格，日冕光晕外缘再 +14.75，
        // 包围盒半径必须 ≥ 32，否则光晕外圈会被剔除时整块消失
        return new AABB(machine.getPos()).inflate(34.0);
    }
}
