# -*- coding: utf-8 -*-
"""
生成 gtncore 深空枢纽体系（deep_space_hub + 6 类扩展模块）的全部资源：
  1. 深空枢纽主机：blockstate（24 朝向）/ 机器模型 / 物品模型 / 掉落表 / 镐子 tag
  2. 6 台扩展模块：同上（模型按 EARTH-STAR 调色板：浅灰混凝土 + 青陶瓦）
  3. 深空燃料单元：物品模型
  4. zh_cn / en_us 语言条目补丁

外观原则（用户要求）：全部使用 EARTH-STAR 空间站本身的原版方块，
不新增任何自创方块或贴图。主机 = 平滑石英壳 + 深板岩砖地板 +
浅灰染色玻璃窗 + 青陶瓦核心 + 海晶灯；模块 = 浅灰混凝土壳 + 青陶瓦核心。

用法：python tools/add_deep_space_resources.py
"""
import json
import os
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "gtncore")

# 模块清单（与 Java ModuleKind 保持一致）
MODULES = [
    ("parallel",   "并行阵列模块",   "Parallel Array Module",
     "每台 +%s 并行", " +%s parallel each"),
    ("overclock",  "算力超频模块",   "Overclock Module",
     "每台配方耗时 ×0.97", " x0.97 recipe duration each"),
    ("efficiency", "能效优化模块",   "Efficiency Module",
     "每台 EU 消耗 ×0.97", " x0.97 EU usage each"),
    ("output",     "产出增幅模块",   "Output Amplifier Module",
     "每台产物产出 ×1.05", " x1.05 output each"),
    ("sensing",    "深空传感模块",   "Deep Sensing Module",
     "每台 +1 网络等级", " +1 network tier each"),
    ("recycler",   "耗材回收模块",   "Fuel Recycler Module",
     "每台燃料消耗 ×0.96", " x0.96 fuel usage each"),
]

# EARTH-STAR 配色贴图（原版方块）
HUB_CASING = "minecraft:block/smooth_quartz"          # 主壳体
MODULE_CASING = "minecraft:block/light_gray_concrete"  # 模块壳体


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("write", os.path.relpath(path, RES))


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=OrderedDict)


def make_blockstate(dest_name, model_path, rotation_state):
    """复用已有机器 blockstate 模板换模型名。

    rotation_state: 'ALL' → 24 变体（NON_Y_AXIS/ALL 通用模板 clone_production_workshop）
                    'NON_Y_AXIS' → 同 24 变体模板
    """
    src = os.path.join(ASSETS, "blockstates", "clone_production_workshop.json")
    bs = read_json(src)
    for v in bs["variants"].values():
        v["model"] = model_path
    dump(os.path.join(ASSETS, "blockstates", dest_name + ".json"), bs)


def make_machine_model(dest_name, casing_tex, overlay_dir):
    """机器模型：底壳贴图 + 覆层（idle/suspend 静态，waiting/working 动态）。"""
    ovl = overlay_dir + "/overlay_front"
    states = {
        "idle":    (ovl, ovl + "_emissive"),
        "suspend": (ovl, ovl + "_emissive"),
        "waiting": (ovl + "_active", ovl + "_active_emissive"),
        "working": (ovl + "_active", ovl + "_active_emissive"),
    }
    variants = OrderedDict()
    for formed in ("false", "true"):
        for status, (o, oe) in states.items():
            variants[f"is_formed={formed},recipe_logic_status={status}"] = {
                "model": {
                    "parent": "gtceu:block/machine/template/cube_all/sided",
                    "textures": {"all": casing_tex,
                                 "overlay_front": o,
                                 "overlay_front_emissive": oe}}}
    dump(os.path.join(ASSETS, "models", "block", "machine", dest_name + ".json"),
         {"parent": "minecraft:block/block",
          "loader": "gtceu:machine",
          "machine": "gtncore:" + dest_name,
          "texture_overrides": {"all": casing_tex},
          "variants": variants})
    dump(os.path.join(ASSETS, "models", "item", dest_name + ".json"),
         {"parent": "gtncore:block/machine/" + dest_name})


