package com.gtoecore.objdemo;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 「机架（rig）类多方块」的<b>多格碰撞体</b>铺设/拆除执行器。
 *
 * <p>本类服务所有「底座是一层平板、塔是叠加视觉」的机器（{@code tower_rig} /
 * {@code arch_rig} / {@code mast_rig}）。每台机器有自己的离线体素化坐标表
 * （{@code assets/gtoecore/structures/<name>_collision.txt}），
 * 表的路径由调用方传入，本类按路径缓存、互不干扰。</p>
 *
 * <h2>为什么需要这套东西</h2>
 * <p>塔是 {@code DynamicRender} 画出来的<b>纯视觉</b>模型（40 × 66 × 21 格那种），
 * 方块本体只有底座那 1 层，玩家会直接穿过塔身。而<b>往单个方块上挂超大碰撞箱是行不通的</b>：
 * 原版 {@code BlockCollisions} 只扫描「实体自身 AABB ±1 格」
 * （字节码实测 {@code floor(aabb.min - 1e-7) - 1} … {@code floor(aabb.max + 1e-7) + 1}），
 * 离那个方块一格远，它的形状就再也不会被查询。大体积碰撞只能落成真实方块。</p>
 *
 * <h2>坐标约定</h2>
 * <p>坐标表里的 {@code (dx, dy, dz)} 是<b>相对「原点格」</b>的偏移，原点格 = 那一个
 * 「模型原点正下方」的方块：</p>
 * <pre>
 *   原点格 = 控制器坐标 + BACK 方向 × 底座前后半深 + (0, COLLISION_ORIGIN_Y_OFFSET, 0)
 * </pre>
 * <p>（原点格 = 底座中心正上方那一格；模型原点正好落在它的底面中心。）
 * 「底座前后半深」由机器自己给（{@code RigController.rigPlateHalfFb()} ——
 * tower 11 / arch 9 / mast 9），本类不做任何尺寸假设。</p>
 *
 * <h2>为什么要按朝向旋转</h2>
 * <p>底座 pattern 是矩形（非正方形）、而塔是不对称造型 —— 机器朝东和朝北时，
 * 塔在世界里的摆放差 90°。姿态（模型的偏航）与碰撞体必须用<b>同一套轴</b>算，
 * 否则塔会横着长到底座外面去。整套换算只有一个来源：{@code RigController} 的
 * {@code rigAxisX() / rigAxisZ()}（GT 的 {@code RelativeDirection} 约定，
 * 与 pattern 的字符/aisle 轴一一对应），本类只做「整数格旋转」。</p>
 * <p>旋转用<b>格中心映射</b>而不是格角：格子 (i,j,k) 的中心在模型坐标系里就是
 * (i,j,k)，把两个水平基向量换成世界方向后中心仍是整数坐标 ⇒ 直接就是目标格，
 * 不需要 ±1 修正（格角映射才需要，很容易错一格）。</p>
 *
 * <h2>为什么分帧</h2>
 * <p>一次性改几千格会阻塞服务端主线程；分帧后 320 格/tick ≈ 1 秒铺完，
 * 玩家只在最初一两秒感觉到「多了道看不见的墙」。</p>
 *
 * <h2>为什么绕开 {@code Level.setBlock}</h2>
 * <p>与 {@code HullViewportBlock} 完全同一个理由：GTM 在
 * {@code Level.markAndNotifyBlock} 上挂了 {@code updateChunkMultiblocks}，
 * 任何 {@code setBlock} 都会让附近的多方块机器跑一次<b>完整结构重新匹配</b>
 * （事件视界引擎的 pattern 有 47915 格，单次约 100ms）。这里直接改 chunk 里的
 * 方块状态（不触发 markAndNotify、不发邻居更新），再用 {@code sendBlockUpdated}
 * 单独把新状态同步给客户端。</p>
 *
 * <p><b>光照不需要处理</b>：{@link TowerRigCollisionBlock} 的
 * {@code getLightBlock=0} 且 {@code propagatesSkylightDown=true}，
 * 铺/拆都不改变任何一格的光照值，所以跳过光照更新是无副作用的。</p>
 *
 * <h2>为什么不会覆盖玩家的东西</h2>
 * <p>铺设只认「空气 / 可替换且无流体」的格子；玩家的建筑、水、岩浆一律不动。
 * 反过来，玩家在塔身范围内放自己的方块也会直接覆盖碰撞块（本方块
 * {@code replaceable}），补漏格也不会把它抢回来。</p>
 *
 * <h2>补漏格：为什么不是「每 N tick 全表体检」</h2>
 * <p>结构成形那一刻，塔跨越的区块未必全部加载（LHC 跨 13 × 13 个区块），
 * 那些格子在玩家走过去之前根本铺不上 —— 必须回头补，否则会出现「塔的一半能撞、
 * 另一半能穿过去」。</p>
 * <p>但补的方式<b>不是</b>周期性全表重扫（2026-10-13 改掉）：</p>
 * <ul>
 *   <li>已经铺好的格子不会自己消失 —— 塔身范围内玩家放了自己的方块我们本来就不抢，
 *       所以重扫的结果 99.99% 是「什么都不用做」。</li>
 *   <li>对 12.3 万格的 {@code lhc_combo}，全表扫一遍要 60 tick，比间隔还长 ⇒
 *       服务端会变成<b>永远在跑体检</b>。</li>
 * </ul>
 * <p>现在只记账：铺的时候唯一会失败的原因是 {@code hasChunkAt} 为 false，
 * 于是把<b>那些格的表内下标</b>记进 {@link Active#pending}，之后每
 * {@link #PENDING_RETRY_INTERVAL} tick 只重试<b>这几格</b>；补上就从列表里划掉，
 * 列表空了这台机器<b>彻底零开销</b>。区块长时间加载不上时重试间隔会自动拉长（最多 10 倍）。</p>
 * <p>拆除同理：清不掉（区块没加载）的格子会带着 {@link #CLEAR_RETRY_DELAY} 的延时重排队，
 * 最多 {@link #CLEAR_RETRY_ROUNDS} 轮 —— 隐形碰撞块留在世界里是永久隐形墙，必须尽力清干净。</p>
 * <p>好处是机器类<b>完全不需要 tick 订阅、也不需要实例字段</b> ——
 * 只写 {@code onStructureFormed → rigOnFormed()}、
 * {@code onStructureInvalid → rigOnInvalid()} 两行转发即可
 * （{@code MetaMachine.serverTick} 是 final，本来也覆写不了，用
 * {@code subscribeServerTick} 每台机器还要多挂一个订阅，这里省掉了）。</p>
 *
 * <h2>底座最外一圈是故意留空的</h2>
 * <p>塔底那块实心台把整个塔脚印盖死 —— 如果碰撞体照铺，玩家连控制器都点不到
 * （右键不出界面）。每台机器的底座都比自己那座塔的脚印<b>每边多 1 格</b>
 * （取法 = 碰撞体在该轴的最大外延 + 1，tower 43×23 / arch 31×19 / mast 21×17），
 * 而坐标表里 <b>本来就没有</b>最外圈那些格（生成时 {@code --inset 0.02} 把发丝级溢出丢掉了）
 * ⇒ 玩家可以站在外圈上低头点仓室与控制器。<b>别把 inset 改回 0</b>，
 * 否则底座上会凭空长出一圈看不见的墙。</p>
 */
public final class RigCollision {

    private RigCollision() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 每 tick 处理的格数上限 —— 按任务规模自适应（小表用小批量，巨构用大批量）。
     *
     * <p>改几千格必须摊开：单 tick 全铺会让服务端明显卡一下。但摊得太开也不行 ——
     * {@code lhc_combo} 有 12.3 万格，按 320/tick 要铺 6 分半钟。所以：
     * {@code batch = clamp(格数 / 32, 320, } {@value #MAX_BATCH_PER_TICK}{@code )}。</p>
     */
    private static final int MIN_BATCH_PER_TICK = 320;
    private static final int MAX_BATCH_PER_TICK = 2048;

    /**
     * 补漏格的间隔（tick）。<b>不再是「全表体检」</b> ——
     * 只有「成形时因为区块没加载而没铺上」的格子会被重试，见 {@link #tickAll}。
     */
    public static final int PENDING_RETRY_INTERVAL = 20;

    /** 拆除失败（区块没加载）时的最大重试次数 —— 每次间隔 {@link #CLEAR_RETRY_DELAY} tick。 */
    private static final int CLEAR_RETRY_ROUNDS = 60;
    private static final int CLEAR_RETRY_DELAY = 40;

    /**
     * 坐标表缓存：classpath 路径 → 三连一组的 {@code (dx, dy, dz)} 数组。
     * <b>只在服务端主线程访问</b>（ensureTable 由 enqueue / cellCount 触发）。
     */
    private static final Map<String, int[]> TABLES = new HashMap<>();

    /** 与 {@link #TABLES} 配套的格数（0 = 资源缺失）。 */
    private static final Map<String, Integer> TABLE_SIZES = new HashMap<>();

    /**
     * 未完成的任务，按维度排队。
     * <b>只在服务端主线程访问</b>（提交发生在 onStructureFormed / onStructureInvalid /
     * onMachineRemoved，推进发生在 ServerTickEvent）⇒ 无需加锁。
     */
    private static final Map<ServerLevel, ArrayDeque<Job>> JOBS = new HashMap<>();

    /**
     * 结构已成形、需要定期体检的机器。
     *
     * <p>用 (维度, 原点格, 坐标表) 三元组标识 —— 一台机器换朝向后原点格会变，
     * 那时旧的那条会被 {@link #clear} 注销、新的由 {@link #start} 登记。</p>
     */
    private static final List<Active> ACTIVE = new ArrayList<>();

    /**
     * 活跃机器的一条登记。
     *
     * <p><b>不再有「定期全表体检」</b>（2026-10-13 改）：铺的时候唯一会失败的原因是
     * 「那一格所在区块还没加载」（{@code hasChunkAt} 为 false，本类不为碰撞体强制加载区块）。
     * 所以只需要把<b>当时漏掉的那几格</b>记在 {@link #pending} 里，之后每隔
     * {@link #PENDING_RETRY_INTERVAL} tick 重试一次；一旦补上就把它从列表里划掉，
     * 列表空了这台机器就<b>完全不再产生任何 tick 开销</b>。</p>
     *
     * <p>为什么不能只「成形时铺一次」：塔跨几十格（LHC 跨 13 × 13 个区块），
     * 成形的那一刻远处区块多半没加载 —— 那些格子在玩家走过去之前根本铺不上，
     * 不补的话就会出现「塔的一半能撞、另一半能穿过去」。而全表重扫对 12 万格的
     * 巨构是纯浪费（每次扫完要 60 tick，比间隔还长）。</p>
     */
    private static final class Active {
        final ServerLevel level;
        final BlockPos origin;
        final String tablePath;
        Direction axisX;
        Direction axisZ;
        /** 还没铺上的格（表内格号）；null / 空 = 这台机器已经铺全，不再需要任何重试。 */
        int[] pending;
        int retryCountdown = PENDING_RETRY_INTERVAL;
        /** 连续几轮没补上（区块一直没加载）—— 用来把重试间隔逐步拉长，别死磕。 */
        int stalled;

        Active(ServerLevel level, BlockPos origin, String tablePath,
               Direction axisX, Direction axisZ) {
            this.level = level;
            this.origin = origin;
            this.tablePath = tablePath;
            this.axisX = axisX;
            this.axisZ = axisZ;
        }

        boolean is(ServerLevel level, BlockPos origin, String tablePath) {
            return this.level == level
                    && this.origin.equals(origin)
                    && this.tablePath.equals(tablePath);
        }
    }

    /**
     * 一个铺/拆任务。
     *
     * <p>两种形态：<b>整表任务</b>（{@code targets == null}）顺序遍历整张坐标表；
     * <b>定向任务</b>（{@code targets} = 表内格号数组）只处理指定的那些格 ——
     * 补漏格与拆除重试都用它，代价与「待办格数」成正比，与表的总规模无关。</p>
     */
    private static final class Job {
        /** 本任务跑在哪个维度 —— 拆除重试要按它把新任务放回同一张队列。 */
        final ServerLevel level;
        /** 本任务用的坐标表（三连一组，相对原点格）。 */
        final int[] cells;
        /** 表里的格数。 */
        final int cellCount;
        /** 要处理哪些格（表内格号）；null = 整表。 */
        int[] targets;
        /** 坐标表来自哪个 classpath 路径（用于任务去重）。 */
        final String tablePath;
        /** 原点格：坐标表 (0,0,0) 落在世界里的哪一格。 */
        final BlockPos origin;
        /** 塔局部 +x 轴在世界里的方向（长轴，43 格那条）。 */
        final Direction axisX;
        /** 塔局部 +z 轴在世界里的方向（短轴，23 格那条）。 */
        final Direction axisZ;
        final boolean placing;
        /** 还要等几 tick 才开始推进（拆除重试用；普通任务为 0）。 */
        int delay;
        /** 本次没做成的格（区块没加载）—— 铺的任务会转成 Active.pending，拆的任务会重排队。 */
        final List<Integer> skipped = new ArrayList<>();
        /** 拆除重试已经跑了几轮。 */
        int attempt;
        int cursor;

        Job(ServerLevel level, int[] cells, int cellCount, String tablePath, BlockPos origin,
            Direction axisX, Direction axisZ, boolean placing) {
            this.level = level;
            this.cells = cells;
            this.cellCount = cellCount;
            this.tablePath = tablePath;
            this.origin = origin;
            this.axisX = axisX;
            this.axisZ = axisZ;
            this.placing = placing;
        }

        /** 本任务一共要处理几格。 */
        int count() {
            return targets == null ? cellCount : targets.length;
        }

        /** 第 {@code cursor} 个待办格在表里的格号。 */
        int target(int cursor) {
            return targets == null ? cursor : targets[cursor];
        }
    }

    /** 某张坐标表的格数（0 表示资源缺失）；顺带触发加载。 */
    public static int cellCount(String tablePath) {
        ensureTable(tablePath);
        return TABLE_SIZES.getOrDefault(tablePath, 0);
    }

    /**
     * 收回碰撞体时，沿「控制器背面（BACK）」方向额外多清的格数。
     *
     * <h2>为什么要这个容差</h2>
     * <p>原点格 = 控制器 + BACK × <b>底座前后半深</b>（{@code rigPlateHalfFb()}）。
     * 一旦某台机器的底盘尺寸被改小，<b>同一台机器的旧碰撞体是用旧半深铺的</b> ——
     * 比如 2026-10-12 先把 arch / mast 从 43×23 缩到 31×19 / 21×19（半深 11 → 9），
     * 随后 mast 因外形修正再次缩到 <b>21×17</b>（半深 9 → 8），
     * 旧原点就比新原点<b>靠后 2 格</b>。此时按新原点去清，整片清除区域跟着平移 2 格，
     * 于是旧布局<b>靠后的那 2 排会永远留在世界里</b>（隐形、生存挖不动、又看不见）。</p>
     * <p>所以收回时按 BACK 方向多清几格，把「历史上底盘更大时留下的布局」一并收掉。
     * 4 格的容差足够覆盖任何合理的尺寸调整，且各机器间距 ≥ 21 格，
     * 不会误伤邻居（万一擦到，邻居的定期体检也会在 10 秒内补回来）。</p>
     * <p>注意<b>只有 BACK 方向需要容差</b>：原点只由前后半深决定，
     * 左右半宽变了不影响原点位置。</p>
     */
    private static final int CLEAR_BACK_TOLERANCE = 4;

    /**
     * 铺开某台机器的整片碰撞体，并把它登记为「活跃」（之后由 {@link #tickAll} 定期补块）。
     * 重复调用是安全的（只补空缺格）。
     */
    public static void start(ServerLevel level, BlockPos origin, Direction axisX, Direction axisZ,
                             String tablePath) {
        markActive(level, origin, axisX, axisZ, tablePath);
        enqueue(level, origin, axisX, axisZ, tablePath, true);
    }

    /**
     * 收回某台机器的整片碰撞体并注销「活跃」登记
     * （只清本 mod 的碰撞方块，别的一律不动）。
     *
     * <p>会连带清掉「同一台机器、但按<b>更大的旧底盘</b>铺出来的残留」，见
     * {@link #CLEAR_BACK_TOLERANCE}。</p>
     */
    public static void clear(ServerLevel level, BlockPos origin, Direction axisX, Direction axisZ,
                              String tablePath) {
        unmarkActive(level, origin, tablePath);
        enqueue(level, origin, axisX, axisZ, tablePath, false);
        // 容差：底盘尺寸被改小过的话，旧原点比现在更靠 BACK 方向 ⇒ 顺着 BACK 多清几格。
        // axisZ 是塔局部 +z 轴（= 机器的 FRONT 朝向）⇒ BACK 取它的反方向。
        Direction back = axisZ.getOpposite();
        for (int extra = 1; extra <= CLEAR_BACK_TOLERANCE; extra++) {
            enqueue(level, origin.relative(back, extra), axisX, axisZ, tablePath, false);
        }
    }

    /**
     * 补漏格 —— <b>只重试这台机器「成形时因为区块没加载而没铺上」的那几格</b>。
     *
     * <p>这是 2026-10-13 从「每 200 tick 全表体检」改过来的。原来的做法有两个毛病：
     * ① 已经铺好的格子根本不会变（玩家的方块我们本来就不抢），全表重扫纯属浪费；
     * ② 对 {@code lhc_combo} 这种 12 万格的巨构，扫一遍要 60 tick，比间隔还长，
     * 等于服务端永远在跑体检。</p>
     *
     * <p>现在：铺全了（{@code pending} 空）就<b>完全不做任何事</b>；
     * 有漏格时只重试漏的那几格，代价与漏格数成正比。</p>
     */
    public static void verify(ServerLevel level, BlockPos origin, Direction axisX, Direction axisZ,
                              String tablePath) {
        Active active = findActive(level, origin, tablePath);
        if (active == null || active.pending == null || active.pending.length == 0) {
            return;
        }
        if (hasJob(level, origin, tablePath)) {
            return; // 同类任务正在跑，别把它顶掉（顶掉会让游标归零、永远铺不完）
        }
        enqueueTargets(level, origin, axisX, axisZ, tablePath, active.pending, true);
    }

    /** 服务器停机时清空任务表与活跃表（避免跨存档累积引用）。 */
    public static void reset() {
        JOBS.clear();
        ACTIVE.clear();
    }

    private static Active findActive(ServerLevel level, BlockPos origin, String tablePath) {
        for (Active a : ACTIVE) {
            if (a.is(level, origin, tablePath)) {
                return a;
            }
        }
        return null;
    }

    private static boolean hasJob(ServerLevel level, BlockPos origin, String tablePath) {
        ArrayDeque<Job> queue = JOBS.get(level);
        if (queue == null) {
            return false;
        }
        for (Job job : queue) {
            if (job.origin.equals(origin) && job.tablePath.equals(tablePath)) {
                return true;
            }
        }
        return false;
    }

    private static void markActive(ServerLevel level, BlockPos origin, Direction axisX,
                                    Direction axisZ, String tablePath) {
        for (Active a : ACTIVE) {
            if (a.is(level, origin, tablePath)) {
                a.axisX = axisX;
                a.axisZ = axisZ;
                a.retryCountdown = PENDING_RETRY_INTERVAL;
                return;
            }
        }
        ACTIVE.add(new Active(level, origin, tablePath, axisX, axisZ));
    }

    private static void unmarkActive(ServerLevel level, BlockPos origin, String tablePath) {
        ACTIVE.removeIf(a -> a.is(level, origin, tablePath));
    }

    private static void enqueue(ServerLevel level, BlockPos origin, Direction axisX, Direction axisZ,
                                String tablePath, boolean placing) {
        ensureTable(tablePath);
        int cellCount = TABLE_SIZES.getOrDefault(tablePath, 0);
        if (cellCount == 0) {
            return;
        }
        int[] cells = TABLES.get(tablePath);
        ArrayDeque<Job> queue = JOBS.computeIfAbsent(level, k -> new ArrayDeque<>());
        // 同一个 (坐标表, 原点) 只留最新任务（两种模式一起顶掉）：「拆掉又立刻摆上」时
        // 旧的拆除任务必须先作废，否则两个任务先后跑完会互相覆盖成错的结果。
        // 注意：朝向变了以后原点也会变（原点 = 控制器 + BACK×N），所以「转到别的朝向」
        // 得到的是两个不同的任务 —— 这正确：旧朝向那片必须按旧原点拆掉。
        queue.removeIf(j -> j.origin.equals(origin) && j.tablePath.equals(tablePath));
        queue.add(new Job(level, cells, cellCount, tablePath, origin, axisX, axisZ, placing));
        // 注意：这里**不**立即推进。enqueue 是在 onStructureFormed / onMachineRemoved 里
        // 被调用的，此刻外层的 setBlock 还没结束，就地改邻格是重入；交给 ServerTickEvent 更安全。
    }

    /**
     * 定向任务：只处理 {@code targets} 里那些格（表内格号）。
     * 补漏格用它 —— 代价与待办格数成正比，与表的总规模无关。
     */
    private static void enqueueTargets(ServerLevel level, BlockPos origin, Direction axisX,
                                       Direction axisZ, String tablePath,
                                       int[] targets, boolean placing) {
        if (targets == null || targets.length == 0) {
            return;
        }
        ensureTable(tablePath);
        int cellCount = TABLE_SIZES.getOrDefault(tablePath, 0);
        if (cellCount == 0) {
            return;
        }
        Job job = new Job(level, TABLES.get(tablePath), cellCount, tablePath, origin,
                axisX, axisZ, placing);
        job.targets = targets;
        ArrayDeque<Job> queue = JOBS.computeIfAbsent(level, k -> new ArrayDeque<>());
        queue.removeIf(j -> j.origin.equals(origin) && j.tablePath.equals(tablePath));
        queue.add(job);
    }

    /** 由 {@code ObjDemoEvents} 订阅的 {@code ServerTickEvent} 每 tick 调一次。 */
    public static void tickAll() {
        // ① 推进铺设 / 拆除任务
        if (!JOBS.isEmpty()) {
            Iterator<Map.Entry<ServerLevel, ArrayDeque<Job>>> it = JOBS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ServerLevel, ArrayDeque<Job>> entry = it.next();
                step(entry.getKey(), entry.getValue());
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        // ② 补漏格：**只对「还有漏格」的机器干活**，铺全的机器这里是纯 continue。
        //    注意 verify 只动 JOBS、不动 ACTIVE ⇒ 遍历过程中不会并发修改。
        for (Active a : ACTIVE) {
            if (a.pending == null || a.pending.length == 0) {
                continue;
            }
            if (--a.retryCountdown > 0) {
                continue;
            }
            if (hasJob(a.level, a.origin, a.tablePath)) {
                continue;
            }
            // 区块一直没加载的话别死磕：连续补不上就把间隔拉长（最多 10 倍）。
            a.retryCountdown = Math.min(PENDING_RETRY_INTERVAL * (1 + a.stalled), PENDING_RETRY_INTERVAL * 10);
            // ⚠ 一次把 <b>整张 pending</b> 交给任务 —— 分帧由 Job 自己的 batchFor()
            //   负责。**不要**在这里只切前 N 格投递：任务收尾时
            //   {@code a.pending = job.skipped}，那样会把这一批之外的漏格永久丢掉。
            enqueueTargets(a.level, a.origin, a.axisX, a.axisZ, a.tablePath, a.pending, true);
        }
    }

    private static void step(ServerLevel level, ArrayDeque<Job> queue) {
        Job job = queue.peekFirst();
        if (job.delay > 0) {
            job.delay--;
            return;
        }
        int budget = batchFor(job);
        while (budget-- > 0 && job.cursor < job.count()) {
            int cell = job.target(job.cursor++);
            int i = cell * 3;
            if (!apply(level, job, job.cells[i], job.cells[i + 1], job.cells[i + 2])) {
                job.skipped.add(cell); // 区块没加载 ⇒ 记账，回头只补这一格
            }
        }
        if (job.cursor >= job.count()) {
            queue.pollFirst();
            finish(job);
        }
    }

    /** 每 tick 处理几格：小表 320 起步，巨构按 表大小/32 提速（上限 2048）。 */
    private static int batchFor(Job job) {
        int n = Math.max(job.count(), 1);
        return Math.max(MIN_BATCH_PER_TICK, Math.min(MAX_BATCH_PER_TICK, n / 32));
    }

    /** 任务收尾：铺的任务把漏格交给 Active，拆的任务给漏格排一次重试。 */
    private static void finish(Job job) {
        if (job.placing) {
            Active a = findActive(job.level, job.origin, job.tablePath);
            if (a != null) {
                a.stalled = job.skipped.isEmpty() ? 0 : a.stalled + 1;
                a.pending = toIntArray(job.skipped);
                a.retryCountdown = PENDING_RETRY_INTERVAL;
            }
            if (!job.skipped.isEmpty()) {
                LOGGER.info("[gtoecore] rig collision {}: {} cell(s) not placed yet "
                                + "(their chunks are not loaded) - will retry only those",
                        job.tablePath, job.skipped.size());
            }
            return;
        }
        // 拆除同样会碰到「区块没加载」：那些隐形的碰撞块留在世界里就是永久隐形墙，
        // 必须重试；重试若干轮还不行就如实报错（玩家可以靠近后重新拆一次）。
        if (job.skipped.isEmpty()) {
            return;
        }
        if (job.attempt < CLEAR_RETRY_ROUNDS) {
            Job retry = new Job(job.level, job.cells, job.cellCount, job.tablePath, job.origin,
                    job.axisX, job.axisZ, false);
            retry.targets = toIntArray(job.skipped);
            retry.attempt = job.attempt + 1;
            retry.delay = CLEAR_RETRY_DELAY;
            ArrayDeque<Job> queue = JOBS.computeIfAbsent(job.level, k -> new ArrayDeque<>());
            queue.removeIf(j -> j.origin.equals(job.origin) && j.tablePath.equals(job.tablePath));
            queue.add(retry);
        } else {
            LOGGER.warn("[gtoecore] rig collision {}: {} cell(s) could NOT be cleared "
                            + "(chunks never loaded) - invisible blocks may remain at {}",
                    job.tablePath, job.skipped.size(), job.origin);
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /**
     * 把坐标表里的一格 (i, j, k)（相对原点格、塔自身坐标系）落到世界里。
     *
     * <p>旋转用格中心映射：格子中心在塔坐标系里是 (i, j, k)，把两个水平基向量
     * 换成世界方向即可 —— 90° 的整数倍旋转下中心仍是整数坐标，直接就是目标格。
     * （用格角映射需要 ±1 修正，是这一块最容易错一格的地方。）</p>
     *
     * @return false = 那一格所在区块没加载（需要回头重试）；true = 处理过了
     *         （铺上了 / 本来就该跳过）
     */
    private static boolean apply(ServerLevel level, Job job, int i, int j, int k) {
        int dx = job.axisX.getStepX() * i + job.axisZ.getStepX() * k;
        int dz = job.axisX.getStepZ() * i + job.axisZ.getStepZ() * k;
        BlockPos pos = job.origin.offset(dx, j, dz);

        // 不为了碰撞体去强制加载/生成区块 —— 没加载就先记账，等区块加载后再补。
        // 注：这里**不**跳过「原点格自身」—— 原点格在底座中心的正上方
        //（塔底的一部分），必须照铺，否则塔底会留下一个 1 格空洞。
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        BlockState old = chunk.getBlockState(pos);
        BlockState want;
        if (job.placing) {
            if (old.is(GTOEObjDemo.RIG_COLLISION.get()) || !isFreeSpace(old)) {
                return true; // 已经铺上 / 那里有玩家自己的东西 ⇒ 不再重试
            }
            want = GTOEObjDemo.RIG_COLLISION.get().defaultBlockState();
        } else {
            if (!old.is(GTOEObjDemo.RIG_COLLISION.get())) {
                return true;
            }
            want = Blocks.AIR.defaultBlockState();
        }
        setFast(level, chunk, pos, old, want);
        return true;
    }

    /** 只往「空 / 可替换且没有流体」的格子里塞，玩家的建筑、水、岩浆都保持原样。 */
    private static boolean isFreeSpace(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /**
     * 绕开 {@code Level.setBlock} 的写入：直接改 chunk 状态 + 单独同步客户端。
     * 见类注释「为什么绕开 Level.setBlock」。
     */
    private static void setFast(ServerLevel level, LevelChunk chunk, BlockPos pos,
                                BlockState old, BlockState state) {
        BlockState replaced = chunk.setBlockState(pos, state, false);
        if (replaced == null || replaced == state) {
            return;
        }
        chunk.setUnsaved(true);
        level.sendBlockUpdated(pos, old, state, Block.UPDATE_CLIENTS);
    }

    /** 懒加载并缓存一张坐标表。 */
    private static void ensureTable(String path) {
        if (TABLES.containsKey(path) || TABLE_SIZES.containsKey(path)) {
            return;
        }
        List<int[]> parsed = new ArrayList<>();
        try (InputStream in = RigCollision.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.error("[gtoecore] rig collision table missing: {}", path);
                TABLES.put(path, new int[0]);
                TABLE_SIZES.put(path, 0);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                parsed.add(new int[]{
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])});
            }
        } catch (Throwable t) {
            LOGGER.error("[gtoecore] failed to read rig collision table {}", path, t);
            TABLES.put(path, new int[0]);
            TABLE_SIZES.put(path, 0);
            return;
        }
        int[] cells = new int[parsed.size() * 3];
        for (int i = 0; i < parsed.size(); i++) {
            int[] c = parsed.get(i);
            cells[i * 3] = c[0];
            cells[i * 3 + 1] = c[1];
            cells[i * 3 + 2] = c[2];
        }
        TABLES.put(path, cells);
        TABLE_SIZES.put(path, parsed.size());
        LOGGER.info("[gtoecore] rig collision table loaded: {} cells ({})", parsed.size(), path);
    }
}
