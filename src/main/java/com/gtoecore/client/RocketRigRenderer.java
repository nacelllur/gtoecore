package com.gtoecore.client;

import com.gtoecore.objdemo.ObjDemoModels;
import com.gtoecore.objdemo.ObjDemoTextures;
import com.gtoecore.objdemo.RigGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * 用 OBJ 模型替换 ad_astra 火箭的程序化模型 —— 一份渲染器、多台火箭共用。
 *
 * <h2>预烘焙 VBO（和机架塔同一套 {@link RigBakedMesh} 基建）</h2>
 * <p>火箭是纯静态模型（无动画部件），整份烘进 VBO 后每帧只剩 1~3 个 draw call，
 * 逐帧顶点处理≈零。key 由 {@code (renderer类, objPath, 姿态哈希, light, overlay)} 组成：</p>
 * <ul>
 *   <li><b>姿态 = 90° 量化偏航</b>。火箭放置时 yaw 恰是 {@code Direction.toYRot()}（90° 整数倍），
 *       飞行中才连续漂移；量化后<b>停放状态残余角 = 0</b>，烘焙姿态与真实姿态完全一致。</li>
 *   <li>非 90° 倍数的残余角（只有飞行中会出现）在<b>绘制时</b>用 PoseStack 补转 ——
 *       位置正确，法线光照偏差 ≤45°，飞行中的火箭没人细看。</li>
 * </ul>
 *
 * <h2>姿势</h2>
 * <ul>
 *   <li>Blender <b>Z-up</b> ⇒ 先 {@code rotateX(-90)} 立直成 Y-up，底部(z=0)贴实体脚底；</li>
 *   <li>{@code SCALE} 把水平长宽收敛到 <b>3 × 3 格</b>，高度随比例走；</li>
 *   <li>偏航取实体 yaw 插值 + {@code YAW_OFFSET} 修正。</li>
 * </ul>
 *
 * <h2>每台火箭的参数</h2>
 * <table>
 *   <tr><th>tier</th><th>OBJ</th><th>SCALE</th><th>高度</th></tr>
 *   <tr><td>1</td><td>rocket_rig.obj</td><td>0.8587</td><td>≈20.85 格</td></tr>
 *   <tr><td>2</td><td>falcon_rig.obj</td><td>0.5676</td><td>≈39.74 格</td></tr>
 *   <tr><td>3</td><td>next_rig.obj</td><td>0.4518</td><td>≈34.34 格</td></tr>
 *   <tr><td>4</td><td>quad_rig.obj</td><td>0.4478</td><td>≈42.63 格</td></tr>
 * </table>
 *
 * <p><b>光照</b>：{@code FULL_BRIGHT}（火箭是自发光人造物，
 * 也避免月球/太空的环境光差异让纯色 Kd 变黑）。</p>
 *
 * <p><b>剔除</b>：火箭实体碰撞箱只有 1.1×4.6，而模型高 20~40 格 —— 必须覆写
 * {@link #shouldRender} 用「模型实际体积」的 AABB 做视锥判定，否则抬头看塔顶
 * 时整枚火箭会被当成「盒子出了视锥」而凭空消失。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RocketRigRenderer extends EntityRenderer<Entity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] NO_ANIMATED = new String[0];

    /** 最远渲染距离（格）—— 发射时往往要站远看，给足余量。 */
    private static final double MAX_RENDER_DISTANCE_SQ = 512.0D * 512.0D;

    private final String objPath;
    private final float scale;
    private final float yawOffset;
    private final float baseYOffset;
    private final String logName;
    /** 模型视觉高度（格），用于视锥剔除盒。 */
    private final double modelHeight;
    /** 模型视觉半径（格），用于视锥剔除盒。 */
    private final double modelRadius;

    /** 烘焙姿态的复用缓冲区（烘焙时姿态每次都一样：90° 量化后的四个值之一）。 */
    private final Matrix4f bakePoseScratch = new Matrix4f();

    private boolean loggedFirstFrame = false;

    /** 一级火箭（rocket_rig.obj，3×3×≈20.85 格）。 */
    public static RocketRigRenderer tier1(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/rocket_rig.obj", 0.8587F, 0.0F, 0.0F,
                "tier_1_rocket", 20.85D, 1.6D);
    }

    /** 二级火箭（falcon_rig.obj = Falcon 9，3×3×≈39.74 格）。 */
    public static RocketRigRenderer tier2(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/falcon_rig.obj", 0.5676F, 0.0F, 0.0F,
                "tier_2_rocket", 39.74D, 1.6D);
    }

    /** 三级火箭（next_rig.obj，3×3×≈34.34 格）。 */
    public static RocketRigRenderer tier3(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/next_rig.obj", 0.4518F, 0.0F, 0.0F,
                "tier_3_rocket", 34.34D, 1.6D);
    }

    /** 四级火箭（quad_rig.obj，3×3×≈42.63 格）。 */
    public static RocketRigRenderer tier4(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/quad_rig.obj", 0.4478F, 0.0F, 0.0F,
                "tier_4_rocket", 42.63D, 1.6D);
    }

    private RocketRigRenderer(EntityRendererProvider.Context context,
                              String objPath, float scale, float yawOffset,
                              float baseYOffset, String logName,
                              double modelHeight, double modelRadius) {
        super(context);
        this.objPath = objPath;
        this.scale = scale;
        this.yawOffset = yawOffset;
        this.baseYOffset = baseYOffset;
        this.logName = logName;
        this.modelHeight = modelHeight;
        this.modelRadius = modelRadius;
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(Entity entity) {
        return new net.minecraft.resources.ResourceLocation("gtoecore", "textures/block/obj/obj_white.png");
    }

    /**
     * 视锥剔除：用模型实际体积（3×3×高）而不是实体那 1.1×4.6 的小碰撞箱。
     *
     * <p>⚠ 不调用 {@code entity.shouldRender(...)} —— 它内部用实体碰撞箱（1.1×4.6）算
     * 「最远可见距离」（{@code getBoundingBox().getSize() * 64 * viewScale} ≈ 294 格），
     * 对 20~40 格高的火箭偏紧。这里换成显式的 512 格上限，保证站在远处也能看发射。</p>
     */
    @Override
    public boolean shouldRender(Entity entity, Frustum frustum,
                                double camX, double camY, double camZ) {
        if (entity.noCulling) {
            return true;
        }
        if (entity.distanceToSqr(camX, camY, camZ) > MAX_RENDER_DISTANCE_SQ) {
            return false;
        }
        AABB box = new AABB(
                entity.getX() - modelRadius, entity.getY() - 0.5D, entity.getZ() - modelRadius,
                entity.getX() + modelRadius, entity.getY() + modelHeight + 0.5D, entity.getZ() + modelRadius);
        return frustum.isVisible(box);
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        RigGeometry.Split split = RigGeometry.of(objPath, NO_ANIMATED);
        if (split == null) {
            if (!loggedFirstFrame) {
                loggedFirstFrame = true;
                LOGGER.warn("[gtoecore] {} OBJ unavailable ({}) - drawn as NOTHING", logName, objPath);
            }
            return;
        }
        CompositeRenderable model = split.statics();
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOGGER.info("[gtoecore] {} renderer drew FIRST FRAME ({} scale={})", logName, objPath, scale);
        }

        // 偏航：插值防抖动 + 手动修正
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) + yawOffset;

        // 量化到 90° 网格：停车状态 yaw 必是 90° 倍数 ⇒ 残余角 0、烘焙姿态精确；
        // 飞行中的连续角度只把残余角补在绘制矩阵上（位置正确，光照略有偏差）。
        float snappedYaw = Math.round(yaw / 90.0F) * 90.0F;
        float residualYaw = yaw - snappedYaw;

        // 烘焙姿态 = 量化偏航 → Z-up 立直 → 等比缩放（不含平移）
        Matrix4f bakePose = this.bakePoseScratch;
        bakePose.identity();
        if (snappedYaw != 0.0F) {
            bakePose.rotateY((float) Math.toRadians(snappedYaw));
        }
        bakePose.rotateX((float) Math.toRadians(-90.0F));
        if (scale != 1.0F) {
            bakePose.scale(scale, scale, scale);
        }

        RigBakedMesh mesh = RigBakedMesh.get(getClass(), objPath, bakePose,
                Float.floatToIntBits(snappedYaw), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, model);

        poseStack.pushPose();
        poseStack.translate(0.0F, baseYOffset, 0.0F);
        if (residualYaw != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(residualYaw));
        }
        mesh.draw(poseStack);
        poseStack.popPose();
    }
}
