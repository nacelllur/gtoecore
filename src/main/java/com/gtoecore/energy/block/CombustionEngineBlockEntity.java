package com.gtoecore.energy.block;

import com.gtoecore.GTNBlocks;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.energy.IGTOEnergyEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 燃烧引擎 BE：燃料余量计时 + 转速档位。
 *
 * <p>转速标称（MVP，按熔炉燃烧时间分档）：</p>
 * <ul>
 *   <li>burnTime ≥ 2400（煤块/干 kcal 块）→ 128 rpm（MV 档）</li>
 *   <li>burnTime ≥ 800（煤/烈焰棒）→ 64 rpm（LV 高）</li>
 *   <li>burnTime ≥ 100（木板/木棍等）→ 32 rpm（LV）</li>
 * </ul>
 * 运转中一律输出当前档位标称转速；停火 = 0（轴链自然归零）。
 */
public class CombustionEngineBlockEntity extends BlockEntity implements IGTOEnergyEmitter,
        GTOConduitBlock.GTOEnergyEmitterHost {

    /** 剩余燃烧时间（tick） */
    private int burnRemaining;
    /** 当前档位标称转速（rpm） */
    private long nominalSpeed;

    public CombustionEngineBlockEntity(BlockPos pos, BlockState state) {
        super(GTNBlocks.COMBUSTION_ENGINE_BE.get(), pos, state);
    }

    /** 手持燃料右键：填充一单位燃料。一格燃料固定燃烧 2 分钟（2400 tick），燃料品质决定转速档位。 */
    public InteractionResult tryAddFuel(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int burn = ForgeHooks.getBurnTime(stack, null);
        if (burn <= 0 || level == null || level.isClientSide) {
            return InteractionResult.PASS;
        }
        consumeFuel(burn);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.6f, 1.0f);
        setChanged();
        return InteractionResult.CONSUME;
    }

    /** GUI 燃料槽注入：一格燃料 = 2400 tick，燃料品质决定转速档位（与手持右键同规则）。 */
    public void addFuel(ItemStack stack) {
        int burn = ForgeHooks.getBurnTime(stack, null);
        if (burn <= 0 || level == null || level.isClientSide) {
            return;
        }
        consumeFuel(burn);
        level.playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.6f, 1.0f);
        setChanged();
    }

    private void consumeFuel(int burnTime) {
        // 一格燃料 = 固定 2400 tick（2 分钟）——避免木板 100 tick 5 秒烧完的坑
        burnRemaining += 2400;
        nominalSpeed = Math.max(nominalSpeed, nominalForBurn(burnTime));
    }

    public int getBurnRemaining() {
        return burnRemaining;
    }

    public long getNominalSpeed() {
        return nominalSpeed;
    }

    private static long nominalForBurn(int burnTime) {
        if (burnTime >= 2400) return 128L;
        if (burnTime >= 800) return 64L;
        return 32L;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CombustionEngineBlockEntity be) {
        boolean burning = be.burnRemaining > 0;
        if (burning) {
            be.burnRemaining--;
            if (be.burnRemaining == 0) {
                be.nominalSpeed = 0L; // 烧完归零
            }
        }
        if (burning != state.getValue(CombustionEngineBlock.LIT)) {
            level.setBlock(pos, state.setValue(CombustionEngineBlock.LIT, burning), 3);
        }
    }

    // ---------------- IGTOEnergyEmitter ----------------

    @Override
    public long pullGTOEnergy(GTOEnergyType type, Direction side, int tier) {
        if (burnRemaining <= 0) return 0L;
        return switch (type) {
            case ROTATION -> nominalSpeed;
            case HEAT -> nominalSpeed * 3L + 300L;   // 燃烧 → 同时产热（约 400~700 K）
            default -> 0L;
        };
    }

    @Override
    public boolean emits(GTOEnergyType type) {
        return type == GTOEnergyType.ROTATION || type == GTOEnergyType.HEAT;
    }

    // ---------------- NBT ----------------

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        burnRemaining = tag.getInt("Burn");
        nominalSpeed = tag.getLong("Speed");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Burn", burnRemaining);
        tag.putLong("Speed", nominalSpeed);
    }
}
