package com.gtoecore.client;

import com.gtoecore.objdemo.ObjDemoModels;
import com.gtoecore.objdemo.ObjDemoTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.slf4j.Logger;

/**
 * 用 OBJ 模型替换 ad_astra 火箭的程序化模型 —— 一份渲染器、多台火箭共用。
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
 * </table>
 *
 * <p><b>光照</b>：{@code FULL_BRIGHT}（火箭是自发光人造物，
 * 也避免月球/太空的环境光差异让纯色 Kd 变黑）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RocketRigRenderer extends EntityRenderer<Entity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String objPath;
    private final float scale;
    private final float yawOffset;
    private final float baseYOffset;
    private final String logName;

    private boolean loggedFirstFrame = false;

    /** 一级火箭（rocket_rig.obj，3×3×≈20.85 格）。 */
    public static RocketRigRenderer tier1(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/rocket_rig.obj", 0.8587F, 0.0F, 0.0F, "tier_1_rocket");
    }

    /** 二级火箭（falcon_rig.obj = Falcon 9，3×3×≈39.74 格）。 */
    public static RocketRigRenderer tier2(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/falcon_rig.obj", 0.5676F, 0.0F, 0.0F, "tier_2_rocket");
    }

    /** 三级火箭（next_rig.obj，3×3×≈34.34 格）。 */
    public static RocketRigRenderer tier3(EntityRendererProvider.Context ctx) {
        return new RocketRigRenderer(ctx, "obj/next_rig.obj", 0.4518F, 0.0F, 0.0F, "tier_3_rocket");
    }

    private RocketRigRenderer(EntityRendererProvider.Context context,
                              String objPath, float scale, float yawOffset,
                              float baseYOffset, String logName) {
        super(context);
        this.objPath = objPath;
        this.scale = scale;
        this.yawOffset = yawOffset;
        this.baseYOffset = baseYOffset;
        this.logName = logName;
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(Entity entity) {
        return new net.minecraft.resources.ResourceLocation("gtoecore", "textures/block/obj/obj_white.png");
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        CompositeRenderable model = ObjDemoModels.get(objPath);
        if (model == null) {
            if (!loggedFirstFrame) {
                loggedFirstFrame = true;
                LOGGER.warn("[gtoecore] {} OBJ unavailable ({}) - drawn as NOTHING", logName, objPath);
            }
            return;
        }
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOGGER.info("[gtoecore] {} renderer drew FIRST FRAME ({} scale={})", logName, objPath, scale);
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, baseYOffset, 0.0F);
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) + yawOffset;
        if (yaw != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(scale, scale, scale);
        model.render(poseStack, buffer, ObjDemoTextures.FLAT_COLOR_LOOKUP,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, partialTick,
                CompositeRenderable.Transforms.EMPTY);
        poseStack.popPose();
    }
}
