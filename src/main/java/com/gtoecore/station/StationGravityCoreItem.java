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

/** 舱室重力核心物品 —— 附用法说明 tooltip */
public class StationGravityCoreItem extends BlockItem {

    public StationGravityCoreItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("gtoecore.gravity_core.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gtoecore.gravity_core.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gtoecore.gravity_core.tooltip.3")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
