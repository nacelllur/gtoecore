# -*- coding: utf-8 -*-
"""六面蜂窝舱壁（hull_panel_hex）的资源生成。

方块本体与舷窗版 hull_viewport 完全同类（`HullViewportBlock(sixSided=true)`），
差别只在客户端：舷窗版静态模型省略朝内面、BER 只补绘一个面；
六面版**静态模型为空**，六个面全由 BER 画 —— 所以这里只需要：

- blockstates/hull_panel_hex.json   inner × open × signal = 24 变体，全部指向同一个空模型
- models/block/hull_panel_hex.json  空模型（零 elements），只给 particle 贴图
- models/item/hull_panel_hex.json   物品图标：满立方体 + 单格蜂窝贴图（BER 在物品栏不跑）
- textures/block/station/hull_panel_hex_item.png
    单格可平铺蜂窝（2×2 个六边形 / 格）。⚠ 不能直接用 hull_viewport_inner.png：
    那张是「6 格一轮」的连接纹理（12 列 × 14 行），单格裁出来 2 列 × 2.33 行不整，
    贴在物品上会在四条边错位。这张用正方形周期（b=2,c=2），代价是六边形瘦高 8.6%。
- lang 键（方块名 + 两条说明）
"""
import json
import os
import sys

from PIL import Image

import gen_hull_hex_tex as hh

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "gtoecore")
TEX_OUT = os.path.join(ASSETS, "textures", "block", "station")
BS_OUT = os.path.join(ASSETS, "blockstates")
MODEL_OUT = os.path.join(ASSETS, "models", "block")
ITEM_OUT = os.path.join(ASSETS, "models", "item")
LANG_OUT = os.path.join(ASSETS, "lang")

BLOCK = "hull_panel_hex"
TEX_ITEM = "hull_panel_hex_item"
SIZE = 128          # 物品图标贴图边长
SS = 4              # 超采样

LANG_PATCH = {
    "zh_cn": {
        "block.gtoecore.hull_panel_hex": "全周天舱壁（六面蜂窝）",
        "tooltip.gtoecore.hull_panel_hex.1": "六面都是蜂窝晶格面板的实心墙块；整片墙的图案跨方块连续。",
        "tooltip.gtoecore.hull_panel_hex.2": "通红石信号后，整片相连舱壁由信号点向外波纹式变半透明；断电反向收拢。",
    },
    "en_us": {
        "block.gtoecore.hull_panel_hex": "Omni Hull Panel (Hex, All Sides)",
        "tooltip.gtoecore.hull_panel_hex.1": "Solid hull block: honeycomb lattice on all six faces, pattern continuous across blocks.",
        "tooltip.gtoecore.hull_panel_hex.2": "A redstone signal turns the whole connected hull semi-transparent as an outward ripple; powering down reverses it.",
    },
}


def build_item_tex():
    """单格可平铺蜂窝：正方形周期（cols=rows=2 ⇒ 每格 2×2 个六边形）。"""
    img = hh.build(size=SIZE, cols=2, rows=2, ss=SS)
    return img


def write_blockstate():
    """24 个变体（inner × open × signal）全部指向同一个空模型。

    ⚠ 三个属性都得列全：方块类沿用基类的 INNER/OPEN/SIGNAL，缺任一键
    游戏会在加载资源时报「missing variant」并给方块套上紫黑块。
    """
    variants = {}
    for inner in ("down", "up", "north", "south", "west", "east"):
        for open_v in ("false", "true"):
            for sig in ("false", "true"):
                key = "inner=%s,open=%s,signal=%s" % (inner, open_v, sig)
                variants[key] = {"model": "gtoecore:block/%s" % BLOCK}
    path = os.path.join(BS_OUT, BLOCK + ".json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"variants": variants}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("blockstate %s: %d 变体" % (BLOCK, len(variants)))


def write_block_model():
    """空模型：六个面全由 BER 画，静态模型一个四边形都不给。

    ⚠ 仍必须有 particle：破坏粒子 / 落在方块上的粒子颜色取自它。
    """
    path = os.path.join(MODEL_OUT, BLOCK + ".json")
    data = {
        "parent": "block/block",
        "textures": {
            "particle": "gtoecore:block/station/hull_viewport_frame",
        },
        "elements": [],
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("block model %s: 空模型（零 elements）" % BLOCK)


def write_item_model():
    """物品图标：满立方体，六面都贴单格蜂窝（物品栏里 BER 不跑，必须靠静态模型）。"""
    path = os.path.join(ITEM_OUT, BLOCK + ".json")
    data = {
        "parent": "minecraft:block/cube_all",
        "textures": {
            "all": "gtoecore:block/station/%s" % TEX_ITEM,
        },
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("item model %s: cube_all(%s)" % (BLOCK, TEX_ITEM))


def patch_lang():
    for lang, entries in LANG_PATCH.items():
        path = os.path.join(LANG_OUT, "%s.json" % lang)
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        data.update(entries)
        # 保持 key 顺序稳定：新增键追加到末尾即可（原文件已按块排序）
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("lang %s: +%d 条 (共 %d)" % (lang, len(entries), len(data)))


def main():
    for d in (TEX_OUT, BS_OUT, MODEL_OUT, ITEM_OUT):
        os.makedirs(d, exist_ok=True)

    img = build_item_tex()
    p = os.path.join(TEX_OUT, TEX_ITEM + ".png")
    img.save(p, optimize=True)
    print("贴图 %s.png %s  %d 字节" % (TEX_ITEM, img.size, os.path.getsize(p)))

    # 无缝自检：左右 / 上下边缘的差异要小
    a = img.convert("L")
    arr = __import__("numpy").asarray(a).astype(float)
    dlr = float(abs(arr[:, 0] - arr[:, -1]).mean())
    dtb = float(abs(arr[0, :] - arr[-1, :]).mean())
    print("   接缝差 左右 %.1f / 上下 %.1f（越小越无缝）" % (dlr, dtb))

    write_blockstate()
    write_block_model()
    write_item_model()
    patch_lang()
    print("done")


if __name__ == "__main__":
    sys.exit(main())
