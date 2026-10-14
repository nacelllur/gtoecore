package com.gtoecore.station;

/**
 * 全周天舱壁的「面板皮肤」—— 只决定<b>关闭（无红石信号）时朝内面</b>的纹理。
 *
 * <p>整片舱壁的行为（单向舷窗、红石波纹、自动外框）对所有皮肤完全一致：
 * 皮肤仅仅替换 {@code HullViewportRenderer} 在那块 {@code progress &lt; 1}
 * 的四边形上采样的贴图。开启（变透明）后一律不画，因此皮肤只在关闭态可见。</p>
 *
 * <p>贴图位于 {@code assets/gtoecore/textures/block/station/} 下。
 * 除梗图外都必须是<b>自身可平铺</b>的无缝图：渲染器用「世界坐标对
 * {@link #period()}（默认 6 格）取模」算 UV，于是整片舱壁拼成一张连续的图案
 * （连接纹理）。梗图皮肤的贴图四边是金属画框，靠画框自然形成分隔带。
 * 生成器见 {@code tools/gen_hull_panel_skins.py}。</p>
 */
public enum PanelSkin {

    /** 蜂窝晶格（默认，原有的那张）。 */
    HEX("hex", "hull_viewport_inner"),

    /** 深空星图：星点 + 淡星云，关闭时像在看真实太空。 */
    STAR("star", "hull_viewport_inner_star"),

    /** 警示斜纹：黄黑 hazard stripe，用于危险区 / 气闸 / 反应堆舱段。 */
    HAZARD("hazard", "hull_viewport_inner_hazard"),

    /** 电路网格：暗底发光走线 + 过孔，控制舱 / 数据舱用。 */
    CIRCUIT("circuit", "hull_viewport_inner_circuit"),

    /** 装甲板：分块钢甲 + 铆钉 + 角落编号铭牌。 */
    ARMOR("armor", "hull_viewport_inner_armor"),

    // ── 第二批 ────────────────────────────────────────────────────────────────

    /** 太阳能板：深蓝电池片 + 银栅线，能源舱段外壁/伪装用。 */
    SOLAR("solar", "hull_viewport_inner_solar"),

    /** 工业格栅：细密金属网格 + 网孔，机房/管廊。 */
    GRID("grid", "hull_viewport_inner_grid"),

    /** 通风百叶：横向百叶条 + 立筋，风道/散热舱段。 */
    VENT("vent", "hull_viewport_inner_vent"),

    /** 锈蚀钢板：斑驳锈迹 + 划痕 + 螺栓，废弃舱段 / 旧船坞。 */
    RUST("rust", "hull_viewport_inner_rust"),

    /** 舱内瓷砖：乳白瓷砖 + 勾缝 + 污渍，居住舱 / 医护舱。 */
    TILE("tile", "hull_viewport_inner_tile"),

    /** 防滑花纹板：密集凸点 + 板缝，机库地板 / 检修平台。 */
    TREAD("tread", "hull_viewport_inner_tread"),

    /** 拉丝金属：横向拉丝 + 拼缝 + 斜向高光，简约现代舱段。 */
    BRUSHED("brushed", "hull_viewport_inner_brushed"),

    /** 外星铭文：暗石板底 + 青色象形符号，遗迹 / 未知科技舱段。 */
    GLYPH("glyph", "hull_viewport_inner_glyph"),

    /** 数码迷彩：灰蓝数码迷彩，军用 / 隐匿用途。 */
    CAMO("camo", "hull_viewport_inner_camo"),

    /** 耐火砖：红棕砖 + 灰浆缝（错缝砌法），熔炉 / 反应堆舱段。 */
    BRICK("brick", "hull_viewport_inner_brick"),

    // ── 第三批：科幻动态 ──────────────────────────────────────────────────────
    // 贴图是「纵向堆叠 frames 帧」的图集，渲染器按游戏时间选帧循环播放。
    // 每帧自身仍是 6×6 格的无缝图（边长 = 6 格 × 64 px），所以连接纹理照旧成立。

    /** 雷达扫描：暗绿屏 + 同心圆环 + 旋转扫描扇区。 */
    SCAN("scan", "hull_viewport_inner_scan", 8),

    /** 数据流：黑底 + 垂落的数据光柱与字符，整体向下流动。 */
    DATASTREAM("datastream", "hull_viewport_inner_datastream", 8),

    /** 全息投影：青色全网格 + 上下往复的扫描亮带。 */
    HOLO("holo", "hull_viewport_inner_holo", 8),

    /** 能量脉冲：中心环阵向外扩散的脉冲波。 */
    PULSE("pulse", "hull_viewport_inner_pulse", 8),

    /** 等离子：紫蓝等离子湍流 + 六边形力场网格。 */
    PLASMA("plasma", "hull_viewport_inner_plasma", 8),

    // ── 第四批：梗图 ──────────────────────────────────────────────────────────
    // 贴图覆盖 5×5 格（不是默认的 6），一张图占 5 格宽 ——
    // 连接纹理的规则下「贴图覆盖 N 格」= 「每 N 格重复一次」，
    // 所以整片墙上是每隔 5 格出现一张大图，图看得清「这是什么梗」。
    // 16 帧由原 GIF 的 33 帧重采样而来；每帧 1.25 刻 ⇒ 16 × 1.25 = 20 刻 = 1 秒一轮，
    // 正好对上原 GIF 的 0.99 秒。

