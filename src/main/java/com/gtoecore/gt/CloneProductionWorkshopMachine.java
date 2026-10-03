package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.ItemRecipeHandler;
import com.gtoecore.GTNItems;
import com.gtoecore.item.GTNCloneItem;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 克隆体生产车间 —— 无配方、不耗电的复活专用多方块。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>只接受输入总线（存放克隆体），无线程仓 / 无维护舱 / 无能源舱；</li>
 *   <li>克隆体本身记录绑定玩家（{@link GTNCloneItem}，右键空气绑定）；</li>
 *   <li>硬核（极限）模式下，玩家死亡时若本机输入总线内有<b>可用于该玩家</b>的克隆体
 *       （绑定了该玩家，或未绑定），则拦截死亡、消耗 1 个克隆体，
 *       在结构内的床上满血复活 + 重设出生点。</li>
 * </ol>
 *
 * <p>不检测配方：本机 recipeType 仍是 bio，但 pattern 只开放输入仓（无流体/能源仓），
 * bio 配方（维护室「水+生物团→克隆体」需要流体仓）无法匹配 → recipeLogic 恒空闲。</p>
 *
 * <p>全局注册表 {@link #FORMED} 供死亡事件查找；服务端维护。</p>
 */
public class CloneProductionWorkshopMachine extends GTNWorkableMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(CloneProductionWorkshopMachine.class, GTNWorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 服务端：所有已形成的生产车间（供死亡事件查找） */
    private static final Set<CloneProductionWorkshopMachine> FORMED = ConcurrentHashMap.newKeySet();

    /** 床的绝对位置（结构形成时记录；用 long 便于持久化） */
    @Persisted
    private long bedPosLong;

    public CloneProductionWorkshopMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 从 pattern 匹配上下文里读出「bed」槽位标记的位置（见 GTNMachines 的床谓词 setSlotName("bed")）
        Long2ObjectMap<Set<String>> slots =
                (Long2ObjectMap<Set<String>>) (Object) getMultiblockState().getMatchContext().get("slots");
        if (slots != null) {
            for (Long2ObjectMap.Entry<Set<String>> entry : slots.long2ObjectEntrySet()) {
                if (entry.getValue() != null && entry.getValue().contains("bed")) {
                    this.bedPosLong = entry.getLongKey();
                    break;
                }
            }
        }
        FORMED.add(this);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        FORMED.remove(this);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        FORMED.remove(this);
    }

    /** 输入总线内可用于复活指定玩家的克隆体数量（未绑定的克隆体对任何玩家可用） */
    public int countUsableClones(UUID playerUuid) {
        int count = 0;
        Item clone = GTNItems.CLONE.get();
        for (IItemHandler handler : inputItemViews()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.is(clone) && GTNCloneItem.canRevive(stack, playerUuid)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /** 消耗 1 个可用于复活指定玩家的克隆体；没有则返回 false */
    public boolean consumeCloneFor(UUID playerUuid) {
        Item clone = GTNItems.CLONE.get();
        for (IRecipeHandler<?> handler : getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP)) {
            if (handler instanceof NotifiableItemStackHandler nish) {
                // ⚠️ 必须走 extractItemInternal：总线对外的 extractItem 有能力门控
                //（import bus 的 capabilityIO=IN → canCapOutput()=false → 静默返回空堆），
                // 走外部通道会"复活成功但克隆体没扣"。
                for (int i = 0; i < nish.getSlots(); i++) {
                    ItemStack stack = nish.getStackInSlot(i);
                    if (stack.is(clone) && GTNCloneItem.canRevive(stack, playerUuid)
                            && !nish.extractItemInternal(i, 1, false).isEmpty()) {
                        return true;
                    }
                }
            } else if (handler instanceof ItemRecipeHandler irh) {
                for (int i = 0; i < irh.storage.getSlots(); i++) {
                    ItemStack stack = irh.storage.getStackInSlot(i);
                    if (stack.is(clone) && GTNCloneItem.canRevive(stack, playerUuid)
                            && !irh.storage.extractItem(i, 1, false).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 把 GT 的两种物品 handler 统一看成 IItemHandler */
    private static IItemHandler asItemHandler(IRecipeHandler<?> handler) {
        if (handler instanceof NotifiableItemStackHandler nish) return nish;
        if (handler instanceof ItemRecipeHandler irh) return irh.storage;
        return null;
    }

    /** 所有输入侧物品仓（输入总线）统一成 IItemHandler 视图 */
    private Iterable<IItemHandler> inputItemViews() {
        return () -> getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).stream()
                .map(CloneProductionWorkshopMachine::asItemHandler)
                .filter(java.util.Objects::nonNull)
                .iterator();
    }

    /** 床上满血复活 + 重设出生点 */
    public void resurrectPlayer(ServerPlayer player) {
        BlockPos bed = bedPosLong != 0 ? BlockPos.of(bedPosLong) : getPos();
        // 1. 重设出生点（床的位置）
        player.setRespawnPosition(getLevel().dimension(), bed, 0.0F, true, true);
        // 2. 满血传送 + 清状态
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.teleportTo(bed.getX() + 0.5, bed.getY() + 1.0, bed.getZ() + 0.5);
    }

    /** GUI 额外显示：输入总线里（通用）克隆体数量 */
    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed() && getLevel() != null && !getLevel().isClientSide) {
            // 服务器侧统计：未绑定克隆体对所有玩家通用，绑定克隆体按玩家区分，
            // GUI 无法得知“当前玩家”以外的绑定情况，这里统计总数即可。
            int total = 0;
            Item clone = GTNItems.CLONE.get();
            for (IItemHandler handler : inputItemViews()) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.is(clone)) total += stack.getCount();
                }
            }
            textList.add(Component.translatable("gtoecore.workshop.clones", total));
        }
    }

    /** 供死亡事件遍历所有已形成的生产车间 */
    public static Set<CloneProductionWorkshopMachine> formedMachines() {
        return FORMED;
    }
}