def make_loot_and_tag(block_id, drop_id):
    dump(os.path.join(RES, "data", "gtncore", "loot_tables", "blocks", block_id + ".json"),
         {"type": "minecraft:block",
          "pools": [{"bonus_rolls": 0.0,
                     "conditions": [{"condition": "minecraft:survives_explosion"}],
                     "entries": [{"type": "minecraft:item", "name": drop_id}],
                     "rolls": 1.0}]})
    # 注意：pickaxe tag 必须【读-合并-写】，不能直接覆盖 ——
    # 否则每个方块都会把前一个从 tag 里冲掉（本脚本踩过）。
    tag_path = os.path.join(RES, "data", "minecraft", "tags", "blocks", "mineable",
                            "pickaxe.json")
    tag = read_json(tag_path) if os.path.isfile(tag_path) else {"replace": False, "values": []}
    values = tag.setdefault("values", [])
    if drop_id not in values:
        values.append(drop_id)
    dump(tag_path, tag)


def make_fuel_item():
    dump(os.path.join(ASSETS, "models", "item", "deep_space_fuel.json"),
         {"parent": "minecraft:item/generated",
          "textures": {"layer0": "gtncore:item/deep_space_fuel"}})


def main():
    # ---- 1. 深空枢纽主机 ----
    make_blockstate("deep_space_hub", "gtncore:block/machine/deep_space_hub", "NON_Y_AXIS")
    make_machine_model("deep_space_hub", HUB_CASING,
                       "gtncore:block/multiblock/deep_space_hub")
    make_loot_and_tag("deep_space_hub", "gtncore:deep_space_hub")

    # ---- 2. 6 台扩展模块 ----
    for mid, cn, en, cn_bonus, en_bonus in MODULES:
        name = mid + "_module"
        make_blockstate(name, "gtncore:block/machine/" + name, "ALL")
        make_machine_model(name, MODULE_CASING,
                           "gtncore:block/multiblock/deep_space_module")
        make_loot_and_tag(name, "gtncore:" + name)

    # ---- 3. 深空燃料单元 ----
    make_fuel_item()

    # ---- 4. 语言条目 ----
    zh = OrderedDict()
    en = OrderedDict()

    zh["block.gtncore.deep_space_hub"] = "深空枢纽"
    en["block.gtncore.deep_space_hub"] = "Deep Space Hub"
    zh["item.gtncore.deep_space_hub"] = "深空枢纽"
    en["item.gtncore.deep_space_hub"] = "Deep Space Hub"
    zh["gtncore.machine.deep_space_hub.tooltip.1"] = \
        "深空探索体系的中枢核心：为周围 16 格内的扩展模块供能并聚合加成"
    en["gtncore.machine.deep_space_hub.tooltip.1"] = \
        "Central core of the deep space system: powers and aggregates modules within 16 blocks"
    zh["gtncore.machine.deep_space_hub.tooltip.2"] = \
        "运行需持续供给 1A 本机电压的电力，并每 80 tick 消耗 1 个深空燃料单元"
    en["gtncore.machine.deep_space_hub.tooltip.2"] = \
        "Runs on 1A of its own voltage and consumes 1 Deep Space Fuel Cell every 80 ticks"
    zh["gtncore.machine.deep_space_hub.tooltip.3"] = \
        "断供（缺电或缺燃料）时，全网络模块立即停摆"
    en["gtncore.machine.deep_space_hub.tooltip.3"] = \
        "Cutting power or fuel instantly shuts down every module on the network"
    zh["gtncore.machine.deep_space_hub.tooltip.4"] = \
        "结构 7×7×11：平滑石英壳体 + 深板岩砖地板 + 浅灰玻璃窗 + 青陶瓦核心"
    en["gtncore.machine.deep_space_hub.tooltip.4"] = \
        "Structure 7x7x11: smooth quartz shell, deepslate tile floor, gray glass windows, cyan terracotta core"

    zh["gtncore.machine.deep_space_module.tooltip"] = \
        "放在深空枢纽 16 格内自动挂载；同类模块可无限叠加，越多越强"
    en["gtncore.machine.deep_space_module.tooltip"] = \
        "Auto-attaches within 16 blocks of a Deep Space Hub; stack unlimited copies for stronger effects"

    for mid, cn, en_name, cn_bonus, en_bonus in MODULES:
        name = mid + "_module"
        zh[f"block.gtncore.{name}"] = cn
        en[f"block.gtncore.{name}"] = en_name
        zh[f"item.gtncore.{name}"] = cn
        en[f"item.gtncore.{name}"] = en_name
        zh[f"gtncore.deepspace.module.{mid}.tooltip"] = cn + "：扩展模块"
        en[f"gtncore.deepspace.module.{mid}.tooltip"] = en_name + ": extension module"
        zh[f"gtncore.deepspace.module.{mid}.bonus"] = cn_bonus % 4 if mid == "parallel" else cn_bonus
        en[f"gtncore.deepspace.module.{mid}.bonus"] = \
            (en_bonus % 4) if mid == "parallel" else en_bonus

    zh["item.gtncore.deep_space_fuel"] = "深空燃料单元"
    en["item.gtncore.deep_space_fuel"] = "Deep Space Fuel Cell"
    zh["gtncore.item.deep_space_fuel.tooltip"] = "深空枢纽的运营耗材，每 80 tick 消耗 1 个"
    en["gtncore.item.deep_space_fuel.tooltip"] = \
        "Operating fuel for the Deep Space Hub - 1 consumed every 80 ticks"

    # 运行期 GUI 文案
    zh["gtncore.deepspace.hub.online"] = "§a网络在线：全部模块正常运转"
    en["gtncore.deepspace.hub.online"] = "§aNetwork online: all modules running"
    zh["gtncore.deepspace.hub.offline"] = "§c网络离线：缺电或缺燃料，模块全部停摆"
    en["gtncore.deepspace.hub.offline"] = "§cNetwork offline: no power or fuel, modules halted"
    zh["gtncore.deepspace.hub.fuel"] = "已消耗深空燃料：%s"
    en["gtncore.deepspace.hub.fuel"] = "Deep Space Fuel consumed: %s"
    zh["gtncore.deepspace.hub.modules"] = "已挂载模块：%s 台"
    en["gtncore.deepspace.hub.modules"] = "Attached modules: %s"
    zh["gtncore.deepspace.hub.tier"] = "网络等级：%s"
    en["gtncore.deepspace.hub.tier"] = "Network tier: %s"
    zh["gtncore.deepspace.hub.bonus_header"] = "── 网络加成 ──"
    en["gtncore.deepspace.hub.bonus_header"] = "-- Network bonuses --"
    zh["gtncore.deepspace.module_line"] = "  %s × %s"
    en["gtncore.deepspace.module_line"] = "  %s x %s"
    zh["gtncore.deepspace.module.kind"] = "模块类型：%s"
    en["gtncore.deepspace.module.kind"] = "Module type: %s"
    zh["gtncore.deepspace.module.attached"] = "已挂载：网络等级 %s / 同类 %s 台 / 共 %s 台"
    en["gtncore.deepspace.module.attached"] = \
        "Attached: network tier %s / same kind %s / total %s"
    zh["gtncore.deepspace.module.detached"] = "§c未挂载：不在任何深空枢纽的 16 格范围内"
    en["gtncore.deepspace.module.detached"] = \
        "§cDetached: not within 16 blocks of any Deep Space Hub"
    zh["gtncore.deepspace.module.net_bonus"] = "网络加成：并行 +%s / 耗时 ×%s / 耗能 ×%s / 产出 ×%s"
    en["gtncore.deepspace.module.net_bonus"] = \
        "Network bonus: parallel +%s / duration x%s / EU x%s / output x%s"

    for lang, patch in (("zh_cn", zh), ("en_us", en)):
        path = os.path.join(ASSETS, "lang", f"{lang}.json")
        data = read_json(path)
        data.update(patch)
        dump(path, data)


if __name__ == "__main__":
    main()