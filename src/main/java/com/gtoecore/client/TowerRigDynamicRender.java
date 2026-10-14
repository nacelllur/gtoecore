package com.gtoecore.client;

import com.google.common.collect.ImmutableMap;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gtoecore.gt.TowerRigMachine;
import com.gtoecore.objdemo.ObjDemoModels;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Set;

/**
 * 塔架动力塔（{@code tower_rig}）的视觉 —— 把 {@code tower_rig.obj} 画在
 * 43 × 1 × 23 单层脱氧钢底座之上，并让塔上的涡轮转起来。
 *
 * <p>落座、姿态偏航、包围盒、视距、诊断日志全部来自
 * {@link AbstractRigDynamicRender}；本类只提供「用哪个模型、放大多少倍」
 * 以及<b>逐部件旋转</b>这两件事。</p>
 *
 * <h2>涡轮是会转的</h2>
 * <p>模型里的 4 级涡轮（22/20/18/16 片叶）、主轴和塔顶排气涡轮盘都按机器状态旋转：
 * 在烧燃料时转得快、待机时慢转。逐部件变换用 Forge 的
 * {@link CompositeRenderable.Transforms}（按 OBJ 的 {@code o} 组名索引）。</p>
 *
 * <p>⚠ 为了性能，这些可动部件会被 {@link #rigAnimatedPartPrefixes()} 从塔体里
 * <b>拆出来</b>单独逐帧绘制（塔体本体一次性烘进 VBO，见
 * {@link AbstractRigDynamicRender} 的「性能」一节）——
 * 所以 {@link #rigTransforms} 拿到的 {@code model} 是「只含这些部件的子渲染体」，
 * 而不是完整模型。</p>
 */
