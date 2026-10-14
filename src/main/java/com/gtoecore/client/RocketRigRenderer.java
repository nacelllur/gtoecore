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
 * 把 {@code rocket_rig.obj}（Blender 自制一级火箭模型）画在 ad_astra 的
 * {@code tier_1_rocket} 实体身上，替换原版程序化 {@code RocketModel}。
 *
 * <h2>为什么能替换</h2>
 * <p>{@code gtoecore} 在 mods.toml 里声明了对 ad_astra 的 {@code ordering="AFTER"}
 * 软依赖 ⇒ 本 mod 监听 {@code EntityRenderersEvent.RegisterRenderers} 时，
 * ad_astra 已经把 {@code tier_1_rocket} 注册进 {@code EntityRenderers} 表；
 * 我们<b>后注册同名类型</b> ⇒ put 覆盖 ⇒ 原版渲染器被整个换掉。</p>
 *
 * <h2>为什么不需要编译期依赖 ad_astra</h2>
 * <p>{@link #registerIfLoaded} 用 {@code BuiltInRegistries.ENTITY_TYPE} 按 id
 * 查实体类型（存在才注册），本类泛型也只到原版 {@link Entity} —— ad_astra
 * 不在 classpath 也照常编译、运行（没装它这步自然跳过）。</p>
 *
 * <h2>姿态 / 缩放</h2>
 * <ul>
 *   <li>{@code rocket_rig.obj} 是 Blender <b>Z-up</b>（x/y ±1.7469、z 0..24.28）⇒
 *       先 {@code rotateX(-90)} 立直成 Y-up，底部(z=0)贴实体脚底；</li>
 *   <li><b>SCALE = 0.8587</b>（= 3.0 / 3.493786）：水平长宽收敛到 <b>3 × 3 格</b>，
 *       高度随之 = 24.28 × 0.8587 ≈ <b>20.85 格</b>（模型自身长高比固定）；</li>
 *   <li>偏航取实体 yaw 插值（{@code yRotO → yRot}），正面朝向可再旋
 *       {@code YAW_OFFSET} 修正（进游戏发现头朝反了改 180 即可）。</li>
 * </ul>
 *
 * <p><b>光照</b>：与机架塔一致用 {@code FULL_BRIGHT} —— 火箭是自发光人造物，
 * 统一满光（也避免月球/太空/地下的环境光差异让纯色 Kd 变得一团黑）。</p>
 *
 * <p><b>性能</b>：OBJ 约 1.9 万面，逐帧 {@code CompositeRenderable} 渲染。
 * 场上火箭通常只有一两枚、且离玩家近才画，可接受；若实测掉帧再考虑按 yaw
 * 烘 VBO（火箭只有一个水平旋转自由度，缓存可行）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RocketRigRenderer extends EntityRenderer<Entity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean loggedFirstFrame = false;

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/rocket_rig.obj";

    /**
     * 缩放：让模型水平长宽 = 3 格。
     *
     * <p>原始 OBJ x/y 跨度为 3.493786 ⇒ scale = 3.0 / 3.493786 ≈ 0.8587。
     * 高度 = 24.28 × 0.8587 ≈ 20.85 格（模型自己的长高比，用户已确认 3×3 底面）。</p>
     */
    private static final float SCALE = 0.8587F;

    /** 额外偏航（度）；进游戏发现火箭头朝向不对就改（0 / 90 / 180 / 270）。 */
    private static final float YAW_OFFSET = 0.0F;

    /** 模型底部在实体脚底之上的额外抬高（格），一般 0（OBJ 底部 z=0 正好贴地）。 */
    private static final float BASE_Y_OFFSET = 0.0F;

    public RocketRigRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * 贴图位置 —— 本渲染器完全用 OBJ 的顶点色画（{@code FLAT_COLOR_LOOKUP} 顶白图），
     * 这个返回值不会被用到，但 {@code EntityRenderer} 声明为 abstract，必须实现。
     */
    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(Entity entity) {
        return new net.minecraft.resources.ResourceLocation("gtoecore", "textures/block/obj/obj_white.png");
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        CompositeRenderable model = ObjDemoModels.get(OBJ_PATH);
        if (model == null) {
            if (!loggedFirstFrame) {
                loggedFirstFrame = true;
                LOGGER.warn("[gtoecore] rocket_rig.obj unavailable - tier_1_rocket drawn as NOTHING");
            }
            return;
        }
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOGGER.info("[gtoecore] RocketRigRenderer drew its FIRST FRAME - override is live");
        }
        poseStack.pushPose();
        // ① 底部贴实体脚底
        poseStack.translate(0.0F, BASE_Y_OFFSET, 0.0F);
        // ② 偏航：实体朝向（插值防抖动）+ 手动修正
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) + YAW_OFFSET;
        if (yaw != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        }
        // ③ Blender Z-up → 游戏 Y-up（底部 z=0 → y=0，头朝 +z → +y）
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        // ④ 收敛到 3 × 3 底面
        poseStack.scale(SCALE, SCALE, SCALE);

        model.render(poseStack, buffer, ObjDemoTextures.FLAT_COLOR_LOOKUP,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, partialTick,
                CompositeRenderable.Transforms.EMPTY);
        poseStack.popPose();
    }

    /**
     * 给 {@code ad_astra:tier_1_rocket} 注册本渲染器（覆盖原版）。
     *
     * <p>⚠ 只能从 {@code EntityRenderersEvent.RegisterRenderers} 里调用（那时
     * ad_astra 已注册完，且本 mod 因 ordering AFTER 排在后 —— put 覆盖才成立）。
     * 用注册表查类型 ⇒ ad_astra 未加载时零影响。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerIfLoaded(
            net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        try {
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getOptional(new net.minecraft.resources.ResourceLocation("ad_astra", "tier_1_rocket"))
                    .ifPresent(type -> {
                        event.registerEntityRenderer((net.minecraft.world.entity.EntityType) type,
                                RocketRigRenderer::new);
                        LOGGER.info("[gtoecore] tier_1_rocket renderer overridden with rocket_rig.obj");
                    });
        } catch (Throwable t) {
            LOGGER.warn("[gtoecore] failed to override ad_astra tier_1_rocket renderer", t);
        }
    }
}
