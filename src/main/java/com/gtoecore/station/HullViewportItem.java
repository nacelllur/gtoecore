package com.gtoecore.station;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 全周天舱壁物品 —— 只为了加说明文本。 */
public class HullViewportItem extends BlockItem {

    /** true = 六面蜂窝舱壁：说明文本不同（没有单向舷窗 / 朝向翻转）。 */
    private final boolean sixSided;

    public HullViewportItem(Block block, Properties properties) {
        this(block, properties, false);
    }

    public HullViewportItem(Block block, Properties properties, boolean sixSided) {
        super(block, properties);
        this.sixSided = sixSided;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (sixSided) {
            tooltip.add(Component.translatable("tooltip.gtoecore.hull_panel_hex.1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.gtoecore.hull_panel_hex.2").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.gtoecore.hull_viewport.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtoecore.hull_viewport.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtoecore.hull_viewport.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
