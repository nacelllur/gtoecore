# -*- coding: utf-8 -*-
"""全周天舱壁「正面」蜂窝连接纹理生成。

参考图实测（1098x965 截图）：
- 尖顶正六边形蜂窝，晶格 v1=(232,0)、v2=(116,192.9)
- 六边形外接圆/内切圆 = 114.8/99.7 ≈ 1.151（正六边形理论值 1.1547）
- 层次（六边形度规，外缘 =1.0）：中心暗圆点 r≈0.2207（内切方向）
  → 奶油圈 [dot, 0.463] → 暗 [0.463, 0.733] → 奶油外圈 [0.733, 1.0]
- 通道宽度 ≈ 0.142 × 晶格水平间距

连接纹理原理：贴图自身无缝，BER 用「世界坐标取模」算 UV，
于是整片舱壁图案连续，且不依赖 GL_REPEAT（UV 恒在 0~1 内）。

数学限制：正六边形蜂窝晶格夹角 60°，无法嵌入正方形周期晶格。
取 v1=(Dh,0)、v2=(Dh/2,Dv)，正方形周期要求 Dv/Dh = c/b（b 偶数）。
b=2,c=2 → Dv/Dh=1.000，六边形偏瘦高 8.6%（肉眼可辨）
b=6,c=12 → Dv/Dh=0.857，仅偏 1%（= 参考图水平），代价是图案每 6 格重复一次。
本文件采用后者。
"""
import math
import os
import numpy as np
from PIL import Image

# 参考图比例
R_DOT = 0.2207       # 中心圆点半径 / 六边形内切圆半径
RHO_RING_IN = 0.463  # 内奶油圈外缘（六边形度规）
RHO_RING_OUT = 0.733 # 外奶油圈内缘（六边形度规）
CH_RATIO = 0.142     # 通道宽度 / 晶格水平间距

DARK = (50, 50, 52)
CREAM = (242, 238, 229)

# 正式参数：晶格下每 6 格重复一次（渲染器 PERIOD = 6）
COLS = 12            # 每周期 12 列 → 1 格 2 列
ROWS = 14            # 每周期 14 行（偶数）
BLOCKS = 6           # 周期 = 6 格
SIZE = 512           # 贴图边长（85.33 px/格）
SS = 4               # 超采样倍数


def render_band(y0, bh, cols, rows, size, ss):
    """渲染一条超采样横带并返回分类后的 RGB 数组（未降采样）。"""
    w = size * ss
    dh = size / float(cols)
    dv = size / float(rows)
    ch = CH_RATIO * dh

    ex = dh / 2.0 - ch / 2.0
    v2 = math.hypot(dh / 2.0, dv)
    rhs = v2 * v2 / 2.0 - ch / 2.0 * v2

    xs = (np.arange(w) + 0.5) / ss
    ys = (np.arange(y0 * ss, (y0 + bh) * ss) + 0.5) / ss
    px, py = np.meshgrid(xs, ys)

    best = np.full(px.shape, 1e9)
    bdx = np.zeros(px.shape)
    bdy = np.zeros(px.shape)

    j0 = np.round(py / dv)
    for dj in (-1, 0, 1):
        j = j0 + dj
        offset = dh / 2.0 * j
        i0 = np.round((px - offset) / dh)
        for di in (-1, 0, 1):
            cx = (i0 + di) * dh + offset
            cy = j * dv
            dx = px - cx
            dy = py - cy
            rho = np.maximum.reduce([
                np.abs(dx) / ex,
                np.abs(dh / 2.0 * dx + dv * dy) / rhs,
                np.abs(dh / 2.0 * dx - dv * dy) / rhs,
            ])
            upd = rho < best
            best = np.where(upd, rho, best)
            bdx = np.where(upd, dx, bdx)
            bdy = np.where(upd, dy, bdy)

    dist = np.hypot(bdx, bdy)
    buf = np.zeros(best.shape + (3,), dtype=np.uint8)
    buf[...] = DARK
    cream = ((best >= RHO_RING_OUT) & (best <= 1.0)) | \
            ((best <= RHO_RING_IN) & (dist > R_DOT * ex))
    buf[cream] = CREAM
    return buf


def build(size=SIZE, cols=COLS, rows=ROWS, ss=SS):
    out = Image.new("RGB", (size, size))
    step = 64
    for y0 in range(0, size, step):
        bh = min(step, size - y0)
        arr = render_band(y0, bh, cols, rows, size, ss)
        img = Image.fromarray(arr, "RGB").resize(
            (size, bh), Image.BOX)          # 整数倍降采样 = 盒式均值
        out.paste(img, (0, y0))
    return out.convert("RGBA")


def tile(img, nx, ny):
    t = Image.new("RGBA", (img.width * nx, img.height * ny))
    for i in range(nx):
        for j in range(ny):
            t.paste(img, (i * img.width, j * img.height))
    return t


def selfcheck(img, cols, rows, size):
    """数值自检：中心暗点 / 通道 / 内外奶油圈 / 无缝性。"""
    a = np.array(img.convert("L")).astype(float)
    dh = size / float(cols)
    ex = dh / 2.0 - CH_RATIO * dh / 2.0
    checks = [(0, 0, "中心暗点", 50),
              (int(round(dh / 2)), 0, "通道中点", 50),
              (0, int(round(ex * 0.86)), "外奶油圈", 242),
              (0, int(round(ex * 0.34)), "内奶油圈", 242),
              (0, int(round(ex * 0.60)), "两层间暗带", 50)]
    bad = 0
    for x, y, name, want in checks:
        got = a[y % size, x % size]
        ok = abs(got - want) < 40
        bad += 0 if ok else 1
        print("   %s%s = %.0f (期望~%d)" % ("OK  " if ok else "!!  ", name, got, want))
    # 无缝性：左右边缘、上下边缘相邻列差异
    d_lr = float(np.abs(a[:, 0] - a[:, -1]).mean())
    d_tb = float(np.abs(a[0, :] - a[-1, :]).mean())
    print("   接缝差 左右 %.1f / 上下 %.1f （越小越无缝）" % (d_lr, d_tb))
    print("   均值 %.0f  尺寸 %s" % (a.mean(), img.size))
    return bad


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    pv = os.path.join(here, "_preview")
    os.makedirs(pv, exist_ok=True)

    img = build()
    out = os.path.join(here, "..", "src", "main", "resources", "assets", "gtoecore",
                       "textures", "block", "station", "hull_viewport_inner.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    tile(img, 2, 2).save(os.path.join(pv, "hex_final_tile.png"))

    print("cols=%d rows=%d size=%d  Dv/Dh=%.3f  周期=%d 格  px/格=%.1f"
          % (COLS, ROWS, SIZE, COLS / float(ROWS), BLOCKS, SIZE / float(BLOCKS)))
    bad = selfcheck(img, COLS, ROWS, SIZE)
    print("saved:", out, "  异常项:", bad)


if __name__ == "__main__":
    main()
