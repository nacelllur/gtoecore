package com.gtoecore.deepspace;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 深空枢纽扩展模块 —— 对标 GTO 通天之路的「模块基座」。
 *
 * <p>每台模块是一个<b>独立的多方块机器</b>（自己的结构、自己跑配方），
 * 放在深空枢纽 {@link DeepSpaceHubManager#SCAN_RADIUS} 格内即自动挂载。
 * 与通天之路模块的差异：通天之路模块跑配方时从电梯借算力；
 * 本模块自己跑配方，但<b>加成来自网络上同类模块的数量</b>——
 * 挂 1 台就是 ×1 份，挂 10 台就是 ×10 份，因此「机器越多越强」，
 * 且数量上没有上限，属于可无限扩张的 EARTH-STAR 式玩法。</p>
 *
 * <p><b>加成注入点：</b>配方修饰器 {@link ModuleRecipeModifiers} 会读本机缓存的
 * {@link DeepSpaceHubManager.Bonus}，把并行 / 耗时 / EU / 产出四项乘进配方。
 * 枢纽离线时加成整体失效（{@link #onNetworkUpdate} 收到 online=false）。</p>
 */
public class DeepSpaceModuleMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(DeepSpaceModuleMachine.class,
                    WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 本模块类型（惰性从方块注册名解析并缓存；见 {@link #getModuleKind()}） */
    private ModuleKind kind;

    /** 是否已挂载到在线的枢纽上 */
    @Persisted
    @DescSynced
    private boolean attached;

    /** 网络等级（枢纽等级 + 传感模块数），GUI 显示用 */
    @Persisted
    @DescSynced
    private int networkTier;

    /** 网络内同类模块数量（GUI 显示「×N 份加成」） */
    @Persisted
    @DescSynced
    private int sameKindCount;

    /** 网络内模块总数 */
    @Persisted
    @DescSynced
    private int networkModuleTotal;

    /** 当前生效的加成快照（不持久化，每次枢纽推送重建） */
    private DeepSpaceHubManager.Bonus bonus = new DeepSpaceHubManager.Bonus();

    /** 挂载的枢纽（不持久化：由枢纽主动推送，枢纽不在线自然为 null） */
    private DeepSpaceHubMachine hub;

    public DeepSpaceModuleMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * 本模块类型 —— 从自身方块注册名反查（{@code gtoecore:parallel_module} → PARALLEL）。
     *
     * <p>为什么不走构造参数：{@code GTRegistrate.multiblock(&lt;id&gt;, factory)} 的 factory
     * 只吃一个 {@link IMachineBlockEntity}，没地方传自定义参数。注册名反查零成本且永不脱节。</p>
     */
    public ModuleKind getModuleKind() {
        if (kind == null) {
            String path = BuiltInRegistries.BLOCK.getKey(self().getBlockState().getBlock()).getPath();
            for (ModuleKind k : ModuleKind.values()) {
                if (k.blockId().equals(path)) {
                    kind = k;
                    break;
                }
            }
            if (kind == null) kind = ModuleKind.PARALLEL;
        }
        return kind;
    }

    public boolean isAttached() {
        return attached;
    }

    /** 配方修饰器读它拿并行/耗时/EU/产出倍率 */
    public DeepSpaceHubManager.Bonus getBonus() {
        return attached ? bonus : new DeepSpaceHubManager.Bonus();
    }

    // ---------------- 枢纽推送 ----------------

    /**
     * 枢纽推送网络状态（{@link DeepSpaceHubManager#refreshNetwork} 每 40 tick 调一次）。
     *
     * @param hub    枢纽（null = 下线/失联）
     * @param bonus  该网络的聚合加成（null = 下线）
     * @param online 枢纽是否在线
     */
    public void onNetworkUpdate(DeepSpaceHubMachine hub, DeepSpaceHubManager.Bonus bonus, boolean online) {
        this.hub = online ? hub : null;
        this.bonus = (online && bonus != null) ? bonus : new DeepSpaceHubManager.Bonus();
        this.attached = online && hub != null;
        if (this.attached) {
            this.networkTier = hub.getNetworkTier();
            this.sameKindCount = this.bonus.count(getModuleKind());
            this.networkModuleTotal = this.bonus.totalModules();
        } else {
            this.networkTier = 0;
            this.sameKindCount = 0;
            this.networkModuleTotal = 0;
        }
    }

    // ---------------- 生命周期 ----------------

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        detached();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        detached();
    }

    private void detached() {
        attached = false;
        hub = null;
        bonus = new DeepSpaceHubManager.Bonus();
        sameKindCount = 0;
        networkModuleTotal = 0;
        networkTier = 0;
    }

    // ---------------- GUI ----------------

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive())
                .addWorkingStatusLine()
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(getTier())
                .addProgressLine(recipeLogic);
        // 父类 WorkableElectricMultiblockMachine 已实现该方法，走 super
        super.addDisplayText(textList);

        if (!isFormed()) return;
        ModuleKind kind = getModuleKind();
        textList.add(Component.translatable("gtoecore.deepspace.module.kind",
                Component.translatable(kind.cn())));
        textList.add(Component.translatable(kind.bonusKey()));

        if (attached) {
            textList.add(Component.translatable("gtoecore.deepspace.module.attached",
                    networkTier, sameKindCount, networkModuleTotal));
        } else {
            textList.add(Component.translatable("gtoecore.deepspace.module.detached"));
        }

        if (bonus != null && !bonus.isEmpty()) {
            textList.add(Component.translatable("gtoecore.deepspace.module.net_bonus",
                    bonus.bonusParallel(),
                    String.format("%.2f", bonus.durationFactor()),
                    String.format("%.2f", bonus.eutFactor()),
                    String.format("%.2f", bonus.outputFactor())));
        }
    }
}