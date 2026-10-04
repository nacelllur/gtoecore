package com.gtoecore.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 旋转石磨物品 —— 附使用说明 tooltip（放在工具链 item 上，BlockItem 默认没有）。
 */
public class RotaryMillItem extends BlockItem {

    public RotaryMillItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("gtoecore.machine.rotary_mill.tooltip.1"));
        tooltip.add(Component.translatable("gtoecore.machine.rotary_mill.tooltip.2"));
        tooltip.add(Component.translatable("gtoecore.machine.rotary_mill.tooltip.3"));
    }
}
