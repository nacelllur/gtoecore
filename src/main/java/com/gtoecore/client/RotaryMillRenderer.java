package com.gtoecore.client;

import com.gtoecore.energy.block.RotaryMillBlock;
import com.gtoecore.energy.block.RotaryMillBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * 旋转石磨方块实体渲染器 —— 群峦 Quern 形态的上磨盘。
 *
 * <p>底座（下磨盘）走普通 blockstate 模型；这里只叠加渲染「上磨盘」，
 * 绕方块中心 Y 轴旋转：LIT（有转速在研磨）时快转，停机时几乎停转。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RotaryMillRenderer implements BlockEntityRenderer<RotaryMillBlockEntity> {

    /** 当前旋转角（度）。纯客户端自增，无需从服务端同步。 */
    private float angle;

    public RotaryMillRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RotaryMillBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof RotaryMillBlock)) return;

        boolean active = state.getValue(RotaryMillBlock.ACTIVE);
        // 工作中快速旋转；停机时缓慢到几乎静止（保留一点惯性表现）
        angle = (angle + (active ? 6.0f : 0.2f) * partialTick) % 360.0f;

        BakedModel wheel = Minecraft.getInstance().getModelManager()
                .getModel(RotaryMillClient.WHEEL_MODEL);
        if (wheel == null || wheel == Minecraft.getInstance().getModelManager().getMissingModel()) {
            return;
        }

        pose.pushPose();
        // 磨盘中心 = 方块中心；绕该中心旋转
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-0.5, 0.0, -0.5);

        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), consumer, state, wheel,
                1.0f, 1.0f, 1.0f, packedLight, packedOverlay,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
