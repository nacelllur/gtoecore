package com.gtoecore.util;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 多方块结构「预览」(MultiblockShapeInfo) 与「pattern 谓词」的通用工具。
 *
 * <h2>为什么需要它</h2>
 * <p>GTM 的 {@code FactoryBlockPattern} 只能描述每个格子<b>放什么方块</b>，<b>不能描述方块状态</b>
 * （{@code facing} / {@code half} / {@code type} / {@code shape} …）。这是刻意的：pattern 匹配走
 * {@code Predicates.blocks(Block)}（也就是 {@code PredicateBlocks}），比较的是
 * {@code state.getBlock()}，因此<b>机器怎么转都能成形</b>，玩家怎么摆按钮/楼梯都不影响判定。</p>
 *
 * <p>代价是：JEI / GTM 面板里的<b>结构预览</b>由 {@code BlockPattern.getPreview()} 生成，
 * 它取的是每个谓词的 {@code candidates}；而 {@code Predicates.blocks(...)} 的候选是
 * {@code BlockInfo.fromBlock(block)} —— <b>方块的默认状态</b>。于是：</p>
 * <ul>
 *   <li>{@code polished_blackstone_button} 默认 {@code face=wall, facing=north} →
 *       预览里 16 个按钮全挤在朝北那面墙上，另一半"消失"；</li>
 *   <li>{@code smooth_quartz_stairs} 默认 {@code facing=north, half=bottom} → 朝向/上下全错；</li>
 *   <li>{@code *_slab} 默认 {@code type=bottom} → 上半砖变下半砖。</li>
 * </ul>
 *
 * <h2>解决办法</h2>
 * <p>给机器补一个显式的 {@code MultiblockShapeInfo}（{@code .shapeInfo(definition -> ...)}），
 * 每个符号映射到<b>完整的 BlockState</b>。这才是 GTM 官方做法 —— 例如研究站用
 * {@code Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, ...)} 表达门。</p>
 *
 * <p>本类把「造状态」和「安全查方块」两件事收敛成几个静态方法，于是任意结构的预览都只需要
 * 一行 {@code .where('x', GTNPreview.state(Blocks.XXX, "facing=east", "half=top"))}。</p>
 *
 * <h2>注意：预览是「控制器朝北」的渲染</h2>
 * <p>{@code BlockPattern.getPreview()} 内部固定调用
 * {@code setActualRelativeOffset(..., Direction.NORTH, Direction.UP, false)}，GTM 自带的所有
 * 手写 shapeInfo 也都写 {@code where('S', definition, Direction.NORTH)}。所以预览永远是把结构
 * 摆成"控制器朝北"的样子。投影（.litematic）里控制器朝别的方向时，<b>方块状态必须整体旋转到
 * 朝北的坐标系</b>，否则位置对了、朝向还是错。工作区脚本
 * {@code tools/litematic2gtm.py} 会自动完成这一步，不要手工推。</p>
 *
 * @see com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo
 */
public final class GTNPreview {

    private GTNPreview() {}

    // ------------------------------------------------------------------
    // 方块查找（对可选 mod 友好）
    // ------------------------------------------------------------------

    /**
     * 按注册 id 查方块。
     *
     * <p>注意 {@code BuiltInRegistries.BLOCK.get()} 对<b>不存在的方块</b>返回的是
     * {@code Blocks.AIR} 而不是 {@code null} —— 直接拿它去建谓词会得到
     * {@code Predicates.blocks(Blocks.AIR)}，也就是"这个位置必须是空气"，机器永远不成形。
     * 所以这里显式把这种情况转成 {@code null}。</p>
     *
     * @return 方块；不存在时返回 {@code null}
     */
    @Nullable
    public static Block block(String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation rl;
        try {
            rl = new ResourceLocation(id);
        } catch (Exception e) {
            return null;
        }
        Block b = BuiltInRegistries.BLOCK.get(rl);
        if (b == Blocks.AIR && !"minecraft:air".equals(id)) return null;
        return b;
    }

    // ------------------------------------------------------------------
    // 状态构造
    // ------------------------------------------------------------------

    /**
     * 在方块默认状态上施加若干 {@code "属性名=值"} 覆盖。
     *
     * <p>未知属性名、非法属性值都<b>静默跳过</b>（不会抛异常），所以可以放心地把整张状态表
     * 原样喂进来 —— 换 MC 版本或换方块时最多是丢一条属性，而不是崩存档。</p>
     *
     * <pre>{@code
     * GTNPreview.state(Blocks.SMOOTH_QUARTZ_STAIRS, "facing=east", "half=top")
     * GTNPreview.state(Blocks.POLISHED_BLACKSTONE_BUTTON, "face=wall", "facing=south", "powered=false")
     * }</pre>
     */
    public static BlockState state(Block block, String... keyValues) {
        BlockState state = block.defaultBlockState();
        if (keyValues == null) return state;
        for (String pair : keyValues) {
            if (pair == null) continue;
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            for (Property<?> property : state.getProperties()) {
                if (!property.getName().equals(key)) continue;
                Optional<?> parsed = property.getValue(value);
                if (parsed.isPresent()) {
                    state = apply(state, property, parsed.get());
                }
                break;
            }
        }
        return state;
    }

    /** 按 id 查方块并构造状态；方块缺失时退化为空气（可选 mod 兼容）。 */
    public static BlockState stateOrAir(String blockId, String... keyValues) {
        Block b = block(blockId);
        return b == null ? Blocks.AIR.defaultBlockState() : state(b, keyValues);
    }

    // Property<T> 与 BlockState.setValue 的泛型在擦除后不匹配，这里集中做一次 unchecked 转换。
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState apply(BlockState state, Property property, Object value) {
        return state.setValue(property, (Comparable) value);
    }

    // ------------------------------------------------------------------
    // pattern 谓词
    // ------------------------------------------------------------------

    /**
     * pattern 侧：方块存在就要求它，不存在就退化为"任意"。
     *
     * <p>写可选 mod（AE2、其它 addon）装饰方块时的标准写法 —— 缺失时该位置随便放什么，
     * 而不是变成"必须是空气"。</p>
     */
    public static TraceabilityPredicate blocksOrAny(String blockId) {
        return blocksOrAny(block(blockId));
    }

    /** pattern 侧：方块存在就要求它，{@code null} 则退化为"任意"。 */
    public static TraceabilityPredicate blocksOrAny(@Nullable Block block) {
        return block == null ? Predicates.any() : Predicates.blocks(block);
    }
}
