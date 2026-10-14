package com.gtoecore.objdemo;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.joml.Matrix4f;

/**
 * OBJ 示范涡轮机渲染器 —— 用 Forge ObjLoader 加载外部 OBJ 模型，
 * CompositeRenderable 逐 part 渲染，rotor / shaft 绕 Y 轴旋转。
 *
 * <p>这是「HBM 式机器外形」在 1.20.1 的最小实现：外部模型资产 + BER 逐 part 动画。
 * 模型按方块空间建模（0~1），放在方块原点上。</p>
 *
 * <p>OBJ 由 {@link ObjDemoModels} 在首帧懒加载。</p>
 */
@OnlyIn(Dist.CLIENT)
public class TurbineDemoRenderer implements BlockEntityRenderer<TurbineDemoBlockEntity> {

    private static final String OBJ_PATH = "obj/turbine_demo.obj";

    private static CompositeRenderable model;

    /** 当前旋转角（度）。纯客户端自增，不需要同步。 */
    private float angle;

    public TurbineDemoRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TurbineDemoBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (model == null) {
            model = ObjDemoModels.get(OBJ_PATH);
            if (model == null) {
                return;
            }
        }

        int mode = be.getMode();
        float speed = switch (mode) {
            case 0 -> 0.0f;
            case 1 -> 12.0f;
            case 2 -> 60.0f;
            case 3 -> -45.0f;
            default -> 0.0f;
        };
        angle = (angle + speed * partialTick) % 360.0f;

        pose.pushPose();

        // 旋转矩阵（绕 Y 轴，方块中心 0.5, 0, 0.5）：先平移到中心、旋转、再平移回来
        float rad = (float) Math.toRadians(angle);
        Matrix4f rotorMat = new Matrix4f()
                .translate(0.5f, 0.0f, 0.5f)
                .rotate(rad, 0, 1, 0)
                .translate(-0.5f, 0.0f, -0.5f);
        Matrix4f shaftMat = new Matrix4f()
                .translate(0.5f, 0.0f, 0.5f)
                .rotate(rad, 0, 1, 0)
                .translate(-0.5f, 0.0f, -0.5f);

        CompositeRenderable.Transforms transforms = CompositeRenderable.Transforms.of(
                ImmutableMap.of(
                        "rotor", rotorMat,
                        "shaft", shaftMat
                ));

        model.render(pose, buffer, ObjDemoTextures.FLAT_COLOR_LOOKUP, packedLight, packedOverlay,
                partialTick, transforms);
        pose.popPose();
    }
}
