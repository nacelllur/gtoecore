package com.gtoecore.client;

import com.gtoecore.objdemo.ObjDemoModels;
import com.gtoecore.objdemo.ObjDemoTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.slf4j.Logger;

/**
 * 用 {@code rocket_rig.obj} 替换 ad_astra 一级火箭的<b>物品渲染</b>（背包/手持/展示框）。
 *
 * <p>ad_astra 的火箭物品走 {@code RocketRenderer$ItemRenderer}（BEWLR），
 * 只覆盖实体渲染器是看不到变化的 —— 这是「物品栏里还是原版火箭」的直接原因。
 * 本类把该物品的 BEWLR 也换成 OBJ 管线，姿态与实体一致（Z-up 立直 + 0.8587 缩放）。</p>
 *
 * <p>物品坐标系是「y 向上、原点在物品中心」：先 {@code rotateX(-90)} 把 Blender 的
 * Z-up 立直，再等比缩放。物品渲染没有朝向（随 ItemDisplayContext 旋转），所以不用偏航。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RocketRigItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String OBJ_PATH = "obj/rocket_rig.obj";

    /** 与实体渲染器一致：水平长宽 = 3 格（3.0 / 3.493786）。 */
    private static final float SCALE = 0.8587F;

    private static volatile RocketRigItemRenderer instance;

    /** 懒加载单例（需要 Minecraft 实例，只能在客户端取）。 */
    public static RocketRigItemRenderer get() {
        RocketRigItemRenderer local = instance;
        if (local == null) {
            Minecraft mc = Minecraft.getInstance();
            local = new RocketRigItemRenderer(
                    mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
            instance = local;
        }
        return local;
    }

    private RocketRigItemRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher be,
                                  net.minecraft.client.model.geom.EntityModelSet models) {
        super(be, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        CompositeRenderable model = ObjDemoModels.get(OBJ_PATH);
        if (model == null) {
            return;
        }
        poseStack.pushPose();
        // Blender Z-up → 物品 Y-up（立直）；物品无朝向，只需立直 + 缩放。
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(SCALE, SCALE, SCALE);
        // 物品在 1.5 格左右高处，把底部抬到物品中心略下方更自然。
        poseStack.translate(0.0F, 0.5F, 0.0F);
        model.render(poseStack, buffer, ObjDemoTextures.FLAT_COLOR_LOOKUP,
                LightTexture.FULL_BRIGHT, packedOverlay, 0.0F,
                CompositeRenderable.Transforms.EMPTY);
        poseStack.popPose();
        LOGGER.debug("[gtoecore] rocket_rig item rendered via BEWLR");
    }
}
