# -*- coding: utf-8 -*-
"""合并 lumina_fusion_core 双语 lang 条目。"""
import json, os

AS = r'C:\Users\23737\WorkBuddy\2026-09-21-20-07-18\gtn-core\src\main\resources\assets\gtoecore\lang'

NEW_ZH = {
    "block.gtoecore.luv_lumina_fusion_core": "聚变光源核心 MK1",
    "block.gtoecore.zpm_lumina_fusion_core": "聚变光源核心 MK2",
    "block.gtoecore.uv_lumina_fusion_core": "聚变光源核心 MK3",
    "gtoecore.machine.lumina_fusion_core.tooltip.1": "与 GT 聚变反应堆结构/逻辑完全一致，专门产生聚变光源（光凝流的光子源）",
    "gtoecore.machine.lumina_fusion_core.tooltip.2": "需要 eu_to_start 预热；能量仓喂入 EU，流体仓注入聚变燃料，输出聚变光源流体",
}
NEW_EN = {
    "block.gtoecore.luv_lumina_fusion_core": "Lumina Fusion Core MK1",
    "block.gtoecore.zpm_lumina_fusion_core": "Lumina Fusion Core MK2",
    "block.gtoecore.uv_lumina_fusion_core": "Lumina Fusion Core MK3",
    "gtoecore.machine.lumina_fusion_core.tooltip.1": "Identical to GT Fusion Reactor in structure and logic, produces Fusion Light (photon source for Luminofluid)",
    "gtoecore.machine.lumina_fusion_core.tooltip.2": "Requires eu_to_start preheat; feed EU via energy hatches, fusion fuel via fluid hatches, output fusion light fluid",
}

def merge(path, new):
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    added = 0
    for k, v in new.items():
        if k not in d:
            d[k] = v
            added += 1
    with open(path, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
    print(os.path.basename(path), "added", added, "keys, total", len(d))

merge(os.path.join(AS, "zh_cn.json"), NEW_ZH)
merge(os.path.join(AS, "en_us.json"), NEW_EN)
