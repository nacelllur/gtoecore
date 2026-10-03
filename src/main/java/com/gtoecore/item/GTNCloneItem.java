package com.gtoecore.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 克隆体 —— 复活消耗品。
 *
 * <p><b>用法：</b>手持克隆体<b>右键空气</b> → 绑定为自己（NBT 记录玩家 UUID+名字，
 * tooltip 实时显示已绑定玩家）。把绑定后的克隆体放进<b>克隆体生产车间</b>的输入总线，
 * 硬核（极限）模式下该玩家死亡时消耗 1 个克隆体并在车间的床上复活。未绑定的克隆体
 * 对任何玩家都生效。</p>
 *
 * <p><b>实现要点（踩坑记录）：</b>绑定走原版 {@link Item#use}（右键空气时服务端
 * 一定会执行）。<b>不要</b>用 {@code PlayerInteractEvent.RightClickEmpty} ——
 * 它只在<b>客户端</b>触发，服务端永远收不到（此前绑定功能因此完全没生效）。</p>
 */
public class GTNCloneItem extends Item {

    public static final String TAG_BOUND_UUID = "BoundPlayer";
    public static final String TAG_BOUND_NAME = "BoundPlayerName";

    public GTNCloneItem(Properties properties) {
        super(properties);
    }

    /** 右键空气：绑定为自己（服务端生效） */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            bind(stack, player);
            player.displayClientMessage(Component.translatable("gtoecore.clone.bind_success",
                    player.getGameProfile().getName()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void bind(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BOUND_UUID, player.getUUID());
        tag.putString(TAG_BOUND_NAME, player.getGameProfile().getName());
    }

    /** 绑定的玩家 UUID；null = 未绑定（任何玩家可用） */
    @Nullable
    public static UUID getBoundUuid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_BOUND_UUID)) {
            return tag.getUUID(TAG_BOUND_UUID);
        }
        return null;
    }

    @Nullable
    public static String getBoundName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_BOUND_NAME)) {
            return tag.getString(TAG_BOUND_NAME);
        }
        return null;
    }

    /** 该克隆体能否用于复活指定玩家（未绑定 = 通用） */
    public static boolean canRevive(ItemStack stack, UUID playerUuid) {
        UUID bound = getBoundUuid(stack);
        return bound == null || bound.equals(playerUuid);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gtoecore.clone.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gtoecore.clone.tooltip.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gtoecore.clone.tooltip.3").withStyle(ChatFormatting.GRAY));
        UUID bound = getBoundUuid(stack);
        if (bound == null) {
            tooltip.add(Component.translatable("gtoecore.clone.unbound").withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("gtoecore.clone.bound_to", getBoundName(stack))
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** 已绑定的克隆体带附魔光效，便于区分 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getBoundUuid(stack) != null;
    }
}
