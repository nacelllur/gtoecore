package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;

/**
 * 聚变光源核心（Lumina Fusion Core）—— 与 GT 聚变反应堆各种方面完全一致，
 * 仅主方块有区别，专门用来产生「聚变光源」（光凝流生产链路的第一环）。
 *
 * <p>直接继承 {@link FusionReactorMachine}：</p>
 * <ul>
 *   <li>配方逻辑 100% 复用：{@code recipeModifier}（eu_to_start 预热 + FUSION_OC 超频）、
 *       {@code onWorking}/{@code updateHeat}（热管理）、能量仓聚合等全部继承；</li>
 *   <li>主方块不同：本机由 REGISTRATE 生成自己的控制器方块（gtoecore:lumina_fusion_core），
 *       外观用 GTOE 专属外壳贴图，不引用 GT 的 fusion casing；</li>
 *   <li>配方类型仍用 {@code GTRecipeTypes.FUSION_RECIPES}（eu_to_start 预热、2 流体进 1 流体出）。</li>
 * </ul>
 *
 * <p>产出：聚变反应产生的「聚变光源」—— 为光凝流（Luminofluid）生产链路提供光子源。</p>
 */
public class LuminaFusionCoreMachine extends FusionReactorMachine {

    protected static final com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder(
                    LuminaFusionCoreMachine.class, FusionReactorMachine.MANAGED_FIELD_HOLDER);

    public LuminaFusionCoreMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
    }

    @Override
    public com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
