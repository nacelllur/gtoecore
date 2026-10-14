package com.gtoecore.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从<b>碰撞体坐标表</b>里读出「塔在本地坐标系里占多大」——只读表头数据，不读表本身。
 *
 * <h2>为什么需要它</h2>
 * <p>GTM 的机器方块实体的 {@code getRenderBoundingBox()} 是<b>直接问渲染器的</b>
 * （{@code MetaMachineBlockEntity:354} → {@code berModel.getRenderBoundingBox(this)}），
 * 而这个盒子就是<b>原版视锥剔除的判据</b>（{@code LevelRenderer} 的
 * {@code frustum.isVisible(be.getRenderBoundingBox())}）。</p>
 * <p>本 mod 之前给的是 {@code inflate(140)} —— 一个 281 × 281 × 281 的立方体。
 * 那等于<b>关掉剔除</b>：只要玩家在 256 格内，不管朝哪看，四台塔全都会走一遍
 * 顶点处理。改成「贴着塔的真实范围」以后，背过身去就能整台剔掉。</p>
 *
 * <h2>为什么按坐标表算，而不是写死数字</h2>
 * <p>坐标表就是这座塔的离线体素化结果（{@code gen_tower_collision.py} 用同一个
 * {@code --scale / --rx} 生成），它的 min/max 天然等于「模型在世界里的实际外延」
 * —— 表重烤时盒子自动跟着变，不会像手写常量那样悄悄失配。</p>
 *
 * <p>结果按表路径缓存（{@link ConcurrentHashMap}）：客户端渲染线程会读它，
 * 而坐标表本身由服务端线程加载，两边不共享任何可变状态。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RigModelBounds {

    /** {@code {minX, maxX, minY, maxY, minZ, maxZ}}（格，相对「原点格」）。 */
    private static final Map<String, int[]> CACHE = new ConcurrentHashMap<>();

    /** 读失败的表（资源缺失等）——记住失败，避免每帧重试 + 刷日志。 */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    /**
     * @param tablePath 坐标表的 classpath 路径，如
     *                  {@code /assets/gtoecore/structures/tower_rig_collision.txt}
     * @return {@code {minX, maxX, minY, maxY, minZ, maxZ}}；读不到返回 {@code null}
     */
    public static int[] of(String tablePath) {
        int[] cached = CACHE.get(tablePath);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(tablePath)) {
            return null;
        }
        int[] bounds = read(tablePath);
        if (bounds == null) {
            FAILED.add(tablePath);
        } else {
            CACHE.put(tablePath, bounds);
        }
        return bounds;
    }

    /** 资源包重载后调用：下次读取重新解析（坐标表不会变，纯粹为了开发期改表即生效）。 */
    public static void invalidate() {
        CACHE.clear();
        FAILED.clear();
    }

    private static int[] read(String tablePath) {
        try (InputStream in = RigModelBounds.class.getResourceAsStream(tablePath)) {
            if (in == null) {
                return null;
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            boolean any = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    // 行格式固定为 "dx dy dz"
                    int firstSpace = line.indexOf(' ');
                    if (firstSpace < 0) {
                        continue;
                    }
                    int secondSpace = line.indexOf(' ', firstSpace + 1);
                    if (secondSpace < 0) {
                        continue;
                    }
                    int dx = Integer.parseInt(line.substring(0, firstSpace).trim());
                    int dy = Integer.parseInt(line.substring(firstSpace + 1, secondSpace).trim());
                    int dz = Integer.parseInt(line.substring(secondSpace + 1).trim());
                    if (dx < minX) minX = dx;
                    if (dx > maxX) maxX = dx;
                    if (dy < minY) minY = dy;
                    if (dy > maxY) maxY = dy;
                    if (dz < minZ) minZ = dz;
                    if (dz > maxZ) maxZ = dz;
                    any = true;
                }
            }
            return any ? new int[] { minX, maxX, minY, maxY, minZ, maxZ } : null;
        } catch (Exception e) {
            return null;
        }
    }

    private RigModelBounds() {
    }
}
