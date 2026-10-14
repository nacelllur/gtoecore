# -*- coding: utf-8 -*-
"""全周天舱壁「自动外框」效果预览。

按 HullViewportRenderer 的公式，把一片 NX×NY 格的舱壁正面画出来：
- 面：蜂窝连接纹理，UV 按世界坐标取模（u=frac(worldU/6), v=frac(worldV/6)）；
- 框：只有「邻居不是同朝向舱壁」的那几条边才画，宽度 = 3/16 格，
  沿边贴图坐标 = frac(沿边世界坐标/6)（跨格连续，铆钉每 6 格一次），
  跨边取贴图靠外缘的 3 行（0 = 面板外缘）。

生成的图与游戏内 1 texel : 1 屏幕像素 对应（1 格 = 85.33 px）。
"""
import os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "gtoecore",
                   "textures", "block", "station")

PERIOD = 6
BORDER = 3.0 / 16.0
PB = 512.0 / PERIOD          # 每格 85.33 texel


def sample(img, u, v):
    """u,v ∈ [0,1) → 取像素（v=0 为贴图第一行）"""
    a = np.asarray(img)
    h, w = a.shape[:2]
    x = np.clip((u * w).astype(int), 0, w - 1)
    y = np.clip((v * h).astype(int), 0, h - 1)
    return a[y, x]


def build(nx=9, ny=6):
    heximg = Image.open(os.path.join(TEX, "hull_viewport_inner.png")).convert("RGB")
    frm = Image.open(os.path.join(TEX, "hull_viewport_frame.png")).convert("RGB")
    W = int(round(nx * PB))
    H = int(round(ny * PB))
    tx = (np.arange(W) + 0.5) / PB        # 世界 u（格）
    ty = (np.arange(H) + 0.5) / PB        # 世界 v（格）

    bx = (tx // 1).astype(int)
    by = (ty // 1).astype(int)
    lx = tx - bx
    ly = ty - by

    # ⚠ UV = frac(世界坐标 / PERIOD)：贴图覆盖 PERIOD×PERIOD 格。
    # 不能写成 (tx % 1.0)/PERIOD —— 那是「每格一个周期」，图案会逐格重复。
    U, V = np.meshgrid((tx % PERIOD) / PERIOD, (ty % PERIOD) / PERIOD)
    img = sample(heximg, U, V)

    LX, LY = np.meshgrid(lx, ly)
    BX, BY = np.meshgrid(bx, by)

    def put(mask, u, v):
        img[mask] = sample(frm, u[mask], v[mask])

    # 左右两条整条边（含角）
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
    out = build()
    p = os.path.join(HERE, "_preview", "frame_preview.png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    out.save(p)
    print("saved", p, out.size)
