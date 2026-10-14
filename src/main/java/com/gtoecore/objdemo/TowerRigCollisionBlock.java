package com.gtoecore.objdemo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 机架类机器（{@code tower_rig} / {@code arch_rig} / {@code mast_rig}）共用的
 * 「隐形碰撞方块」—— 没有任何外观，只提供碰撞与遮挡。
 *
 * <p><b>为什么需要它</b>：塔模型是 4.45 倍放大后的纯视觉模型
 * （tower 约 40 × 66 × 21 格、arch 约 24 × 34 × 15 格、mast 约 17 × 33 × 13 格），
 * 方块本体只有底座那 1 层，玩家会直接穿过去。而<b>往单个方块上挂超大碰撞箱是行不通的</b>
 * —— 原版 {@code BlockCollisions} 只扫描「实体自身 AABB ±1 格」的范围
 * （字节码实测：{@code floor(aabb.min - 1e-7) - 1} … {@code floor(aabb.max + 1e-7) + 1}），
 * 所以离那个方块一格远，它的形状就再也不会被查询。大体积碰撞只能落成真实方块。</p>
 *
 * <p>碰撞体的坐标表由 {@code tools/gen_tower_collision.py} 离线体素化生成
 * （三角形×立方格精确相交，非采样，所以没有缝），
 * 由 {@link RigCollision} 在结构成形后分批铺开。</p>
 *
 * <h2>⚠ 必须显式给出碰撞形状（空模型 ≠ 满方块碰撞）</h2>
 * <p>{@code blockstates} 指向的 {@code obj_collision_empty} 是 {@code "elements": []}
 * 的空模型，而方块形状是从模型推出来的 —— <b>空模型得到的是空形状，也就是「既不挡人、
 * 也点不到、更没有轮廓」</b>，整片碰撞体会彻底失效。所以这里显式覆写：</p>
 * <ul>
 *   <li>{@link #getShape} = 满方块：让方块可被准星瞄准、可显示轮廓、可被
 *       创造模式拆除（万一自动收回失效，这是唯一的补救口子）。</li>
 *   <li>{@link #getCollisionShape} = 满方块：真正的碰撞 + 可站立面。</li>
 * </ul>
 * <p>注意<b>不能</b>让 {@code getShape} 返回 {@code Shapes.empty()}：那样虽然轮廓更干净，
 * 但碰撞块会变成「点不到也拆不掉」的永久隐形障碍 —— 一旦收回任务失败就是不可恢复的脏档。</p>
 *
 * <h2>为什么是「不可破坏」</h2>
 * <p>{@code strength(-1, 3600000)} = 基岩式：生存模式挖不动
 * （{@code getDestroyProgress} 见 destroySpeed == -1 直接返回 0），爆炸也炸不掉，
 * 掉落为空。这样玩家不会在看不见的情况下误挖出一个洞，
 * 也不会有苦力怕在塔底炸开一条缝。创造模式与 {@code /setblock} 仍可清除。</p>
 */
public class TowerRigCollisionBlock extends Block {

    public TowerRigCollisionBlock() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .strength(-1.0F, 3600000.0F)   // 基岩式：生存挖不动、炸不烂
                .sound(SoundType.METAL)
                .noLootTable()
                // 本体外观是空模型（blockstates 指向 obj_collision_empty），
                // 不遮挡、不透光、不憋气 —— 一座 66 格高的塔不该在脚下留一片黑影。
                .noOcclusion()
                // 可被替换：玩家想在这块「看起来是空气」的地方放自己的方块时不该被拒绝
                // （放置会直接覆盖本方块，之后由定期体检按需补回）。
                .replaceable()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false));
    }

    /** 准星形状 = 满方块（可瞄准 / 有轮廓 / 可被创造模式拆掉）。见类注释的警告。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 真正的碰撞形状 = 满方块。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
