package com.gtoecore.deepspace;

/**
 * 深空枢纽扩展模块的类型与数值定义。
 *
 * <p><b>设计原理（对标 GTO 通天之路的模块体系）：</b>通天之路是「动力模块决定等级上限 +
 * 模块基座各自跑配方 + 电梯主体供能并吃碳纳米管线轴」。本体系把这个原理拆成两层：</p>
 * <ul>
 *   <li><b>主机（深空枢纽）</b>：供能中枢 + 耗材仓，越级解锁、维持全网络在线。</li>
 *   <li><b>扩展模块</b>：独立多方块，放在主机附近即自动挂载；<b>同类模块可无限叠加</b>，
 *       每多一台就把对应数值再推进一份。</li>
 * </ul>
 *
 * <p>各模块的加成都是「每个模块一份」，多台累加/累乘，并设有下限保护，
 * 保证无限叠加时不会出现 0 耗时 / 0 耗能这类退化。</p>
 */
public enum ModuleKind {

    /** 并行阵列模块：每台 +4 并行 */
    PARALLEL("parallel", "并行阵列模块", "Parallel Array Module",
            4, 1.0, 1.0, 1.0, 0, 1.0),

    /** 算力超频模块：每台配方耗时 ×0.97 */
    OVERCLOCK("overclock", "算力超频模块", "Overclock Module",
            0, 0.97, 1.0, 1.0, 0, 1.0),

    /** 能效优化模块：每台 EU 消耗 ×0.97 */
    EFFICIENCY("efficiency", "能效优化模块", "Efficiency Module",
            0, 1.0, 0.97, 1.0, 0, 1.0),

    /** 产出增幅模块：每台产物 ×1.05 */
    OUTPUT("output", "产出增幅模块", "Output Amplifier Module",
            0, 1.0, 1.0, 1.05, 0, 1.0),

    /** 深空传感模块：每台 +1 枢纽等级（解锁更高等级配方） */
    SENSING("sensing", "深空传感模块", "Deep Sensing Module",
            0, 1.0, 1.0, 1.0, 1, 1.0),

    /** 耗材回收模块：每台深空燃料消耗 ×0.96 */
    RECYCLER("recycler", "耗材回收模块", "Fuel Recycler Module",
            0, 1.0, 1.0, 1.0, 0, 0.96);

    private final String id;
    private final String cn;
    private final String en;
    /** 每台模块提供的并行数（累加） */
    private final int parallelPerModule;
    /** 每台模块的耗时倍率（累乘） */
    private final double durationFactor;
    /** 每台模块的 EU 倍率（累乘） */
    private final double eutFactor;
    /** 每台模块的产出倍率（累乘） */
    private final double outputFactor;
    /** 每台模块提供的枢纽等级（累加） */
    private final int tierPerModule;
    /** 每台模块的耗材倍率（累乘，越小越省） */
    private final double fuelFactor;

    ModuleKind(String id, String cn, String en,
               int parallelPerModule, double durationFactor, double eutFactor,
               double outputFactor, int tierPerModule, double fuelFactor) {
        this.id = id;
        this.cn = cn;
        this.en = en;
        this.parallelPerModule = parallelPerModule;
        this.durationFactor = durationFactor;
        this.eutFactor = eutFactor;
        this.outputFactor = outputFactor;
        this.tierPerModule = tierPerModule;
        this.fuelFactor = fuelFactor;
    }

    public String id() { return id; }
    public String cn() { return cn; }
    public String en() { return en; }
    public int parallelPerModule() { return parallelPerModule; }
    public double durationFactor() { return durationFactor; }
    public double eutFactor() { return eutFactor; }
    public double outputFactor() { return outputFactor; }
    public int tierPerModule() { return tierPerModule; }
    public double fuelFactor() { return fuelFactor; }

    /** 模块方块注册 id（如 gtoecore:parallel_module） */
    public String blockId() { return id + "_module"; }

    /** tooltip 语言键 */
    public String tooltipKey() { return "gtoecore.deepspace.module." + id + ".tooltip"; }

    /** 单台模块的加成描述语言键 */
    public String bonusKey() { return "gtoecore.deepspace.module." + id + ".bonus"; }
}