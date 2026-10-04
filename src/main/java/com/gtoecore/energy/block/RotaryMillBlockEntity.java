package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 旋转石磨 BE —— RU 消费端，让燃烧引擎真正"有用"。
 *
 * <p>工作流：</p>
 * <ul>
 *   <li>相邻旋转轴把转速推入（{@link IGTOEnergyAcceptor}），rpm 每 tick 由轴刷新；</li>
 *   <li>输入槽放可磨物（矿石/原矿等），输出槽空置；</li>
 *   <li>转速 ≥ 配方要求 → 每 tick 推进进度（转速越快越快），满后产出；</li>
 *   <li>转速不足 → 空转（ACTIVE=false），不推进。</li>
 * </ul>
 */
public class RotaryMillBlockEntity extends BlockEntity implements IGTOEnergyAcceptor {

    /** 内置配方表：输入 tag → 输出物品 registry id + 所需最小转速 + 基准进度。
     *  用 tag 匹配（forge:raw_materials/* / forge:ores/*），原版矿和 GT 矿都通吃。 */
    private static final List<MillRecipe> RECIPES = new ArrayList<>();

    static {
        // 原矿 → 锭（16 rpm 即可，木板档就能磨）
        RECIPES.add(new MillRecipe(tag("forge", "raw_materials/iron"),    "minecraft:iron_ingot",   16L, 200));
        RECIPES.add(new MillRecipe(tag("forge", "raw_materials/gold"),    "minecraft:gold_ingot",   16L, 200));
        RECIPES.add(new MillRecipe(tag("forge", "raw_materials/copper"),  "minecraft:copper_ingot", 16L, 200));
        // 矿石 → 原矿（32 rpm 即可磨，不会被轴损耗卡死）
        RECIPES.add(new MillRecipe(tag("forge", "ores/iron"),             "minecraft:raw_iron",     32L, 240));
        RECIPES.add(new MillRecipe(tag("forge", "ores/gold"),             "minecraft:raw_gold",     32L, 240));
        RECIPES.add(new MillRecipe(tag("forge", "ores/copper"),           "minecraft:raw_copper",   32L, 240));
        // 建筑链
        RECIPES.add(new MillRecipe(tag("minecraft", "sand"),              "minecraft:gravel",       16L, 60));
        RECIPES.add(new MillRecipe(tag("minecraft", "gravel"),            "minecraft:flint",        16L, 80));
        RECIPES.add(new MillRecipe(tag("minecraft", "stone_crafting_materials"), "minecraft:cobblestone", 16L, 60));
        RECIPES.add(new MillRecipe(tag("minecraft", "cobblestone"),       "minecraft:gravel",       16L, 80));
        RECIPES.add(new MillRecipe(tag("minecraft", "bones"),             "minecraft:bone_meal",    16L, 60));
    }

    private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag(String ns, String path) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                new net.minecraft.resources.ResourceLocation(ns, path));
    }

    /** 输入（1 格）与输出（1 格） */
    public final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** 当前转速（由轴每 tick 推送） */
    private long rpm;
    /** 最近一次收到能量注入的 gameTime —— 用于「轴断供后自动停转」 */
    private long lastInjectTime = -100L;
    /** 当前配方 */
    private MillRecipe current;
    /** 研磨进度（0..duration） */
    private int progress;

    public RotaryMillBlockEntity(BlockPos pos, BlockState state) {
        super(GTNBlocks.ROTARY_MILL_BE.get(), pos, state);
    }

    /** 玩家右键：空手取出输出；持可磨物放入输入。 */
    public InteractionResult onPlayerUse(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            ItemStack out = inventory.extractItem(1, 1, false);
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        ItemStack remain = inventory.insertItem(0, held.copy(), false);
        if (remain.getCount() < held.getCount()) {
            held.shrink(held.getCount() - remain.getCount());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RotaryMillBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        if (level == null) return;
        ItemStack input = inventory.getStackInSlot(0);
        ItemStack output = inventory.getStackInSlot(1);

        // 解析当前配方（tag 匹配，第一个命中的生效）
        if (current == null || !current.matches(input)) {
            current = null;
            if (!input.isEmpty()) {
                for (MillRecipe r : RECIPES) {
                    if (r.matches(input)) {
                        current = r;
                        progress = 0;
                        break;
                    }
                }
            }
        }

        boolean active = false;
        if (current != null && output.getCount() < output.getMaxStackSize()) {
            // 断供/引擎停机后轴推 0 → rpm 归零 → 缓退不推进
            long effRpm = (level.getGameTime() - lastInjectTime <= 20L) ? rpm : 0L;
            // 转速达标才推进；rpm 越高越快
            if (effRpm >= current.minRpm) {
                int step = Math.max(1, (int) (effRpm / 32L));
                progress += step;
                active = true;
                if (progress >= current.duration) {
                    // 完成：产出
                    ItemStack out = current.makeOutput();
                    boolean ok = false;
                    if (!out.isEmpty()) {
                        if (output.isEmpty()) {
                            inventory.setStackInSlot(1, out);
                            ok = true;
                        } else if (ItemStack.isSameItemSameTags(output, out)
                                && output.getCount() < output.getMaxStackSize()) {
                            output.grow(1);
                            inventory.setStackInSlot(1, output);
                            ok = true;
                        }
                    }
                    if (ok) {
                        input.shrink(1);
                        inventory.setStackInSlot(0, input);
                        progress = 0;
                        current = null;
                    } else {
                        // 输出槽满/异类无法叠加：暂停，不吞输入
                        progress = current.duration;
                    }
                }
            } else {
                progress = Math.max(0, progress - 1); // 转速不足缓退
            }
        }

        if (active != state.getValue(RotaryMillBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(RotaryMillBlock.ACTIVE, active), 3);
        }
    }

    public long getRpm() { return rpm; }
    public int getProgress() { return progress; }
    public MillRecipe getCurrent() { return current; }

    // ---------------- IGTOEnergyAcceptor ----------------

    @Override
    public boolean acceptsGTOEnergy(GTOEnergyType type, Direction side) {
        return type == GTOEnergyType.ROTATION;
    }

    @Override
    public long injectGTOEnergy(GTOEnergyType type, Direction side, int tier, long amount, boolean simulate) {
        if (type != GTOEnergyType.ROTATION) return 0L;
        if (!simulate) {
            rpm = amount;
            if (level != null) lastInjectTime = level.getGameTime();
        }
        return amount;
    }

    // ---------------- Capabilities（供自动输出/管道等取用） ----------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of(() -> inventory).cast();
        }
        return super.getCapability(cap, side);
    }

    // ---------------- NBT ----------------

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inv"));
        rpm = tag.getLong("Rpm");
        progress = tag.getInt("Prog");
        lastInjectTime = tag.contains("LastInject") ? tag.getLong("LastInject") : -100L;
        current = null; // 重新解析
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inv", inventory.serializeNBT());
        tag.putLong("Rpm", rpm);
        tag.putInt("Prog", progress);
        tag.putLong("LastInject", lastInjectTime);
    }

    /** 配方条目 */
    public record MillRecipe(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> inputTag,
                             String outputId, long minRpm, int duration) {

        public boolean matches(ItemStack input) {
            if (input.isEmpty()) return false;
            return input.is(inputTag);
        }

        public ItemStack makeOutput() {
            var outItem = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(new net.minecraft.resources.ResourceLocation(outputId));
            return outItem == null ? ItemStack.EMPTY : new ItemStack(outItem);
        }
    }
}