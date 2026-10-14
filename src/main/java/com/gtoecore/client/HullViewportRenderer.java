package com.gtoecore.client;

import com.gtoecore.GTNCore;
import com.gtoecore.station.HullViewportBlock;
import com.gtoecore.station.HullViewportBlockEntity;
import com.gtoecore.station.PanelSkin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * 全周天舱壁渲染器 —— 动态补绘「朝内」那一面，实现变透明动画，并给整片舱壁自动包一圈外框。
 *
 * <p>方块模型永久省略朝内面（见 {@link HullViewportBlock}），这一面完全由本渲染器负责：
 * 透明度 = 1 - 进度，因此</p>
 * <ul>
 *   <li>进度 0：该面完全不透明 → 看上去就是一堵实心舱壁；</li>
 *   <li>进度 0~1：半透明叠加在<b>已经画好的舱外世界</b>之上 → 半透明时能看见背景；</li>
 *   <li>进度 1：不画 → 完全看穿。</li>
 * </ul>
 *
 * <p>明暗变化：过渡过程中按进度做一次「能量涌动」式的亮度脉动
 * （中间暗下去、带回闪，两端回到正常亮度），让打开/关闭有仪式感。</p>
 *
 * <p>用 {@code entityTranslucent}（NO_CULL、NEW_ENTITY 格式、按贴图路径直接取图）
 * 而不是 {@code translucent}（BLOCK_SHEET 图集）：前者 UV 用 0~1，且不剔除背面 ——
 * 从舱外看时这一面会被前面那层不透明的外壁用深度测试挡住，不会干扰外观。</p>
 *
 * <h2>面板皮肤（变种）</h2>
 * <p>贴图由方块自身的 {@link PanelSkin} 决定（蜂窝 / 星空 / 警示 / 电路 / 装甲…），
 * 经 {@link #skinType(PanelSkin)} 缓存为 RenderType。皮肤只影响关闭态这一面；
 * 开启后本渲染器不再画它，所以各变种只在「无信号（闭合）」时看得出区别。</p>
 *
 * <h2>动态皮肤（帧图集 + 交叉淡化）</h2>
 * <p>{@link PanelSkin#frames()} &gt; 1 的皮肤，贴图是<b>纵向堆叠</b>的帧图集
 * （每帧等高，从上到下依次排列）。选帧不用「整数帧硬切」——那样 8 帧就只有 8 级台阶，
 * 看起来一格一格跳；而是按<b>连续时间</b>取相邻两帧 A/B 与混合系数 blend，
 * 把两帧叠画两遍：</p>
 * <ul>
 *   <li>第一遍：帧 A，alpha = 面本身的不透明度（<b>不透明</b>打底，挡住身后世界）；</li>
 *   <li>第二遍：帧 B，alpha = 面不透明度 × blend，叠在第一遍之上。</li>
 * </ul>
 * <p>因为第一遍是满的，混合结果精确等于 {@code 帧A×(1-blend) + 帧B×blend}
 * —— 真正的线性插值，动画是连续的。若两遍都按同一系数打折，背景色会掺进来，
 * 面板会「透」；而第二遍若不乘面不透明度，开合淡出的末尾会残留下一帧不消失。</p>
 *
 * <p>连续时间用 {@code level.getGameTime() + partialTick}：前者每刻才 +1，
 * 后者是刻内的亚刻插值，两者相加才平滑到渲染帧率。</p>
 *
 * <h2>连接纹理（图案）</h2>
 * <p>UV 不按「每方块 0~1」取，而是按方块的世界坐标对 {@link PanelSkin#period()} 取模，
 * 于是相邻方块拼出的图案严丝合缝连成一片，看不出方块边界。周期是 per-skin 的：
 * 默认 6 格，梗图那类需要看清内容的皮肤会调到 5（一张图占 5 格宽）。</p>
 *
 * <h2>⚠ 图案朝向（曾经全错，2026-10-06 修）</h2>
 * <p>u/v 走哪个世界轴、往哪个方向，由 {@link #UV_DIRS} 决定，规则是
 * 「<b>观者从这一面看向它时，图案正立且不镜像</b>」，与<b>原版方块模型 / 原版的画</b>一致。
 * 曾经的写法把 u/v 直接绑在四边形边的顺序上，导致六个朝向全都错位：
 * 南北向墙面上下颠倒、北东向墙面转 90°、地面也转 90° —— 对称图案（蜂窝/电路/迷彩）
 * 看不出来，照片类的梗图一上墙就露馅。改朝向只动 {@link #UV_DIRS} 一张表。</p>
 *
 * <h2>自动外框（参考「无缝玻璃」的边框逻辑）</h2>
 * <p>朝内面本身会沿<b>这片舱壁拼成的面的边缘</b>画一圈框：对四条边各查一次邻居，
 * 只有「邻居不是同朝向的舱壁」时才画 —— 也就是面在此处断开的地方。
 * 面内部相邻方块之间不画，所以只有最外圈有框，跟方块模型外侧面用的
 * {@link #FRAME_TEXTURE} 是同一张图，内外框在棱上接得上。</p>
 *
 * <p>框的纹理：沿边方向复用与世界坐标取模的 UV（每 {@link #FRAME_PERIOD} 格一轮，框上的
 * 铆钉因此每 6 格出现一次，并保证跨方块连续）；跨边方向取贴图靠外缘的
 * {@link #BORDER} 那一条，0 = 面板外缘。</p>
 *
 * <p>框是「结构件」不是「面板材料」：面板开合时框<b>不跟着淡出</b>，
 * 这样全开时留下的是一圈干净的窗框（和模型外侧面一样，本来就不受 open 影响）。
 * 若希望它也一起淡出，把 {@code FRAME_ALPHA} 改成 {@code alpha} 即可。</p>
 *
 * <p><b>⚠ 框与面绝不能重叠：</b>两者都躺在同一个平面（法线方向同一个常量坐标）上，
 * 重叠会产生共面 z-fighting —— 同一像素处两个四边形的深度插值舍入不同，谁通过
 * 深度测试按像素随机，看上去就是「框和中心图案重叠并闪烁」。所以<b>有框的那条边上，
 * 面会内缩 {@link #BORDER}</b>，两者共享边界、零重叠（没框的边仍是 0~1，图案严丝合缝）。</p>
 *
 * <h2>⚠ 为什么必须在这里「立刻画」，不能延后到后面的渲染阶段</h2>
 * <p>有些 mod 会在 {@code RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES} 做整屏后处理合成，
 * 典型是 {@code shadered} 的「天空盒方块」：它把天空盒渲进离屏 FBO，然后用
 * {@code depth_discarder.fsh} 逐像素和主目标的深度做比较，再整屏贴回：</p>
 * <pre>
 * MainDepth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();  // ← 实时主深度
 * if (mainDepth.r &lt;= skyboxDepth.r) { fragColor = vec4(0,0,0,0); gl_FragDepth = mainDepth.r; }
 * else                              { fragColor = skybox;      gl_FragDepth = skyboxDepth.r; }
 * </pre>
 * <p>关键点：<b>它会写 gl_FragDepth</b>。所以只要面板几何体在合成那一刻还没进入主目标的
 * 深度缓冲，天空盒就会判定自己「更近」，把天空盒颜色<b>和天空盒深度</b>一起写进去；
 * 面板随后再画就会被深度测试判负 → 整块面板消失，看上去就是「被天空盒遮蔽」。</p>
 *
 * <p>方块实体的绘制阶段<b>严格早于</b> {@code AFTER_BLOCK_ENTITIES}，因此这里绝不能用
 * 「先登记、等后面某个 Stage 再统一 flush」的写法（那样延迟缓冲区直到很晚才 endBatch，
 * 合成时主深度里是空的）。唯一可靠的做法：在方块实体阶段就用
 * {@link #immediateBuffers()} + {@code endBatch(...)} 把几何体<b>立刻</b>画进主渲染目标，
 * 让深度在合成之前就位。</p>
 *
 * <p>代价是每个面板一次 draw call；对本整合包的机库尺度（几百块）完全可接受，
 * 换来的是「面板可见性 + 正确遮挡」都成立。</p>
 */
public class HullViewportRenderer implements BlockEntityRenderer<HullViewportBlockEntity> {

    /**
     * 面板皮肤 → RenderType 缓存（见 {@link PanelSkin}）。
     *
     * <p>{@code RenderType.entityTranslucent(...)} 每次调用都会新建对象并进入 RenderType
     * 的内部缓存表，逐帧新建是浪费，所以按皮肤缓存一份。变种只换贴图，
     * 其余参数（NO_CULL、NEW_ENTITY 顶点格式、按贴图路径直接取图）完全一致。</p>
     */
    private static final Map<PanelSkin, RenderType> SKIN_TYPES = new EnumMap<>(PanelSkin.class);

    private static RenderType skinType(PanelSkin skin) {
        return SKIN_TYPES.computeIfAbsent(skin, s -> RenderType.entityTranslucent(
                new ResourceLocation(GTNCore.MODID,
                        "textures/block/station/" + s.textureName() + ".png")));
    }

    /** 外框贴图：与方块模型 `hull_viewport_inner_*.json` 外侧面用的是同一张，棱上接得上。 */
    private static final ResourceLocation FRAME_TEXTURE =
            new ResourceLocation(GTNCore.MODID, "textures/block/station/hull_viewport_frame.png");

    private static final RenderType FRAME_TYPE = RenderType.entityTranslucent(FRAME_TEXTURE);

    /** 外框宽度（格）。取贴图 16px 里靠外缘的 3 行。 */
    private static final float BORDER = 3.0F / 16.0F;

    /**
     * 即时绘制缓冲：自带一个 {@link net.minecraft.client.renderer.MultiBufferSource.BufferSource}，
     * 写入后用 {@code endBatch(...)} 立刻上传绘制（会走 RenderType 的
     * setupRenderState / draw / clearRenderState，等价于方块实体阶段正常的批量绘制，
     * 只是不会等到后面才 flush）。
     *
     * <p>用独立的 builder 而不是共享的 {@code renderBuffers().bufferSource()}，
     * 避免把别的方块实体的批量数据一起提前冲掉。</p>
     *
     * <p>⚠ 惰性初始化：{@code Tesselator.getInstance()} 内部有
     * {@code RenderSystem.assertOnGameThreadOrInit()}，而这个类可能在非渲染线程被首次加载
     * （BER 注册/资源重载），放静态字段里初始化会直接炸。{@code render()} 必定在渲染线程调用。</p>
     */
    private static MultiBufferSource.BufferSource immediate;

    private static MultiBufferSource.BufferSource immediateBuffers() {
        MultiBufferSource.BufferSource b = immediate;
        if (b == null) {
            b = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            immediate = b;
        }
        return b;
    }

    /** 贴到方块边界内侧一点点，避免与外壁共面 */
    private static final float LO = 0.001F;
    private static final float HI = 0.999F;

    /** 每个朝向的四边形四角（局部 0~1），逆时针朝外 */
    private static final float[][][] CORNERS = {
            // DOWN
            {{0, LO, 0}, {1, LO, 0}, {1, LO, 1}, {0, LO, 1}},
            // UP
            {{0, HI, 0}, {0, HI, 1}, {1, HI, 1}, {1, HI, 0}},
            // NORTH
            {{0, 0, LO}, {0, 1, LO}, {1, 1, LO}, {1, 0, LO}},
            // SOUTH
            {{0, 0, HI}, {1, 0, HI}, {1, 1, HI}, {0, 1, HI}},
            // WEST
            {{LO, 0, 0}, {LO, 0, 1}, {LO, 1, 1}, {LO, 1, 0}},
            // EAST
            {{HI, 0, 0}, {HI, 1, 0}, {HI, 1, 1}, {HI, 0, 1}},
    };

    /**
     * <b>外框</b>贴图的世界周期（格）：框上的铆钉每 6 格一颗。
     *
     * <p>⚠ 这跟<b>面板图案</b>的周期不是一回事 —— 后者是 per-skin 的
     * （{@link PanelSkin#period()}，默认也是 6，但梗图那种需要看清内容的皮肤会调到 5）。
     * 框是结构件，不管皮肤怎么换、图案多大，铆钉的疏密都该保持一致，
     * 所以这里用固定常量，不跟着皮肤走。</p>
     */
    private static final int FRAME_PERIOD = 6;

    /**
     * 动态皮肤的「每帧边长」（px）。动态贴图 = {@code 384} 宽 × {@code 384 × frames} 高，
     * 纵向依次堆叠各帧（见 {@link PanelSkin#frames()}）。必须与
     * {@code tools/gen_hull_panel_skins.py} 的 {@code ANIM_FRAME_SIZE} 一致。
     *
     * <p>只用于计算「帧内半纹素内缩」（防止相邻帧在线性过滤下互相渗色），
     * 所以即使两边数值不完全一致，也只会让内缩量略有偏差，不会错位。</p>
     */
    private static final float ANIM_FRAME_PX = 384.0F;

    /** 第二遍（下一帧）的最小混合量，低于此值直接跳过，省一次绘制。 */
    private static final float BLEND_EPSILON = 1.0E-3F;

    /**
     * 每个内面朝向的「贴图 u 轴 / v 轴」——决定图案在墙上/地上是正立还是转的。
     *
     * <p>编码 {@code {u轴, u符号, v轴, v符号}}，轴 0=X / 1=Y / 2=Z；
     * u 随 {@code u符号 × 世界坐标(u轴)} 增大，v（贴图向下）随 {@code v符号 × 世界坐标(v轴)} 增大。</p>
     *
     * <h3>⚠ 这张表必须按「观者视角正立」来定，不能图省事绑在四边形边的顺序上</h3>
     * <p>以前是把 u/v 直接绑到 {@link #CORNERS} 的边顺序（第 0→1 个顶点沿 u），
     * 对蜂窝/电路/迷彩那种近似对称的图案看不出问题，但**六个朝向其实全是错的**：
     * 南北向的墙面上下颠倒、北东向的墙面转了 90°、地板上也转 90°。
     * 梗图（照片）一上墙就露馅 —— 会看到画面转着、或者站着的人倒过来。</p>
     *
     * <p>正确规则取自原版「画」的顶点映射（{@code PaintingRenderer.renderPainting}）：
     * 局部 {@code +Z} 是正面法线，正面上 {@code u 沿 +X_local}、{@code v 沿 -Y_local}，
     * 再按各朝向把局部轴换到世界轴。换算结果是：</p>
     * <ul>
     *   <li><b>四个墙面</b>：v 一律沿 <b>-Y</b>（贴图上方 = 世界 +Y ⇒ 图案正立）；
     *       u 沿「观者的右手方向」：法线朝南 +X、朝北 -X、朝西 +Z、朝东 -Z。</li>
     *   <li><b>朝上的面</b>（地面，观者在上方俯视）：u 沿 +X、v 沿 +Z。</li>
     *   <li><b>朝下的面</b>（天花板，观者在下方仰视）：u 沿 +X、v 沿 -Z
     *       —— 仰视看到的天花板本就与俯视地板上下相反。</li>
     * </ul>
     *
     * <p>顺序与 {@link Direction#ordinal()} 一致：DOWN, UP, NORTH, SOUTH, WEST, EAST。</p>
     */
    private static final int[][] UV_DIRS = {
            /* DOWN  */ {0, +1, 2, -1},
            /* UP    */ {0, +1, 2, +1},
            /* NORTH */ {0, -1, 1, -1},
            /* SOUTH */ {0, +1, 1, -1},
            /* WEST  */ {2, +1, 1, -1},
            /* EAST  */ {2, -1, 1, -1},
    };

    private static int worldAxis(BlockPos pos, int axis) {
        if (axis == 0) {
            return pos.getX();
        }
        if (axis == 1) {
            return pos.getY();
        }
        return pos.getZ();
    }

    /** 世界轴下标 + 正负 → 方向（与 {@link #UV_DIRS} 的 0=X,1=Y,2=Z 对应）。 */
    private static Direction axisDir(int axis, boolean positive) {
        if (axis == 0) {
            return positive ? Direction.EAST : Direction.WEST;
        }
        if (axis == 1) {
            return positive ? Direction.UP : Direction.DOWN;
        }
        return positive ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * 邻居是不是「同一片舱壁面」上的一块（决定这一侧画不画外框）。
     *
     * @param six 本方块是不是六面舱壁：六面版<b>不看 INNER</b>（它的 INNER 只是沿用基类的
     *            占位属性，放置朝向各不相同，比了会让整面墙每个方块都长出外框）
     */
    private static boolean isSamePanel(Level level, BlockPos pos, Direction inner, boolean six) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof HullViewportBlock panel)) {
            return false;
        }
        return six ? panel.sixSided() : s.getValue(HullViewportBlock.INNER) == inner;
    }

    @Override
    public void render(HullViewportBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HullViewportBlock viewport)) {
            return;
        }
        if (viewport.sixSided()) {
            // 六面舱壁已静态化（C 档，2026-10-07）：外观完全由静态烘焙模型提供，
            // BER 一根线都不画。这个分支只为兜住旧存档里已写进 NBT 的遗留方块实体
            // （区块加载时仍会反序列化出 BE），新放置的六面舱壁根本不会到这里。
            return;
        }
        Direction inner = state.getValue(HullViewportBlock.INNER);
        // 皮肤只决定关闭态这一面用哪张贴图；开启后不画，因此皮肤只在关闭态可见。
        PanelSkin skin = viewport.skin();
        RenderType faceType = skinType(skin);

        // ── 动态皮肤：贴图是纵向堆叠的帧图集，取相邻两帧做「交叉淡化」 ──────────
        // 不再硬切帧（那样 8 帧就是 8 级台阶），而是按**连续时间**取相邻两帧 A/B 与
        // 混合系数 blend ∈ [0,1)，把两帧都画出来叠在一起 —— 见下方 faceQuad 的两遍调用。
        // 时间用 gameTime + partialTick：前者每刻才 +1，后者是刻内的亚刻插值，
        // 加起来才是连续的，动画因此跟得上渲染帧率（不是 20 fps 的台阶）。
        // 静态皮肤（frames == 1）不插值，vBase/vScale 退化成 0/1，
        // 现有 15 种皮肤的行为**一个像素都不变**。
        int frames = skin.frames();
        int frameA = 0;
        int frameB = 0;
        float blend = 0.0F;
        float vScale = 1.0F;
        float vBaseA = 0.0F;
        float vBaseB = 0.0F;
        if (frames > 1) {
            // ⚠ 用 double：gameTime 是 long，长时间游玩会累加到千万刻级，
            // 此时 float（24 位尾数）的刻度精度只剩不到 1 刻，帧号与 blend 都会抖。
            double ft = (level.getGameTime() + (double) partialTick) / skin.frameTicks();
            int base = Mth.floor(ft);
            blend = (float) (ft - base);
            frameA = Math.floorMod(base, frames);
            frameB = Math.floorMod(base + 1, frames);
            // 帧内上下各内缩半纹素：不让采样点落到帧与帧的交界上，
            // 免得线性过滤把邻帧的像素混进来。
            float inset = 0.5F / (frames * ANIM_FRAME_PX);
            vScale = 1.0F / frames - 2.0F * inset;
            vBaseA = frameA / (float) frames + inset;
            vBaseB = frameB / (float) frames + inset;
        }

        // ── 画几个面？ ────────────────────────────────────────────────────────
        // 普通舱壁 = 单向舷窗：只画朝内那一个面（静态模型省略了它）。
        // 六面舱壁：静态模型为空，六个面全靠 BER 画 —— 六个面都是面板纹理的实心墙块。
        boolean six = viewport.sixSided();
        Direction[] faces = six ? Direction.values() : new Direction[]{inner};
        boolean anyFace = false;
        boolean anyFrame = false;

        // 方块实体阶段的 pose 已经包含「相机姿态 × (方块坐标 - 相机坐标)」，
        // 所以这里直接用 pose 矩阵配局部坐标 —— 与其它方块实体渲染器一致。
        // ⚠ 矩阵与缓冲是「整块共用」的，必须在面的循环外面取：六个面写进同一批顶点，
        // 循环结束统一 endBatch 一次（见文件末尾）。
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource.BufferSource imm = immediateBuffers();

        for (Direction face : faces) {
        if (six && !faceVisible(level, pos, face)) {
            continue;   // 对着实心邻居的面不画：内部面画出来会与邻居方块互相穿插
        }
        int ord = face.ordinal();
        int[] uvDirs = UV_DIRS[ord];
        int axisU = uvDirs[0];
        int signU = uvDirs[1];
        int axisV = uvDirs[2];
        int signV = uvDirs[3];
        // 图案周期是 per-skin 的：默认 6 格，梗图那类需要看清内容的皮肤会调到 5。
        float period = skin.period();
        // 贴图 u/v 沿世界坐标走，乘 sign 让纹理方向符合「观者视角正立」（见 UV_DIRS）。
        // floorMod 吃负数没问题（结果恒非负），所以 -worldX 这种写法是安全的。
        float u0 = Math.floorMod(signU * worldAxis(pos, axisU), (int) period) / period;
        float v0 = Math.floorMod(signV * worldAxis(pos, axisV), (int) period) / period;
        // 外框**不跟着贴图方向走**：它是结构件，沿边方向一律按正的世界坐标取模，
        // 这样铆钉跨方块连续、疏密也不随皮肤或朝向变。
        float frameU0 = Math.floorMod(worldAxis(pos, axisU), FRAME_PERIOD) / (float) FRAME_PERIOD;
        float frameV0 = Math.floorMod(worldAxis(pos, axisV), FRAME_PERIOD) / (float) FRAME_PERIOD;
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        // ── 0) 邻居判定 + 面的内缩量（必须先算，见下） ────────────────────────
        // 框与面躺在**同一个平面**上（法线方向都是 LO/HI 那个常量），
        // 若框压在面上 → 两个共面四边形在同一像素上的深度插值舍入不同，
        // 谁通过深度测试按像素随机 → 表现就是「框和中心图案重叠、闪烁」。
        // 所以有框的那条边上，面必须内缩 BORDER，两者各占其位、零重叠。
        //
        // ⚠ 这四个判定问的是「**局部几何轴**的 0 / 1 端有没有框」（外框画在几何边上），
        //   跟 UV_DIRS 里的 u/v 符号无关 —— 后者只决定贴图怎么贴，不改几何。
        //   所以这里一律用正的 axisDir(axis, false/true)，别跟 sign 搞混。
        boolean uNeg = !isSamePanel(level, pos.relative(axisDir(axisU, false)), face, six);
        boolean uPos = !isSamePanel(level, pos.relative(axisDir(axisU, true)), face, six);
        boolean vNeg = !isSamePanel(level, pos.relative(axisDir(axisV, false)), face, six);
        boolean vPos = !isSamePanel(level, pos.relative(axisDir(axisV, true)), face, six);
        boolean anyBorder = uNeg || uPos || vNeg || vPos;

        float lu0 = uNeg ? BORDER : 0.0F;
        float lu1 = uPos ? 1.0F - BORDER : 1.0F;
        float lv0 = vNeg ? BORDER : 0.0F;
        float lv1 = vPos ? 1.0F - BORDER : 1.0F;

        // ── 1) 连接纹理面（受开合进度影响） ──────────────────────────────────
        float progress = be.progress(partialTick);
        // 六面版通电后要「半透明」而不是消失，所以地板值 = openAlphaFloor()，永远要画。
        boolean faceDrawn = six || progress < 0.999F;
        if (faceDrawn) {
            // 明暗脉动：两端回到 1.0，过渡中段暗下去并带回闪
            float bump = Mth.sin(progress * Mth.PI);
            float flicker = 0.5F + 0.5F * Mth.sin(progress * Mth.PI * 6.0F);
            float brightness = 1.0F - 0.45F * bump * flicker;

            // alpha 从 1（关闭）淡到 openAlphaFloor()（完全开启）：
            // 舷窗版地板 0 = 看穿；六面版 0.30 = 半透明、蜂窝纹理仍在。
            float alpha = 1.0F - progress * (1.0F - viewport.openAlphaFloor());
            float r = Math.min(1.0F, brightness * 0.96F);
            float g = Math.min(1.0F, brightness * 0.98F);
            float b = Math.min(1.0F, brightness * 1.00F);

            float[][] corners = CORNERS[ord];
            VertexConsumer vc = imm.getBuffer(faceType);

            // UV 仍按世界坐标取模：内缩只是裁掉被框盖住的那条，图案位置不变。
            // 动态皮肤再把 v 折叠进「选中帧」的区间（见上面的 vBaseA / vScale）。
            faceQuad(vc, matrix, corners, axisU, signU, axisV, signV, lu0, lu1, lv0, lv1,
                    u0, v0, period, vBaseA, vScale, alpha, r, g, b, nx, ny, nz, packedLight);

            // 第二遍：下一帧，按 blend 叠上去。第一遍是**不透明**打底（alpha = 面本身
            // 的 alpha），所以第二遍的混合结果精确等于 帧A·(1-blend) + 帧B·blend；
            // 若两遍都按同一个系数打折，会掺进身后的背景色，画面会「透」。
            // ⚠ blend 必须再乘 alpha：否则淡出末尾（alpha → 0）时这一帧会残留不消失。
            if (blend > BLEND_EPSILON) {
                faceQuad(vc, matrix, corners, axisU, signU, axisV, signV, lu0, lu1, lv0, lv1,
                        u0, v0, period, vBaseB, vScale, alpha * blend, r, g, b, nx, ny, nz, packedLight);
            }
        }

        // ── 2) 面外缘的自动外框（结构件，不随开合淡出） ──────────────────────
        if (anyBorder) {
            VertexConsumer fc = imm.getBuffer(FRAME_TYPE);
            float[] base = CORNERS[ord][0];   // 该面在法线方向上的常量坐标
            // 面已经在有框的边上缩掉了 BORDER，这里四条边各画各的：
            // 先占掉左右两条整条边（含四个角），上下两条边再避开角部 → 与面不重不漏
            float along0 = uNeg ? BORDER : 0.0F;
            float along1 = uPos ? 1.0F - BORDER : 1.0F;
            if (uNeg) {
                borderQuad(fc, matrix, base, axisU, axisV,
                        0.0F, BORDER, 0.0F, 1.0F, true, 0.0F, frameV0, packedLight, nx, ny, nz);
            }
            if (uPos) {
                borderQuad(fc, matrix, base, axisU, axisV,
                        1.0F - BORDER, 1.0F, 0.0F, 1.0F, true, 1.0F, frameV0, packedLight, nx, ny, nz);
            }
            if (vNeg) {
                borderQuad(fc, matrix, base, axisU, axisV,
                        along0, along1, 0.0F, BORDER, false, 0.0F, frameU0, packedLight, nx, ny, nz);
            }
            if (vPos) {
                borderQuad(fc, matrix, base, axisU, axisV,
                        along0, along1, 1.0F - BORDER, 1.0F, false, 1.0F, frameU0, packedLight, nx, ny, nz);
            }
        }
        anyFace |= faceDrawn;
        anyFrame |= anyBorder;
        }   // ← 六个面 / 单个面的循环结束

        // 立刻上传：必须赶在 AFTER_BLOCK_ENTITIES 的整屏合成之前进主目标（含深度）。
        // 所有面共用同一种 RenderType，所以整块只在循环外统一 endBatch 一次。
        if (anyFace) {
            imm.endBatch(faceType);
        }
        if (anyFrame) {
            imm.endBatch(FRAME_TYPE);
        }
    }

    /**
     * 六面舱壁：这一面要不要画。
     *
     * <p>静态模型是空的（六个面全靠 BER 画），所以<b>没有原版的面剔除</b> ——
     * 对着实心邻居的面必须自己跳过，否则会画出夹在两块之间的内部面
     * （与邻居方块互相穿插、并且白白多画 6 个四边形）。</p>
     *
     * <p>判据用 {@code canOcclude()}：实心不透明方块 true（跳过），玻璃 / 空气 /
     * 半透明方块 false（照画，所以透过玻璃能看到舱壁侧面）。</p>
     */
    private static boolean faceVisible(Level level, BlockPos pos, Direction face) {
        BlockPos npos = pos.relative(face);
        BlockState nb = level.getBlockState(npos);
        return !nb.canOcclude();
    }

    /**
     * 画面板那一面（4 个顶点的四边形）。局部矩形由 {@code lu0/lu1/lv0/lv1} 给出
     * （已被外框内缩过）；贴图坐标 = 世界取模（{@code u0/v0}，周期 {@code period} 格）
     * + 局部量 / {@code period}，v 方向再经 {@code vBase/vScale} 折叠进帧图集的某一帧。
     *
     * <p>顶点位置用「局部几何轴」算（{@code axisU/axisV} 的 0/1 端），
     * 贴图坐标则按 {@code signU/signV} 决定的正负方向走 —— 两者分开算，
     * 这样「框画在几何边上」和「图案朝向观者正立」互不干扰（见 {@link #UV_DIRS}）。</p>
     *
     * <p>动态皮肤每帧调用两次（当前帧 + 下一帧换不同的 {@code vBase} 和 {@code alpha}），
     * 见 {@code render} 里的交叉淡化注释。</p>
     */
    private static void faceQuad(VertexConsumer vc, Matrix4f m, float[][] corners,
                                 int axisU, int signU, int axisV, int signV,
                                 float lu0, float lu1, float lv0, float lv1,
                                 float u0, float v0, float period, float vBase, float vScale,
                                 float alpha, float r, float g, float b,
                                 float nx, float ny, float nz, int light) {
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            // ① 几何内缩：有框的那条边起点/终点改成 BORDER，没框的边保持 0/1
            //    （这样面与框共享边界、不重叠；内部相邻方块之间仍是 0/1，图案严丝合缝）
            float gu = c[axisU] < 0.5F ? lu0 : lu1;
            float gv = c[axisV] < 0.5F ? lv0 : lv1;
            float x = c[0];
            float y = c[1];
            float z = c[2];
            if (axisU == 0) {
                x = gu;
            } else if (axisU == 1) {
                y = gu;
            } else {
                z = gu;
            }
            if (axisV == 0) {
                x = gv;
            } else if (axisV == 1) {
                y = gv;
            } else {
                z = gv;
            }
            // ② 贴图坐标：拿**内缩后**的几何坐标算，符号决定纹理往里走还是往外走。
            //    用内缩后的坐标算，边缘裁切自动就对了：有框那条边上贴图也正好被裁到
            //    BORDER 处，与框严丝合缝（不必再单独处理一次内缩）。
            float cu = signU > 0 ? gu : 1.0F - gu;
            float cv = signV > 0 ? gv : 1.0F - gv;
            vc.vertex(m, x, y, z)
                    .color(r, g, b, alpha)
                    .uv(u0 + cu / period, vBase + (v0 + cv / period) * vScale)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(nx, ny, nz)
                    .endVertex();
        }
    }

    /**
     * 画一条边框。局部矩形 {@code u ∈ [u0,u1], v ∈ [v0,v1]}；
     * 沿边方向贴图坐标 = {@code alongBase + 沿边坐标 / FRAME_PERIOD}（跨方块连续），
     * 跨边方向 = 0（面板外缘）~ {@link #BORDER}（朝图案一侧），由 {@code crossOuter} 决定哪端是外缘。
     *
     * @param alongIsV   沿边方向是局部 v 轴（左右两条边：贴图随之旋转 90°，纹理走向顺着框）
     * @param crossOuter 跨边方向取 0 的那一端（0 或 1）
     * @param alongBase  沿边方向的贴图基准（左右边传 v0，上下边传 u0）
     */
    private static void borderQuad(VertexConsumer vc, Matrix4f m, float[] base,
                                   int axisU, int axisV,
                                   float u0, float u1, float v0, float v1,
                                   boolean alongIsV, float crossOuter, float alongBase,
                                   int light, float nx, float ny, float nz) {
        borderVertex(vc, m, base, axisU, axisV, u0, v0, alongIsV, crossOuter, alongBase, light, nx, ny, nz);
        borderVertex(vc, m, base, axisU, axisV, u1, v0, alongIsV, crossOuter, alongBase, light, nx, ny, nz);
        borderVertex(vc, m, base, axisU, axisV, u1, v1, alongIsV, crossOuter, alongBase, light, nx, ny, nz);
        borderVertex(vc, m, base, axisU, axisV, u0, v1, alongIsV, crossOuter, alongBase, light, nx, ny, nz);
    }

    private static void borderVertex(VertexConsumer vc, Matrix4f m, float[] base,
                                     int axisU, int axisV, float lu, float lv,
                                     boolean alongIsV, float crossOuter, float alongBase,
                                     int light, float nx, float ny, float nz) {
        float along = alongIsV ? lv : lu;
        float cross = alongIsV ? lu : lv;
        float tu = alongBase + along / FRAME_PERIOD;
        float tv = Math.abs(cross - crossOuter);

        float x = base[0];
        float y = base[1];
        float z = base[2];
        if (axisU == 0) {
            x = lu;
        } else if (axisU == 1) {
            y = lu;
        } else {
            z = lu;
        }
        if (axisV == 0) {
            x = lv;
        } else if (axisV == 1) {
            y = lv;
        } else {
            z = lv;
        }
        vc.vertex(m, x, y, z)
                .color(1.0F, 1.0F, 1.0F, 1.0F)
                .uv(tu, tv)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(nx, ny, nz)
                .endVertex();
    }

    /** 采集靠方块实体阶段（受视锥/区块可见性过滤），这里放宽一点，避免大机库远端舱壁被提前裁掉。 */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
