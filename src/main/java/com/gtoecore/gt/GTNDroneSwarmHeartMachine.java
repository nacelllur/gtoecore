package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtoecore.util.GTNStructureFixup;

/**
 * 无人机蜂群之心 —— 在 GTM 的 {@link WorkableElectricMultiblockMachine} 之上，
 * 只加了一件事：结构成形后把装饰方块（按钮 / 楼梯 / 半砖）的朝向还原成投影的样子。
 *
 * <p>为什么必须换成本类：GTM 终端「自动建造」走
 * {@code BlockPattern.autoBuild()}，它按 pattern 摆位置、按"玩家视线"定朝向，
 * 从不读投影 ⇒ 摆出来的按钮/楼梯方向必错。原来的
 * {@code WorkableElectricMultiblockMachine::new} 是 GTM 的类，没有可覆盖的成形回调，
 * 所以这里继承一层，拿到 {@code onStructureFormed()}。</p>
 *
 * <p>修复逻辑与坐标推导见 {@link GTNStructureFixup}；
 * 回归验收脚本 {@code tools/verify_structfix.py}。</p>
 */
public class GTNDroneSwarmHeartMachine extends WorkableElectricMultiblockMachine {

    public GTNDroneSwarmHeartMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 安排到下一个服务端 tick：此刻仍在 checkPattern() 的 patternLock 里，不能改方块
        GTNStructureFixup.scheduleRepair(this);
    }
}
