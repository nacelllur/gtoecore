package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 事件视界引擎（Event Horizon Engine）—— 巨型电动多方块。
 *
 * <p>结构见 {@link GTNEventHorizonStructure}（35³ 球面线框笼，中央是黑洞）。</p>
 *
 * <p>逻辑侧刻意保持最薄：直接继承 {@link WorkableElectricMultiblockMachine}
 * （GTM 原生 FancyUI + 显示器 + 配方逻辑），只加一点界面文本与「视觉模式」开关。
 * 特效全部由 {@code com.gtoecore.client.EventHorizonRenderer} 在客户端现算，
 * 服务端只同步一个 {@link #renderMode} 整数。</p>
 *
 * <h2>视觉模式（renderMode）</h2>
 * <ul>
 *   <li><b>1 = 黑洞</b>：默认。离线烘焙的引力透镜帧集（{@code blackhole_iXX.png}），
 *       以固定世界尺寸的公告板呈现；</li>
 *   <li><b>2 = 日冕</b>：纯 <b>3D 球体几何</b> —— 不透明黑球 + 菲涅尔边缘光 +
 *       湍流燃烧状光晕 + 下缘光柱。从任何角度看都是球，
 *       不像公告板那样「转到侧面就露馅」。</li>
 * </ul>
 *
 * <p><b>为什么字段上要 {@code @DescSynced} + {@code @Persisted}：</b>
 * 渲染器在客户端跑，必须读到服务端的值。{@code @DescSynced} 把值送进描述包
 * （别人进视野 / 打开 GUI 也能看到正确模式），{@code @Persisted} 让存档重载保留选择。
 * <b>两者都要求本类拥有自己的 {@link ManagedFieldHolder}</b>，否则这些字段不会被扫描。</p>
 *
 * <p><b>⚠ 按钮挂载点：</b>{@code attachConfigurators} 是 GT FancyUI 左侧竖排按钮栏的
 * 扩展点（GT 自己的电源 / 软锤 / 批量模式都挂这里）。父类实现<b>必须先调用</b>
 * （里面有虚空保护与批量模式，不能吞掉）。按钮点击的回调在服务端执行
 * （{@code Tab.onClick → writeUpdateInfo}），所以直接改字段即可，不需要自己发包。</p>
 */
public class EventHorizonMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EventHorizonMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 视觉模式：黑洞（默认，烘焙帧集） */
    public static final int MODE_BLACK_HOLE = 1;
    /** 视觉模式：日冕（3D 黑球 + 燃烧光晕） */
    public static final int MODE_CORONA = 2;
    /** 模式总数（切换时循环用） */
    public static final int MODE_COUNT = 2;

    /**
     * 视觉模式 —— 1 = 黑洞（烘焙帧集），2 = 日冕（3D 球体）。
     *
     * <p>{@code @DescSynced} 把值同步到客户端（渲染器读它），
     * {@code @Persisted} 让存档重载后保留选择。</p>
     */
    @Persisted
    @DescSynced
    private int renderMode = MODE_BLACK_HOLE;

    public EventHorizonMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- 视觉模式 ----------------

    /** 当前视觉模式（1 = 黑洞，2 = 日冕）。客户端读到的是同步后的值。 */
    public int getRenderMode() {
        return renderMode;
    }

    /**
     * 设置视觉模式。越界会被夹到合法区间；值真的变了才落盘 + 同步，
     * 避免重复点击产生多余的描述包。
     */
    public void setRenderMode(int mode) {
        int clamped = Math.max(MODE_BLACK_HOLE, Math.min(MODE_COUNT, mode));
        if (clamped != renderMode) {
            renderMode = clamped;
            // LDLib 的周期编辑检查会发现 @DescSynced 字段变化并下发；
            // markDirty 保证存档也立刻跟上（切换模式后立刻退游戏也不会丢）
            markDirty();
        }
    }

    /** 在两种模式间循环切换，返回切换后的值。 */
    public int cycleRenderMode() {
        setRenderMode(renderMode >= MODE_COUNT ? MODE_BLACK_HOLE : renderMode + 1);
        return renderMode;
    }

    /**
     * 挂「视觉模式」按钮到 FancyUI 左侧按钮栏。
     *
     * <p>用 {@link IFancyConfiguratorButton.Toggle}：弹起 = 模式 1（黑洞），
     * 按下 = 模式 2（日冕）。图标是底纹 + 居中放大的「1」「2」文字 ——
     * 比借用一个语义不符的 GT 贴图直观，也不用发布新贴图。</p>
     */
    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        // 父类必须先走：里面挂着虚空保护与批量模式，不能吞掉
        super.attachConfigurators(configuratorPanel);

        configuratorPanel.attachConfigurators(
                new IFancyConfiguratorButton.Toggle(
                        modeIcon("1", 0xFFDDE6FF),
                        modeIcon("2", 0xFFFFB060),
                        // 按下状态 == 模式 2
                        () -> renderMode == MODE_CORONA,
                        (clickData, pressed) -> setRenderMode(pressed ? MODE_CORONA : MODE_BLACK_HOLE))
                        .setTooltipsSupplier(pressed -> List.of(
                                Component.translatable("gtoecore.machine.event_horizon_engine.mode",
                                        Component.translatable(pressed
                                                ? "gtoecore.machine.event_horizon_engine.mode.corona"
                                                : "gtoecore.machine.event_horizon_engine.mode.black_hole")),
                                Component.translatable("gtoecore.machine.event_horizon_engine.mode.hint")))
        );
    }

    /** 16×16 图标区：半透明底纹 + 居中放大的数字。 */
    private static IGuiTexture modeIcon(String digit, int argb) {
        return new GuiTextureGroup(
                ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF2A2A2A),
                new TextTexture(digit)
                        .setColor(argb)
                        .setDropShadow(false)
                        .setWidth(16));
    }

    // ---------------- 界面文本 ----------------

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) {
            return;
        }
        boolean working = getRecipeLogic().isWorking();
        textList.add(Component.translatable(working
                ? "gtoecore.machine.event_horizon_engine.status.working"
                : "gtoecore.machine.event_horizon_engine.status.idle"));
        textList.add(Component.translatable("gtoecore.machine.event_horizon_engine.mode",
                Component.translatable(renderMode == MODE_CORONA
                        ? "gtoecore.machine.event_horizon_engine.mode.corona"
                        : "gtoecore.machine.event_horizon_engine.mode.black_hole")));
    }
}