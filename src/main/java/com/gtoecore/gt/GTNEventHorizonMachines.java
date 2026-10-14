package com.gtoecore.gt;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gtoecore.GTNBlocks;
import com.gtoecore.client.ArchRigDynamicRender;
import com.gtoecore.client.BladeRigDynamicRender;
import com.gtoecore.client.CmsRigDynamicRender;
import com.gtoecore.client.DrumRigDynamicRender;
import com.gtoecore.client.EventHorizonRenderer;
import com.gtoecore.client.LhcComboRigDynamicRender;
import com.gtoecore.client.MaimaiRigDynamicRender;
import com.gtoecore.client.MastRigDynamicRender;
import com.gtoecore.client.TowerRigDynamicRender;
import com.gtoecore.util.GTNPreview;
import com.gtoecore.deepspace.DeepSpaceHubMachine;
import com.gtoecore.deepspace.DeepSpaceModuleMachine;
import com.gtoecore.deepspace.ModuleKind;
import com.gtoecore.deepspace.ModuleRecipeModifiers;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 事件视界引擎 —— 35³ 舱壁球壳 + 黑洞/日冕特效。
 * 从 {@link GTNMachines} 拆出（2026-10-14）。
 */
public final class GTNEventHorizonMachines {

    // ------------------------------------------------------------------
    // 事件视界引擎（Event Horizon Engine）—— 35³ 六面舱壁球壳 + 黑洞/日冕特效
    // ------------------------------------------------------------------

    /**
     * 事件视界引擎 —— 35³ 球壳里关着一个黑洞（或一团日冕）。
     *
     * <p><b>结构</b>：{@link GTNEventHorizonStructure} 直接投影自
     * {@code 144.litematic} —— 3395 格 {@code hull_panel_hex} 六面舱壁构成的球壳，
     * 内部是 GTNA 骨架（6 根轴柱 + 赤道带 casing + 16 格 annihilate_core），
     * 外加一点 AE2 装饰（纯外观，不参与成形）。网格走资源文件，
     * 由 {@code tools/gen_eh_structure.py} 生成。</p>
     *
     * <p><b>控制器位置</b>：球壳赤道面上（aisle=35/row=17/char=18），
     * 球壳几何中心在控制器 BACK 方向 17 格 —— 与旧结构完全一致，
     * 所以 {@link EventHorizonRenderer} 的 {@code CENTER_OFFSET = 17.0} 不用改。</p>
     *
     * <p><b>特效</b>：{@link EventHorizonRenderer}。UI 里有 1/2 模式按钮：
     * 1 = 烘焙黑洞（引力透镜帧集公告板），2 = 真 3D 碗口日冕（黑碗 + 白热光子环）。</p>
     *
     * <p>⚠ 2026-10-07 结构已从「球面线框笼」换成「实心舱壁球壳」⇒
     * 存档里已摆好的旧机器<b>必定不成形，必须拆掉重摆</b>。</p>
     */
    public static MultiblockMachineDefinition registerEventHorizonEngine() {
        return GTNMachines.REGISTRATE
                .multiblock("event_horizon_engine", EventHorizonMachine::new)
                .langValue("Event Horizon Engine")
                // 控制器贴在球面外侧的水平面上 ⇒ 只用水平朝向
                .rotationState(RotationState.NON_Y_AXIS)
                // ⚠ 关掉扩展朝向（照抄 GTNA 的 eye_of_harmony）：
                //   开着的话 block 会多出 upwards_facing 属性、16 种状态，
                //   而 checkPatternAt 用的是控制器**真实的** upwards_facing
                //   —— 球面线框只关于 x↔z 互换对称、不关于 y↔z 互换，
                //   一旦玩家用扳手敲正面把 upwards 滚成东/南/西，
                //   结构映射就整体转 90°，机器会毫无提示地不成形。
                .allowExtendedFacing(false)
                .recipeType(GTNRecipeTypes.EVENT_HORIZON_RECIPES)
                .recipeModifiers(GTRecipeModifiers.OC_NON_PERFECT_SUBTICK,
                        GTRecipeModifiers.BATCH_MODE)
                .tooltips(
                        Component.translatable("gtoecore.machine.event_horizon_engine.tooltip.1"),
                        Component.translatable("gtoecore.machine.event_horizon_engine.tooltip.2"),
                        Component.translatable("gtoecore.machine.event_horizon_engine.tooltip.3"))
                // 外壳现在是六面舱壁 —— JEI 里显示它（而不是旧的 singularity 外壳）
                .appearanceBlock(GTNBlocks.HULL_PANEL_HEX)
                .pattern(GTNEventHorizonStructure::create)
                // 有 RecipeLogic ⇒ 模型必须带 recipe_logic_status 变体，否则紫黑格
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                .model(GTMachineModels.createWorkableCasingMachineModel(
                                GTNCoreGT.id("block/casing/singularity_constraint_casing"),
                                GTNCoreGT.id("block/multiblock/event_horizon_engine"))
                        .andThen(b -> b.addDynamicRenderer(EventHorizonRenderer::new)))
                .hasBER(true)
                .register();
    }
    private GTNEventHorizonMachines() {
    }
}
