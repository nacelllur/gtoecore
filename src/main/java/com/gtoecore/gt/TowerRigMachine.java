package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.List;

/**
 * 塔架动力塔（Tower Rig Power Tower）—— 消耗液体燃料发电的 GT 多方块。
 *
 * <h2>从「OBJ 装饰方块」到「真正的 GT 机器」</h2>
 * <p>这台机器原先只是 {@code com.gtoecore.objdemo} 里一个「隐形占位方块 + BER 画 OBJ」
 * 的渲染示范：它没有 {@code MachineDefinition}、没有配方逻辑、没有能量、没有 GT 界面，
 * 也不进 GT 创造栏 —— 换句话说「摆着好看，但什么也不干」。</p>
 *
 * <p>现在它是一台真正的 GTM 多方块：</p>
 * <ul>
 *   <li><b>配方类型</b>直接复用 GTM 现成的
 *       {@code GTRecipeTypes.COMBUSTION_GENERATOR_FUELS}（燃烧发电机燃料）——
 *       玩家往流体输入仓里灌柴油 / 汽油 / 天然气等任意燃料就能发电，
 *       JEI 里已有全部燃料配方，不需要写一条配方；</li>
 *   <li><b>配方逻辑</b>由 {@link WorkableElectricMultiblockMachine} 自带
 *       （GTM 官方的大型燃烧引擎 {@code LargeCombustionEngineMachine} 也是这个基类、
 *       同样没有覆写 {@code createRecipeLogic}，所以本机与它行为一致）；</li>
 *   <li><b>界面</b>是 GT 原生 FancyUI（左侧按钮栏 + 配置器 + 结构状态 + 发电读数）；</li>
 *   <li><b>外观</b>仍是原来那座 {@code tower_rig.obj} 塔 —— 但改由
 *       {@code com.gtoecore.client.TowerRigDynamicRender} 在「结构成形后」把它画在
 *       底座顶面上方。也就是说：<b>摆好结构 → 塔才长出来</b>，塔是机器的视觉，
 *       不再是一个独立方块。</li>
 * </ul>
 *
 * <h2>结构与姿态：全部委托给 {@link RigController}</h2>
 * <p>底座是 43 × 1 × 23 的单层实心脱氧钢平板（控制器嵌在正面边缘的中点），
 * 塔的姿态换算与碰撞体生命周期由 {@link RigController} 统一提供 ——
 * 本类只负责「这台机器用哪张碰撞表」和自己的成形日志。
 * 另外两台同门机器 {@code arch_rig} / {@code mast_rig} 用的是同一个接口，
 * 底座尺寸与逻辑与这里完全一致。</p>
 *
 * <p>⚠ 本机 {@code allowExtendedFacing(false)}（只保留水平朝向）—— 与事件视界引擎同理：
 * 塔是细高的非对称造型，若允许 {@code upwards_facing} 被扳手滚动，结构映射会整体转 90°，
 * 机器会毫无提示地不成形。</p>
 */
public class TowerRigMachine extends WorkableElectricMultiblockMachine implements RigController {

    /**
     * 服务端日志。塔不显示时「机器到底有没有成形」是最关键的一个未知量 ——
     * 客户端塔渲染器有「未成形就不画」的短路，会把这条线索吞掉，
     * 所以在这里（服务端、与渲染无关）明确打一次。
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 本机塔的碰撞体坐标表（6920 格；工具 {@code tools/gen_tower_collision.py}）。 */
    private static final String COLLISION_TABLE = "/assets/gtoecore/structures/tower_rig_collision.txt";

    /** 上一次已经打过日志的成形状态（只在状态跳变时打，不刷屏）。 */
    private Boolean lastLoggedFormed = null;

    public TowerRigMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public String rigCollisionTable() {
        return COLLISION_TABLE;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 结构成形 → 把塔的整片碰撞体铺出来（分帧，约 1.1 秒铺完）
        rigOnFormed();
        if (lastLoggedFormed == null || !lastLoggedFormed) {
            LOGGER.info("[gtoecore] tower_rig FORMED at {} (front={}, upwards={}, origin={}, axisX={}, axisZ={})",
                    getPos(), getFrontFacing(), getUpwardsFacing(), rigCollisionOrigin(),
                    rigAxisX(), rigAxisZ());
        }
        lastLoggedFormed = Boolean.TRUE;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // 结构散架 / 换朝向 → 立刻把碰撞体收回（只清本 mod 的方块，别的都不动）
        rigOnInvalid();
        if (lastLoggedFormed == null || lastLoggedFormed) {
            LOGGER.info("[gtoecore] tower_rig structure INVALID at {}", getPos());
        }
        lastLoggedFormed = Boolean.FALSE;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        textList.add(Component.translatable(isFormed()
                ? "gtoecore.machine.tower_rig.display"
                : "gtoecore.machine.tower_rig.incomplete"));
    }
}
