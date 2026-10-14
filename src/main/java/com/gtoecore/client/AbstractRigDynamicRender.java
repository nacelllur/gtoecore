package com.gtoecore.client;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gtoecore.gt.RigController;
import com.gtoecore.objdemo.ObjDemoTextures;
import com.gtoecore.objdemo.RigGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * 「机架（rig）类机器」塔身渲染的公共骨架 —— 把外部 Blender 导出的 OBJ 画在
 * <b>单层脱氧钢底座的顶面之上</b>，并保证姿态与碰撞体严格同步。
 *
 * <p>底座尺寸每台机器一套（tower 43 × 23 / arch 31 × 19 / mast 21 × 17，
 * 见 {@link RigController#rigPlateHalfLr()}），渲染器不写死任何尺寸 ——
 * 「底座中心在哪」直接问机器（{@link RigController#rigPlateHalfFb()}）。</p>
 *
 * <p>子类只需给出「用哪个 OBJ」「放大多少倍」，可选覆写「额外偏航 / 抬高」与
 * 「逐部件变换（动画）」。</p>
 *
 * <h2>为什么是 DynamicRender 而不是自建 BER</h2>
 * <ul>
 *   <li>机器是真正的 {@code MachineDefinition}，方块 / 物品 / 存档 / 终端自动建造
 *       全部沿用 GT 体系，不需要自己维护方块实体；</li>
 *   <li>{@code DynamicRender} 拿到的 {@link PoseStack} 已经在<b>控制器方块局部坐标系</b>里，
 *       只需要算出「底座中心」在局部坐标下是哪个位置；</li>
 *   <li>GTM 的 {@code hasBER(true)} 会让机器的模型渲染走方块实体渲染阶段，
 *       OBJ 用的原版 {@code RenderType} 照常工作。</li>
 * </ul>
 *
 * <h2>⚠ 必须同时在模型 JSON 里声明 {@code dynamic_renders}</h2>
 * <p>运行时只有模型 JSON 生效（{@code MachineModelLoader} 直接读那个数组）；
 * {@code GTNMachines} 里的 {@code .addDynamicRenderer(...)} 只影响 datagen。
 * 漏了它渲染器根本不会被实例化，而且<b>不报任何错</b> —— 玩家只会看到
 * 「结构成形了，但塔不见了」。</p>
 *
 * <h2>⚠ 姿态必须自己按 {@code facing} 转（不转就会横着长出去）</h2>
 * <p>模型 JSON 顶层的 {@code "dynamic_renders"} 是<b>所有朝向共享</b>的，方块
 * {@code facing} 的旋转只烘焙进静态模型的顶点，<b>不会</b>作用到这里的
 * {@link PoseStack} 上（GTM 官方的 {@code QuantumChestItemRender.setupModelRotation}
 * 就是每个动态渲染器自己转姿态）。底座是<b>矩形</b>（宽 ≠ 深）、塔自身也不对称 ⇒
 * 机器朝东时塔必须跟着转 90°，否则会横着伸到底座外面。</p>
 * <p>偏航角由 {@link RigController#rigYawDegrees()} 给出（那套轴与 pattern 的
 * 字符轴 / aisle 轴一一对应），<b>碰撞体用的是同一套轴</b>，所以视觉与碰撞永远同步。
 * 若发现塔转了 180°（正面朝里），说明模型自身的「正面」与猜测相反 ——
 * 覆写 {@link #rigYawOffset()} 返回 180 即可，<b>碰撞体会跟着一起转</b>。</p>
 *
 * <h2>塔底高度：固定贴在底座顶面上（不检测任何方块）</h2>
 * <p>塔底恒 = 结构顶面（局部 {@code y = RigController.TOWER_BASE_Y} = 1.0，
 * 底座只有一层）+ {@link #rigBaseExtraOffset()}。</p>
 * <p>⚠ 这里曾经有一段「每 500ms 扫整片甲板范围的高度图、取最高可站立面」的
 * <b>自动落座</b>逻辑，已整段删除。它的判据（窗口内最高点）太脆：窗口里任何一根
 * 柱子、脚手架、旁边的塔都会被当成「平台」；而且当时还叠了一道 +48 格上限，
 * 玩家把框架搭到塔基顶面上方 61 格时，塔底被硬钳到 +48 ⇒ 比框架顶面低 13 格、
 * 直接穿进框架里。最终选择<b>「不检测上方方块，固定位置」</b> —— 这也是
 * 可预期性最好的方案：<b>底座搭多高，塔就坐在多高</b>。</p>
 *
 * <h2>性能：两件事，缺一不可（2026-10-19）</h2>
 * <p><b>症状：</b>堆四台塔就掉到 20 帧。实测塔有 7000~8400 个面（每台每帧 ≈ 1 万个
 * 方块面 / 4 万个顶点），而 GTM 的方块实体渲染每帧会把每个顶点在 CPU 上重新
 * 变换 + 打包一遍 —— 四台就是 4 万面 / 17 万顶点，纯单线程。</p>
 * <ol>
 *   <li><b>顶点烘进 VBO。</b>整个静态塔体只在<b>第一次</b>过一遍 CPU 并上传显存，
 *       之后每帧只剩几个 draw call（{@link RigBakedMesh}）。
 *       ⚠ 代价：姿态（含偏航）与光照都烘死在顶点里 ⇒ <b>四个朝向各一份 VBO</b>；
 *       换朝向不重烘（朝向是每个机器固定的）。</li>
 *   <li><b>渲染包围盒换成塔的真实范围。</b>GTM 的方块实体包围盒是<b>直接问渲染器的</b>
 *       （{@code MetaMachineBlockEntity:354} → {@code berModel.getRenderBoundingBox(this)}），
 *       而那个盒子正是原版视锥剔除的判据（{@code LevelRenderer} 的
 *       {@code frustum.isVisible(be.getRenderBoundingBox())}）。本 mod 之前给的是
 *       {@code inflate(140)} —— 281³ 的立方体 ≈ <b>永远在视锥内</b>，
 *       等于把剔除关掉了。现在按碰撞体坐标表算出「贴着塔」的盒子
 *       （{@link RigModelBounds}），背过身去就能整台剔掉。</li>
 * </ol>
 * <p>为了让「会转的部件」还能转，模型被拆成「静态部分（进 VBO）」与
 * 「逐帧动画部分（每帧重画，塔上只有约 8% 的面）」两份 —— 见
 * {@link RigGeometry}。子类通过 {@link #rigAnimatedPartPrefixes()} 声明哪些部件名
 * 需要逐帧更新（默认空 = 整个模型都可以烘死）。</p>
 */
public abstract class AbstractRigDynamicRender<
        M extends MultiblockControllerMachine & RigController,
        R extends AbstractRigDynamicRender<M, R>>
        extends DynamicRender<M, R> {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------
    // 子类必须提供的两个量
    // ------------------------------------------------------------------

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}），如 {@code obj/arch_rig.obj}。 */
    protected abstract String rigObjPath();

    /**
     * 模型自身比例缩放（等比例）。1.0 = Blender 单位 1:1。
     *
     * <p>缩放恒以「塔底面中心」为原点 ⇒ 底面永远贴着底座顶面。
     * ⚠ 改这个值必须用同样的 {@code --scale} 重烤碰撞体坐标表，否则碰撞与视觉对不上。</p>
     */
    protected abstract float rigScale();

    // ------------------------------------------------------------------
    // 子类可选的微调
    // ------------------------------------------------------------------

    /**
     * 塔相对机器朝向的<b>额外</b>偏航角（度）。
     *
     * <p>机器朝向决定的那部分偏航由 {@link RigController#rigYawDegrees()} 给出；
     * 这里只用来修「塔的正面朝里了」这类观感问题 —— <b>加 180 即可把塔转半圈</b>，
     * 碰撞体会跟着一起转。</p>
     */
    protected float rigYawOffset() {
        return 0.0F;
    }

    /**
     * 塔底在「底座顶面」基础上的额外抬高量（格）。
     *
     * <p>想让塔整体悬高一点填正数，想稍微埋一点填负数。这是落座唯一的旋钮
     * —— 落座不做任何方块检测（理由见类头注释）。</p>
     */
    protected double rigBaseExtraOffset() {
        return 0.0D;
    }

    /**
     * 逐部件变换（塔上的可动部件，如涡轮）。默认无 —— 整个模型静态绘制。
     *
     * <p>由 {@code CompositeRenderable.Transforms} 按 OBJ 的 {@code o} 组名索引。
     * <b>只会作用在「动画部分」那份子渲染体上</b>（见 {@link #rigAnimatedPartPrefixes()}），
     * 静态部分已经被烘进 VBO 了。</p>
     */
    protected CompositeRenderable.Transforms rigTransforms(M machine, CompositeRenderable model) {
        return CompositeRenderable.Transforms.EMPTY;
    }

    /**
     * 需要逐帧更新（会动）的部件名<b>前缀</b> —— 即 OBJ 的 {@code o} 组名前缀。
     *
     * <p>这些部件会被排除出静态 VBO、留在逐帧路径上（数量越少越好：它们每帧都要
     * 在 CPU 上重新变换 + 打包）。默认空数组 ⇒ 整个模型都能烘死，每帧零顶点处理。</p>
     *
     * <p>⚠ 前缀必须覆盖 {@link #rigTransforms} 里出现的所有部件名，否则那些部件会
     * 既在 VBO 里画一遍、又被逐帧变换画一遍（重影）。</p>
     */
    protected String[] rigAnimatedPartPrefixes() {
        return EMPTY_PREFIXES;
    }

    private static final String[] EMPTY_PREFIXES = new String[0];

    /**
     * 渲染包围盒的额外余量（格）。
     *
     * <p>盒子是按<b>碰撞体坐标表</b>算的（{@link RigModelBounds}），而那张表在生成时
     * 用 {@code --inset 0.02} 丢掉了发丝级溢出 ⇒ 模型真身可能比表大一丁点。
     * 给 1 格余量足够覆盖，也仍然远小于「关掉剔除」的量级。</p>
     */
    protected double rigBoundingBoxPad() {
        return 1.0D;
    }

    /** 坐标表读不到时的兜底膨胀量 —— 宁可关掉剔除，也不能让塔整座消失。 */
    private static final double BOUNDS_FALLBACK_INFLATE = 140.0D;

    // ------------------------------------------------------------------
    // 一次性诊断开关（静默失败排查用；每条只打一次，不会刷屏）
    //
    // 这串日志能把「塔不显示」的所有可能原因切开：
    //   ① 子类构造器日志  → 模型 JSON 的 "dynamic_renders" 解析成功（渲染器被实例化）
    //   ② shouldRenderOffScreen 日志 → 渲染器真的进了 MachineModel.dynamicRenders 列表
    //   ③ render() 未成形日志 → 列表通了、render 也调了，但结构没成形
    //   ④ render() 首帧日志 / OBJ 缺失日志 → 全链路通，塔应该可见
    // ------------------------------------------------------------------

    private boolean loggedOffScreenQuery = false;
    private boolean loggedNotFormed = false;
    private boolean loggedModelMissing = false;
    private boolean loggedFirstFrame = false;

    @Override
    public void render(M machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // 未成形（拆了 / 结构散架）时不画塔 —— 塔是「结构成形后的产物」
        if (!machine.isFormed()) {
            if (!loggedNotFormed) {
                loggedNotFormed = true;
                LOGGER.info("[gtoecore] {} render() reached at {} but the structure "
                        + "is NOT formed yet - tower deliberately hidden", rigObjPath(), machine.getPos());
            }
            return;
        }
        RigGeometry.Split split = RigGeometry.of(rigObjPath(), rigAnimatedPartPrefixes());
        if (split == null) {
            if (!loggedModelMissing) {
                loggedModelMissing = true;
                LOGGER.warn("[gtoecore] OBJ '{}' unavailable - tower not drawn "
                        + "(see the ObjDemoModels error above)", rigObjPath());
            }
            return;
        }

        // BACK = 控制器背面 = 底座内部方向（控制器在正面边缘上）
        Direction back = RelativeDirection.BACK.getRelative(
                machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());

        // 塔底：恒贴在底座顶面上（+ 手动偏移），不做任何方块检测。
        // 无底座的机器（rigPlateLayers = 0）这里是 0 ⇒ 直接坐在控制器方块下面的地面上。
        double baseY = machine.rigBaseY() + rigBaseExtraOffset();

        // 姿态偏航：让模型的局部 +x 轴对准机器要求的 world 方向。
        // **必须**自己转 —— 模型 JSON 的 dynamic_renders 是所有朝向共享的。
        float yaw = machine.rigYawDegrees() + rigYawOffset();

        // ① 移到塔底面中心（底座中心正上方、底座顶面高度）
        //    控制器在正面边缘的中点 ⇒ 往 BACK 方向走「底座前后半深」格就是底座中心。
        //    这个值每台机器不同（tower 11 / arch 9 / mast 9），必须问机器要。
        int centerBackOffset = machine.rigPlateHalfFb();
        double centerX = 0.5D + (double) back.getStepX() * centerBackOffset;
        double centerZ = 0.5D + (double) back.getStepZ() * centerBackOffset;

        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOGGER.info("[gtoecore] {} renderer drew its FIRST FRAME at {} "
                    + "(scale={}, yaw={}, baseY={}, axisX={}, axisZ={}, animated={})",
                    rigObjPath(), machine.getPos(), rigScale(), yaw, baseY,
                    machine.rigAxisX(), machine.rigAxisZ(), split.hasAnimated());
        }

        // ------------------------------------------------------------------
        // ⚠ 光照：用 FULL_BRIGHT，不用传入的 packedLight。
        //
        // 原版 BlockEntityRenderDispatcher 用 LevelRenderer.getLightColor(level,
        // be.getBlockPos()) —— 只取控制器那一格。塔跨几十格、且在 y=-60 地下，
        // 控制器格子的天空光/方块光都是 0 ⇒ packedLight=0 ⇒ VBO 顶点 UV2=(0,0) ⇒
        // 着色器 texelFetch(Sampler2, (0,0), 0) 采到 lightmap 左下角 = 纯黑。
        // 光影不读原版 lightmap 所以看不出来；原版下一片漆黑。
        //
        // 塔太大、控制器一格的光照不能代表整座塔，所以统一给满光。
        // 这与项目里 EventHorizonRenderer（uv2(FULL_BRIGHT)）一致。
        // ------------------------------------------------------------------
        int light = LightTexture.FULL_BRIGHT;

        // ------------------------------------------------------------------
        // ① 静态部分：整个塔体只烘一次 VBO，之后每帧只剩平移 + 几个 draw call
        // ------------------------------------------------------------------
        Matrix4f bakePose = bakePose(yaw);
        RigBakedMesh mesh = RigBakedMesh.get(getClass(), rigObjPath(), bakePose,
                Float.floatToIntBits(yaw), light, packedOverlay, split.statics());

        poseStack.pushPose();
        // 烘进去的姿态「不含平移」，所以这里补的<b>只有</b>「塔底面中心」那一格。
        poseStack.translate(centerX, baseY, centerZ);
        mesh.draw(poseStack);
        poseStack.popPose();

        // ------------------------------------------------------------------
        // ② 动画部分（塔上会转的涡轮等）：留在逐帧路径 —— 位置与法线都按当帧姿态重算
        // ------------------------------------------------------------------
        if (split.hasAnimated()) {
            poseStack.pushPose();
            poseStack.translate(centerX, baseY, centerZ);
            if (yaw != 0.0F) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            float scale = rigScale();
            if (scale != 1.0F) {
                poseStack.scale(scale, scale, scale);
            }
            // 缺 map_Kd 的材质由 ObjDemoTextures.FLAT_COLOR_LOOKUP 兜底成 1×1 白图，
            // 最终颜色 = MTL 里的纯 Kd 顶点色 ⇒ MTL 可以保持 Blender 原样导出。
            split.animated().render(poseStack, buffer, ObjDemoTextures.FLAT_COLOR_LOOKUP,
                    light, packedOverlay, partialTick, rigTransforms(machine, split.animated()));
            poseStack.popPose();
        }
    }

    /**
     * 烘焙姿态 = 偏航 → 把 Blender 的 Z-up 立成 Y-up → 等比缩放（<b>不含平移</b>）。
     *
     * <p>顺序与逐帧路径完全一致；写成 {@link Matrix4f} 直接后乘是为了每帧零分配
     * （{@code PoseStack} 会 push 一份 {@code Pose} 副本）。矩阵是原地复用的字段，
     * {@code RigBakedMesh.get} 只在内部拷贝它的值，不会持有引用。</p>
     */
    private Matrix4f bakePose(float yaw) {
        Matrix4f pose = this.bakePoseScratch;
        pose.identity();
        if (yaw != 0.0F) {
            pose.rotateY((float) Math.toRadians(yaw));
        }
        pose.rotateX((float) Math.toRadians(-90.0F));
        float scale = rigScale();
        if (scale != 1.0F) {
            pose.scale(scale, scale, scale);
        }
        return pose;
    }

    private final Matrix4f bakePoseScratch = new Matrix4f();

    /**
     * 视距内且已成形才画。
     *
     * <p>视距由机器自己给（{@link RigController#rigViewDistance()}，默认 256）——
     * 203 格直径的 {@code lhc_combo} 覆写成 512，否则站远一点整座环线就消失。</p>
     */
    @Override
    public boolean shouldRender(M machine, Vec3 cameraPos) {
        double d = machine.rigViewDistance();
        return machine.isFormed()
                && cameraPos.distanceToSqr(Vec3.atCenterOf(machine.getPos())) < d * d;
    }

    /**
     * 让这个方块实体走「全局方块实体」通道 —— 必须返回 {@code true}。
     *
     * <p>{@code ChunkRenderDispatcher$RenderChunk$RebuildTask} 在重建区块渲染数据时问
     * {@code shouldRenderOffScreen(be)}：返回 {@code true} 的方块实体<b>不会</b>被塞进
     * 「这个区块可见时才画」的那张表，而是走 {@code LevelRenderer} 的
     * {@code globalBlockEntities}。</p>
     * <p>塔横跨几十格，而方块实体只有控制器那一个 —— 若走区块通道，
     * 只要控制器所在区块出了视锥，整座塔就会凭空消失。
     * 走全局通道后，剔除判据变成 {@link #getRenderBoundingBox}：那个盒子现在贴着塔的
     * 真实范围，所以「背过身去整台剔掉」依然成立，只是不会再出现「半截塔」。</p>
     */
    @Override
    public boolean shouldRenderOffScreen(M machine) {
        // 这个回调由 GTM 的 MachineModel 每帧问一次（BE 分发路径），
        // 与「是否在视锥内」无关 ⇒ 它被调到就说明渲染器确实躺在
        // MachineModel.dynamicRenders 里。
        if (!loggedOffScreenQuery) {
            loggedOffScreenQuery = true;
            LOGGER.info("[gtoecore] {} DynamicRender is LIVE inside MachineModel "
                    + "(shouldRenderOffScreen queried; formed={})", rigObjPath(), machine.isFormed());
        }
        return machine.isFormed();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
    /**
     * 必须返回「塔的真实范围」—— 这个盒子就是视锥剔除唯一的判据。
     *
     * <p>GTM 的方块实体包围盒是直接问渲染器的（{@code MetaMachineBlockEntity} →
     * {@code berModel.getRenderBoundingBox(this)}），而 {@code LevelRenderer} 拿它做
     * {@code frustum.isVisible(...)}。给一个 281³ 的大盒子等于关掉剔除
     * （见类头「性能」一节），给得太小又会让塔被剔掉半截。</p>
     *
     * <p>这里按<b>碰撞体坐标表</b>算：表就是这座塔的离线体素化结果，它的
     * min/max 天然等于模型在世界里的实际外延，重烤表时盒子自动跟着变。
     * 格子/轴的映射与 {@code RigCollision.apply} 用同一套
     * （{@code rigAxisX()} / {@code rigAxisZ()}），所以视觉、碰撞、剔除三者永远同步。</p>
     */
    @Override
    public AABB getRenderBoundingBox(M machine) {
        BlockPos pos = machine.getPos();
        int[] bounds = RigModelBounds.of(machine.rigCollisionTable());
        if (bounds == null) {
            // 表读不到 ⇒ 宁可关掉剔除，也不能让塔整座消失
            return new AABB(pos).inflate(BOUNDS_FALLBACK_INFLATE);
        }
        int minI = bounds[0];
        int maxI = bounds[1] + 1;
        int minJ = bounds[2];
        int maxJ = bounds[3] + 1;
        int minK = bounds[4];
        int maxK = bounds[5] + 1;

        // 坐标表里的格 (i,j,k) 的中心落在「原点格 + axisX×i + axisZ×k」，
        // 所以格子的最小角就是「原点格 + axisX×i + axisZ×k」。
        Direction back = RelativeDirection.BACK.getRelative(
                machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        Direction axisX = machine.rigAxisX();
        Direction axisZ = machine.rigAxisZ();
        int halfFb = machine.rigPlateHalfFb();
        double originX = pos.getX() + (double) back.getStepX() * halfFb;
        double originZ = pos.getZ() + (double) back.getStepZ() * halfFb;
        double originY = pos.getY() + machine.rigCollisionOriginYOffset();

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i <= 1; i++) {
            int ci = i == 0 ? minI : maxI;
            for (int k = 0; k <= 1; k++) {
                int ck = k == 0 ? minK : maxK;
                double wx = originX + (double) axisX.getStepX() * ci + (double) axisZ.getStepX() * ck;
                double wz = originZ + (double) axisX.getStepZ() * ci + (double) axisZ.getStepZ() * ck;
                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wz < minZ) minZ = wz;
                if (wz > maxZ) maxZ = wz;
            }
        }
        double pad = rigBoundingBoxPad();
        return new AABB(
                minX - pad, originY + minJ - pad, minZ - pad,
                maxX + pad, originY + maxJ + pad, maxZ + pad);
    }
}
