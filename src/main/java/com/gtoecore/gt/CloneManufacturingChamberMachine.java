package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.ItemRecipeHandler;
import com.gtoecore.GTNItems;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 克隆体制造仓 —— 不耗电的多方块：成形后每 {@value #PRODUCE_INTERVAL} tick（5 分钟）
 * 产出 1 个生物团到输出总线。
 *
 * <p><b>为什么不用 bio 配方（空输入 → 生物团）：</b>{@code GTRecipeLookup.prepareRecipeFind}
 * 在机器没有任何输入 handler 内容时直接返回 null，且 {@code recurseIngredientTreeAdd}
 * 不会给 0 成分的配方在成分树上挂任何查找路径 —— 空输入配方<b>永远无法被匹配</b>。
 * 因此产出逻辑改为机器内定时器实现（成形时订阅 serverTick，失效/卸载退订）。</p>
 *
 * <p>输出总线放满时计时器停在 100% 等待，腾出空间后立即产出并重新计时。</p>
 */
public class CloneManufacturingChamberMachine extends GTNWorkableMultiblockMachine {

    /** 产出周期：6000 tick = 5 分钟 */
    public static final int PRODUCE_INTERVAL = 6000;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(CloneManufacturingChamberMachine.class,
                    GTNWorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 已计时 tick（持久化 + 同步到客户端供 GUI 显示进度） */
    @Persisted
    @DescSynced
    private int produceTimer;

    /** 成形期间的 serverTick 订阅（仅服务端非 null） */
    private TickableSubscription produceSub;

    public CloneManufacturingChamberMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // subscribeServerTick 内部已判断 isRemote，客户端调用返回 null
        if (produceSub == null) {
            produceSub = subscribeServerTick(this::produceTick);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        unsubscribe();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        unsubscribe();
    }

    private void unsubscribe() {
        if (produceSub != null) {
            produceSub.unsubscribe();
            produceSub = null;
        }
    }

    private void produceTick() {
        if (getLevel() == null || getLevel().isClientSide) return;
        if (!isFormed()) return;
        if (produceTimer < PRODUCE_INTERVAL) {
            produceTimer++;
        }
        if (produceTimer >= PRODUCE_INTERVAL) {
            // 输出总线有空间才产出并重置；满了就停在 100% 等待
            if (tryInsertOutput(new ItemStack(GTNItems.BIO_CLUSTER.get()))) {
                produceTimer = 0;
            }
        }
    }

    /** 先整体模拟，全部放得下才真插；放不下返回 false（等待） */
    private boolean tryInsertOutput(ItemStack stack) {
        List<IRecipeHandler<?>> handlers = getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP);
        ItemStack sim = stack.copy();
        for (IRecipeHandler<?> handler : handlers) {
            sim = insertInto(handler, sim, true);
            if (sim.isEmpty()) break;
        }
        if (!sim.isEmpty()) return false;

        ItemStack rest = stack.copy();
        for (IRecipeHandler<?> handler : handlers) {
            rest = insertInto(handler, rest, false);
            if (rest.isEmpty()) break;
        }
        return rest.isEmpty();
    }

    /**
     * 往单个输出 handler 插入物品。
     *
     * <p>⚠️ {@code NotifiableItemStackHandler} 必须走 {@code insertItemInternal}：
     * 总线对外的 {@code insertItem} 有能力门控（export bus 的 capabilityIO=OUT →
     * {@code canCapInput()=false} → 原样退回、一个都插不进），走外部通道会让
     * 产出永远卡在 100%。</p>
     */
    private static ItemStack insertInto(IRecipeHandler<?> handler, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        if (handler instanceof NotifiableItemStackHandler nish) {
            for (int i = 0; i < nish.getSlots() && !stack.isEmpty(); i++) {
                stack = nish.insertItemInternal(i, stack, simulate);
            }
        } else if (handler instanceof ItemRecipeHandler irh) {
            for (int i = 0; i < irh.storage.getSlots() && !stack.isEmpty(); i++) {
                stack = irh.storage.insertItem(i, stack, simulate);
            }
        }
        return stack;
    }

    /** GUI 额外显示：产出进度百分比 */
    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            int percent = Math.min(100, produceTimer * 100 / PRODUCE_INTERVAL);
            textList.add(Component.translatable("gtoecore.manufacture.progress", percent));
        }
    }
}
