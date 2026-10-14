package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.List;

/**
 * 「机架（rig）类机器」的公共骨架 —— 结构成形/散架日志 + 右键界面的结构状态行。
 *
 * <p>目前有两个子类：{@code ArchRigMachine}（拱架）与 {@code MastRigMachine}（桅杆）。
 * 两者都是<b>纯结构 + 外观</b>的多方块：能成形、能把 OBJ 塔画出来、能把塔的碰撞体
 * 铺出来，但<b>不接任何配方逻辑</b>（不是 {@code Workable*} 系基类，没有
 * {@code RecipeLogic}、没有能量缓冲）。所以本类直接继承最朴素的
 * {@link MultiblockControllerMachine}，并实现 {@link IDisplayUIMachine}
 * 以便右键能打开 GT 原生的界面（显示结构状态与部件列表）。</p>
 *
 * <p>结构尺寸、塔的姿态换算、碰撞体的铺/收全部来自 {@link RigController} ——
 * 与 {@code tower_rig} 用的是同一套机制；<b>底座尺寸则各按自己那具塔的脚印</b>
 * 覆写 {@link RigController#rigPlateHalfLr()} / {@link RigController#rigPlateHalfFb()}
 * （默认值 = tower 的 43 × 23）。</p>
 *
 * <h2>为什么用 {@link IDisplayUIMachine} 而不是 {@code WorkableElectricMultiblockMachine}</h2>
 * <p>后者会带来 {@code RecipeLogic} 与能量仓两个对本机毫无意义的系统：
 * 界面上会多出一条永远空的能量条、一个永远显示「无配方」的状态灯，
 * 而且模型必须带 {@code recipe_logic_status} 变体（4 个状态 × 2 =
 * 8 个多余变体）。用 {@link IDisplayUIMachine} 就只有一个干净的界面：
 * 部件列表 + 一行结构状态。</p>
 *
 * <p>⚠ 因此这两台机器的 model JSON 里<b>不能</b>引用
 * {@code recipe_logic_status} 属性（本机没有这个属性，写了会在模型烘焙时直接崩），
 * 详见 {@code GTNMachines.registerDecorRig} 的注释。</p>
 */
public abstract class AbstractRigMachine extends MultiblockControllerMachine
        implements IDisplayUIMachine, RigController {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 上一次已经打过日志的成形状态（只在状态跳变时打，不刷屏）。 */
    private Boolean lastLoggedFormed = null;

    protected AbstractRigMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 日志与语言键用的短名（= 机器注册名，如 {@code arch_rig}）。 */
    protected String rigName() {
        MachineDefinition definition = getDefinition();
        return definition == null ? "rig" : definition.getId().getPath();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 结构成形 → 把塔的整片碰撞体铺出来（分帧，约 1 秒铺完）
        rigOnFormed();
        if (lastLoggedFormed == null || !lastLoggedFormed) {
            LOGGER.info("[gtoecore] {} FORMED at {} (front={}, upwards={}, origin={}, axisX={}, axisZ={})",
                    rigName(), getPos(), getFrontFacing(), getUpwardsFacing(),
                    rigCollisionOrigin(), rigAxisX(), rigAxisZ());
        }
        lastLoggedFormed = Boolean.TRUE;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // 结构散架 / 换朝向 → 立刻把碰撞体收回（只清本 mod 的方块，别的都不动）
        rigOnInvalid();
        if (lastLoggedFormed == null || lastLoggedFormed) {
            LOGGER.info("[gtoecore] {} structure INVALID at {}", rigName(), getPos());
        }
        lastLoggedFormed = Boolean.FALSE;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        IDisplayUIMachine.super.addDisplayText(textList);
        textList.add(Component.translatable("gtoecore.machine." + rigName()
                + (isFormed() ? ".display" : ".incomplete")));
    }
}
