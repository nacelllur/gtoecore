# -*- coding: utf-8 -*-
"""
生成 gtncore 无人机蜂群版本的全部资源 JSON 与语言条目：
  1. 28 个无人机物品模型（item/generated）
  2. 原始计算机外壳：blockstate / 方块模型 / 物品模型 / 掉落表 / 镐子可挖 tag
  3. 无人机蜂群之心：blockstate（24 朝向）/ 机器模型 / 物品模型
     （贴图复用 gtceu 电脑外壳 + HPCA 正面覆层）
  4. zh_cn / en_us 语言条目补丁

用法：python tools/add_drone_resources.py
"""
import json
import os
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "gtncore")

VN = ["ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV",
      "UEV", "UIV", "UXV", "OpV", "MAX"]
TIERS = list(range(1, 15))  # LV..MAX


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("write", os.path.relpath(path, RES))


def item_generated(parent_tex):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": parent_tex}}


def main():
    # ---- 1. 无人机物品模型 ×28 ----
    for tier in TIERS:
        vn = VN[tier].lower()
        for kind in ("survey", "mining"):
            dump(os.path.join(ASSETS, "models", "item", f"{kind}_drone_{vn}.json"),
                 item_generated(f"gtncore:item/{kind}_drone_{vn}"))

    # ---- 2. 原始计算机外壳 ----
    dump(os.path.join(ASSETS, "blockstates", "primitive_computer_casing.json"),
         {"variants": {"": {"model": "gtncore:block/primitive_computer_casing"}}})
    dump(os.path.join(ASSETS, "models", "block", "primitive_computer_casing.json"),
         {"parent": "minecraft:block/cube_all",
          "textures": {"all": "gtceu:block/casings/hpca/computer_casing/front"}})
    dump(os.path.join(ASSETS, "models", "item", "primitive_computer_casing.json"),
         {"parent": "gtncore:block/primitive_computer_casing"})
    dump(os.path.join(RES, "data", "gtncore", "loot_tables", "blocks",
                      "primitive_computer_casing.json"),
         {"type": "minecraft:block",
          "pools": [{"bonus_rolls": 0.0,
                     "conditions": [{"condition": "minecraft:survives_explosion"}],
                     "entries": [{"type": "minecraft:item",
                                  "name": "gtncore:primitive_computer_casing"}],
                     "rolls": 1.0}]})
    dump(os.path.join(RES, "data", "minecraft", "tags", "blocks", "mineable",
                      "pickaxe.json"),
         {"replace": False, "values": ["gtncore:primitive_computer_casing"]})

    # ---- 3. 无人机蜂群之心 ----
    # 3a. blockstate：复用生产车间模板（24 个朝向组合），只换模型名
    with open(os.path.join(ASSETS, "blockstates", "clone_production_workshop.json"),
              encoding="utf-8") as f:
        bs = json.load(f)
    for v in bs["variants"].values():
        v["model"] = "gtncore:block/machine/drone_swarm_heart"
    dump(os.path.join(ASSETS, "blockstates", "drone_swarm_heart.json"), bs)

    # 3b. 机器模型：底壳 = gtceu 电脑外壳 front；覆层 = gtceu HPCA 正面
    CASING = "gtceu:block/casings/hpca/computer_casing/front"
    OVL = "gtceu:block/multiblock/hpca/overlay_front"
    states = {  # (idle/suspend 用静态覆层，waiting/working 用动态覆层)
        "idle": (OVL, OVL + "_emissive"),
        "suspend": (OVL, OVL + "_emissive"),
        "waiting": (OVL + "_active", OVL + "_active_emissive"),
        "working": (OVL + "_active", OVL + "_active_emissive"),
    }
    variants = OrderedDict()
    for formed in ("false", "true"):
        for status, (ovl, ovl_e) in states.items():
            variants[f"is_formed={formed},recipe_logic_status={status}"] = {
                "model": {"parent": "gtceu:block/machine/template/cube_all/sided",
                          "textures": {"all": CASING,
                                       "overlay_front": ovl,
                                       "overlay_front_emissive": ovl_e}}}
    dump(os.path.join(ASSETS, "models", "block", "machine", "drone_swarm_heart.json"),
         {"parent": "minecraft:block/block",
          "loader": "gtceu:machine",
          "machine": "gtncore:drone_swarm_heart",
          "texture_overrides": {"all": CASING},
          "variants": variants})
    dump(os.path.join(ASSETS, "models", "item", "drone_swarm_heart.json"),
         {"parent": "gtncore:block/machine/drone_swarm_heart"})

    # ---- 4. 语言条目 ----
    zh = OrderedDict()
    en = OrderedDict()
    zh["gtceu.drone_swarm"] = "无人机蜂群"
    en["gtceu.drone_swarm"] = "Drone Swarm"
    for tier in TIERS:
        vn_low = VN[tier].lower()
        vn = VN[tier]
        zh[f"item.gtncore.survey_drone_{vn_low}"] = f"{vn} 探测无人机"
        en[f"item.gtncore.survey_drone_{vn_low}"] = f"{vn} Survey Drone"
        zh[f"item.gtncore.mining_drone_{vn_low}"] = f"{vn} 开采无人机"
        en[f"item.gtncore.mining_drone_{vn_low}"] = f"{vn} Mining Drone"
    zh["gtncore.drone.type.survey"] = "探测型：执行勘察/测绘任务"
    en["gtncore.drone.type.survey"] = "Survey drone: runs recon & mapping tasks"
    zh["gtncore.drone.type.mining"] = "开采型：执行钻探/采集任务"
    en["gtncore.drone.type.mining"] = "Mining drone: runs drilling & gathering tasks"
    zh["gtncore.drone.tooltip.tier"] = "无人机蜂群之心耗材 · 电压等级 %s"
    en["gtncore.drone.tooltip.tier"] = "Drone Swarm Heart consumable - Voltage tier %s"
    zh["block.gtncore.primitive_computer_casing"] = "原始计算机外壳"
    en["block.gtncore.primitive_computer_casing"] = "Primitive Computer Casing"
    zh["item.gtncore.primitive_computer_casing"] = "原始计算机外壳"
    en["item.gtncore.primitive_computer_casing"] = "Primitive Computer Casing"
    zh["block.gtncore.drone_swarm_heart"] = "无人机蜂群之心"
    en["block.gtncore.drone_swarm_heart"] = "Drone Swarm Heart"
    zh["item.gtncore.drone_swarm_heart"] = "无人机蜂群之心"
    en["item.gtncore.drone_swarm_heart"] = "Drone Swarm Heart"
    zh["gtncore.machine.drone_swarm_heart.tooltip.1"] = \
        "GTO 高性能计算阵列的降级电动版：指挥无人机蜂群执行任务"
    en["gtncore.machine.drone_swarm_heart.tooltip.1"] = \
        "A downgraded electric take on GTO's HPCA: commands drone swarms on missions"
    zh["gtncore.machine.drone_swarm_heart.tooltip.2"] = \
        "结构：2×5×5 原始计算机外壳；需能量仓(1~2)、维护仓与物品/流体输入输出仓"
    en["gtncore.machine.drone_swarm_heart.tooltip.2"] = \
        "Structure: 2x5x5 Primitive Computer Casings; needs Energy Hatch (1-2), Maintenance Hatch and item/fluid IO hatches"
    zh["gtncore.machine.drone_swarm_heart.tooltip.3"] = \
        "在输入总线放入对应电压等级的探测/开采无人机以执行配方"
    en["gtncore.machine.drone_swarm_heart.tooltip.3"] = \
        "Put survey/mining drones of the matching voltage tier in the input bus to run recipes"

    for lang, patch in (("zh_cn", zh), ("en_us", en)):
        path = os.path.join(ASSETS, "lang", f"{lang}.json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f, object_pairs_hook=OrderedDict)
        data.update(patch)
        dump(path, data)


if __name__ == "__main__":
    main()
