package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gtoecore.GTNBlocks;
import com.gtoecore.util.GTNPreview;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 「事件视界引擎」多方块结构 —— 由 {@code 144.litematic} 直接投影而来。
 *
 * <h2>结构长什么样</h2>
 * <p>35³ 的<b>球壳</b>（3395 格 {@code gtoecore:hull_panel_hex} 六面舱壁），
 * 内部是 GTNA 的骨架：6 根沿 ±X/±Y/±Z 伸向球心的轴柱
 * （{@code dimensional_bridge_casing} + 末端 {@code dimensional_stability_casing}）、
 * 赤道带两侧的 {@code dimension_injection_casing} /
 * {@code dimensionally_transcendent_casing}、正中心的 16 格 {@code annihilate_core}。
 * 另外还有一点 AE2 装饰（量子环、福鲁伊克斯半砖/台阶）和 2 个拉杆 —— 那些是纯外观。</p>
 *
 * <h2>为什么网格走资源文件而不是 Java 常量</h2>
 * <p>裁剪掉外围空气后仍有 37(aisle) × 35(row) × 37(char) = 47915 格、
 * 1295 行字符串常量。JLS 规定<b>单个方法的字节码上限 64KB</b>，
 * 把这些 {@code ldc} 全塞进 {@code register*()} 必然超限。
 * 所以网格本体放在
 * {@code assets/gtoecore/structures/event_horizon_engine.txt}，
 * Java 端只留十几条「符号 → 方块」映射。</p>
 *
 * <p>那份文件由 {@code tools/gen_eh_structure.py} 生成，
 * 索引映射是 GTM {@code BlockPattern.setActualRelativeOffset} 的逐行移植，
 * 脚本里对每个格子做了正反变换自检。</p>
 *
 * <h2>两层符号</h2>
 * <ul>
 *   <li><b>状态级</b>（资源文件里存的）：同一方块的不同 {@code BlockState}
 *       是不同字符，例如舱壁的 {@code inner=north} / {@code inner=up}；</li>
 *   <li><b>类型级</b>（pattern 用的）：同一方块合并成一个字符，
 *       这样外壳的 {@code setMinGlobalLimited} 才统计得对。</li>
 * </ul>
 *
 * <h2>坐标与特效定位</h2>
 * <p>控制器在 (aisle=35, row=17, char=18)，球壳几何中心相对控制器是
 * <b>(0, 0, +17)</b> —— 即控制器 BACK 方向 17 格，
 * 与旧结构的 {@code CENTER_OFFSET = 17.0} 完全一致，所以
 * {@code EventHorizonRenderer} 的定位一行都不用改。</p>
 *
 * <p>⚠ 结构换了 ⇒ 存档里已摆好的旧机器<b>必定不成形，必须拆掉重摆</b>。</p>
 */
public final class GTNEventHorizonStructure {

    /** 结构网格资源（由 tools/gen_eh_structure.py 生成） */
    private static final String GRID_PATH = "/assets/gtoecore/structures/event_horizon_engine.txt";

    // ---- 类型级符号（pattern） ----
    private static final char CONTROLLER = '@';
    /** 外壳：六面舱壁，且允许替换成任意仓室 */
    private static final char CASING = '#';
    private static final char INJECTION = 'I';
    private static final char TRANSCENDENT = 'T';
    private static final char BRIDGE = 'B';
    private static final char STABILITY = 'S';
    private static final char CORE = 'C';
    /** 装饰：AE2 量子环 / 量子链接 */
    private static final char DEC_RING = 'q';
    private static final char DEC_LINK = 'l';
    /** 装饰：福鲁伊克斯半砖 / 台阶 */
    private static final char DEC_SLAB = 'a';
    private static final char DEC_STAIRS = 'e';
    /** 装饰：拉杆 */
    private static final char DEC_LEVER = 'v';
    private static final char AIR = ' ';

    /** [aisle][row] → 行字符串（状态级符号）。类加载时读一次。 */
    private static final String[][] GRID = loadGrid();
    /** [aisle][row] → 行字符串（类型级符号）。pattern 用它。 */
    private static final String[][] PATTERN_GRID = toTypeGrid(GRID);

    /**
     * 外壳最少需要多少格 —— 取投影里舱壁总数的 95%，
     * 剩下 5%（约 170 格）留给玩家替换成能量/物品/流体/维护仓，
     * 语义与旧结构「外壳 min 400 / 总线环 min 200」一致。
     */
    private static final int CASING_MIN = countCasing();

    private GTNEventHorizonStructure() {}

