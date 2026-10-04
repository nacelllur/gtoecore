package com.gtoecore.energy;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * GT6 风味能量类型（gtoecore 自研能量子系统）。
 *
 * <p>借鉴 GregTech 6 的 TagData 开放能量体系，但简化为枚举——每种能量有
 * 自己的计量单位与区间语义（温度用开尔文区间、转速用 rpm 区间）。</p>
 *
 * <p>MVP 覆盖 4 种核心能量；QU/LU/MU 等后续按需扩充。</p>
 */
public enum GTOEnergyType implements StringRepresentable {

    /** 旋转能（RU）：计量 = 转速 rpm；配方区间 = 可用转速带 */
    ROTATION("ru", "旋转能", "RPM"),
    /** 热能（HU）：计量 = 温度 K；配方区间 = 可用温度带 */
    HEAT("hu", "热能", "K"),
    /** 冷能（CU）：计量 = 温度 K；配方区间 = 可用低温带 */
    COLD("cu", "冷能", "K"),
    /** 动能（KU，活塞/气动推动）：计量 = 压强 kPa；预留 */
    KINETIC("ku", "动能", "kPa");

    /** NBT / codec 序列化用编解码器 */
    public static final Codec<GTOEnergyType> CODEC = StringRepresentable.fromEnum(GTOEnergyType::values);

    private final String id;
    private final String displayName;
    private final String unit;

    GTOEnergyType(String id, String displayName, String unit) {
        this.id = id;
        this.displayName = displayName;
        this.unit = unit;
    }

    public String getId() {
        return "gtoecore." + id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
