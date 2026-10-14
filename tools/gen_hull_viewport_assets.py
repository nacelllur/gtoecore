# -*- coding: utf-8 -*-
"""全周天舱壁（hull_viewport）资源生成。

产出：
- textures/block/station/hull_viewport_frame.png  金属舱壁（侧面/顶底）
- textures/block/station/hull_viewport_glass.png  舷窗玻璃（朝外那一面）
- textures/block/station/hull_viewport_inner.png  舱内面板（BER 动态补绘用）
- blockstates/hull_viewport.json                  inner × open × signal = 24 个变体
- models/block/hull_viewport_inner_<dir>.json     6 个模型，各自省略「朝内」那一面
- models/item/hull_viewport.json                  物品栏模型
- lang/zh_cn.json / en_us.json                    方块名 + 说明文本

原理：模型永久省略朝内面 → 从舱内看不见它，朝外面背对相机被背面剔除，
于是舱内直接看穿、舱外仍是实心壳。朝内面由客户端 BER 按透明度进度动态补绘，
所以能做出「半透明时也看得见背景」的效果。
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "gtoecore")
TEX_OUT = os.path.join(ASSETS, "textures", "block", "station")
BS_OUT = os.path.join(ASSETS, "blockstates")
MODEL_OUT = os.path.join(ASSETS, "models", "block")
ITEM_OUT = os.path.join(ASSETS, "models", "item")
LANG_OUT = os.path.join(ASSETS, "lang")

DIRS = ["down", "up", "north", "south", "west", "east"]
OPPOSITE = {"down": "up", "up": "down", "north": "south",
            "south": "north", "west": "east", "east": "west"}
MOD_ID = "gtoecore"


def clamp(v):
    return max(0, min(255, int(v)))


# ---------------------------------------------------------------- 贴图

def tex_frame():
    """金属舱壁：横向拉丝 + 四角铆钉 + 暗边。"""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    rnd = random.Random(20261006)
    base = (86, 94, 108)
    for y in range(16):
        shift = rnd.randint(-10, 10)
        for x in range(16):
            n = rnd.randint(-4, 4)
            d = shift + n
            px[x, y] = (clamp(base[0] + d), clamp(base[1] + d), clamp(base[2] + d), 255)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(56, 62, 74, 255))
    for (cx, cy) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[cx, cy] = (146, 156, 172, 255)
        px[cx, min(15, cy + 1)] = (64, 70, 82, 255)
    return img


def tex_glass():
    """舷窗玻璃：2px 金属窗框 + 内嵌深色玻璃 + 斜向高光 + 星点。"""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    rnd = random.Random(771)
    frame = (92, 100, 116)
    # 窗框
    for y in range(16):
        for x in range(16):
            if 2 <= x <= 13 and 2 <= y <= 13:
                continue
            n = rnd.randint(-6, 6)
            px[x, y] = (clamp(frame[0] + n), clamp(frame[1] + n), clamp(frame[2] + n), 255)
    # 玻璃
    for y in range(2, 14):
        for x in range(2, 14):
            base = (22, 32, 50)
            shade = (x - y) * 3           # 斜向明暗
            v = clamp(base[2] + shade)
            px[x, y] = (clamp(base[0] + shade // 2), clamp(base[1] + shade // 2), v, 255)
    # 斜向高光带
    for y in range(2, 14):
        for x in range(2, 14):
            if (x + y) % 11 in (0, 1):
                r, g, b, a = px[x, y]
                px[x, y] = (clamp(r + 26), clamp(g + 34), clamp(b + 44), a)
    # 星点
    for (sx, sy) in ((5, 5), (10, 7), (7, 11)):
        px[sx, sy] = (222, 236, 255, 255)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(62, 68, 80, 255))
    d.rectangle([2, 2, 13, 13], outline=(40, 52, 74, 255))
    return img


def tex_inner():
    """舱内面板（BER 动态补绘用）—— 蜂窝连接纹理，由 gen_hull_hex_tex 生成。

    图案每 6 格重复一次，渲染器 HullViewportRenderer.PERIOD 必须与之相同。
    """
    import gen_hull_hex_tex
    return gen_hull_hex_tex.build()


# ---------------------------------------------------------------- 模型

def face_entry(tex, cull):
    return {"uv": [0, 0, 16, 16], "texture": tex, "cullface": cull}


def write_model(inner):
    """省略 inner 面；其对面（朝外）用玻璃贴图，其余用金属贴图。"""
    outer = OPPOSITE[inner]
    faces = {}
    for d in DIRS:
        if d == inner:
            continue
        faces[d] = face_entry("#glass" if d == outer else "#frame", d)
    model = {
        "parent": "block/block",
        "textures": {
            "frame": f"{MOD_ID}:block/station/hull_viewport_frame",
            "glass": f"{MOD_ID}:block/station/hull_viewport_glass",
            "particle": f"{MOD_ID}:block/station/hull_viewport_frame",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}
        ],
    }
    path = os.path.join(MODEL_OUT, f"hull_viewport_inner_{inner}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=2)
    return path


def write_blockstate():
    variants = {}
    for inner in DIRS:
        model = f"{MOD_ID}:block/hull_viewport_inner_{inner}"
        for op in ("false", "true"):
            for sg in ("false", "true"):
                variants[f"inner={inner},open={op},signal={sg}"] = {"model": model}
    path = os.path.join(BS_OUT, "hull_viewport.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"variants": variants}, f, ensure_ascii=False, indent=2)
    return path


def write_item_model():
    path = os.path.join(ITEM_OUT, "hull_viewport.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"parent": f"{MOD_ID}:block/hull_viewport_inner_down"}, f,
                  ensure_ascii=False, indent=2)
    return path


# ---------------------------------------------------------------- 语言

LANG_PATCH = {
    "zh_cn": {
        "block.gtoecore.hull_viewport": "全周天舱壁",
        "tooltip.gtoecore.hull_viewport.1": "单向舷窗：舱内一侧通透，舱外看似实心。",
        "tooltip.gtoecore.hull_viewport.2": "通红石信号后，整片相连舱壁由信号点向外波纹式变透明；断电反向收拢。",
        "tooltip.gtoecore.hull_viewport.3": "放置时自动判定朝内面；潜行 + 空手右键可翻转朝向。",
    },
    "en_us": {
        "block.gtoecore.hull_viewport": "Omni Hull Viewport",
        "tooltip.gtoecore.hull_viewport.1": "One-way viewport: see-through from inside, solid from outside.",
        "tooltip.gtoecore.hull_viewport.2": "A redstone signal opens the whole connected hull as an outward ripple; powering down reverses it.",
        "tooltip.gtoecore.hull_viewport.3": "Inner face is auto-detected on placement; sneak + empty-hand right-click to flip it.",
    },
}


def patch_lang():
    for lang, entries in LANG_PATCH.items():
        path = os.path.join(LANG_OUT, f"{lang}.json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.update(entries)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"lang {lang}: +{len(entries)} 条 (共 {len(data)})")


def main():
    for d in (TEX_OUT, BS_OUT, MODEL_OUT, ITEM_OUT):
        os.makedirs(d, exist_ok=True)

    tex_frame().save(os.path.join(TEX_OUT, "hull_viewport_frame.png"))
    tex_glass().save(os.path.join(TEX_OUT, "hull_viewport_glass.png"))
    tex_inner().save(os.path.join(TEX_OUT, "hull_viewport_inner.png"))
    print("textures: frame / glass / inner")

    for inner in DIRS:
        write_model(inner)
    print(f"models: {len(DIRS)} 个（省略 inner 面）")

    write_blockstate()
    print("blockstate: hull_viewport.json (24 变体)")
    write_item_model()
    print("item model: hull_viewport.json")

    # 皮肤变种（星空 / 警示 / 电路 / 装甲）：贴图由 gen_hull_panel_skins 单独生成，
    # 这里只保证 blockstates / 物品模型 / 语言条目齐备，避免重跑主生成器后资源缺失。
    import gen_hull_panel_skins
    print("皮肤变种资源:")
    gen_hull_panel_skins.ensure_variant_assets()

    patch_lang()


if __name__ == "__main__":
    main()