public class TowerRigDynamicRender
        extends AbstractRigDynamicRender<TowerRigMachine, TowerRigDynamicRender> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<TowerRigDynamicRender> CODEC = Codec.unit(TowerRigDynamicRender::new);
    public static final DynamicRenderType<TowerRigMachine, TowerRigDynamicRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** OBJ 路径（相对 {@code assets/gtoecore/models/}）。 */
    private static final String OBJ_PATH = "obj/tower_rig.obj";

    /**
     * 模型自身比例缩放（等比例）⇒ 约 <b>40.0 宽 × 66.4 高 × 21.0 厚</b>格。
     *
     * <p>嫌大/嫌小改这一个数即可，不用动 OBJ —— 缩放恒以「塔底面中心」为原点，
     * 所以底面永远贴着底座顶面。⚠ 改它必须用同样的 {@code --scale} 重烤碰撞表。</p>
     */
    private static final float SCALE = 4.45F;

    /**
     * 塔相对机器朝向的<b>额外</b>偏航角（度）。
     *
     * <p>机器朝向决定的那部分偏航由 {@link com.gtoecore.gt.RigController#rigYawDegrees()}
     * 给出（保证塔的长轴和底座 43 格那条边对齐、和碰撞体同步）；这里只用来修
     * 「塔的正面朝里了」这类观感问题 —— <b>加 180 即可把塔转半圈</b>，
     * 碰撞体会跟着一起转。</p>
     */
    private static final float YAW_OFFSET = 0.0F;

    // ------------------------------------------------------------------
    // 涡轮旋转动画（逐部件 Transform，由机器的燃烧状态驱动）
    //
    // tower_rig.obj 里有 5 组可转的部件（共 94 个 `o` 对象）：
    //   · 机身 4 级涡轮（tb_rotor0..3_*  22/20/18/16 片叶，共 76 件）
    //     + 4 个轮毂 tb_hub0..3 + 主轴 tb_shaft      → 共 81 件，同轴
    //   · 塔顶排气涡轮盘 tw_turb_hub + tw_turb_00..11（12 叶，共 13 件）
    // 旋转轴实测（tools/analyze_turbine.py）：
    //   机身 + 主轴 = 沿 +X 的轴，过 (y=0, z=1.650)
    //   塔顶涡轮盘 = 沿 +X 的轴，过 (y=0, z=14.300)
    // 验证方法：转子叶片是等分圆周的，若轴心正确，各叶片质心的夹角偏差应≈0°；
    // 实测偏差 0.0001°（把轴心挪错 0.05 ⇒ 偏差立刻变成 2.8°）。
    //
    // ⚠ 矩阵工作在**模型自身坐标系**（Forge 的 Component.render 是
    //   pose = pose * T，T 在 Z-up→Y-up 旋转与 SCALE 之内），所以轴心用
    //   上面的模型坐标原样填，不要换算成世界坐标。
    // ------------------------------------------------------------------

    /** 参与旋转的部件名前缀（即 OBJ 的 `o` 组名，取自模型自身，不写死全名）。 */
    private static final String[] SPIN_PREFIXES = { "tb_rotor", "tb_hub", "tb_shaft", "tw_turb" };

    /** 其中属于「塔顶涡轮盘」那一组的前缀（旋转轴心更高）。 */
    private static final String SPIN_PREFIX_TOP = "tw_turb";

    /** 机身涡轮/主轴的旋转轴心（模型坐标：绕 +X 轴、过 y=0, z=1.650）。 */
    private static final float SPIN_PIVOT_Z_BODY = 1.650F;

    /** 塔顶涡轮盘的旋转轴心（模型坐标：绕 +X 轴、过 y=0, z=14.300）。 */
    private static final float SPIN_PIVOT_Z_TOP = 14.300F;

    /** 机器「在跑配方（在烧燃料）」时的转速，度/秒。 */
    private static final float SPIN_DEG_PER_SEC_WORKING = 180.0F;

    /** 待机（没烧燃料）时的转速，度/秒；设 0 就是「不工作时完全静止」。 */
    private static final float SPIN_DEG_PER_SEC_IDLE = 8.0F;

    /** 转向（+1 / -1）—— 只看观感，物理上无意义。 */
    private static final float SPIN_SIGN = 1.0F;

    /** 部件名 → 变换矩阵（值只有上面两个实例，同一组的部件共用同一个矩阵）。 */
    private ImmutableMap<String, Matrix4f> spinPartMap;

    /** 上面那张 map 的 {@code Transforms} 视图（只需建一次，矩阵是原地复用的）。 */
    private CompositeRenderable.Transforms spinTransforms;

    /** 建 map 时用的是哪个模型实例 —— 资源包重载后模型对象会换，据此重建。 */
    private CompositeRenderable spinBuiltFrom;

    /** 机身组 / 塔顶组的旋转矩阵（每帧原地重算，避免每帧分配）。 */
    private final Matrix4f spinMatrixBody = new Matrix4f();
    private final Matrix4f spinMatrixTop = new Matrix4f();

    /** 当前累加的旋转角（度）与上一帧的时间戳（算转速用）。 */
    private float spinAngle;
    private long spinLastNanos;

    private boolean loggedSpinBinding = false;

    /**
     * 构造器日志 —— 模型 JSON 里的 {@code "dynamic_renders": [{"type": "gtoecore:tower_rig"}]}
     * 一旦被解析，{@code Codec.unit(TowerRigDynamicRender::new)} 就会走这里。
     * 换句话说：<b>看到这行日志 = JSON 那条声明生效了</b>。
     */
    public TowerRigDynamicRender() {
        LOGGER.info("[gtoecore] tower_rig DynamicRender INSTANTIATED "
                + "(model json \"dynamic_renders\" was decoded successfully)");
    }

    @Override
    public DynamicRenderType<TowerRigMachine, TowerRigDynamicRender> getType() {
        return TYPE;
    }

    @Override
    protected String rigObjPath() {
        return OBJ_PATH;
    }

    @Override
    protected float rigScale() {
        return SCALE;
    }

    @Override
    protected float rigYawOffset() {
        return YAW_OFFSET;
    }

    /** 塔高约 67 格、水平跨约 41 格 —— 包围盒现在按碰撞体坐标表自动算，不需要手写余量。 */
    @Override
    protected double rigBoundingBoxPad() {
        // 表是离线体素化的（--inset 0.02 丢掉了发丝级溢出），给 0.5 格余量足够。
        return 0.5D;
    }

    /**
     * 只有上面这四个前缀的部件需要逐帧更新 —— 其余 600 多个部件一次性烘进 VBO。
     *
     * <p>实测：{@code tower_rig} 共 8044 个面，其中可动部件只有 <b>656 个面（8.2%）</b>
     * ⇒ 每帧的 CPU 顶点处理量降到原来的 ~1/12；{@code arch_rig} / {@code mast_rig}
     * 没有任何可动部件（返回基类的空数组），整个模型都烘死。</p>
     */
    @Override
    protected String[] rigAnimatedPartPrefixes() {
        return SPIN_PREFIXES;
    }

    /**
     * 组装（并每帧更新）逐部件变换表 —— 涡轮就在这里转起来的。
     *
     * <p>部件名从 {@link ObjDemoModels#getPartNames(String)} 现取（= OBJ 的 {@code o} 名），
     * 所以重新导出模型、增删几片叶片都不需要改 Java，只要名字前缀还在。</p>
     *
     * <p>同一组的部件共用<b>同一个</b> {@link Matrix4f} 实例，每帧原地重算 ——
     * 94 个部件只算 2 次矩阵，也避免每帧分配 94 个矩阵。</p>
     */
    @Override
    protected CompositeRenderable.Transforms rigTransforms(TowerRigMachine machine,
                                                           CompositeRenderable model) {
        // 资源包重载（F3+T）后模型对象会换新实例，此时重建绑定
        if (spinTransforms == null || spinBuiltFrom != model) {
            Set<String> names = ObjDemoModels.getPartNames(OBJ_PATH);
            if (names.isEmpty()) {
                return CompositeRenderable.Transforms.EMPTY;
            }
            ImmutableMap.Builder<String, Matrix4f> builder = ImmutableMap.builder();
            int body = 0;
            int top = 0;
            for (String name : names) {
                if (!startsWithAny(name, SPIN_PREFIXES)) {
                    continue;
                }
                if (name.startsWith(SPIN_PREFIX_TOP)) {
                    builder.put(name, spinMatrixTop);
                    top++;
                } else {
                    builder.put(name, spinMatrixBody);
                    body++;
                }
            }
            spinPartMap = builder.build();  // 部件名唯一，重复 key 不可能出现
            spinTransforms = CompositeRenderable.Transforms.of(spinPartMap);
            spinBuiltFrom = model;
            if (!loggedSpinBinding) {
                loggedSpinBinding = true;
                LOGGER.info("[gtoecore] tower_rig turbine: {} rotating parts bound "
                        + "({} on the 4-stage body shaft, {} on the top exhaust wheel)",
                        body + top, body, top);
            }
        }
        if (spinPartMap.isEmpty()) {
            return CompositeRenderable.Transforms.EMPTY;
        }

        // 按机器状态推进角度，再把两个轴心矩阵原地重算
        float rad = (float) Math.toRadians(advanceSpin(machine) * SPIN_SIGN);
        spinMatrixBody.identity()
                .translate(0.0F, 0.0F, SPIN_PIVOT_Z_BODY)
                .rotate(rad, 1.0F, 0.0F, 0.0F)
                .translate(0.0F, 0.0F, -SPIN_PIVOT_Z_BODY);
        spinMatrixTop.identity()
                .translate(0.0F, 0.0F, SPIN_PIVOT_Z_TOP)
                .rotate(rad, 1.0F, 0.0F, 0.0F)
                .translate(0.0F, 0.0F, -SPIN_PIVOT_Z_TOP);
        return spinTransforms;
    }

    /**
     * 推进旋转角：机器在跑配方（烧燃料）时转得快，待机时慢转。
     *
     * <p>用真实时间差积分而不是 {@code partialTick} —— 后者只是「当前 tick 内的插值比例」，
     * 直接拿它当帧间隔会让转速随帧率乱飘。</p>
     */
    private float advanceSpin(TowerRigMachine machine) {
        long now = System.nanoTime();
        float dt = spinLastNanos == 0L ? 0.0F : (now - spinLastNanos) / 1.0E9F;
        spinLastNanos = now;
        // 卡顿/暂停（>0.25s）时不追补角度，免得一顿就猛转一圈
        if (dt < 0.0F || dt > 0.25F) {
            dt = 0.0F;
        }
        boolean working = machine.getRecipeLogic() != null && machine.getRecipeLogic().isWorking();
        float dps = working ? SPIN_DEG_PER_SEC_WORKING : SPIN_DEG_PER_SEC_IDLE;
        spinAngle = (spinAngle + dps * dt) % 360.0F;
        return spinAngle;
    }

    private static boolean startsWithAny(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
