package com.gtoecore.deepspace;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;

/**
 * 深空模块的配方修饰器 —— 模块加成的唯一注入点。
 *
 * <p>四种加成分别落在 {@link ModifierFunction.Builder} 的四个维度上：</p>
 * <ul>
 *   <li><b>并行阵列模块</b> → {@code parallels}：先用 {@link ParallelLogic#getParallelAmount}
 *       在「1 + 模块提供的并行数」上限内求实际并行（受输入/输出仓容量钳制，不会溢出）。</li>
 *   <li><b>算力超频模块</b> → {@code durationMultiplier}：配方耗时乘算。</li>
 *   <li><b>能效优化模块</b> → {@code eutMultiplier}：EU 消耗乘算。</li>
 *   <li><b>产出增幅模块</b> → {@code outputModifier}：所有产物乘算。</li>
 * </ul>
 *
 * <p><b>为什么加成要在这里读、而不是在机器构造时算死：</b>模块的加成来自「网络上同类
 * 模块的数量」，而网络每 40 tick 才刷新一次、且枢纽可能随时掉线。修饰器每次配方
 * 处理都会被调用，天然就是「即时读当前网络状态」的最佳位置。</p>
 */
public final class ModuleRecipeModifiers {

    /** 深空模块专用修饰器：把网络聚合加成乘进配方 */
    public static final RecipeModifier MODULE_BONUS = ModuleRecipeModifiers::applyBonus;

    private static ModifierFunction applyBonus(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof DeepSpaceModuleMachine module)) {
            return ModifierFunction.NULL;
        }
        DeepSpaceHubManager.Bonus bonus = module.getBonus();
        // 未挂载（枢纽离线/超距）→ 不加成，按基础单份跑
        if (bonus.isEmpty()) {
            return ModifierFunction.builder().build();
        }

        int maxParallel = Math.max(1, 1 + bonus.bonusParallel());
        int parallel = ParallelLogic.getParallelAmount(machine, recipe, maxParallel);
        if (parallel <= 0) {
            return ModifierFunction.NULL;
        }
        recipe.parallels = 1;

        return ModifierFunction.builder()
                .parallels(parallel)
                .durationMultiplier(bonus.durationFactor())
                .eutMultiplier(bonus.eutFactor())
                .outputModifier(ContentModifier.multiplier(bonus.outputFactor()))
                .build();
    }

    private ModuleRecipeModifiers() {}
}