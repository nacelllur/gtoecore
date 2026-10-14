package com.gtoecore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtoecore.energy.GTOEnergyType;
import com.gtoecore.gt.CombustionEngineMachine;
import com.gtoecore.gt.GTOEnergyConverterMachine;
import com.gtoecore.gt.GTOEnergyInputHatchPartMachine;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * GTOE 机器的 Jade 读数 provider。
 *
 * <p>服务端（{@link #appendServerData}）：把机器的实时读数写入 accessor 的 serverData NBT。
 * 客户端（{@link #appendTooltip}）：按方块类型从 NBT 读数据渲染工具提示。</p>
 */
public class GTOEnergyProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {

    private static final ResourceLocation UID =
            new ResourceLocation("gtoecore", "gtoe_energy");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    // ---------------- 服务端：打包数据 ----------------

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MetaMachineBlockEntity mmbe)) return;
        var machine = mmbe.getMetaMachine();

        if (machine instanceof GTOEnergyConverterMachine converter) {
            tag.putString("gtok", "converter");
            tag.putString("gtoType", converter.getOutputType().getSerializedName());
            tag.putLong("gtoVal", converter.getCurrentValue());
            tag.putLong("gtoCap", converter.energyContainer.getEnergyCapacity());
            tag.putLong("gtoStored", converter.energyContainer.getEnergyStored());
            tag.putInt("gtoDraw", converter.getLastDraw());
            tag.putInt("gtoDrawMax", converter.getDrawBudget());
        } else if (machine instanceof GTOEnergyInputHatchPartMachine hatch) {
            tag.putString("gtok", "hatch");
            CompoundTag vals = new CompoundTag();
            for (GTOEnergyType t : GTOEnergyType.values()) {
                vals.putLong(t.getSerializedName(), hatch.getGTOEnergyValue(t));
            }
            tag.put("gtoVals", vals);
        } else if (machine instanceof CombustionEngineMachine engine) {
            tag.putString("gtok", "engine");
            tag.putBoolean("gtoBurn", engine.isBurning());
            tag.putInt("gtoBurnTicks", engine.getBurnRemaining());
            tag.putLong("gtoRpm", engine.getNominalSpeed());
        }
    }

    // ---------------- 客户端：渲染工具提示 ----------------

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) return;
        String kind = data.getString("gtok");
        switch (kind) {
            case "converter" -> appendConverter(tooltip, data);
            case "hatch" -> appendHatch(tooltip, data);
            case "engine" -> appendEngine(tooltip, data);
            default -> { /* 不是 GTOE 机器，跳过 */ }
        }
    }

    private void appendConverter(ITooltip tooltip, CompoundTag data) {
        GTOEnergyType type = GTOEnergyType.byName(data.getString("gtoType"));
        if (type == null) return;
        long val = data.getLong("gtoVal");
        long cap = data.getLong("gtoCap");
        long stored = data.getLong("gtoStored");
        int draw = data.getInt("gtoDraw");
        int drawMax = data.getInt("gtoDrawMax");

        tooltip.add(Component.translatable("gtoecore.jade.converter",
                type.getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gtoecore.jade.value",
                val + " " + type.getUnit()).withStyle(
                        val > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("gtoecore.jade.eu",
                stored + " / " + cap + " EU").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("gtoecore.jade.draw",
                draw + " / " + drawMax + " EU/t").withStyle(ChatFormatting.DARK_AQUA));
    }

    private void appendHatch(ITooltip tooltip, CompoundTag data) {
        if (!data.contains("gtoVals")) return;
        CompoundTag vals = data.getCompound("gtoVals");
        for (GTOEnergyType t : GTOEnergyType.values()) {
            long v = vals.getLong(t.getSerializedName());
            tooltip.add(Component.literal(
                    "§7" + t.getDisplayName() + ": " +
                    (v > 0 ? "§6" + v + " " + t.getUnit() : "§8--"))
                    .withStyle(ChatFormatting.RESET));
        }
    }

    private void appendEngine(ITooltip tooltip, CompoundTag data) {
        boolean burn = data.getBoolean("gtoBurn");
        int ticks = data.getInt("gtoBurnTicks");
        long rpm = data.getLong("gtoRpm");

        tooltip.add(Component.translatable("gtoecore.jade.engine_state",
                burn ? Component.translatable("gtoecore.jade.burning")
                     : Component.translatable("gtoecore.jade.idle"))
                .withStyle(burn ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        if (burn) {
            tooltip.add(Component.translatable("gtoecore.jade.burn_time",
                    ticks / 20 + "s").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("gtoecore.jade.rpm",
                    rpm + " rpm").withStyle(ChatFormatting.RED));
        }
    }
}
