# -*- coding: utf-8 -*-
"""全周天舱壁「皮肤变种」的游戏内渲染模拟（含自动外框）。

按 HullViewportRenderer 的公式把一片 NX×NY 格的舱壁正面画出来：
- 面：皮肤贴图，UV 按世界坐标取模（u = frac(worldU / PERIOD)）；
- 框：只在「邻居不是同朝向舱壁」的四条边画（面内部不出框），
  宽度 = 3/16 格，沿边贴图坐标 = frac(沿边世界坐标 / PERIOD)，跨边取贴图靠外缘那条。

预览与贴图实际分辨率无关（按每格 ppb 像素采样），方便一眼对比几种皮肤。
"""
import os

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "gtoecore",
                   "textures", "block", "station")

PERIOD = 6          # 默认 UV 周期（= PanelSkin.DEFAULT_PERIOD），也是「外框」的固定周期
BORDER = 3.0 / 16.0

# (贴图文件名, 标签, 帧数, UV 世界周期) —— 周期必须与 PanelSkin.period() 一致
SKINS = [
    ("hull_viewport_inner.png", "HEX", 1, 6),
    ("hull_viewport_inner_star.png", "STAR", 1, 6),
    ("hull_viewport_inner_hazard.png", "HAZARD", 1, 6),
    ("hull_viewport_inner_circuit.png", "CIRCUIT", 1, 6),
    ("hull_viewport_inner_armor.png", "ARMOR", 1, 6),
    ("hull_viewport_inner_solar.png", "SOLAR", 1, 6),
    ("hull_viewport_inner_grid.png", "GRID", 1, 6),
    ("hull_viewport_inner_vent.png", "VENT", 1, 6),
    ("hull_viewport_inner_rust.png", "RUST", 1, 6),
    ("hull_viewport_inner_tile.png", "TILE", 1, 6),
    ("hull_viewport_inner_tread.png", "TREAD", 1, 6),
    ("hull_viewport_inner_brushed.png", "BRUSHED", 1, 6),
    ("hull_viewport_inner_glyph.png", "GLYPH", 1, 6),
    ("hull_viewport_inner_camo.png", "CAMO", 1, 6),
    ("hull_viewport_inner_brick.png", "BRICK", 1, 6),
    # ── 科幻动态：贴图是「纵向堆叠 frames 帧」的图集 ──
    ("hull_viewport_inner_scan.png", "SCAN", 8, 6),
    ("hull_viewport_inner_datastream.png", "DATASTREAM", 8, 6),
    ("hull_viewport_inner_holo.png", "HOLO", 8, 6),
    ("hull_viewport_inner_pulse.png", "PULSE", 8, 6),
    ("hull_viewport_inner_plasma.png", "PLASMA", 8, 6),
    # ── 梗图：周期 **5 格**（per-skin）—— 一张图在墙上占 5×5 格 ──
    ("hull_viewport_inner_meme.png", "MEME", 16, 5),
    ("hull_viewport_inner_meme_bing.png", "MEME_BING", 15, 5),
    ("hull_viewport_inner_meme_eta.png", "MEME_ETA", 1, 5),
]


def _sample(arr, u, v):
    h, w = arr.shape[:2]
    x = np.clip((np.asarray(u) * w).astype(int), 0, w - 1)
    y = np.clip((np.asarray(v) * h).astype(int), 0, h - 1)
    return arr[y, x]


