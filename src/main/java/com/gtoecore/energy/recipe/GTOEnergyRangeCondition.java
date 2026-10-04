package com.gtoecore.energy.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gtoecore.energy.GTOEnergyBridge;
import com.gtoecore.energy.GTOEnergyType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * GT6 风味能量区间条件 —— 要求运行该配方的多方块主机实现 {@link IGTOEnergyHost}，
 * 且其当前能量值（转速 rpm / 温度 K）落在 [min, max] 区间内。
 *
 * <p>用法：{@code recipe.condition(GTOEnergyRangeCondition.rotation(64, 128))}</p>
 */
public class GTOEnergyRangeCondition extends RecipeCondition<GTOEnergyRangeCondition> {

    /** codec：仿 GTM RainingCondition 的写法——先 isReverse 再业务字段 */
    public static final Codec<GTOEnergyRangeCondition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.fieldOf("reverse").forGetter(RecipeCondition::isReverse),
                    GTOEnergyType.CODEC.fieldOf("energy").forGetter(c -> c.energyType),
                    Codec.LONG.fieldOf("min").forGetter(c -> c.min),
                    Codec.LONG.fieldOf("max").forGetter(c -> c.max)
            ).apply(instance, GTOEnergyRangeCondition::new));

    public static final RecipeConditionType<GTOEnergyRangeCondition> TYPE =
            new RecipeConditionType<>(GTOEnergyRangeCondition::new, CODEC);

    private GTOEnergyType energyType;
    private long min;
    private long max;

    /** 无参构造（TYPE 工厂 + createTemplate 用） */
    public GTOEnergyRangeCondition() {
        super();
        this.energyType = GTOEnergyType.ROTATION;
        this.min = 0L;
        this.max = 0L;
    }

    public GTOEnergyRangeCondition(GTOEnergyType type, long min, long max) {
        this.energyType = type;
        this.min = min;
        this.max = max;
    }

    /** codec 反序列化构造 */
    private GTOEnergyRangeCondition(boolean reverse, GTOEnergyType type, long min, long max) {
        super(reverse);
        this.energyType = type;
        this.min = min;
        this.max = max;
    }

    /** 便捷工厂：旋转能转速区间 */
    public static GTOEnergyRangeCondition rotation(long minRpm, long maxRpm) {
        return new GTOEnergyRangeCondition(GTOEnergyType.ROTATION, minRpm, maxRpm);
    }

    /** 便捷工厂：热能温度区间 */
    public static GTOEnergyRangeCondition heat(long minK, long maxK) {
        return new GTOEnergyRangeCondition(GTOEnergyType.HEAT, minK, maxK);
    }

    @Override
    public RecipeConditionType<GTOEnergyRangeCondition> getType() {
        return TYPE;
    }

    @Override
    protected boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic logic) {
        // 经桥接工具查询：单方块机器直接 instanceof，GTM 多方块主机扫描其仓
        Object host = logic.machine;
        long current = GTOEnergyBridge.queryValue(host, energyType);
        return current >= min && current <= max;
    }

    @Override
    public @NotNull Component getTooltips() {
        return Component.translatable("gtoecore.condition.energy_range",
                energyType.getDisplayName(), min, max, energyType.getUnit());
    }

    @Override
    public GTOEnergyRangeCondition createTemplate() {
        return new GTOEnergyRangeCondition();
    }

    public GTOEnergyType getEnergyType() { return energyType; }
    public long getMin() { return min; }
    public long getMax() { return max; }
}
