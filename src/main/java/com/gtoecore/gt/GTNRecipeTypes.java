package com.gtoecore.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import net.minecraft.world.item.ItemStack;

/**
 * gtoecore 自定义配方类型。
 *
 * <p><b>命名空间坑（勿踩）：</b>{@code GTRecipeTypes.register(name, ...)} 内部用
 * {@code GTCEu.id(name)} → 注册出来的是 <b>gtceu:bio</b>，不是 gtoecore:bio。</p>
 * <ul>
 *   <li>显示名语言键：{@code gtceu.bio}（本 mod 的 lang 文件里提供，任意命名空间都能供键）。</li>
 *   <li>数据包配方 JSON 的 type 字段：{@code "type": "gtceu:bio"}。</li>
 * </ul>
 *
 * <p><b>注册时序：</b>GTNCoreGT 在 mod 构造期挂 {@code GTRecipeType.class} 的
 * RegisterEvent 监听；GTCEu 在 CommonProxy.init 里先 {@code GTRecipeTypes.init()}
 * （派发事件并冻结配方类型注册表）、后 {@code GTMachines.init()}（派发机器注册事件）。
 * 因此本类 init() 一定先于 GTNMachines.initMachines() 执行，
 * 机器引用 BIO_RECIPES 时它已就绪。</p>
 */
public final class GTNRecipeTypes {

    /** 生物配方类型（注册 id：gtceu:bio；显示名：生物 / Biology） */
    public static GTRecipeType BIO_RECIPES;

    /** 太空采矿配方类型（注册 id：gtceu:space_mining；显示名：太空采矿 / Space Mining） */
    public static GTRecipeType SPACE_MINING_RECIPES;

    /** 深空探索配方类型（注册 id：gtceu:deep_space_exploration；显示名：深空探索 / Deep Space Exploration） */
    public static GTRecipeType DEEP_SPACE_EXPLORATION_RECIPES;

    public static void init() {
        BIO_RECIPES = GTRecipeTypes.register("bio", GTRecipeTypes.ELECTRIC)
                // 物品输入 9 / 输出 3，流体输入 4 / 输出 3（生物配方按材料+培养液规模取宽）
                .setMaxIOSize(9, 3, 4, 3)
                .setEUIO(IO.IN)
                // 输入槽贴图用酿造机的小瓶覆层，语义贴合
                .setSlotOverlay(false, false, GuiTextures.BREWER_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW_MULTIPLE,
                        ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                // JEI 分类图标用生产车间控制器（懒加载，注册完成后才会取）
                .setIconSupplier(() -> GTNMachines.CLONE_PRODUCTION_WORKSHOP == null
                        ? ItemStack.EMPTY : GTNMachines.CLONE_PRODUCTION_WORKSHOP.asStack())
                .setSound(GTSoundEntries.BATH);

        // 太空采矿：输入开采无人机 + 任务耗材，输出矿产；流体槽留给钻探液/岩浆等
        SPACE_MINING_RECIPES = GTRecipeTypes.register("space_mining", GTRecipeTypes.ELECTRIC)
                .setMaxIOSize(9, 9, 3, 3)
                .setEUIO(IO.IN)
                .setSlotOverlay(false, false, GuiTextures.INT_CIRCUIT_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW_MULTIPLE,
                        ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setIconSupplier(() -> GTNMachines.DRONE_SWARM_HEART == null
                        ? ItemStack.EMPTY : GTNMachines.DRONE_SWARM_HEART.asStack())
                .setSound(GTSoundEntries.MINER);

        // 深空探索：输入探测无人机 + 任务耗材，输出勘察数据/样本
        DEEP_SPACE_EXPLORATION_RECIPES = GTRecipeTypes.register("deep_space_exploration", GTRecipeTypes.ELECTRIC)
                .setMaxIOSize(9, 9, 3, 3)
                .setEUIO(IO.IN)
                .setSlotOverlay(false, false, GuiTextures.INT_CIRCUIT_OVERLAY)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW_MULTIPLE,
                        ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setIconSupplier(() -> GTNMachines.DRONE_SWARM_HEART == null
                        ? ItemStack.EMPTY : GTNMachines.DRONE_SWARM_HEART.asStack())
                .setSound(GTSoundEntries.COMPUTATION);
    }

    private GTNRecipeTypes() {}
}
