package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gtoecore.GTNBlocks;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;
import java.util.Objects;

/**
 * 太阳能发电模块 —— 1 层高、7×7 正方形被动发电多方块。
 *
 * <p>结构：四周（含控制器所在前墙）为脱氧钢机械方块
 * （{@code gtceu:solid_machine_casing}，可替换为能量输出仓），
 * 中间 5×5 = 光伏面板下半砖（允许留空，发电量随面板数变化）。</p>
 *
 * <p>发电：每 20 tick 重扫 25 个面板位（打掉面板不破坏结构、只降功率），
 * 每 4 块「上方能看到天空」的面板 = 1A LV（32 EU/t），
 * 白天且非雨天、维度有天空时才发电，电直接填入能量输出仓。</p>
 *
 * <p>面板区相对控制器坐标（7×7 对称，左右方向无需区分）：
 * 控制器在正面中点，面板区 = 控制器后方 1~5 排 × 左右各 2 格。</p>
 */
public class SolarArrayMachine extends MultiblockControllerMachine
        implements IFancyUIMachine, IDisplayUIMachine {

    /** 结构横向半径（7×7，控制器所在排除外，左右各 2） */
    private static final int HALF_WIDTH = 2;
    /** 面板区纵深（控制器后方 1~5 排） */
    private static final int DEPTH = 5;
    /** 面板重扫周期（tick） */
    private static final int RESCAN_INTERVAL = 20;
    /** LV 电压 */
    private static final long LV_VOLTAGE = 32L;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(SolarArrayMachine.class,
                    MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    /** 结构内光伏面板总数（含被遮挡的） */
    @Persisted
    @DescSynced
    private int totalPanels;
    /** 上方能看到天空、实际参与发电的面板数 */
    @Persisted
    @DescSynced
    private int sunlitPanels;
    /** 当前实际输出 EU/t（填入输出仓后的值，GUI 显示用） */
    @Persisted
    @DescSynced
    private long euPerTick;
    /** 0=发电中 1=夜间 2=雨天 3=维度无天空 */
    @Persisted
    @DescSynced
    private int status;

    private TickableSubscription tickSub;
    private int tickCounter;

    public SolarArrayMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- 生命周期 ----------------

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (tickSub == null) {
            // subscribeServerTick 内部已判断 isRemote，客户端返回 null
            tickSub = subscribeServerTick(this::solarTick);
        }
        // 成形立即扫一次，不用等 20 tick
        if (getLevel() != null && !getLevel().isClientSide) {
            rescanPanels();
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        unsubscribe();
        totalPanels = sunlitPanels = 0;
        euPerTick = 0;
        status = 0;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        unsubscribe();
    }

    private void unsubscribe() {
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    // ---------------- 发电逻辑 ----------------

    private void solarTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide || !isFormed()) return;

        if (++tickCounter >= RESCAN_INTERVAL) {
            tickCounter = 0;
            rescanPanels();
        }

        int amps = sunlitPanels / 4;
        long output = amps * LV_VOLTAGE;
        if (output > 0 && canGenerate(level)) {
            euPerTick = pushEnergy(output);
        } else {
            euPerTick = 0;
        }
    }

    /**
     * 把电填入所有能量输出仓，返回实际填入量。
     *
     * <p>注意：{@code MultiblockControllerMachine} 不实现 {@code IRecipeCapabilityHolder}
     * （{@code getCapabilitiesFlat} 是 {@code WorkableMultiblockMachine} 才有的），
     * 所以直接遍历部件找 {@link EnergyHatchPartMachine}（其 {@code energyContainer}
     * 为 public）。{@code changeEnergy} 返回实际填入量（受容量钳制），
     * 且绕过对外 amperage 门控。</p>
     */
    private long pushEnergy(long amount) {
        long remaining = amount;
        for (IMultiPart part : getParts()) {
            if (part instanceof EnergyHatchPartMachine hatch
                    && hatch.energyContainer.getHandlerIO() == IO.OUT) {
                remaining -= hatch.energyContainer.changeEnergy(remaining);
                if (remaining <= 0) return amount;
            }
        }
        return amount - remaining;
    }

    /** 天气/时间/维度判定，同时刷新 status 供 GUI 显示 */
    private boolean canGenerate(Level level) {
        if (!level.dimensionType().hasSkyLight()) {
            status = 3;
            return false;
        }
        if (level.isRaining()) {
            status = 2;
            return false;
        }
        if (!level.isDay()) {
            status = 1;
            return false;
        }
        status = 0;
        return true;
    }

    /**
     * 重扫面板区：统计面板总数与「上方能看到天空」的面板数。
     * 打掉面板不影响结构成形（面板位允许空气），只降低发电功率。
     */
    private void rescanPanels() {
        Level level = getLevel();
        if (level == null) return;
        totalPanels = 0;
        sunlitPanels = 0;
        Direction back = getFrontFacing().getOpposite();
        Direction left = getFrontFacing().getCounterClockWise();
        BlockPos base = getPos();
        for (int d = 1; d <= DEPTH; d++) {
            for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
                BlockPos pos = base.relative(back, d).relative(left, w);
                BlockState state = level.getBlockState(pos);
                if (state.is(GTNBlocks.PV_PANEL.get())
                        && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                    totalPanels++;
                    if (level.canSeeSky(pos.above())) {
                        sunlitPanels++;
                    }
                }
            }
        }
    }

    // ---------------- GUI（与 GTNWorkableMultiblockMachine 同款模板，无配方行） ----------------

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 190, 125);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117)
                .setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .textSupplier(getLevel().isClientSide ? null : this::addDisplayText)
                        .setMaxWidthLimit(200)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return getParts().stream().filter(Objects::nonNull).map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed());
        if (isFormed()) {
            textList.add(Component.translatable("gtoecore.solar.panels", sunlitPanels, totalPanels));
            textList.add(Component.translatable("gtoecore.solar.output", euPerTick, sunlitPanels / 4));
            if (status != 0) {
                textList.add(Component.translatable("gtoecore.solar.status." + status));
            }
        }
        IDisplayUIMachine.super.addDisplayText(textList);
    }
}
