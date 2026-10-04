package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyAcceptor;
import com.gtoecore.energy.IGTOEnergyHost;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.EnumMap;
import java.util.Map;

/**
 * GTO 能量输入仓（GTM 多方块部件）—— GT6 风味能量接入 GTM 生态的桥梁。
 *
 * <p>工作流：</p>
 * <ul>
 *   <li>贴在多方块结构面上（与 EU 能量仓同款摆放）；</li>
 *   <li>相邻的 GTO 导体（旋转轴/导热管…）把能量推进来（{@link IGTOEnergyAcceptor}）；</li>
 *   <li>多方块主机的配方条件（{@code GTOEnergyRangeCondition}）通过
 *       {@link IGTOEnergyHost} 查询仓内实时值，决定配方是否运行。</li>
 * </ul>
 *
 * <p>仓是"信号透传"语义：每 tick 被导体刷新为当前值，断供 20 tick 自动归零。
 * 不存能量、不消耗能量——配方条件只是"读表"。</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GTOEnergyInputHatchPartMachine extends TieredIOPartMachine
        implements IGTOEnergyAcceptor, IGTOEnergyHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(GTOEnergyInputHatchPartMachine.class, TieredIOPartMachine.MANAGED_FIELD_HOLDER);

    /** 四种能量的实时值（RU=rpm / HU=K / CU=K / KU=kPa） */
    @Persisted
    public final Map<GTOEnergyType, Long> values = new EnumMap<>(GTOEnergyType.class);
    /** 各类型最近一次注入的 gameTime */
    @Persisted
    public final Map<GTOEnergyType, Long> lastInject = new EnumMap<>(GTOEnergyType.class);
    /** 服务端订阅 */
    protected TickableSubscription watchSub;

    public GTOEnergyInputHatchPartMachine(IMachineBlockEntity holder) {
        super(holder, 1, IO.IN);
        for (GTOEnergyType t : GTOEnergyType.values()) {
            values.put(t, 0L);
            lastInject.put(t, -100L);
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            watchSub = subscribeServerTick(this::tickWatcher);
        }
    }

    @Override
    public void onUnload() {
        if (watchSub != null) {
            watchSub.unsubscribe();
            watchSub = null;
        }
        super.onUnload();
    }

    private void tickWatcher() {
        // 断供归零：20 tick 没有导体刷新就按 0 处理
        long now = getLevel().getGameTime();
        for (GTOEnergyType t : GTOEnergyType.values()) {
            if (now - lastInject.get(t) > 20L && values.get(t) != 0L) {
                values.put(t, 0L);
            }
        }
    }

    // ---------------- IGTOEnergyAcceptor（导体推送入口） ----------------

    @Override
    public boolean acceptsGTOEnergy(GTOEnergyType type, Direction side) {
        // 任意面都收：导体从哪个方向贴过来都行
        return true;
    }

    @Override
    public long injectGTOEnergy(GTOEnergyType type, Direction side, int tier, long amount, boolean simulate) {
        if (!simulate) {
            values.put(type, amount);
            lastInject.put(type, getLevel().getGameTime());
        }
        return amount;
    }

    // ---------------- IGTOEnergyHost（配方条件查询入口） ----------------

    @Override
    public long getGTOEnergyValue(GTOEnergyType type) {
        // 含断供检查：条件读到的一定是"有效值"
        long now = getLevel() == null ? 0L : getLevel().getGameTime();
        return (now - lastInject.getOrDefault(type, -100L) <= 20L)
                ? values.getOrDefault(type, 0L) : 0L;
    }

    // ---------------- UI：仓不开放界面（纯信号部件） ----------------

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }
}