    /** pattern lambda 入口（延迟构建，此时外壳方块与配方类型都已就绪）。 */
    public static BlockPattern create(MultiblockMachineDefinition definition) {
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] aisle : PATTERN_GRID) {
            pattern.aisle(aisle);
        }
        return pattern
                .where(CONTROLLER, Predicates.controller(Predicates.blocks(definition.get())))
                // 外壳：六面舱壁 or 各类仓室（自动按配方类型的 IO 生成可用仓）+ 维护仓
                // ——「舱室逻辑与原机器无异」：还是 autoAbilities + MAINTENANCE
                .where(CASING, Predicates.blocks(GTNBlocks.HULL_PANEL_HEX.get())
                        .setMinGlobalLimited(CASING_MIN)
                        .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE)
                                .setMaxGlobalLimited(4).setPreviewCount(1)))
                // 骨架：GTNA 方块（软依赖 —— 整合包里没 GTNA 时这些位自动退化为"任意"）
                .where(INJECTION, GTNPreview.blocksOrAny("gtna:dimension_injection_casing"))
                .where(TRANSCENDENT, GTNPreview.blocksOrAny("gtna:dimensionally_transcendent_casing"))
                .where(BRIDGE, GTNPreview.blocksOrAny("gtna:dimensional_bridge_casing"))
                .where(STABILITY, GTNPreview.blocksOrAny("gtna:dimensional_stability_casing"))
                .where(CORE, GTNPreview.blocksOrAny("gtna:annihilate_core"))
                // 装饰：只影响外观，玩家摆不摆都不影响成形
                .where(DEC_RING, Predicates.any())
                .where(DEC_LINK, Predicates.any())
                .where(DEC_SLAB, Predicates.any())
                .where(DEC_STAIRS, Predicates.any())
                .where(DEC_LEVER, Predicates.any())
                // 内部空腔与包围盒角落：任意
                .where(AIR, Predicates.any())
                .build();
    }

    /** 供渲染/校验用：结构中心相对控制器方块原点的偏移（格）。 */
    public static double centerOffset() {
        return 17.0;
    }

    // ------------------------------------------------------------------
    // 状态级符号 → 类型级符号
    // ------------------------------------------------------------------

    /**
     * 见 {@code tools/gen/eh144.report.txt} 的「状态级符号表」。
     * ⚠ 重新生成结构后必须同步这张表，否则对应格子会退化成空气。
     */
    private static char typeOf(char stateSym) {
        switch (stateSym) {
            case '@':
                return CONTROLLER;
            case 'E': case 'F':                 // hull_panel_hex 的两种 inner
                return CASING;
            case 'A':                           // dimension_injection_casing
                return INJECTION;
            case 'D':                           // dimensionally_transcendent_casing
                return TRANSCENDENT;
            case 'B':                           // dimensional_bridge_casing
                return BRIDGE;
            case 'H':                           // dimensional_stability_casing
                return STABILITY;
            case 'G':                           // annihilate_core
                return CORE;
            case 'I':                           // ae2:quantum_ring
                return DEC_RING;
            case 'J':                           // ae2:quantum_link
                return DEC_LINK;
            case 'K': case 'L':                 // ae2:fluix_slab top/bottom
                return DEC_SLAB;
            case 'M': case 'N': case 'Q': case 'R':   // ae2:fluix_stairs
                return DEC_STAIRS;
            case 'O': case 'P':                 // minecraft:lever
                return DEC_LEVER;
            default:
                return AIR;
        }
    }

    private static String[][] toTypeGrid(String[][] stateGrid) {
        String[][] out = new String[stateGrid.length][];
        for (int a = 0; a < stateGrid.length; a++) {
            String[] rows = new String[stateGrid[a].length];
            for (int r = 0; r < stateGrid[a].length; r++) {
                char[] ch = stateGrid[a][r].toCharArray();
                for (int i = 0; i < ch.length; i++) {
                    ch[i] = typeOf(ch[i]);
                }
                rows[r] = new String(ch);
            }
            out[a] = rows;
        }
        return out;
    }

    private static int countCasing() {
        int n = 0;
        for (String[] aisle : PATTERN_GRID) {
            for (String row : aisle) {
                for (char c : row.toCharArray()) {
                    if (c == CASING) {
                        n++;
                    }
                }
            }
        }
        return Math.max(1, (int) (n * 0.95));
    }

    // ------------------------------------------------------------------
    // 资源加载
    // ------------------------------------------------------------------

    private static String[][] loadGrid() {
        List<List<String>> aisles = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        try (InputStream in = GTNEventHorizonStructure.class.getResourceAsStream(GRID_PATH)) {
            if (in == null) {
                throw new IllegalStateException("[gtoecore] 缺少结构资源 " + GRID_PATH
                        + "（应由 tools/gen_eh_structure.py 生成）");
            }
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                        line = line.substring(0, line.length() - 1);
                    }
                    // ⚠ 绝不能 trim：行首行尾的空格是 AIR 符号，属于结构的一部分
                    if (line.isEmpty()) {
                        if (!cur.isEmpty()) {
                            aisles.add(cur);
                            cur = new ArrayList<>();
                        }
                    } else {
                        cur.add(line);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("[gtoecore] 读取结构资源失败 " + GRID_PATH, e);
        }
        if (!cur.isEmpty()) {
            aisles.add(cur);
        }
        if (aisles.isEmpty()) {
            throw new IllegalStateException("[gtoecore] 结构资源为空 " + GRID_PATH);
        }
        int rows = aisles.get(0).size();
        int cols = aisles.get(0).get(0).length();
        for (List<String> aisle : aisles) {
            if (aisle.size() != rows) {
                throw new IllegalStateException("[gtoecore] 结构资源行数不一致：期望 " + rows
                        + " 实际 " + aisle.size());
            }
            for (String row : aisle) {
                if (row.length() != cols) {
                    throw new IllegalStateException("[gtoecore] 结构资源列数不一致：期望 " + cols
                            + " 实际 " + row.length());
                }
            }
        }
        String[][] out = new String[aisles.size()][rows];
        for (int a = 0; a < aisles.size(); a++) {
            out[a] = aisles.get(a).toArray(new String[0]);
        }
        return out;
    }
}
