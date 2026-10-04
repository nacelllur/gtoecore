package com.gtoecore.energy.block;

import com.gtoecore.GTNCore;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 燃烧引擎屏幕 —— 燃料槽 + 燃烧进度条 + 转速/余量读数（1.20.1 GuiGraphics API）。
 *
 * <p>数据全部来自 {@link CombustionEngineMenu} 的 DataSlot（每 tick 与服务器同步）。
 * GUI 背景为 176x166 像素贴图（暗金属 + 高亮槽区 + 火焰素材区在 176,0）。</p>
 */
@Mod.EventBusSubscriber(modid = GTNCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CombustionEngineScreen extends AbstractContainerScreen<CombustionEngineMenu> {

    private static final ResourceLocation BG =
            new ResourceLocation(GTNCore.MODID, "textures/gui/combustion_engine.png");

    /** 一格燃料 = 2400 tick，条满按此换算 */
    private static final int FULL_BURN = 2400;

    public CombustionEngineScreen(CombustionEngineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent evt) {
        evt.enqueueWork(() -> MenuScreens.register(
                CombustionEngineMenu.TYPE.get(), CombustionEngineScreen::new));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, BG);
        graphics.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int burn = menu.burnData.get();
        if (burn > 0) {
            // 火焰 14x14，从框底（64）向上生长；素材取贴图 176,0 区
            int h = Math.max(1, (int) (14 * Math.min(1f, burn / (float) FULL_BURN)));
            graphics.blit(BG, leftPos + 80, topPos + 64 - h, 176, 0, 14, h);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int burn = menu.burnData.get();
        int speed = menu.speedData.get();
        String speedTxt = speed > 0
                ? Component.translatable("gtoecore.gui.engine.speed", speed).getString()
                : Component.translatable("gtoecore.gui.engine.idle").getString();
        graphics.drawString(font, speedTxt, 8, 22, 0xE0E0E0);
        if (burn > 0) {
            String burnTxt = Component.translatable("gtoecore.gui.engine.burn",
                    String.format("%.0f", burn / 20.0)).getString();
            graphics.drawString(font, burnTxt, 8, 34, 0xFFB000);
        } else {
            graphics.drawString(font,
                    Component.translatable("gtoecore.gui.engine.no_fuel").getString(), 8, 34, 0x909090);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}