    /** 表情包·兔子：会动的梗图（16 帧，贴图 5×5 格一轮，1 秒一轮）。 */
    MEME("meme", "hull_viewport_inner_meme", 16, 1.25F, 5),

    /**
     * 表情包·冰冰冰：会动的梗图（15 帧，贴图 5×5 格一轮）。
     *
     * <p>源 GIF 有 146 帧 / 9.74 秒且**整段并非无缝循环**，因此只取其中一段真正
     * 能首尾相接的子循环（15 帧 = 1.00 秒）作为循环体。每帧 20/15 刻 ⇒
     * 15 × 20/15 = 20 刻 = 1 秒一轮，与原片段时长一致。</p>
     */
    MEME_BING("meme_bing", "hull_viewport_inner_meme_bing", 15, 20.0F / 15.0F, 5),

    /**
     * 表情包·η：**静态**梗图（贴图就是一张图，不是帧图集）。
     *
     * <p>源是 704×571 的等距水晶方块壁纸，η 只占 21% 宽；生成器裁出一个
     * 480×480 方窗后套上与前两张梗图同样的金属画框，所以三张的画框物理宽度一致。
     * 周期同样是 5 格。贴图四边都是框，因此图案在边界处断开是设计要的效果。</p>
     */
    MEME_ETA("meme_eta", "hull_viewport_inner_meme_eta", 1, 5);

    /** 默认 UV 世界周期（格）：贴图覆盖 PERIOD×PERIOD 格。 */
    public static final int DEFAULT_PERIOD = 6;

    /** 默认每帧时长（刻）：8 帧 × 3 刻 = 1.2 秒一轮。 */
    public static final float DEFAULT_FRAME_TICKS = 3.0F;

    private final String id;
    private final String textureName;
    private final int frames;
    private final float frameTicks;
    private final int period;

    /** 静态皮肤（贴图就是整张图案）。 */
    PanelSkin(String id, String textureName) {
        this(id, textureName, 1, DEFAULT_FRAME_TICKS, DEFAULT_PERIOD);
    }

    /** @param frames 贴图纵向堆叠的帧数；{@code 1} = 静态，{@code &gt; 1} = 动态帧图集。 */
    PanelSkin(String id, String textureName, int frames) {
        this(id, textureName, frames, DEFAULT_FRAME_TICKS, DEFAULT_PERIOD);
    }

    /**
     * @param frames 贴图纵向堆叠的帧数；{@code 1} = 静态
     * @param period 贴图覆盖的世界格数（UV 取模周期）；帧时长取默认值
     *
     * <p>⚠ 静态皮肤（{@code frames == 1}）必须有这个重载：枚举常量的参数表里
     * <b>不能</b>引用本枚举后面声明的 {@link #DEFAULT_FRAME_TICKS}
     * （javac 报「非法前向引用」），但<b>构造器体内</b>引用是合法的。</p>
     */
    PanelSkin(String id, String textureName, int frames, int period) {
        this(id, textureName, frames, DEFAULT_FRAME_TICKS, period);
    }

    /**
     * 全参。
     *
     * @param frames     贴图纵向堆叠的帧数；{@code 1} = 静态
     * @param frameTicks 每帧时长（刻），只在 {@code frames > 1} 时有意义
     * @param period     贴图覆盖的世界格数（UV 取模周期），决定图案「多大一张」
     */
    PanelSkin(String id, String textureName, int frames, float frameTicks, int period) {
        this.id = id;
        this.textureName = textureName;
        this.frames = frames;
        this.frameTicks = frameTicks;
        this.period = period;
    }

    /** 变种短名（注册名后缀 {@code hull_viewport_<id>}，{@link #HEX} 用无后缀的 {@code hull_viewport}）。 */
    public String id() {
        return id;
    }

    /** 贴图文件名（不含扩展名），位于 {@code textures/block/station/} 下。 */
    public String textureName() {
        return textureName;
    }

    /**
     * 贴图纵向堆叠的帧数：{@code 1} = 静态（整张就是图案），
     * {@code &gt; 1} = 动态帧图集（每帧等高，从上到下依次排列）。
     * 渲染器 `HullViewportRenderer` 按游戏时间选帧循环播放。
     */
    public int frames() {
        return frames;
    }

    /**
     * 每帧时长（游戏刻）。只在 {@link #frames()} &gt; 1 时有意义。
     * 渲染时帧与帧之间是交叉淡化，所以这个值决定的是<b>过渡速度</b>：调大 = 变慢变柔。
     */
    public float frameTicks() {
        return frameTicks;
    }

    /**
     * 贴图覆盖的世界格数（UV 取模周期）。默认 {@link #DEFAULT_PERIOD}（6 格）。
     *
     * <p>连接纹理下「贴图覆盖 N 格」等价于「每 N 格重复一次」：数值越大，一张图案
     * 在墙上占的面积越大。梗图这类<b>需要看清内容</b>的皮肤会调小到 5，
     * 让一张图有 5 格宽。</p>
     */
    public int period() {
        return period;
    }

    /** 是否动态皮肤。 */
    public boolean animated() {
        return frames > 1;
    }
}
