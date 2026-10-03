package com.gtoecore.item;

import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 无人机 —— 无人机蜂群之心（drone_swarm_heart）的任务耗材。
 *
 * <p>两种机型（探测 survey / 开采 mining）× 14 个电压等级（LV~MAX），
 * 机身颜色对应官方 {@code GTValues.VC} 等级色。等级越高可执行的任务越高阶
 * （配方中用对应等级无人机作为输入物品）。</p>
 */
public class GTNDroneItem extends Item {

    public enum DroneType {
        /** 探测型：地形/资源勘察任务 */
        SURVEY("survey"),
        /** 开采型：钻探/采集任务 */
        MINING("mining");

        public final String id;

        DroneType(String id) {
            this.id = id;
        }
    }

    private final int tier;
    private final DroneType droneType;

    public GTNDroneItem(Properties properties, int tier, DroneType droneType) {
        super(properties);
        this.tier = tier;
        this.droneType = droneType;
    }

    /** GTValues 电压等级（LV=1 .. MAX=14） */
    public int getTier() {
        return tier;
    }

    public DroneType getDroneType() {
        return droneType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gtoecore.drone.type." + droneType.id)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gtoecore.drone.tooltip.tier", GTValues.VN[tier])
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