def build(path, period=PERIOD, nx=None, ny=None, ppb=64, frames=1, frame=0):
    # 统一展示「2×2 张图案」：不同皮肤的周期不同（6 或 5），按周期算格数才可比
    if nx is None:
        nx = period * 2
    if ny is None:
        ny = period * 2
    tex = np.asarray(Image.open(path).convert("RGB"))
    frm = np.asarray(Image.open(os.path.join(TEX, "hull_viewport_frame.png")).convert("RGB"))

    w = int(round(nx * ppb))
    h = int(round(ny * ppb))
    tx = (np.arange(w) + 0.5) / ppb          # 世界坐标（格）
    ty = (np.arange(h) + 0.5) / ppb

    bx = np.floor(tx).astype(int)
    by = np.floor(ty).astype(int)
    lx = tx - bx
    ly = ty - by

    # ── 面：UV = frac(世界坐标 / period) → 贴图覆盖 period×period 格，
    #        整片连成一张图案（连接纹理）。⚠ 不是「每格一个周期」！
    #        周期是 per-skin 的（默认 6，梗图 5），但外框固定按 PERIOD 走。
    U, V0 = np.meshgrid((tx % period) / period, (ty % period) / period)
    if frames > 1:
        # 动态皮肤：把 v 折叠进「选中帧」的区间，与 HullViewportRenderer 同式
        inset = 0.5 / tex.shape[0]
        V = frame / frames + inset + V0 * (1.0 / frames - 2 * inset)
    else:
        V = V0
    img = _sample(tex, U, V)

    LX, LY = np.meshgrid(lx, ly)
    BX, BY = np.meshgrid(bx, by)

    def put(mask, u, v):
        img[mask] = _sample(frm, np.asarray(u)[mask], np.asarray(v)[mask])

    # 左右两条整条边（含四角）
    m = (BX == 0) & (LX < BORDER)
    put(m, (BY / PERIOD + LY / PERIOD) % 1.0, LX)
    m = (BX == nx - 1) & (LX > 1.0 - BORDER)
    put(m, (BY / PERIOD + LY / PERIOD) % 1.0, 1.0 - LX)
    # 上下两条边（避开已被左右占掉的角部）
    m = (BY == 0) & (LY < BORDER) & (LX >= BORDER) & (LX <= 1.0 - BORDER)
    put(m, (BX / PERIOD + LX / PERIOD) % 1.0, LY)
    m = (BY == ny - 1) & (LY > 1.0 - BORDER) & (LX >= BORDER) & (LX <= 1.0 - BORDER)
    put(m, (BX / PERIOD + LX / PERIOD) % 1.0, 1.0 - LY)

    return Image.fromarray(img)


if __name__ == "__main__":
    ppb = 64
    TILE = 512          # 统一输出块尺寸：各皮肤周期不同（格数不同），缩放到同一大小才拼得齐
    tiles = []
    for name, label, frames, period in SKINS:
        p = os.path.join(TEX, name)
        if not os.path.exists(p):
            print("skip (missing)", name)
            continue
        # 动态皮肤取中间一帧（动效另有 anim_sheet.png 逐帧展示）
        im = build(p, period, ppb=ppb, frames=frames, frame=frames // 3)
        tiles.append((im.resize((TILE, TILE), Image.LANCZOS), label))

    cols = 3
    rows = (len(tiles) + cols - 1) // cols
    w = h = TILE
    sheet = Image.new("RGB", (w * cols + 8 * (cols - 1), h * rows + 8 * (rows - 1)), (16, 16, 18))
    d = ImageDraw.Draw(sheet)
    for i, (im, label) in enumerate(tiles):
        x = (i % cols) * (w + 8)
        y = (i // cols) * (h + 8)
        sheet.paste(im, (x, y))
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(96, 96, 104))
        d.rectangle([x, y, x + 116, y + 20], fill=(12, 12, 14))
        d.text((x + 6, y + 5), label, fill=(255, 226, 120))

    out = os.path.join(HERE, "_preview", "skins_render_preview.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print("saved", out, sheet.size)

    # 附一张「贴图本体」总览：一屏看全所有皮肤（缩略图，看配色与密度）
    ts, tcols = 240, 5
    trows = (len(tiles) + tcols - 1) // tcols
    tex_sheet = Image.new("RGB", (ts * tcols + 8 * (tcols - 1), ts * trows + 8 * (trows - 1)),
                          (16, 16, 18))
    td = ImageDraw.Draw(tex_sheet)
    for i, (name, label, frames, _period) in enumerate(SKINS):
        p = os.path.join(TEX, name)
        if not os.path.exists(p):
            continue
        x = (i % tcols) * (ts + 8)
        y = (i // tcols) * (ts + 8)
        im = Image.open(p).convert("RGB")
        if frames > 1:
            im = im.crop((0, 0, im.width, im.width))      # 帧图集只取第 0 帧
        tex_sheet.paste(im.resize((ts, ts), Image.LANCZOS), (x, y))
        td.rectangle([x - 1, y - 1, x + ts, y + ts], outline=(96, 96, 104))
        td.rectangle([x, y, x + 116, y + 20], fill=(12, 12, 14))
        td.text((x + 6, y + 5), label, fill=(255, 226, 120))
    out2 = os.path.join(HERE, "_preview", "skins_sheet.png")
    tex_sheet.save(out2)
    print("saved", out2, tex_sheet.size)
