# -*- coding: utf-8 -*-
"""全周天舱壁「皮肤变种」贴图与资源生成。

批量一（4 种）：
- hull_viewport_inner_star.png      深空星图（星点 + 星云）
- hull_viewport_inner_hazard.png    警示斜纹（黄黑 45° hazard stripe）
- hull_viewport_inner_circuit.png   电路网格（暗底发光走线 + 过孔 + 焊盘）
- hull_viewport_inner_armor.png     装甲板（6×6 分块钢甲 + 铆钉 + 编号）

批量二（10 种）：
- hull_viewport_inner_solar.png     太阳能板（深蓝电池片 + 银栅线 + 汇流条）
- hull_viewport_inner_grid.png      工业格栅（细密网格 + 冲孔）
- hull_viewport_inner_vent.png      通风百叶（横向百叶条 + 立筋）
- hull_viewport_inner_rust.png      锈蚀钢板（2 格钢板 + 铆钉 + 锈斑 + 划痕）
- hull_viewport_inner_tile.png      舱内瓷砖（2 格瓷砖 + 勾缝 + 污渍）
- hull_viewport_inner_tread.png     防滑花纹板（每格 4×4 凸点 + 板缝）
- hull_viewport_inner_brushed.png   拉丝金属（横向拉丝 + 拼缝 + 斜向高光）
- hull_viewport_inner_glyph.png     外星铭文（暗石板 + 青色象形符号）
- hull_viewport_inner_camo.png      数码迷彩（16 px 单元的灰蓝迷彩）
- hull_viewport_inner_brick.png     耐火砖（2×1 格砖 + 错缝 + 灰浆缝）

以及每个变种的 blockstates / 物品模型 / 中英文语言条目。

关键约束 —— **必须可平铺（无缝）**：
渲染器 HullViewportRenderer 用「世界坐标对 PERIOD(=6) 格取模」算 UV，
所以贴图在整片舱壁上是被周期性重复的：贴图覆盖 6×6 世界格，
左边界必须与右边界接得上、上边界必须与下边界接得上。

三种保证无缝的手法：
1. 用**周期性距离**（wrap 距离）生成高斯斑/噪声 → 天然无缝（星云、磨损斑）；
2. 用**整周期数**的周期函数（hazard：P = SIZE/8，SIZE % P == 0）；
3. **3×3 平铺画布**：把背景平铺 3×3，在其上绘制（允许溢出到相邻格），
   最后裁中心那块 → 任何几何元素都不会被边界截断（星点、走线、板缝）。

顺带做接缝自检：比较「左右/上下边界相接处的像素差」与「图案内部平均相邻差」。
接缝处不明显偏大 ⇒ 可平铺。注意**不能只看接缝差是否等于 0**：
若两端恰好都落在纯色区域，差值天然为 0，那种「通过」是无效的。

SIZE = 768、PERIOD = 6 ⇒ 每世界格 128 px（整数），
装甲板的板缝因此能精确落在格边界上。
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "gtoecore")
TEX_OUT = os.path.join(ASSETS, "textures", "block", "station")
BS_OUT = os.path.join(ASSETS, "blockstates")
MODEL_OUT = os.path.join(ASSETS, "models", "block")
ITEM_OUT = os.path.join(ASSETS, "models", "item")
LANG_OUT = os.path.join(ASSETS, "lang")

MOD_ID = "gtoecore"
PERIOD = 6                 # 默认 UV 世界周期（格），与 PanelSkin.DEFAULT_PERIOD 一致
SIZE = 768                 # 768 / 6 = 128 px 每格（整数）
ANIM_FRAME_SIZE = 384      # 动态皮肤：每帧 384×384（= 6 格 × 64 px，整数）
ANIM_FRAMES = 8            # 动态皮肤帧数（须与 PanelSkin 的 frames 参数一致）

# 梗图皮肤：**周期 5 格**（per-skin，见 PanelSkin.MEME / MEME_BING），贴图 = 5 格 × N px。
# 每帧外面套一圈金属画框：贴图四边都是框，所以整片墙上每 5 格之间会出现一条
# 2×border 宽的分隔带 —— 图案本身**不必可平铺**，这一点和其它皮肤的前提不同。
# 画框细节（线宽 / 螺丝半径）按贴图尺寸等比缩放（见 _meme_frame 的 unit 参数），
# 所以不同尺寸的两张梗图上墙后画框的**物理宽度完全一致**（都是 5 格 × 5% = 0.25 格）。
MEME_REF_SIZE = 480        # 画框比例基准：所有框细节参数按这个尺寸定义
MEME_SRC = os.path.join(HERE, "meme_src", "ccc.gif")
MEME_SIZE = 480            # 5 格 × 96 px
MEME_FRAMES = 16           # 与 PanelSkin.MEME 的 frames 一致（原 GIF 33 帧重采样）
MEME_BORDER = 24           # 每边画框宽度（px）；24/480 = 5%
# grain（白噪声 σ）与螺丝半径也按 unit 缩放，见 _meme_metal / _meme_frame。

# 第二张梗图：冰冰冰.gif。源 320×320 —— 直接用**原生尺寸**当贴图（320 / 5 = 64 px 每格），
# 不放大（源本身是模糊的照片，放大只增加体积不增信息），画框按比例缩到 16 px。
# 源 146 帧 / 9.74 s，且**整段不是无缝循环**（首尾差 18.4 ≫ 典型相邻帧 7.2）——
# 直接整段用会在循环处「跳一下」。所以取一个**无缝子循环** [42,57)：
# 15 帧、正好 1.00 s、接缝差 4.18（低于典型相邻帧 7.2，肉眼接得上）。
MEME_BING_SRC = os.path.join(HERE, "meme_src", "bingbing.gif")
MEME_BING_SIZE = 320       # 原生尺寸（5 格 × 64 px）
MEME_BING_FRAMES = 15      # 与 PanelSkin.MEME_BING 的 frames 一致
MEME_BING_BORDER = 16      # 24 × 320/480，等比
MEME_BING_START = 42       # 无缝子循环起点（源帧号）
MEME_BING_COUNT = 15       # 子循环长度（帧）= 1.00 s

# 第三张梗图：η（等距水晶方块壁纸）。**静态**（源是单帧 JPEG，不是动图）。
# 源 704×571 满幅，但 η 只占 21% 宽、40% 高 —— 整张塞进画框里会显得很小。
# 所以裁一个 480×480 的方窗（x 112..592, y 91..571）：
#   · η 放大到 31% 宽 / 48% 高，明显更醒目；
#   · 底部原作者水印（暗像素 x 235..474, y 528..547）完整留在画面内 —— 署名不裁掉；
#   · 裁出来正好 480 = 5 × 96 px，与 MEME 同尺寸 ⇒ 三张梗图的画框物理宽度完全一致。
# 背景那套等距方块阵列是**不规则**的（全图搜不到晶格周期，左右接缝差 17.4
# vs 内部相邻 4.9），所以这张图**不能**当连接纹理平铺，只能走画框海报。
MEME_ETA_SRC = os.path.join(HERE, "meme_src", "eta.jpg")
MEME_ETA_SIZE = 480        # 5 格 × 96 px
MEME_ETA_FRAMES = 1        # 静态
MEME_ETA_BORDER = 24       # 24/480 = 5%，与 MEME 相同
MEME_ETA_CROP = (112, 91, 592, 571)   # (left, top, right, bottom) —— 480×480

DIRS = ["down", "up", "north", "south", "west", "east"]

TAU = math.tau


# ---------------------------------------------------------------- 公共工具

def _font(size):
    for path in (r"C:\Windows\Fonts\consola.ttf", r"C:\Windows\Fonts\arial.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            continue
    return ImageFont.load_default()


def _tiled(bg_img, size=SIZE, copies=3):
    """把 size×size 的背景平铺成 copies×copies 的大画布（供跨界绘制用）。"""
    big = Image.new("RGB", (size * copies, size * copies))
    for ox in range(copies):
        for oy in range(copies):
            big.paste(bg_img, (ox * size, oy * size))
    return big


def _offsets(size=SIZE):
    return [(dx, dy) for dx in (-size, 0, size) for dy in (-size, 0, size)]


def _wrap_delta(coord, center, size=SIZE):
    """周期性（wrap）距离场：|coord - center| 取环面最近距离。"""
    d = np.abs(coord - center)
    return np.minimum(d, size - d)


def _finish(big, rng, size=SIZE, grain=0.6):
    """裁中心块 + 轻微白噪声（白噪声在接缝处统计上与内部无异，不会形成可见缝）。"""
    out = big.crop((size, size, size * 2, size * 2))
    a = np.asarray(out).astype(np.float64)
    if grain > 0:
        a = a + rng.normal(0.0, grain, a.shape)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


# ---------------------------------------------------------------- 1) 深空星图

def build_star(size=SIZE, seed=20261006):
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    # 底色：极暗深空。用周期函数做缓变（sin/cos 在环面上连续 → 不产生直线接缝）
    t = xx / size * TAU
    u = yy / size * TAU
    slow = 0.5 + 0.5 * np.sin(2 * t + 0.7) * np.cos(3 * u + 1.3)
    img = np.stack([6.0 + 5.0 * slow, 8.0 + 7.0 * slow, 20.0 + 16.0 * slow], axis=-1)

    # 星云斑：wrap 距离的高斯 → 天然无缝
    for _ in range(6):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(80.0, 200.0))
        dx = _wrap_delta(xx, cx, size)
        dy = _wrap_delta(yy, cy, size)
        blob = np.exp(-(dx * dx + dy * dy) / (2.0 * r * r))
        tint = np.array([rng.uniform(12, 30), rng.uniform(10, 24), rng.uniform(30, 62)])
        img += blob[..., None] * tint

    big = _tiled(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), size)
    d = ImageDraw.Draw(big)

    # 星点位置：七成均匀散布，三成集中在一条斜向「银河带」上
    band_c = float(rng.uniform(0, size))
    stars = []
    for i in range(430):
        if i < 140:
            y = float(rng.uniform(0, size))
            w = (band_c + rng.normal(0.0, 55.0)) % size
            x = (w - 0.6 * y) % size
        else:
            x = float(rng.uniform(0, size))
            y = float(rng.uniform(0, size))
        stars.append((x, y, float(rng.random())))

    for (x, y, s) in stars:
        for (dx, dy) in _offsets(size):
            _draw_star(d, x + size + dx, y + size + dy, s)

    return _finish(big, rng, size)


def _draw_star(d, x, y, s):
    xi = int(round(x))
    yi = int(round(y))
    if s < 0.60:                       # 小星：2×2 暗蓝白
        d.rectangle([xi, yi, xi + 1, yi + 1], fill=(150, 168, 202))
    elif s < 0.88:                     # 中星：3×3 冷白
        d.rectangle([xi - 1, yi - 1, xi + 1, yi + 1], fill=(208, 222, 246))
    else:                              # 亮星：5×5 + 十字光芒
        d.rectangle([xi - 2, yi - 2, xi + 2, yi + 2], fill=(255, 255, 255))
        d.line([xi - 7, yi, xi + 7, yi], fill=(196, 218, 255))
        d.line([xi, yi - 7, xi, yi + 7], fill=(196, 218, 255))


# ---------------------------------------------------------------- 2) 警示斜纹

def build_hazard(size=SIZE, seed=20261007):
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    period = size / 8.0                # 96.0：SIZE % period == 0 → 可平铺
    half = period / 2.0
    ph = (xx + yy) % period
    in_yellow = ph < half
    e = np.where(in_yellow, ph, ph - half)     # 条带内位置 0..half

    # 黄条上缘受光、下缘压暗（沿 45° 法线的立体感）
    lift = np.clip((3.0 - e) / 3.0, 0.0, 1.0)
    dip = np.clip((e - (half - 3.0)) / 3.0, 0.0, 1.0)
    shade = (lift - dip) * 30.0

    yellow = np.array([212.0, 166.0, 40.0])
    dark = np.array([32.0, 32.0, 36.0])
    img = np.where(in_yellow[..., None], yellow + shade[..., None],
                   dark + (shade * 0.45)[..., None])

    # 磨损斑（wrap 高斯，乘性压暗）
    for _ in range(16):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(22.0, 74.0))
        dx = _wrap_delta(xx, cx, size)
        dy = _wrap_delta(yy, cy, size)
        blob = np.exp(-(dx * dx + dy * dy) / (2.0 * r * r))
        img *= (1.0 - 0.36 * blob)[..., None]

    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    return _finish(_tiled(out, size), rng, size, grain=0.6)


# ---------------------------------------------------------------- 3) 电路网格

def build_circuit(size=SIZE, seed=20261008):
    rng = np.random.default_rng(seed)
    grid = size // 24                  # 32 px 一格网

    img = np.full((size, size, 3), np.array([13.0, 27.0, 22.0]))
    img += rng.normal(0.0, 1.2, (size, size, 1))

    big = _tiled(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), size)
    d = ImageDraw.Draw(big)
    offs = _offsets(size)

    # 走线：端点全部落在网格点上 → 平铺后跨界自动接续
    paths = []
    for _ in range(44):
        x = int(rng.integers(0, 24)) * grid
        y = int(rng.integers(0, 24)) * grid
        segs = []
        for _ in range(int(rng.integers(2, 6))):
            if rng.random() < 0.5:
                nx = max(0, min(size, x + int(rng.choice([-3, -2, -1, 1, 2, 3])) * grid))
                segs.append(((x, y), (nx, y)))
                x = nx
            else:
                ny = max(0, min(size, y + int(rng.choice([-3, -2, -1, 1, 2, 3])) * grid))
                segs.append(((x, y), (x, ny)))
                y = ny
        paths.append(segs)

    # 先画宽而暗的「辉光底」，再叠窄而亮的线芯
    for segs in paths:
        for (a, b) in segs:
            for (dx, dy) in offs:
                d.line([a[0] + size + dx, a[1] + size + dy,
                        b[0] + size + dx, b[1] + size + dy], fill=(26, 78, 66), width=8)
    for segs in paths:
        for (a, b) in segs:
            for (dx, dy) in offs:
                d.line([a[0] + size + dx, a[1] + size + dy,
                        b[0] + size + dx, b[1] + size + dy], fill=(62, 198, 164), width=3)

    # 过孔：线段端点上的圆环
    for segs in paths:
        for (a, b) in segs:
            if rng.random() >= 0.20:
                continue
            for (dx, dy) in offs:
                px = a[0] + size + dx
                py = a[1] + size + dy
                d.ellipse([px - 9, py - 9, px + 9, py + 9], fill=(30, 86, 72))
                d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=(72, 206, 172))
                d.ellipse([px - 2, py - 2, px + 2, py + 2], fill=(13, 27, 22))

    # 少量金色贴片元件
    for _ in range(9):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        w = float(rng.uniform(18, 34))
        h = float(rng.uniform(12, 26))
        for (dx, dy) in offs:
            d.rectangle([cx + size + dx - w / 2, cy + size + dy - h / 2,
                         cx + size + dx + w / 2, cy + size + dy + h / 2],
                        fill=(198, 168, 84))

    return _finish(big, rng, size, grain=0.4)


# ---------------------------------------------------------------- 4) 装甲板 + 编号

def build_armor(size=SIZE, seed=20261009):
    rng = np.random.default_rng(seed)
    cell = size // PERIOD              # 128 px = 1 世界格 = 1 块板
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    # 板面：金属灰 + 每块板的轻微色调差 + 横向拉丝（轧制方向）
    tone = (rng.normal(0.0, 6.0, (PERIOD, 1, 1)) + rng.normal(0.0, 6.0, (1, PERIOD, 1)))
    ci = np.clip((xx // cell).astype(int), 0, PERIOD - 1)
    ri = np.clip((yy // cell).astype(int), 0, PERIOD - 1)
    brush = rng.normal(0.0, 2.4, (size, 1, 1))
    grain = rng.normal(0.0, 0.8, (size, size, 1))
    img = np.array([98.0, 104.0, 114.0]) + tone[ri, 0] + brush + grain

    big = _tiled(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), size)
    d = ImageDraw.Draw(big)
    off = size

    # 板缝：居中于格边界（±4 px）→ 平铺后相邻板之间正好 8 px 缝，天然无缝
    for k in range(PERIOD + 1):
        c = k * cell + off
        d.rectangle([c - 4, 0, c + 3, size * 3], fill=(40, 44, 52))
        d.rectangle([0, c - 4, size * 3, c + 3], fill=(40, 44, 52))

    font = _font(30)
    for r in range(PERIOD):
        for c in range(PERIOD):
            x0 = c * cell + off
            y0 = r * cell + off
            # 板内蚀刻边线
            d.rectangle([x0 + 14, y0 + 14, x0 + cell - 15, y0 + cell - 15],
                        outline=(76, 82, 94), width=2)
            # 四角铆钉
            for (px, py) in ((x0 + 26, y0 + 26), (x0 + cell - 27, y0 + 26),
                             (x0 + 26, y0 + cell - 27), (x0 + cell - 27, y0 + cell - 27)):
                d.ellipse([px - 5, py - 5, px + 5, py + 5], fill=(56, 61, 70))
                d.ellipse([px - 3, py - 3, px + 3, py + 3], fill=(150, 158, 172))
            # 编号铭牌：行 A~F、列 1~6（每块板唯一，一眼能看出分块）
            label = "%s%d" % (chr(ord('A') + r), c + 1)
            d.text((x0 + cell / 2.0, y0 + cell * 0.60), label, font=font,
                   fill=(178, 186, 200), anchor="mm",
                   stroke_width=2, stroke_fill=(48, 52, 60))

    return _finish(big, rng, size, grain=0.5)


# ---------------------------------------------------------------- 第二批（10 种）

def _torus_noise(shape, rng, passes=3):
    """环面平滑噪声（np.roll 做 wrap-safe 均值）—— 天然无缝，用于迷彩/磨损场。"""
    n = rng.normal(0.0, 1.0, shape)
    for _ in range(passes):
        n = (n + np.roll(n, 1, 0) + np.roll(n, -1, 0)
             + np.roll(n, 1, 1) + np.roll(n, -1, 1)) / 5.0
    return n


def _streak_noise(rows, rng, passes=4):
    """只沿 y 方向平滑的噪声 → 横向「拉丝」条纹（沿 x 常量 ⇒ 横向无缝）。"""
    n = rng.normal(0.0, 1.0, (rows, 1))
    for _ in range(passes):
        n = (n + np.roll(n, 1, 0) + np.roll(n, -1, 0)) / 3.0
    return n


def _canvas(img, size=SIZE):
    """uint8 底图 → 3×3 平铺画布 + 画笔。返回 (big, draw, n)。"""
    big = _tiled(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), size)
    return big, ImageDraw.Draw(big), size * 3


def build_solar(size=SIZE, seed=20261010):
    """太阳能板：深蓝电池片 + 银栅线 + 汇流条。6×6 片（每片 = 1 世界格）。"""
    rng = np.random.default_rng(seed)
    cell = size // PERIOD
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    # 电池片底色：深蓝紫 + 斜向受光（周期函数 → 环面连续）
    t = xx / size * TAU
    u = yy / size * TAU
    sheen = 0.5 + 0.5 * np.sin(2.0 * t + 2.0 * u + 0.9)
    tone = rng.normal(0.0, 5.0, (PERIOD, PERIOD, 1))
    ci = np.clip((xx // cell).astype(int), 0, PERIOD - 1)
    ri = np.clip((yy // cell).astype(int), 0, PERIOD - 1)
    img = (np.array([19.0, 26.0, 76.0]) + tone[ri, ci]
           + (30.0 * sheen)[..., None] * np.array([0.30, 0.42, 1.0]))

    big, d, n = _canvas(img, size)
    # 栅线：每 16 px 一条细银线（768 / 16 = 48，整除 ⇒ 横向无缝）
    for k in range(n // 16 + 1):
        y = k * 16
        d.line([0, y, n, y], fill=(128, 136, 152), width=2)
    # 汇流条：每 64 px 一条（768 / 64 = 12）。⚠ 别太宽太亮 —— 6 px 的亮条会把整张图
    # 读成「竖条」而不是「6×6 电池片」，2~3 px 才像汇流条。
    for k in range(n // 64 + 1):
        x = k * 64
        d.rectangle([x - 2, 0, x + 1, n], fill=(152, 160, 176))
    # 片间边框：每 128 px 一条 10 px 深缝（768 / 128 = 6），外侧再压一条亮边做出层次
    for k in range(n // cell + 1):
        c = k * cell
        d.rectangle([c - 5, 0, c + 4, n], fill=(56, 60, 72))
        d.rectangle([c - 6, 0, c - 5, n], fill=(118, 126, 142))
        d.rectangle([0, c - 5, n, c + 4], fill=(56, 60, 72))
        d.rectangle([0, c - 6, n, c - 5], fill=(118, 126, 142))
    return _finish(big, rng, size, grain=0.25)


def build_grid(size=SIZE, seed=20261011):
    """工业格栅：细密金属网格 + 冲孔（每格 2×2 孔，孔距 64 px）。"""
    rng = np.random.default_rng(seed)
    cell = size // PERIOD
    yy, xx = np.mgrid[0.0:size, 0.0:size]
    tone = rng.normal(0.0, 4.0, (PERIOD, PERIOD, 1))
    ci = np.clip((xx // cell).astype(int), 0, PERIOD - 1)
    ri = np.clip((yy // cell).astype(int), 0, PERIOD - 1)
    img = np.array([54.0, 57.0, 63.0]) + tone[ri, ci] + rng.normal(0.0, 1.4, (size, size, 1))

    big, d, n = _canvas(img, size)
    # 细密网格：每 16 px 交叉（1/8 格）
    for k in range(n // 16 + 1):
        p = k * 16
        d.line([0, p, n, p], fill=(98, 103, 112), width=3)
        d.line([p, 0, p, n], fill=(98, 103, 112), width=3)
    # 冲孔：孔距 64 px，孔心落在 32 + 64k（768 是 64 的整数倍 ⇒ 周期一致）
    rr = 22
    for py in range(32, n, 64):
        for px in range(32, n, 64):
            d.ellipse([px - rr, py - rr, px + rr, py + rr], fill=(19, 20, 24))
            d.arc([px - rr, py - rr, px + rr, py + rr], 180, 360,
                  fill=(126, 132, 144), width=3)
    return _finish(big, rng, size, grain=0.25)


def build_vent(size=SIZE, seed=20261012):
    """通风百叶：横向百叶条（24 px 一片，768/24 = 32）+ 立筋。"""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    # 百叶剖面：上缘高光 → 渐暗 → 深缝（周期 24 px，整除 ⇒ 纵向无缝）
    ph = (yy % 24.0) / 24.0
    prof = np.where(
        ph < 0.10, 216.0 - ph * 300.0,
        np.where(ph < 0.55, 190.0 - (ph - 0.10) / 0.45 * 128.0,
                 np.where(ph < 0.80, 62.0 - (ph - 0.55) / 0.25 * 40.0,
                          22.0 - (ph - 0.80) / 0.20 * 10.0)))
    tint = np.array([1.00, 1.02, 1.08])
    img = prof[..., None] * tint + rng.normal(0.0, 1.2, (size, 1, 1))

    big, d, n = _canvas(img, size)
    # 立筋：每 128 px 一根 14 px 宽的加强条
    for k in range(n // 128 + 1):
        c = k * 128
        d.rectangle([c - 7, 0, c + 6, n], fill=(70, 74, 82))
        d.rectangle([c - 7, 0, c - 5, n], fill=(126, 132, 142))
        d.rectangle([c + 4, 0, c + 6, n], fill=(34, 36, 42))
    return _finish(big, rng, size, grain=0.25)


def build_rust(size=SIZE, seed=20261013):
    """锈蚀钢板：2×2 格钢板 + 铆钉 + 斑驳锈迹 + 划痕。"""
    rng = np.random.default_rng(seed)
    plate = size // 3                      # 256 px = 2 世界格
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    img = (np.array([116.0, 120.0, 128.0])
           + _streak_noise(size, rng, 4)[:, None] * 3.2
           + rng.normal(0.0, 1.0, (size, size, 1)))

    # 锈迹：**多而小**的斑块（wrap 高斯）+ 每个斑块自带锈色，最后叠成色场。
    # ⚠ 不能用「少而大」的斑块 —— 那会变成一大片橙色云雾，不像锈。
    # 再加一个低频遮罩，让金属留出成片的干净区域，锈只在部分区域起。
    weight = np.zeros((size, size, 1))
    tint = np.zeros((size, size, 3))
    for _ in range(120):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(9.0, 58.0))
        g = np.exp(-(_wrap_delta(xx, cx, size) ** 2
                     + _wrap_delta(yy, cy, size) ** 2) / (2.0 * r * r))
        w = (g * float(rng.uniform(0.45, 1.0)))[..., None]
        col = np.array([rng.uniform(104, 172), rng.uniform(46, 98), rng.uniform(20, 54)])
        weight += w
        tint += w * col
    tint = tint / np.maximum(weight, 1e-6)
    mask = np.clip(0.45 + 1.05 * _torus_noise((size, size, 1), rng, 5), 0.0, 1.4)
    a = np.clip(weight * mask, 0.0, 0.94)
    img = img * (1.0 - a) + tint * a
    img -= (12.0 * a)                                   # 锈处整体压暗
    # 锈斑颗粒：只加在锈上，做出粗糙的蚀坑感
    img += rng.integers(-2, 3, (size, size, 1)).astype(float) * (a > 0.30)

    big, d, n = _canvas(img, size)
    # 钢板缝：每 256 px（居中 8 px 深缝）
    for k in range(n // plate + 1):
        c = k * plate
        d.rectangle([c - 4, 0, c + 3, n], fill=(48, 50, 56))
        d.rectangle([0, c - 4, n, c + 3], fill=(48, 50, 56))
    # 铆钉：每块板四角
    for cy in range(0, n + 1, plate):
        for cx in range(0, n + 1, plate):
            for (px, py) in ((cx + 22, cy + 22), (cx + plate - 23, cy + 22),
                             (cx + 22, cy + plate - 23), (cx + plate - 23, cy + plate - 23)):
                d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=(62, 64, 70))
                d.ellipse([px - 4, py - 4, px + 4, py + 4], fill=(158, 164, 176))
                d.ellipse([px - 4, py + 1, px + 1, py + 4], fill=(96, 100, 108))
    # 划痕：细亮线。⚠ 必须 3×3 都画（不能只按 y 偏移）——
    # 否则跨过贴图边界的划痕会「只剩尾巴」，平铺后尾巴在每个接缝处无头开始。
    # 数量少、亮度低：太多太亮会像「彩线」而不是划痕。
    for _ in range(12):
        y = float(rng.uniform(0, size))
        x0 = float(rng.uniform(0, size))
        ln = float(rng.uniform(30.0, 170.0))
        dy_end = float(rng.uniform(-5, 5))
        for (dx, dy) in _offsets(size):
            d.line([x0 + dx, y + dy, x0 + dx + ln, y + dy + dy_end],
                   fill=(150, 155, 165), width=1)
    return _finish(big, rng, size, grain=0.35)


def build_tile(size=SIZE, seed=20261014):
    """舱内瓷砖：2 格见方的乳白瓷砖（3×3）+ 勾缝 + 污渍。"""
    rng = np.random.default_rng(seed)
    tile = size // 3                       # 256 px = 2 世界格
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    tone = rng.normal(0.0, 5.0, (3, 3, 1))
    ti = np.clip((xx // tile).astype(int), 0, 2)
    tj = np.clip((yy // tile).astype(int), 0, 2)
    # 每块砖内的柔和明暗（左上亮 / 右下暗）
    fx = (xx % tile) / tile
    fy = (yy % tile) / tile
    img = (np.array([204.0, 201.0, 192.0]) + tone[tj, ti]
           + (10.0 * (1.0 - fx - fy))[..., None]
           + rng.normal(0.0, 1.0, (size, size, 1)))

    # 污渍：wrap 高斯（乘性）
    for _ in range(14):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(40.0, 150.0))
        blob = np.exp(-(_wrap_delta(xx, cx, size) ** 2
                        + _wrap_delta(yy, cy, size) ** 2) / (2.0 * r * r))
        img *= (1.0 - 0.09 * blob)[..., None]

    big, d, n = _canvas(img, size)
    # 勾缝：每 256 px 一条 12 px 缝（含 2 px 深芯）
    for k in range(n // tile + 1):
        c = k * tile
        d.rectangle([c - 6, 0, c + 5, n], fill=(150, 148, 142))
        d.rectangle([c - 2, 0, c + 1, n], fill=(112, 110, 106))
        d.rectangle([0, c - 6, n, c + 5], fill=(150, 148, 142))
        d.rectangle([0, c - 2, n, c + 1], fill=(112, 110, 106))
    return _finish(big, rng, size, grain=0.35)


def build_tread(size=SIZE, seed=20261015):
    """防滑花纹板：每格 4×4 凸点（带高光/阴影）+ 板缝。"""
    rng = np.random.default_rng(seed)
    cell = size // PERIOD
    yy, xx = np.mgrid[0.0:size, 0.0:size]
    tone = rng.normal(0.0, 5.0, (PERIOD, PERIOD, 1))
    ci = np.clip((xx // cell).astype(int), 0, PERIOD - 1)
    ri = np.clip((yy // cell).astype(int), 0, PERIOD - 1)
    img = (np.array([122.0, 126.0, 134.0]) + tone[ri, ci]
           + _streak_noise(size, rng, 4)[:, None] * 3.0)

    big, d, n = _canvas(img, size)
    # 板缝：每 128 px
    for k in range(n // cell + 1):
        c = k * cell
        d.rectangle([c - 2, 0, c + 1, n], fill=(70, 73, 80))
        d.rectangle([0, c - 2, n, c + 1], fill=(70, 73, 80))
    # 凸点：每格 4×4，点距 32 px（768/32 = 24）
    r = 9
    for py in range(16, n, 32):
        for px in range(16, n, 32):
            d.ellipse([px - r + 2, py - r + 3, px + r + 2, py + r + 3], fill=(84, 87, 94))   # 投影
            d.ellipse([px - r, py - r, px + r, py + r], fill=(150, 155, 165))                 # 本体
            d.ellipse([px - r + 2, py - r + 2, px + r - 3, py + r - 3], fill=(196, 203, 214)) # 受光面
    return _finish(big, rng, size, grain=0.3)


def build_brushed(size=SIZE, seed=20261016):
    """拉丝金属：横向拉丝 + 每 2 格拼缝 + 斜向高光带 + 螺钉。"""
    rng = np.random.default_rng(seed)
    plate = size // 3                      # 256 px = 2 世界格
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    t = xx / size * TAU
    u = yy / size * TAU
    sheen = 0.5 + 0.5 * np.sin(2.0 * u + 0.35 * np.sin(t) * 3.0)     # 斜向柔光带
    img = (np.array([146.0, 150.0, 158.0])
           + (24.0 * sheen)[..., None]
           + _streak_noise(size, rng, 5)[:, None] * 5.0
           + rng.normal(0.0, 0.8, (size, size, 1)))

    big, d, n = _canvas(img, size)
    # 细划痕（沿 x 常量 ⇒ 横向无缝）
    for _ in range(70):
        y = float(rng.uniform(0, size))
        d.line([0, y, n, y], fill=(178, 183, 192), width=1)
    # 拼缝：每 256 px（暗线 + 上缘高光）
    for k in range(n // plate + 1):
        c = k * plate
        d.rectangle([c - 2, 0, c + 1, n], fill=(96, 100, 108))
        d.rectangle([c - 2, 0, c - 1, n], fill=(196, 202, 212))
        d.rectangle([0, c - 2, n, c + 1], fill=(96, 100, 108))
        d.rectangle([0, c - 2, n, c - 1], fill=(196, 202, 212))
    # 螺钉：每块板中心
    for cy in range(plate // 2, n, plate):
        for cx in range(plate // 2, n, plate):
            d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=(104, 108, 116))
            d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=(176, 182, 192))
            d.line([cx - 4, cy, cx + 4, cy], fill=(112, 116, 124), width=2)
    return _finish(big, rng, size, grain=0.3)


def build_glyph(size=SIZE, seed=20261017):
    """外星铭文：暗石板底 + 青色象形符号（每格 3 个，笔画落在 12 px 栅格上）。"""
    rng = np.random.default_rng(seed)
    cell = size // PERIOD
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    t = xx / size * TAU
    u = yy / size * TAU
    slow = 0.5 + 0.5 * np.sin(3.0 * t + 0.4) * np.cos(2.0 * u + 1.1)
    img = np.array([36.0, 37.0, 44.0]) + (8.0 * slow)[..., None]
    for _ in range(9):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(70.0, 180.0))
        blob = np.exp(-(_wrap_delta(xx, cx, size) ** 2
                        + _wrap_delta(yy, cy, size) ** 2) / (2.0 * r * r))
        img *= (1.0 - 0.20 * blob)[..., None]

    big = _tiled(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), size)
    d = ImageDraw.Draw(big)
    off = size

    # 符号「字母表」：3×3 点阵上的 12 条边。每个符号随机取 3~6 条边
    # ⇒ 笔画短、折角硬，看起来是**刻意的文字**而不是随手涂鸦
    # （最初用随机折线，出来是一堆乱划，已废弃）。
    gs = 22
    edges = []
    for rr in range(3):
        for cc in range(3):
            if cc < 2:
                edges.append(((cc, rr), (cc + 1, rr)))
            if rr < 2:
                edges.append(((cc, rr), (cc, rr + 1)))

    def rune(bx, by):
        segs = []
        for i in rng.choice(len(edges), size=int(rng.integers(3, 7)), replace=False):
            (a, b) = edges[int(i)]
            segs.append(((bx + a[0] * gs, by + a[1] * gs),
                         (bx + b[0] * gs, by + b[1] * gs)))
        for (dx, dy) in _offsets(size):
            for (p0, p1) in segs:
                d.line([p0[0] + off + dx, p0[1] + off + dy,
                        p1[0] + off + dx, p1[1] + off + dy], fill=(22, 78, 78), width=9)
        for (dx, dy) in _offsets(size):
            for (p0, p1) in segs:
                d.line([p0[0] + off + dx, p0[1] + off + dy,
                        p1[0] + off + dx, p1[1] + off + dy], fill=(104, 240, 222), width=3)

    # 每格 2 个符号 + 1 个孤立点（当标点用），留出边距
    for cr in range(PERIOD):
        for cc in range(PERIOD):
            bx = cc * cell + 14
            by = cr * cell + 14
            rune(bx, by)
            rune(bx + 58, by + 52)
            px = bx + int(rng.integers(20, 92))
            py = by + int(rng.integers(20, 92))
            for (dx, dy) in _offsets(size):
                d.ellipse([px + off + dx - 6, py + off + dy - 6,
                           px + off + dx + 6, py + off + dy + 6], fill=(26, 88, 86))
                d.ellipse([px + off + dx - 3, py + off + dy - 3,
                           px + off + dx + 3, py + off + dy + 3], fill=(116, 246, 228))
    return _finish(big, rng, size, grain=0.25)


def build_camo(size=SIZE, seed=20261018):
    """数码迷彩：16 px 单元的灰蓝块状迷彩（环面平滑场 + 阈值分层）。"""
    rng = np.random.default_rng(seed)
    unit = 16
    u = size // unit                       # 48 × 48 单元
    field = _torus_noise((u, u, 1), rng, passes=4)[..., 0]
    field = (field - field.min()) / (field.max() - field.min() + 1e-9)

    palette = np.array([
        [46.0, 52.0, 58.0],
        [72.0, 80.0, 90.0],
        [104.0, 113.0, 124.0],
        [140.0, 149.0, 160.0],
    ])
    idx = np.clip((field * 4.0).astype(int), 0, 3)
    small = palette[idx]                                  # (u, u, 3)
    img = np.kron(small, np.ones((unit, unit, 1)))        # 放大到 768×768
    img += rng.normal(0.0, 1.6, (size, size, 1))

    big, d, n = _canvas(img, size)
    # 少量深色斑块，打散规则的数码格
    for _ in range(24):
        bx = int(rng.integers(0, size)) // unit * unit
        by = int(rng.integers(0, size)) // unit * unit
        w = int(rng.integers(2, 6)) * unit
        h = int(rng.integers(1, 4)) * unit
        for (dx, dy) in _offsets(size):
            d.rectangle([bx + dx + size, by + dy + size,
                         bx + dx + size + w - 1, by + dy + size + h - 1],
                        fill=(34, 39, 45))
    return _finish(big, rng, size, grain=0.3)


def build_brick(size=SIZE, seed=20261019):
    """耐火砖：2 格 × 1 格砖 + 错缝砌法 + 灰浆缝。"""
    rng = np.random.default_rng(seed)
    bw, bh, m = size // 3, size // 6, 10    # 256 × 128，缝 10 px
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    # 砖面：基底 + 每块砖的色调差（行列各取模，保证周期）+ 斑驳
    tone = rng.normal(0.0, 9.0, (2, 3, 1))
    mottle = _torus_noise((size, size, 1), rng, passes=3) * 7.0
    img = np.array([124.0, 60.0, 44.0]) + mottle + rng.normal(0.0, 1.2, (size, size, 1))
    # 砖面按行列上色（row 0/1、col 0..2 循环）
    rj = ((yy // bh).astype(int)) % 2
    tj = (((xx + (yy // bh) * (bw // 2)) % size) // bw).astype(int) % 3
    img = img + tone[rj, tj]

    big, d, n = _canvas(img, size)
    # 灰浆缝
    for k in range(n // bh + 1):
        d.rectangle([0, k * bh - 4, n, k * bh + 5], fill=(96, 92, 86))
    for r in range(n // bh + 1):
        y0 = r * bh
        ox = (r % 2) * (bw // 2)
        for c in range(-1, n // bw + 2):
            x0 = c * bw + ox
            d.rectangle([x0 - 4, y0 - 4, x0 + 5, y0 + bh + 4], fill=(96, 92, 86))
    return _finish(big, rng, size, grain=0.4)


# ---------------------------------------------------------------- 第三批（5 种 · 科幻动态）
#
# 动态皮肤 = 「纵向堆叠 frames 帧」的帧图集：宽 ANIM_FRAME_SIZE，高 ANIM_FRAME_SIZE×frames，
# 第 k 帧占 y ∈ [k·H, (k+1)·H)。渲染器 HullViewportRenderer 按游戏时间选帧循环播放，
# 把连接纹理的 v 坐标折叠进选中帧的区间 —— 所以**每一帧自身都必须可平铺**。
#
# 帧内无缝只有两条安全路线：
#   ① **纯径向图案**（只依赖「到贴图中心的距离」）：贴图关于中心有 4 重镜像对称，
#      左右/上下边界天然接得上（雷达、脉冲）。
#   ② **周期整除**（正弦叠加用整数频率、网格周期整除 size、或 np.roll 平移）：
#      左右边界是同相位采样（数据流、全息、等离子）。
# ⚠ 唯一危险的是「角度依赖」的图案（扫描扇区、辐射线）：atan2 在负 x 轴处有跳变，
#   一旦延伸到贴图边界就会裂。必须把这类元素限制在 r < size/2 × 0.92 之内。


def _stack_frames(imgs):
    """把若干等高帧纵向堆叠成帧图集（宽 × 高×帧数）。"""
    w, h = imgs[0].width, imgs[0].height
    out = Image.new("RGB", (w, h * len(imgs)))
    for i, im in enumerate(imgs):
        out.paste(im.convert("RGB"), (0, i * h))
    return out


def _radial(size):
    """以贴图中心为原点的极坐标场：(r, ang, xx, yy)。"""
    c = size / 2.0
    yy, xx = np.mgrid[0.0:size, 0.0:size]
    dx = xx - c + 0.5
    dy = yy - c + 0.5
    return np.hypot(dx, dy), np.arctan2(dy, dx), xx, yy


def _glow(dist, sigma):
    """一维距离场 → 高斯亮线。"""
    return np.exp(-(dist ** 2) / (2.0 * sigma * sigma))


def _norm(a):
    """线性拉伸到 0~1（不依赖 numpy 2.0 移除的 ndarray.ptp）。"""
    lo = float(a.min())
    hi = float(a.max())
    return (a - lo) / max(1e-6, hi - lo)


def build_scan(size=ANIM_FRAME_SIZE, frames=ANIM_FRAMES, seed=20261020):
    """雷达扫描：暗绿屏 + 同心圆环 + 十字准星 + 顺时针旋转的扫描扇区。

    纯径向（圆环/准星/中心点）+ 受限扇区 ⇒ 完全无缝。
    """
    r, ang, xx, yy = _radial(size)
    c = size / 2.0
    R = size / 2.0
    cell = size // PERIOD                                    # 64 px = 1 世界格
    ring = _glow(np.minimum(r % cell, cell - r % cell), 1.6)
    cross = np.clip(_glow(np.abs(yy - c + 0.5), 1.3)
                    + _glow(np.abs(xx - c + 0.5), 1.3), 0.0, 1.0)
    vig = np.clip(1.0 - (r / (R * 1.45)) ** 2, 0.10, 1.0)     # 四角渐暗（径向 ⇒ 无缝）

    base = np.zeros((size, size, 3), dtype=np.float64)
    base[..., 0] = 4.0 + 8.0 * vig
    base[..., 1] = 14.0 + 22.0 * vig
    base[..., 2] = 8.0 + 13.0 * vig
    base += ring[..., None] * np.array([10.0, 46.0, 24.0]) * vig[..., None]
    base += cross[..., None] * np.array([8.0, 40.0, 22.0]) * vig[..., None]

    sweep = math.radians(52.0)
    lim = R * 0.90                                            # 扇区绝不碰边界
    imgs = []
    for i in range(frames):
        scan = -math.pi / 2.0 + TAU * i / frames              # 从正上方起顺时针
        dd = np.abs(np.angle(np.exp(1j * (ang - scan))))      # 归一化角差 [0, π]
        sector = np.where(r < lim, np.clip(1.0 - dd / sweep, 0.0, 1.0) ** 1.8, 0.0) * vig
        edge = np.where(r < lim, _glow(np.abs(dd), 0.035), 0.0) * vig
        col = base + sector[..., None] * np.array([46.0, 186.0, 92.0])
        col += edge[..., None] * np.array([60.0, 120.0, 70.0])
        col += _glow(r, 5.5)[..., None] * np.array([110.0, 255.0, 150.0])
        imgs.append(Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)))
    return _stack_frames(imgs)


def build_datastream(size=ANIM_FRAME_SIZE, frames=ANIM_FRAMES, seed=20261021):
    """数据流：黑底 + 垂落的细数据光柱（头亮尾淡、带字符断点），整体向下流动一圈。

    光柱按 y 取模绘制 ⇒ 画布上下无缝；列宽整除 size ⇒ 左右无缝；
    每帧只是 np.roll 平移，天然保持无缝。
    ⚠ 柱体必须**细**、且每 14 px 一个明暗断点，否则会退化成「绿色柱状图」。
    """
    rng = np.random.default_rng(seed)
    img = Image.new("RGB", (size, size), (2, 5, 4))
    d = ImageDraw.Draw(img)
    col_w = size // 16                                        # 24 px 一列，整除 384
    for col in range(size // col_w):
        bw = max(3, col_w - 10)
        ox = col * col_w + (col_w - bw) // 2
        y = -int(rng.integers(0, 520))
        while y < size + 520:
            seg = int(rng.integers(70, 270))
            for k in range(seg):
                yy = (y + k) % size                           # ← wrap：跨边界自动接回顶部
                t = k / max(1, seg - 1)
                v = (1.0 - t) ** 2.0
                if k < 2:
                    c = (150, 255, 185)                       # 头
                else:
                    band = 0.5 + 0.5 * (((y + k) // 14) % 2)  # 字符块感
                    c = (int(6 + 24 * v * band), int(34 + 150 * v * band), int(16 + 62 * v * band))
                d.rectangle([ox, yy, ox + bw, yy], fill=c)
            y += seg + int(rng.integers(24, 170))

    arr = np.asarray(img)
    step = size // frames
    imgs = [Image.fromarray(np.roll(arr, i * step, axis=0)) for i in range(frames)]
    return _stack_frames(imgs)


def build_holo(size=ANIM_FRAME_SIZE, frames=ANIM_FRAMES, seed=20261022):
    """全息投影：青色全网格 + 上下往复的扫描亮带。

    网格周期整除 size、亮带用环绕距离 ⇒ 无缝。
    """
    yy, xx = np.mgrid[0.0:size, 0.0:size]
    cell = size // PERIOD                                     # 1 世界格
    fine = cell // 4
    gx = _glow(np.minimum(xx % cell, cell - xx % cell), 1.5)
    gy = _glow(np.minimum(yy % cell, cell - yy % cell), 1.5)
    fx = _glow(np.minimum(xx % fine, fine - xx % fine), 0.9)
    fy = _glow(np.minimum(yy % fine, fine - yy % fine), 0.9)
    node = gx * gy

    base = np.zeros((size, size, 3), dtype=np.float64)
    base[..., 0] = 3.0
    base[..., 1] = 12.0
    base[..., 2] = 19.0
    base += (fx + fy)[..., None] * np.array([3.0, 16.0, 22.0])
    base += (gx + gy)[..., None] * np.array([7.0, 44.0, 60.0])
    base += node[..., None] * np.array([14.0, 70.0, 92.0])

    imgs = []
    for i in range(frames):
        t = i / frames
        pos = t * 2.0 if t < 0.5 else (1.0 - t) * 2.0         # 三角波：上→下→上
        band = _wrap_delta(yy, pos * size, size)
        head = np.exp(-(band ** 2) / (2.0 * 26.0 ** 2))
        tail = np.where((band > 0.0) & (band < 150.0), np.exp(-band / 60.0) * 0.45, 0.0)
        col = base + (head + tail)[..., None] * np.array([34.0, 150.0, 190.0])
        col += (head * (fx + fy))[..., None] * np.array([20.0, 90.0, 110.0])
        imgs.append(Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)))
    return _stack_frames(imgs)


def build_pulse(size=ANIM_FRAME_SIZE, frames=ANIM_FRAMES, seed=20261023):
    """能量脉冲：中心环阵 + 向外扩散的两重脉冲波。

    纯径向 ⇒ 无缝；辐射短刻线限制在半径内（避开 atan2 在负 x 轴的跳变）。
    """
    r, ang, xx, yy = _radial(size)
    R = size / 2.0
    cell = size // (PERIOD * 2)
    ring = _glow(np.minimum(r % cell, cell - r % cell), 1.1)
    spokes = np.clip(_glow(np.abs(np.angle(np.exp(1j * (ang * 8.0)))), 0.16), 0.0, 1.0)
    spokes = np.where((r > R * 0.25) & (r < R * 0.92), spokes, 0.0)
    vig = np.clip(1.0 - (r / (R * 1.45)) ** 2, 0.10, 1.0)

    base = np.zeros((size, size, 3), dtype=np.float64)
    base[..., 0] = 5.0 + 6.0 * vig
    base[..., 1] = 9.0 + 12.0 * vig
    base[..., 2] = 22.0 + 30.0 * vig
    base += (ring * vig)[..., None] * np.array([10.0, 22.0, 56.0])
    base += spokes[..., None] * np.array([8.0, 16.0, 40.0])

    maxr = R * 1.40
    imgs = []
    for i in range(frames):
        col = base.copy()
        for k in (0.0, 0.5):                                  # 两圈相位错开的脉冲
            ph = (i / frames + k) % 1.0
            pr = ph * maxr
            w = 9.0 + 26.0 * ph                               # 越远越宽（能量扩散）
            wave = np.exp(-((r - pr) ** 2) / (2.0 * w * w)) * (1.0 - ph) ** 1.4
            col += wave[..., None] * np.array([72.0, 150.0, 255.0])
        col += _glow(r, 6.0)[..., None] * np.array([130.0, 190.0, 255.0])
        imgs.append(Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)))
    return _stack_frames(imgs)


def build_plasma(size=ANIM_FRAME_SIZE, frames=ANIM_FRAMES, seed=20261024):
    """等离子：紫蓝湍流（横向流动）+ 菱形力场网格。

    湍流用**整数频率**正弦叠加（周期恰为 size）⇒ 无缝；帧间只是 np.roll 平移。
    菱形网格用 (x±y) 投影，周期整除 size ⇒ 无缝。
    """
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0.0:size, 0.0:size]

    field = np.zeros((size, size))
    for (fx, fy, amp) in [(1, 2, 1.0), (2, -1, 0.8), (3, 2, 0.55), (4, 3, 0.40),
                          (5, -4, 0.30), (7, 5, 0.22), (9, -6, 0.15)]:
        field += amp * np.sin(TAU * (fx * xx + fy * yy) / size + rng.uniform(0, TAU))
    field = _norm(field) ** 2.2                               # 拉开：亮丝更细、暗区更大

    swirl = np.zeros((size, size))
    for (fx, fy, amp) in [(2, 3, 0.9), (3, -4, 0.6), (6, 5, 0.35)]:
        swirl += amp * np.sin(TAU * (fx * xx - fy * yy) / size + rng.uniform(0, TAU))
    swirl = _norm(swirl)

    P = size // PERIOD
    d1 = np.minimum((xx + yy) % P, P - (xx + yy) % P)
    d2 = np.minimum((xx - yy) % P, P - (xx - yy) % P)
    lattice = np.clip(_glow(d1, 1.3) + _glow(d2, 1.3), 0.0, 1.0)

    step = size // frames
    imgs = []
    for i in range(frames):
        f = np.roll(field, i * step, axis=1)                  # 沿 x 正向流动
        s = np.roll(swirl, -i * step, axis=0)                 # 沿 y 反向流动
        col = np.zeros((size, size, 3), dtype=np.float64)
        col[..., 0] = 6.0 + 56.0 * f + 24.0 * s
        col[..., 1] = 3.0 + 18.0 * f + 32.0 * s
        col[..., 2] = 14.0 + 88.0 * f + 58.0 * s
        hot = np.clip((f * s - 0.62) * 3.0, 0.0, 1.0)         # 高能处偏白（只留少数）
        col += hot[..., None] * np.array([56.0, 66.0, 88.0])
        col += lattice[..., None] * np.array([11.0, 18.0, 44.0])   # 力场网格：压暗、偏蓝
        imgs.append(Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)))
    return _stack_frames(imgs)


# ---------------------------------------------------------------- 6) 梗图（外部 gif）

def _meme_metal(size=MEME_SIZE, seed=20261025, grain=2.2):
    """画框用的深灰拉丝金属底：整数频率的周期条纹 + 白噪声（自身可平铺）。"""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0.0:size, 0.0:size]
    t = xx / size * TAU
    u = yy / size * TAU
    # sin/cos 都取整数倍频率 ⇒ 左右边界同相位；白噪声在接缝处统计上与内部无异
    brush = 0.5 + 0.5 * np.sin(9.0 * t + 1.1) * np.cos(4.0 * u + 0.3)
    base = 30.0 + 18.0 * brush
    img = np.stack([base, base * 1.02, base * 1.12], axis=-1)
    img += rng.normal(0.0, grain, img.shape)
    return img


def _screw(d, cx, cy, r):
    """一颗十字槽螺丝。"""
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(52, 55, 64))
    d.ellipse([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], fill=(92, 96, 108))
    d.line([cx - r + 2, cy, cx + r - 2, cy], fill=(34, 36, 44), width=1)
    d.line([cx, cy - r + 2, cx, cy + r - 2], fill=(34, 36, 44), width=1)


def _meme_frame(d, size, border, inner, unit=1.0):
    """画框细节：外沿高光 / 框上亮线 / 内沿凹槽 / 四角螺丝。

    @param unit 相对 MEME_REF_SIZE（480）的比例，用来等比缩放线宽与螺丝尺寸 ——
                这样 480 贴图与 320 贴图上的画框**物理宽度一致**，混搭不违和。
                480 时 unit=1.0，行为与旧版逐像素相同。
    """
    def u(v):
        return max(1, int(round(v * unit)))

    # 贴图最外沿高光 —— 拼接后成为 2×u(1) px 的网格亮线
    d.rectangle([0, 0, size - 1, size - 1], outline=(84, 88, 100), width=u(1))
    # 框上、内区外侧一圈亮线（金属反光）
    d.rectangle([border - u(3), border - u(3), border + inner + u(2), border + inner + u(2)],
                outline=(104, 108, 120), width=u(1))
    # 内区外沿暗槽（把照片「压」进框里的凹陷感）
    d.rectangle([border - u(1), border - u(1), border + inner, border + inner],
                outline=(12, 12, 16), width=u(2))
    # 四角螺丝：贴在框带中心
    off = border // 2
    r = max(2, int(round(5 * unit)))
    for sx, sy in ((off, off), (size - off, off),
                   (off, size - off), (size - off, size - off)):
        _screw(d, sx, sy, r)


def _meme_pick(n, frames, start=None, count=None):
    """挑出要用的源帧号。

    - {@code start is None}：全段均匀重采样到 {@code frames} 帧，取最近帧不做平均
      （保留锐利的关键姿态）。
    - 给了 {@code start}：取连续区间 {@code [start, start+count)}，均匀取 {@code frames} 帧。
    """
    if start is None:
        pick = [min(int(round(i * n / frames)), n - 1) for i in range(frames)]
        return pick, "全 %d 帧 → 重采样 %d 帧" % (n, frames)
    pick = [min(start + (i * count) // frames, n - 1) for i in range(frames)]
    return pick, "子循环 [%d,%d) 共 %d 帧 → 取 %d 帧（%.2f s）" \
                 % (start, start + count, count, frames, count * 0.0667)


def _build_meme_tile(src_path, size, border, frames, seed,
                     start=None, count=None, label="meme", crop=None):
    """把源图（gif 或静态图）的若干帧各套一圈等比金属画框，纵向堆叠成帧图集。

    @param crop 可选的 {@code (left, top, right, bottom)} 裁切窗，在缩放之前施加 ——
                用来把构图裁近一点（源图主体太小）或切掉无关边缘。
    @param frames 帧数；{@code 1} = 静态（源是单帧图，或只取一帧），贴图就是正方形。

    ⚠ 与其它皮肤不同，这里**不要求贴图自身可平铺**：贴图四边都是画框，
    世界坐标取模到 5 格后，相邻两张之间自然形成一条金属分隔带，
    「图案在边界处断开」正是设计要的效果。
    """
    inner = size - border * 2
    unit = size / float(MEME_REF_SIZE)
    bg = np.clip(_meme_metal(size, seed, grain=2.2 * unit), 0, 255).astype(np.uint8)

    if not os.path.exists(src_path):
        print("  ⚠ 找不到 %s —— %s 皮肤退化为纯金属框占位" % (src_path, label))
        empty = Image.fromarray(bg.copy())
        _meme_frame(ImageDraw.Draw(empty), size, border, inner, unit)
        return _stack_frames([empty] * frames)

    src_img = Image.open(src_path)
    # 静态图（jpeg/png）没有 n_frames，按 1 帧处理
    n = getattr(src_img, "n_frames", 1)
    src = []
    for i in range(n):
        src_img.seek(i)
        fr = src_img.convert("RGB")
        if crop is not None:
            fr = fr.crop(crop)
        src.append(fr)
    pick, desc = _meme_pick(n, frames, start, count)
    print("  %s 源 %d×%d%s，%d 帧；%s（索引 %s）"
          % (label, src_img.width, src_img.height,
             "" if crop is None else "（裁 %s → %d×%d）"
             % (crop, src[0].width, src[0].height),
             n, desc, pick))

    outs = []
    for idx in pick:
        photo = src[idx].resize((inner, inner), Image.LANCZOS)
        tile = Image.fromarray(bg.copy())
        tile.paste(photo, (border, border))
        _meme_frame(ImageDraw.Draw(tile), size, border, inner, unit)
        outs.append(tile)
    return _stack_frames(outs)


def build_meme(size=MEME_SIZE, frames=MEME_FRAMES, seed=20261025):
    """表情包（ccc.gif）：整段 33 帧重采样 16 帧，每帧套金属画框。周期 5 格。"""
    return _build_meme_tile(MEME_SRC, size, MEME_BORDER, frames, seed, label="meme")


def build_meme_bing(size=MEME_BING_SIZE, frames=MEME_BING_FRAMES, seed=20261026):
    """表情包·冰冰冰（bingbing.gif）：取无缝子循环 [42,57) 的 15 帧（1.00 s）。

    源整段 9.74 s 不是无缝循环，直接整段用会在循环处跳一下；[42,57) 这段
    首尾差异 4.18 < 典型相邻帧 7.2，接得上。周期同 MEME，也是 5 格。
    """
    return _build_meme_tile(MEME_BING_SRC, size, MEME_BING_BORDER, frames, seed,
                            start=MEME_BING_START, count=MEME_BING_COUNT,
                            label="meme_bing")


def build_meme_eta(size=MEME_ETA_SIZE, frames=MEME_ETA_FRAMES, seed=20261027):
    """表情包·η（eta.jpg）：**静态**单帧，源裁成 480×480 方窗后套金属画框。

    源 704×571 的等距水晶方块壁纸，η 只占 21% 宽 —— 裁窗后放到 31% 宽，
    并把原作者水印留在画面内。周期同前两张梗图，也是 5 格。
    """
    return _build_meme_tile(MEME_ETA_SRC, size, MEME_ETA_BORDER, frames, seed,
                            crop=MEME_ETA_CROP, label="meme_eta")


# ---------------------------------------------------------------- 接缝自检

def seam_report(img, name):
    """接缝自检：接缝处的相邻像素差**不得比图案内部最陡处更陡**。

    数学依据：若贴图是某个周期函数的采样，那么「接缝那一对像素」与
    「内部任意一对像素」是同一批样本，所以

        接缝差 ≤ 内部相邻差的最大值（容噪声）

    这是**唯一正确**的判据。两个常见误判（都踩过）：

    - ❌ 直接拿「接缝差 ≤ 内部平均差 × 1.25」：当图案的周期边界刚好落在一条
      全高强笔触上（拉丝金属的拼缝高光、百叶的上缘高光）时必然假阳性 ——
      vent 接缝差 187.68，可内部同一条百叶缝隙处的最大差就是 187.67；
      brushed 接缝差 101.23，内部同一道拼缝 |col255-col256| = 100.67。
      这时要拿「内部同位置」的差来对照，而不是拿平均值。
    - ❌ 只拿「≤ max」又抓不住真裂缝（噪声图的接缝差只有 ~1.4σ，远小于内部 max ~5σ）。
      所以**两个判据并列**：满足任一即通过（严格判据优先，另一条用于识别
      「接缝正好落在图案自身陡峭处」这一合法情形）。
    """
    a = np.asarray(img.convert("RGB")).astype(np.int16)
    dlr = np.abs(a[:, 1:] - a[:, :-1]).mean(axis=(0, 2))     # 每对相邻列的平均差
    dtb = np.abs(a[1:, :] - a[:-1, :]).mean(axis=(1, 2))
    lr = float(np.abs(a[:, 0] - a[:, -1]).mean())
    tb = float(np.abs(a[0, :] - a[-1, :]).mean())
    ok_lr = lr <= float(np.percentile(dlr, 99)) * 1.10 or lr <= float(dlr.max()) * 1.02
    ok_tb = tb <= float(np.percentile(dtb, 99)) * 1.10 or tb <= float(dtb.max()) * 1.02

    def tag(v, d, ok):
        if not ok:
            return "!! 偏大"
        return "OK" if v <= float(np.percentile(d, 99)) * 1.10 else "OK(落在自身陡峭处)"

    print("  %-8s 左-右 %7.2f (内部99%% %6.2f, 最大 %6.2f → %s) | 上-下 %7.2f (内部99%% %6.2f, 最大 %6.2f → %s)"
          % (name, lr, float(np.percentile(dlr, 99)), float(dlr.max()), tag(lr, dlr, ok_lr),
             tb, float(np.percentile(dtb, 99)), float(dtb.max()), tag(tb, dtb, ok_tb)))
    return ok_lr and ok_tb


# ---------------------------------------------------------------- 资源

# (皮肤 id, 方块注册名, 贴图文件名, 中文名, 英文名)
VARIANTS = [
    ("star", "hull_viewport_star", "hull_viewport_inner_star",
     "全周天舱壁（星空）", "Omni Hull Viewport (Starfield)"),
    ("hazard", "hull_viewport_hazard", "hull_viewport_inner_hazard",
     "全周天舱壁（警示）", "Omni Hull Viewport (Hazard)"),
    ("circuit", "hull_viewport_circuit", "hull_viewport_inner_circuit",
     "全周天舱壁（电路）", "Omni Hull Viewport (Circuit)"),
    ("armor", "hull_viewport_armor", "hull_viewport_inner_armor",
     "全周天舱壁（装甲）", "Omni Hull Viewport (Armor)"),
    ("solar", "hull_viewport_solar", "hull_viewport_inner_solar",
     "全周天舱壁（太阳能板）", "Omni Hull Viewport (Solar Panel)"),
    ("grid", "hull_viewport_grid", "hull_viewport_inner_grid",
     "全周天舱壁（格栅）", "Omni Hull Viewport (Grating)"),
    ("vent", "hull_viewport_vent", "hull_viewport_inner_vent",
     "全周天舱壁（百叶）", "Omni Hull Viewport (Louver)"),
    ("rust", "hull_viewport_rust", "hull_viewport_inner_rust",
     "全周天舱壁（锈蚀）", "Omni Hull Viewport (Rusted)"),
    ("tile", "hull_viewport_tile", "hull_viewport_inner_tile",
     "全周天舱壁（瓷砖）", "Omni Hull Viewport (Tiled)"),
    ("tread", "hull_viewport_tread", "hull_viewport_inner_tread",
     "全周天舱壁（花纹板）", "Omni Hull Viewport (Tread Plate)"),
    ("brushed", "hull_viewport_brushed", "hull_viewport_inner_brushed",
     "全周天舱壁（拉丝）", "Omni Hull Viewport (Brushed)"),
    ("glyph", "hull_viewport_glyph", "hull_viewport_inner_glyph",
     "全周天舱壁（铭文）", "Omni Hull Viewport (Glyphs)"),
    ("camo", "hull_viewport_camo", "hull_viewport_inner_camo",
     "全周天舱壁（迷彩）", "Omni Hull Viewport (Camo)"),
    ("brick", "hull_viewport_brick", "hull_viewport_inner_brick",
     "全周天舱壁（耐火砖）", "Omni Hull Viewport (Firebrick)"),

    # ── 第三批：科幻动态（贴图为 8 帧纵向图集，渲染器按游戏时间播放） ──────────
    ("scan", "hull_viewport_scan", "hull_viewport_inner_scan",
     "全周天舱壁（雷达扫描）", "Omni Hull Viewport (Radar Sweep)"),
    ("datastream", "hull_viewport_datastream", "hull_viewport_inner_datastream",
     "全周天舱壁（数据流）", "Omni Hull Viewport (Data Stream)"),
    ("holo", "hull_viewport_holo", "hull_viewport_inner_holo",
     "全周天舱壁（全息投影）", "Omni Hull Viewport (Hologram)"),
    ("pulse", "hull_viewport_pulse", "hull_viewport_inner_pulse",
     "全周天舱壁（能量脉冲）", "Omni Hull Viewport (Pulse)"),
    ("plasma", "hull_viewport_plasma", "hull_viewport_inner_plasma",
     "全周天舱壁（等离子）", "Omni Hull Viewport (Plasma)"),

    # ── 第四批：梗图（贴图周期 5 格，与 PanelSkin.MEME / MEME_BING 的 period 一致） ──
    ("meme", "hull_viewport_meme", "hull_viewport_inner_meme",
     "全周天舱壁（表情包·兔子）", "Omni Hull Viewport (Meme: Bunny)"),
    ("meme_bing", "hull_viewport_meme_bing", "hull_viewport_inner_meme_bing",
     "全周天舱壁（表情包·冰冰冰）", "Omni Hull Viewport (Meme: Bingbing)"),
    ("meme_eta", "hull_viewport_meme_eta", "hull_viewport_inner_meme_eta",
     "全周天舱壁（表情包·η）", "Omni Hull Viewport (Meme: Eta)"),
]

# 贴图生成器（顺序与 VARIANTS 的贴图名对应）
BUILDERS = [
    ("hull_viewport_inner_star", build_star),
    ("hull_viewport_inner_hazard", build_hazard),
    ("hull_viewport_inner_circuit", build_circuit),
    ("hull_viewport_inner_armor", build_armor),
    ("hull_viewport_inner_solar", build_solar),
    ("hull_viewport_inner_grid", build_grid),
    ("hull_viewport_inner_vent", build_vent),
    ("hull_viewport_inner_rust", build_rust),
    ("hull_viewport_inner_tile", build_tile),
    ("hull_viewport_inner_tread", build_tread),
    ("hull_viewport_inner_brushed", build_brushed),
    ("hull_viewport_inner_glyph", build_glyph),
    ("hull_viewport_inner_camo", build_camo),
    ("hull_viewport_inner_brick", build_brick),
    ("hull_viewport_inner_scan", build_scan),
    ("hull_viewport_inner_datastream", build_datastream),
    ("hull_viewport_inner_holo", build_holo),
    ("hull_viewport_inner_pulse", build_pulse),
    ("hull_viewport_inner_plasma", build_plasma),
    ("hull_viewport_inner_meme", build_meme),
    ("hull_viewport_inner_meme_bing", build_meme_bing),
    ("hull_viewport_inner_meme_eta", build_meme_eta),
]


def ensure_variant_assets():
    """blockstates / 物品模型 / 语言条目 —— 幂等，可重复调用。

    模型文件**完全复用**基础舱壁那 6 个 `hull_viewport_inner_<dir>.json`
    （变种只换朝内面的贴图，而那一面由 BER 绘制，模型里本来就没有）。
    """
    for d in (BS_OUT, ITEM_OUT, LANG_OUT):
        os.makedirs(d, exist_ok=True)

    for (_id, block, _tex, zh, en) in VARIANTS:
        variants = {}
        for inner in DIRS:
            model = "%s:block/hull_viewport_inner_%s" % (MOD_ID, inner)
            for op in ("false", "true"):
                for sg in ("false", "true"):
                    variants["inner=%s,open=%s,signal=%s" % (inner, op, sg)] = {"model": model}
        p = os.path.join(BS_OUT, block + ".json")
        with open(p, "w", encoding="utf-8") as f:
            json.dump({"variants": variants}, f, ensure_ascii=False, indent=2)

        p = os.path.join(ITEM_OUT, block + ".json")
        with open(p, "w", encoding="utf-8") as f:
            json.dump({"parent": "%s:block/hull_viewport_inner_down" % MOD_ID}, f,
                      ensure_ascii=False, indent=2)
        print("  资源 %s: blockstate + item model" % block)

    for lang, key in (("zh_cn", "zh"), ("en_us", "en")):
        p = os.path.join(LANG_OUT, lang + ".json")
        with open(p, encoding="utf-8") as f:
            data = json.load(f)
        for (_id, block, _tex, zh, en) in VARIANTS:
            data["block.gtoecore." + block] = zh if key == "zh" else en
        with open(p, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("  语言 %s: +%d 条 (共 %d)" % (lang, len(VARIANTS), len(data)))


def main():
    os.makedirs(TEX_OUT, exist_ok=True)
    print("贴图 (静态 %dx%d / 动态每帧 %dx%d, 每格 %d px):"
          % (SIZE, SIZE, ANIM_FRAME_SIZE, ANIM_FRAME_SIZE, SIZE // PERIOD))
    ok = True
    total = 0
    for name, fn in BUILDERS:
        img = fn()
        p = os.path.join(TEX_OUT, name + ".png")
        img.save(p, optimize=True)
        # 调色板量化：这些图案用色很少，128 色基本无损，体积通常砍一半以上；
        # 但「纯色块 + 锐边」的图（数据流）加了抖动反而更大 —— 谁小用谁。
        q = img.quantize(colors=128, method=Image.MEDIANCUT, dither=Image.FLOYDSTEINBERG)
        tmp = p + ".q"
        q.save(tmp, format="PNG", optimize=True)   # ⚠ 必须显式 format：PIL 不认 ".q" 后缀
        if os.path.getsize(tmp) < os.path.getsize(p):
            os.replace(tmp, p)
        else:
            os.remove(tmp)
        final = Image.open(p).convert("RGB")   # 自检用**最终落盘**的那张（量化会改动像素）
        sz = os.path.getsize(p)
        total += sz
        label = name.replace("hull_viewport_inner_", "")
        if final.height > final.width:
            # 帧图集：**逐帧**都要求可平铺（渲染时 v 在帧内取模）
            n = final.height // final.width
            print("  %-12s %8d 字节  动态 %d 帧（每帧 %d×%d）"
                  % (label, sz, n, final.width, final.width))
            for i in range(n):
                f = final.crop((0, i * final.width, final.width, (i + 1) * final.width))
                ok &= seam_report(f, "%s#%d" % (label, i))
        else:
            print("  %-12s %8d 字节" % (label, sz))
            ok &= seam_report(final, label)
    print("  合计 %.2f MB  接缝自检: %s"
          % (total / 1048576.0, "全部通过" if ok else "有可疑项"))

    print("资源:")
    ensure_variant_assets()


if __name__ == "__main__":
    main()
