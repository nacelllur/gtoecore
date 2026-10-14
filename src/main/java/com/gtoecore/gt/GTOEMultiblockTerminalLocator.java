package com.gtoecore.gt;

import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 多方块终端的自定义 {@link MenuLocator}。
 *
 * <p><b>为什么需要它</b>：AE2 自带的方块定位器 {@code BlockEntityLocator} 是**包私有**的
 * （只能通过 {@code MenuLocators.forBlockEntity(be)} 拿到实例），我们想在
 * {@link appeng.menu.MenuOpener#open} 里从 BlockPos 自行定位、并保留自己的类型判断，
 * 就注册一个自己的定位器。</p>
 *
 * <p><b>⚠ 关键</b>：{@code locate} 必须返回 <b>BlockEntity 本身</b>（{@link CraftingTerminalBlockEntity}），
 * 而不是 {@code MetaMachine} —— AE2 的 {@code AEBaseMenu} 只接受
 * BlockEntity / IPart / ItemMenuHost 当宿主，返回机器体会直接抛
 * {@code IllegalArgumentException: Must have a valid host}。
 * 语义与 AE2 自带 {@code BlockEntityLocator} 一致：{@code level.getBlockEntity(pos)} + {@code isInstance}，
 * 服务端与客户端走同一条路径（客户端也有同一个 BE）。</p>
 *
 * <p><b>注册</b>：{@link #register()} 在 mod 构造期调用一次。
 * 序列化只需 BlockPos。</p>
 */
public final class GTOEMultiblockTerminalLocator implements MenuLocator {

    private final BlockPos pos;

    public GTOEMultiblockTerminalLocator(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public <T> T locate(Player player, Class<T> clazz) {
        BlockEntity be = player.level().getBlockEntity(pos);
        return be != null && clazz.isInstance(be) ? clazz.cast(be) : null;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public String toString() {
        return "GTOEMultiblockTerminalLocator{" + pos + "}";
    }

    public static GTOEMultiblockTerminalLocator readFromPacket(FriendlyByteBuf buf) {
        return new GTOEMultiblockTerminalLocator(buf.readBlockPos());
    }

    /** 注册到 AE2 定位器注册表（mod 构造期调用一次，重复注册会抛异常）。AE2 缺失时静默跳过。 */
    public static void register() {
        try {
            MenuLocators.register(GTOEMultiblockTerminalLocator.class,
                    GTOEMultiblockTerminalLocator::writeToPacket,
                    GTOEMultiblockTerminalLocator::readFromPacket);
        } catch (Throwable t) {
            // AE2 未加载：终端机器照常注册，只是右键打不开 AE 界面
            com.lowdragmc.lowdraglib.LDLib.LOGGER.warn("[gtoecore] AE2 not present, multiblock AE terminal disabled: {}", t.toString());
        }
    }
}
