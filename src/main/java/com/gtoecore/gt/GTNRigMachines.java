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
 * 机架类机器（八台）—— 单层脱氧钢底座 + 底座顶面上的 OBJ 塔。
 *
 * <p>本文件含全套共用基建：{@code registerDecorRig}（两参便捷版 / 四向完整版）、
 * {@code rigPattern}（两参 / 四向）、{@code rigShapeInfo}（两参 / 四向）。
 * 从 {@link GTNMachines} 拆出（2026-10-14），逻辑与注册顺序完全不变。</p>
 */
public final class GTNRigMachines {

    // ------------------------------------------------------------------
    // 塔架动力塔（Tower Rig Power Tower）
    //
    // 原来的 gtoecore:tower_rig 只是 objdemo 里的「隐形占位 + BER 画 OBJ」渲染示范：
    // 摆着好看但什么也不干。现在它是真正的 GTM 多方块 —— 结构塔基 + 成形后长出的塔，
    // 烧液体燃料发电。塔的视觉走 DynamicRender（见 TowerRigDynamicRender 的类注释）。
    // ------------------------------------------------------------------

    /**
     * 塔架动力塔 —— 43 × 1 × 23 的<b>单层实心脱氧钢底座</b>，控制器嵌在正面边缘的中点；
     * 底座顶面之上由 {@code TowerRigDynamicRender} 画出 {@code tower_rig.obj} 塔。
     *
     * <p><b>结构（aisle 从背面到正面；本结构只有 1 层，所以每个 aisle 只有 1 个字符串）：</b></p>
     * <ul>
     *   <li>23 张切片全是同一行 43 个 {@code #}（脱氧钢），只有<b>正面</b>那一切片的
     *       中点换成控制器 {@code S}；</li>
     *   <li>底座共 43 × 23 = 989 格，1 格是控制器，其余 988 格全是脱氧钢 ——
     *       任意位置都可以换成仓室，也就是「边缘位置放舱室」；</li>
     *   <li>本机的配方类型是 {@code COMBUSTION_GENERATOR_FUELS}（EU 输出 + 流体输入），
     *       所以 {@code autoAbilities} 会自动开放<b>能量输出仓</b>（下限 1，必需）
     *       与<b>流体输入仓</b>；</li>
     *   <li>维护仓用「可选槽」写法（{@code setMaxGlobalLimited(1)}，不设下限）——
     *       不强制玩家放维护仓。</li>
     * </ul>
     *
     * <p><b>为什么是 43 × 23 而不是正好等于塔脚印的 41 × 21：</b>塔底是一块 1.7 格厚的
     * <b>实心台</b>（OBJ 里的 {@code foot_edge} 伸到 x ±19.97 / z ±10.51）—— 41 × 21 的
     * 底座会被它 100% 盖死，连控制器都点不到（右键不出界面）。每边多留一圈（+1 格）正好
     * 给出一条可站立的边；而落地碰撞体的坐标表本来就不含最外圈（生成时用了
     * {@code --inset 0.02}，0.01 格的发丝级溢出不会刷出整格），所以那一条边是空的。
     * 详见 {@link TowerRigMachine} 与 {@code RigCollision} 的类注释。</p>
     *
     * <p><b>三台机器的底座尺寸互不相同</b>（都是「自己那具塔的脚印 + 一圈」）：
     * tower 43 × 23 / arch 31 × 19 / mast 21 × 17 —— 尺寸定义在各自的机器类里
     * （{@code PLATE_HALF_LR / PLATE_HALF_FB}），{@link #registerTowerRig} 与
     * {@link #registerDecorRig} 显式传给下面的生成器。
     * {@code RigController} 只提供<b>默认值</b>（= tower 的 21 / 11）。</p>
     *
     * <p><b>⚠ 结构尺寸变了要同步改三处：</b>机器类里的 {@code PLATE_HALF_LR} /
     * {@code PLATE_HALF_FB} 常量<b>与</b>它覆写的 {@code rigPlateHalfLr()} /
     * {@code rigPlateHalfFb()}（渲染器和碰撞体读的是方法，pattern 读的是常量 ——
     * 两者必须一致）、该机器自己的碰撞体坐标表
     * {@code assets/gtoecore/structures/}<i>&lt;name&gt;</i>{@code _collision.txt}
     * （重烤：{@code tools/gen_tower_collision.py <obj> <out> --scale 4.45 --keep-origin
     * --clamp-x <半宽> --clamp-z <半深>}），
     * 以及 lang 里 tooltip 中写死的尺寸文案。
     * 本文件的 {@link #rigPattern} / {@link #rigShapeInfo} <b>不用改</b>（尺寸是入参）。
     * {@code blockstates/*.json} 不用动 —— {@code allowExtendedFacing(false)}
     * ⇒ 只有 4 个 facing 变体，不随结构尺寸变。</p>
     */
    public static MultiblockMachineDefinition registerTowerRig() {
        return GTNMachines.REGISTRATE
                .multiblock("tower_rig", TowerRigMachine::new)
                .langValue("Tower Rig Power Tower")
                .rotationState(RotationState.NON_Y_AXIS)
                // 塔是细高的非对称造型 ⇒ 关掉扩展朝向（同事件视界引擎）：
                // 开着的话 block 会多出 upwards_facing 属性（16 种状态），
                // 而 checkPatternAt 用的是控制器真实的 upwards_facing ——
                // 玩家用扳手敲正面把它滚 90°，结构映射跟着整体转，机器会
                // 毫无提示地不成形。
                .allowExtendedFacing(false)
                // 复用 GTM 现成的燃料发电机配方类型：玩家灌任意可燃液体就能发电，
                // JEI 里已有全部燃料配方。EU 是「输出」（setEUIO(IO.OUT)）。
                .recipeType(GTRecipeTypes.COMBUSTION_GENERATOR_FUELS)
                .recipeModifiers(GTRecipeModifiers.OC_NON_PERFECT)
                .tooltips(
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.1"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.2"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.3"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.4"),
                        Component.translatable("gtoecore.machine.tower_rig.tooltip.5"))
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                // 结构与预览由三台机架机器共用（见 rigPattern / rigShapeInfo）；
                // 只有本机是「可工作」的 ⇒ 外壳谓词额外开放配方需要的能力仓。
                // 底座尺寸显式传 tower 的 43 × 23（= RigController 的默认值）。
                .pattern(definition -> rigPattern(definition, true,
                        RigController.PLATE_HALF_LR, RigController.PLATE_HALF_FB))
                .shapeInfo(definition -> rigShapeInfo(definition, true,
                        RigController.PLATE_HALF_LR, RigController.PLATE_HALF_FB))
                // 有 RecipeLogic ⇒ 模型必须带 recipe_logic_status 变体，否则紫黑格
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                // 视觉：控制器本体还是常规 GT 外壳 + 覆层；塔由动态渲染器画在底座顶面上方
                .model(GTMachineModels.createWorkableCasingMachineModel(
                                GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                                GTCEu.id("block/multiblock/generator/large_combustion_engine"))
                        .andThen(b -> b.addDynamicRenderer(TowerRigDynamicRender::new)))
                .hasBER(true)
                .register();
    }

    /**
     * 拱架（{@code arch_rig}）/ 桅杆（{@code mast_rig}）/ 滚筒（{@code drum_rig}）/
     * 叶片（{@code blade_rig}）/ CMS（{@code cms_rig}）—— 与 {@code tower_rig}
     * <b>同一种底座</b>（单层实心脱氧钢、控制器嵌在正面边缘中点、尺寸 = 自己那具塔的
     * 脚印 + 一圈），只是把底座上的塔换成另一具 OBJ 模型，并且<b>不接配方逻辑</b>
     * （基类是 {@code MultiblockControllerMachine}，不是 {@code Workable*} 系）。
     *
     * <p><b>⚠ 无 RecipeLogic ⇒ 模型不能带 {@code recipe_logic_status} 变体</b>：
     * 那台机器的方块根本没有这个属性，写了会在模型烘焙时直接崩。
     * 所以这里用 {@code createOverlayCasingMachineModel}（全状态同一模型），
     * 手写的资源 JSON 里也只有 {@code is_formed=false / true} 两个变体。</p>
     *
     * <p>外壳谓词也不开放 {@code autoAbilities} —— 本机没有任何配方，
     * 因此不需要能量仓 / 流体仓；只保留一个<b>可选</b>维护仓槽。
     * 预览里 {@code E}/{@code F} 的位置因此摆的是普通脱氧钢（见 {@link #rigShapeInfo}），
     * 否则 JEI 预览会不成形。</p>
     *
     * <p><b>这是「控制器贴正面边缘」形态的便捷重载</b>，等价于
     * {@code (halfLr, halfLr, halfFb × 2, 0, 3)}。底座形不成这种形态的机器
     * （尺寸是偶数、或干脆不要底座）请用下面那个完整重载。</p>
     *
     * @param plateHalfLr 底座左右半宽（格）—— 传机器类自己的 {@code PLATE_HALF_LR}，
     *                    必须与该机器 {@code rigPlateHalfLr()} 的返回值一致
     * @param plateHalfFb 底座前后半深（格）—— 同上，对应 {@code rigPlateHalfFb()}
     */
    public static MultiblockMachineDefinition registerDecorRig(
            String name,
            Function<IMachineBlockEntity, ? extends MultiblockControllerMachine> factory,
            String langValue,
            String tooltipKey,
            Supplier<com.gregtechceu.gtceu.client.renderer.machine.DynamicRender<?, ?>> renderer,
            int plateHalfLr,
            int plateHalfFb) {
        return registerDecorRig(name, factory, langValue, tooltipKey, renderer,
                plateHalfLr, plateHalfLr, plateHalfFb * 2, 0, 3);
    }

    /**
     * 同上，但底座的四向延伸与 tooltip 行数都是显式的（2026-10-13 新增）。
     *
     * <p>老机器的底座一律「控制器贴正面边缘、平板整个长在背面」，用一对半宽 / 半深
     * 就够；这台重载留给两种表达不了的形态：</p>
     * <ul>
     *   <li><b>深度是偶数</b>（{@code maimai_rig} 的 1 × 2 —— {@code back = 0,
     *       front = 1}）：塔中心 = 控制器 + BACK × {@code rigPlateHalfFb()}，
     *       而碰撞体坐标表的格子是方块对齐的、没法落在半格上 ⇒ 把控制器摆在
     *       靠后那一排，原点就等于控制器自己那一格。</li>
     *   <li><b>完全没有底座</b>（{@code lhc_combo} 的 {@code 0,0,0,0}）：
     *       pattern 退化成 1 × 1 × 1，放下去即成形；塔底面贴控制器方块底面
     *       （{@code rigBaseY()} = 0），直接坐在地面上。</li>
     * </ul>
     *
     * <p>⚠ 这四个数必须与机器类覆写的
     * {@code rigPlateHalfLr()} / {@code rigPlateBackExtent()} /
     * {@code rigPlateFrontExtent()} <b>逐个对齐</b>，否则碰撞体原点会偏到底座外面。</p>
     *
     * @param left         控制器左边几格（= {@code rigPlateHalfLr()}）
     * @param right        控制器右边几格（同上，恒与 left 相等）
     * @param back         控制器背面几排（= {@code rigPlateBackExtent()}）
     * @param front        控制器正面几排（= {@code rigPlateFrontExtent()}）
     * @param tooltipLines lang 里 {@code gtoecore.machine.<tooltipKey>.tooltip.N} 的行数
     */
    public static MultiblockMachineDefinition registerDecorRig(
            String name,
            Function<IMachineBlockEntity, ? extends MultiblockControllerMachine> factory,
            String langValue,
            String tooltipKey,
            Supplier<com.gregtechceu.gtceu.client.renderer.machine.DynamicRender<?, ?>> renderer,
            int left, int right, int back, int front, int tooltipLines) {
        Component[] tooltips = new Component[tooltipLines];
        for (int i = 0; i < tooltipLines; i++) {
            tooltips[i] = Component.translatable(
                    "gtoecore.machine." + tooltipKey + ".tooltip." + (i + 1));
        }
        return GTNMachines.REGISTRATE
                .multiblock(name, factory)
                .langValue(langValue)
                .rotationState(RotationState.NON_Y_AXIS)
                // 同 tower_rig：非对称高塔 + 关掉扩展朝向
                .allowExtendedFacing(false)
                .tooltips(tooltips)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(definition -> rigPattern(definition, false, left, right, back, front))
                .shapeInfo(definition -> rigShapeInfo(definition, false, left, right, back, front))
                // 无 RecipeLogic ⇒ 不加 modelProperty，模型 JSON 里也不出现该属性
                .model(GTMachineModels.createOverlayCasingMachineModel(
                                GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                                GTCEu.id("block/machine/template/cube_all/sided"))
                        .andThen(b -> b.addDynamicRenderer(renderer)))
                .hasBER(true)
                .register();
    }

    /**
     * 机架类机器共用的结构 pattern：{@code (2×halfLr+1)}(左右) × 1(高) ×
     * {@code (2×halfFb+1)}(前后) 的<b>单层实心脱氧钢底座</b>。
     *
     * <p>aisle 顺序 = 背面 → 正面，本结构只有 1 层 ⇒ 每个 aisle 只有 1 个字符串。
     * 正面那一切片的中点是控制器 {@code S}（左右各 {@code halfLr} 格）。</p>
     *
     * <p><b>尺寸由调用方给</b>（= 机器类自己的 {@code PLATE_HALF_*}），
     * 因为三台机器各要一块大小不同的底盘：tower 43 × 23、arch 31 × 19、mast 21 × 17。
     * 取法固定在「该塔碰撞体在本轴的最大外延 + 1 格」，多出的一圈是可站立走道
     * —— 塔脚在贴地那层是铺满的，与脚印等大的底盘会被碰撞体 100% 盖死。</p>
     *
     * @param workable    是否可工作（有 RecipeLogic）—— 决定外壳是否额外开放
     *                    {@code autoAbilities}（配方需要的能力仓：能量输出 / 流体输入）
     * @param halfLr      底座左右半宽（格）
     * @param halfFb      底座前后半深（格）
     */
    private static BlockPattern rigPattern(MultiblockMachineDefinition definition, boolean workable,
                                           int halfLr, int halfFb) {
        return rigPattern(definition, workable, halfLr, halfLr, halfFb * 2, 0);
    }

    /**
     * 机架类机器共用的结构 pattern（四向可配版，2026-10-13）。
     *
     * <p>{@code (left+right+1)}(左右) × 1(高) × {@code (back+front+1)}(前后) 的
     * <b>单层实心脱氧钢底座</b>；控制器放在「控制器行」上，它的背面还有
     * {@code back} 排、正面还有 {@code front} 排。</p>
     *
     * <p>aisle 顺序 = 背面 → 正面，所以控制器所在 aisle 的下标正好是 {@code back}。
     * 老机器沿用 {@code (halfLr, halfLr, 2×halfFb, 0)}（控制器贴正面边缘、
     * 平板整个长在背面）⇒ 逐格与旧实现完全相同。</p>
     *
     * <p>⚠ 允许 {@code back + front + 1 == 1}（{@code lhc_combo} 无底座）：
     * 那时整张 pattern 只有控制器一格，放下即成形。</p>
     *
     * <p>⚠ <b>row 内必须严格等于 width 个字符</b>，否则 GTM 会抛
     * "Aisle length does not match"。</p>
     *
     * @param workable 是否可工作（有 RecipeLogic）—— 决定外壳是否额外开放 {@code autoAbilities}
     * @param left     控制器左边几格
     * @param right    控制器右边几格
     * @param back     控制器背面几排
     * @param front    控制器正面几排
     */
    private static BlockPattern rigPattern(MultiblockMachineDefinition definition, boolean workable,
                                           int left, int right, int back, int front) {
        int width = left + right + 1;
        int depth = back + front + 1;
        // ⚠ 口径：casingCells 含控制器那一格 —— 老的 tower 是 43 × 23 = 989 取 900
        //    （≈ 91%，即约 89 格可换仓）；arch 31×19 = 589 取 536、mast 21×17 = 357 取 325。
        int casingCells = width * depth;
        FactoryBlockPattern pattern = FactoryBlockPattern.start()
                .where('S', Predicates.controller(Predicates.blocks(definition.get())));

        if (casingCells > 1) {
            String solidRow = "#".repeat(width);
            // 控制器那一排：左右各留够，控制器在中点（左右不对称时也照样摆）
            String controllerRow = "#".repeat(left) + "S" + "#".repeat(right);
            for (int z = 0; z < depth; z++) {
                pattern.aisle(z == back ? controllerRow : solidRow);
            }
            // 外壳：实心脱氧钢（下限见上），再叠一个可选维护仓；
            // 可工作的机器额外叠加「本配方类型需要的能力仓」。
            // ⚠ 小底盘要夹一下上限：1×2 只有 1 格外壳，按 91% 算会要 2 格 ⇒ 永远无法成形。
            int minCasing = Math.min(Math.max(casingCells - 1, 0),
                    (int) Math.round(casingCells * 900.0 / 989.0));
            TraceabilityPredicate shell = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                    .setMinGlobalLimited(minCasing);
            if (workable) {
                shell = shell.or(Predicates.autoAbilities(definition.getRecipeTypes()));
            }
            shell = shell.or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1));
            pattern.where('#', shell);
        } else {
            // 无底座（lhc_combo）：整张 pattern 只有控制器那一格，放下即成形。
            // ⚠ 这里<b>不能</b>顺手把 '#' 谓词也挂上 —— 一个 '#' 格都不存在时
            //   minGlobalLimited 只能取 0，虽然 GTM 不报错，但语义上没必要，
            //   而且给未来留了个「0 下限」的坑。
            pattern.aisle("S");
        }
        return pattern.build();
    }

    /**
     * 机架类机器共用的结构预览（JEI / GTM 面板）。
     *
     * <p>顺序约定：shapeInfo 的 aisle 顺序与 pattern 相反、行内字符串左右相反
     * （= {@code BlockPattern.getPreview} 的排布）。本结构左右对称，所以行内不用倒。</p>
     *
     * <p><b>⚠ 预览必须真的摆上与 pattern 匹配的方块</b>，否则结构在预览里不成形 ——
     * 那种情况下在 JEI 里点一下会 NPE 崩游戏（packcompanion 的
     * {@code PatternPreviewWidgetMixin} 只是兜底，别依赖它）。
     * 所以 {@code E}/{@code F}（能量输出仓 / 流体输入仓）只给 {@code workable} 的机器摆，
     * 纯结构的机器在同一位置摆普通脱氧钢。</p>
     *
     * <p><b>⚠ 控制器必须留在中点</b>：{@code halfLr} 至少要 ≥ 2，否则
     * {@code "#".repeat(halfLr - 2)} 会抛 {@code IllegalArgumentException}。
     * 目前最小的 mast 是 10，安全。</p>
     *
     * @param workable 与 {@link #rigPattern} 保持一致
     * @param halfLr   底座左右半宽（格）
     * @param halfFb   底座前后半深（格）
     */
    private static MultiblockShapeInfo rigShapeInfo(MultiblockMachineDefinition definition, boolean workable,
                                                    int halfLr, int halfFb) {
        return rigShapeInfo(definition, workable, halfLr, halfLr, halfFb * 2, 0);
    }

    /**
     * 机架类机器共用的结构预览（JEI / GTM 面板）—— 四向可配版。
     *
     * <p>顺序约定：shapeInfo 的 aisle 顺序与 pattern 相反（= {@code BlockPattern.getPreview}
     * 的排布），所以 <b>preview 第 0 张切片 = 正面</b>，而控制器那一排就落在下标
     * {@code front} 上（老机器 {@code front=0} ⇒ 控制器在第 0 张切片上，与旧实现一致）。</p>
     *
     * <p><b>⚠ 预览必须真的摆上与 pattern 匹配的方块</b>，否则结构在预览里不成形 ——
     * 那种情况下在 JEI 里点一下会 NPE 崩游戏（packcompanion 的
     * {@code PatternPreviewWidgetMixin} 只是兜底，别依赖它）。
     * 所以 {@code E}/{@code F}（能量输出仓 / 流体输入仓）只给 {@code workable} 的机器摆，
     * 纯结构的机器在同一位置摆普通脱氧钢。</p>
     *
     * <p><b>⚠ 小底盘不能照抄「左右各留 2 格放 EFSFE」</b>：{@code "#".repeat(-2)} 会抛
     * {@code IllegalArgumentException}。这里按宽度分档 —— 只有左右都 ≥ 2 时才摆功能仓，
     * 否则退化成「# ＊ left + S + # ＊ right」。</p>
     */
    private static MultiblockShapeInfo rigShapeInfo(MultiblockMachineDefinition definition, boolean workable,
                                                    int left, int right, int back, int front) {
        int width = left + right + 1;
        int depth = back + front + 1;
        String solidRow = "#".repeat(width);
        // preview 第 front 张切片 = 控制器那一排（可工作的机器左右各挂一个功能仓）
        String controllerRow = (workable && left >= 2 && right >= 2)
                ? "#".repeat(left - 2) + "EFSFE" + "#".repeat(right - 2)
                : "#".repeat(left) + "S" + "#".repeat(right);
        // preview 最后 1 张切片 = 背面（维护仓 M 在中点，两台都允许）
        String backRow = "#".repeat(left) + "M" + "#".repeat(right);
        MultiblockShapeInfo.ShapeInfoBuilder builder = MultiblockShapeInfo.builder();
        for (int z = 0; z < depth; z++) {
            if (z == front) {
                builder.aisle(controllerRow);
            } else if (z == depth - 1) {
                builder.aisle(backRow);
            } else {
                builder.aisle(solidRow);
            }
        }
        return builder
                .where('S', definition.get(), Direction.NORTH)
                .where('#', GTNPreview.state(GTBlocks.CASING_STEEL_SOLID.get()))
                // 索引 = GT 电压档位（GTValues.V：0=ULV, 1=LV, 2=MV, 3=HV…），
                // 本机按 HV 设计（脱氧钢外壳 + HV 合成配方），预览也给 HV 仓。
                // 注意结构本身不挑档位：外壳谓词用的是 autoAbilities（只看能力），
                // 玩家塞 LV 仓也照样成形，只是发电量受仓的电压上限限制。
                // （纯结构的机器不会走到这两行 —— 那里 E/F 位置本来就是 '#'。）
                .where('E', GTMachines.ENERGY_OUTPUT_HATCH[3], Direction.NORTH)
                .where('F', GTMachines.FLUID_IMPORT_HATCH[3], Direction.NORTH)
                .where('M', GTMachines.MAINTENANCE_HATCH, Direction.SOUTH)
                .build();
    }
    private GTNRigMachines() {
    }
}
