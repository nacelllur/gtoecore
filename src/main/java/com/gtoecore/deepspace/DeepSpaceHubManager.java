package com.gtoecore.deepspace;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 深空枢纽网络管理器 —— 对标 GTO 通天之路的「电梯 ↔ 模块 ↔ 连接舱」三方联动。
 *
 * <p>GTO 用 {@code IIWirelessInteractor} 无线登记（模块主动 register 到电梯）。
 * 本实现改用<b>就近扫描</b>：枢纽每 {@link #SCAN_INTERVAL} tick 扫一次自身半径
 * {@link #SCAN_RADIUS} 内的方块实体，凡已成形的扩展模块即计入网络。
 * 模块放下就生效、拆掉就失效，不需要任何存盘绑定信息，也不会因区块
 * 卸载/加载顺序产生脏登记。</p>
 *
 * <p><b>识别方式：</b>GTM 的机器方块实体统一是 {@link MetaMachineBlockEntity}，
 * 直接 {@code getMetaMachine()} 判类型即可（GTO 的 MonitorMachine 读任意机器同法），
 * 无需为枢纽/模块单独注册 BlockEntityType。</p>
 *
 * <p><b>遍历方式（1.20.1 踩坑）：</b>此版本没有
 * {@code ServerLevel.getBlockEntities(AABB)}，只能按区块遍历
 * {@link LevelChunk#getBlockEntities()}；未加载的区块直接跳过。</p>
 */
public final class DeepSpaceHubManager {

    /** 扫描半径（方块）—— 模块放在枢纽 16 格内即自动挂载 */
    public static final int SCAN_RADIUS = 16;
    /** 扫描周期（tick）—— GTO 模块每 10 tick 校验在线，这里取更省的 40 */
    public static final int SCAN_INTERVAL = 40;

    /** 加成下限保护：无限叠加时不允许出现 0 耗时 / 0 耗能这种退化 */
    private static final double FACTOR_FLOOR = 0.15;

    /** 各类模块的数量统计 + 聚合加成 */
    public static final class Bonus {
        private final Map<ModuleKind, Integer> counts = new EnumMap<>(ModuleKind.class);
        private int totalModules;

        /** 计入一台模块（内部用；对外只读 {@link #count(ModuleKind)}） */
        void add(ModuleKind kind) {
            counts.merge(kind, 1, Integer::sum);
            totalModules++;
        }

        /** 某类模块的台数 */
        public int count(ModuleKind kind) {
            return counts.getOrDefault(kind, 0);
        }

        public int totalModules() {
            return totalModules;
        }

        /** 并行加成（累加：每台 +N） */
        public int bonusParallel() {
            return count(ModuleKind.PARALLEL) * ModuleKind.PARALLEL.parallelPerModule();
        }

        /** 耗时倍率（累乘，下限保护） */
        public double durationFactor() {
            return clamp(Math.pow(ModuleKind.OVERCLOCK.durationFactor(), count(ModuleKind.OVERCLOCK)));
        }

        /** EU 倍率（累乘，下限保护） */
        public double eutFactor() {
            return clamp(Math.pow(ModuleKind.EFFICIENCY.eutFactor(), count(ModuleKind.EFFICIENCY)));
        }

        /** 产出倍率（累乘） */
        public double outputFactor() {
            return Math.pow(ModuleKind.OUTPUT.outputFactor(), count(ModuleKind.OUTPUT));
        }

        /** 等级加成（累加：每台 +N） */
        public int bonusTier() {
            return count(ModuleKind.SENSING) * ModuleKind.SENSING.tierPerModule();
        }

        /** 耗材倍率（累乘，下限保护，越小越省） */
        public double fuelFactor() {
            return clamp(Math.pow(ModuleKind.RECYCLER.fuelFactor(), count(ModuleKind.RECYCLER)));
        }

        public Map<ModuleKind, Integer> counts() {
            return counts;
        }

        /** 是否存在任何加成（GUI 判定） */
        public boolean isEmpty() {
            return totalModules == 0;
        }
    }

    /** 扫描枢纽周围已成形的扩展模块，返回聚合加成 */
    public static Bonus scan(Level level, BlockPos center) {
        Bonus bonus = new Bonus();
        forEachBlockEntity(level, center, SCAN_RADIUS, be -> {
            DeepSpaceModuleMachine module = asModule(be);
            if (module != null && module.isFormed()) {
                bonus.add(module.getModuleKind());
            }
        });
        return bonus;
    }

    /** 机器方块实体 → 扩展模块机器（非模块返回 null） */
    public static DeepSpaceModuleMachine asModule(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity mbe) {
            MetaMachine machine = mbe.getMetaMachine();
            if (machine instanceof DeepSpaceModuleMachine module) return module;
        }
        return null;
    }

    /** 机器方块实体 → 深空枢纽机器（非枢纽返回 null） */
    public static DeepSpaceHubMachine asHub(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity mbe) {
            MetaMachine machine = mbe.getMetaMachine();
            if (machine instanceof DeepSpaceHubMachine hub) return hub;
        }
        return null;
    }

    /** 找离本模块最近、已成形的枢纽；返回 null 表示模块孤立（不在任何网络内） */
    public static DeepSpaceHubMachine findHub(Level level, BlockPos modulePos) {
        DeepSpaceHubMachine[] best = new DeepSpaceHubMachine[1];
        double[] bestDist = {Double.MAX_VALUE};
        forEachBlockEntity(level, modulePos, SCAN_RADIUS, be -> {
            DeepSpaceHubMachine hub = asHub(be);
            if (hub != null && hub.isFormed()) {
                double d = hub.getPos().distSqr(modulePos);
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                    best[0] = hub;
                }
            }
        });
        return best[0];
    }

    /** 加成明细（枢纽 GUI / 模块 GUI 共用） */
    public static List<Component> describe(Bonus bonus) {
        List<Component> out = new ArrayList<>();
        for (ModuleKind kind : ModuleKind.values()) {
            int n = bonus.count(kind);
            if (n > 0) {
                out.add(Component.translatable("gtoecore.deepspace.module_line", n,
                        Component.translatable(kind.cn())));
            }
        }
        return out;
    }

    /** 遍历半径内已成形的扩展模块（枢纽推送用） */
    public static void forEachModule(Level level, BlockPos center,
                                     java.util.function.Consumer<DeepSpaceModuleMachine> action) {
        forEachBlockEntity(level, center, SCAN_RADIUS, be -> {
            DeepSpaceModuleMachine module = asModule(be);
            if (module != null && module.isFormed()) {
                action.accept(module);
            }
        });
    }

    /**
     * 按区块遍历半径内的方块实体（1.20.1 无 AABB 版 API）。
     * 未加载区块直接跳过；重复坐标以区块 Map 为准（每格只出现一次）。
     */
    private static void forEachBlockEntity(Level level, BlockPos center, int radius,
                                           java.util.function.Consumer<BlockEntity> action) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;
        double r2 = (double) radius * radius;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.getBlockPos().distSqr(center) <= r2) {
                        action.accept(be);
                    }
                }
            }
        }
    }

    private static double clamp(double v) {
        return Math.max(FACTOR_FLOOR, v);
    }

    private DeepSpaceHubManager() {}
}