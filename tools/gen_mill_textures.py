#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""旋转石磨（Rotary Mill）贴图生成器 —— 16×16 像素、与能量导体同风格。

设计：
- top     磨盘顶面：同心圆碾槽 + 中心轴孔（石灰色）
- side    侧面：下部石磨身（横向拉丝 + 竖向磨痕），上部进料斗（深色金属）
- bottom  底座：金属底座 + 四角铆钉
- side_lit 工作中变体：进料斗区域透出橙光（内部研磨）
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "gtoecore", "textures", "block", "energy")
os.makedirs(OUT, exist_ok=True)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(d, x, y, seed):
    # 简单确定性噪点：让纹理不单调
    h = (x * 928371 + y * 413 + seed * 13) % 100
    return -1 if h < 45 else (1 if h > 55 else 0)


def make_top():
    img = Image.new("RGB", (16, 16))
    d = ImageDraw.Draw(img)
    base = (96, 98, 104)      # 石灰
    dark = (74, 76, 82)       # 深灰
    cx = cy = 7.5
    # 底色
    for y in range(16):
        for x in range(16):
            n = noise(d, x, y, 1)
            img.putpixel((x, y), lerp(base, dark, (n + 1) * 0.12))
    # 同心圆碾槽（半径 2/4/6 的环，深色沟槽）
    for r in (2, 4, 6):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(60, 62, 68), width=1)
    # 径向磨痕（从中心向外画短线）
    for ang in range(0, 360, 30):
        import math
        for rr in (3, 5, 7):
            x0 = cx + rr * math.cos(math.radians(ang))
            y0 = cy + rr * math.sin(math.radians(ang))
            x1 = cx + (rr + 1.2) * math.cos(math.radians(ang))
            y1 = cy + (rr + 1.2) * math.sin(math.radians(ang))
            d.line([x0, y0, x1, y1], fill=(54, 56, 60))
    # 中心轴孔
    d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=(38, 40, 44))
    img.save(os.path.join(OUT, "rotary_mill_top.png"))
    print("rotary_mill_top.png")


def make_side(lit=False):
    img = Image.new("RGB", (16, 16))
    d = ImageDraw.Draw(img)
    stone = (102, 104, 110)
    stone_dark = (82, 84, 90)
    hopper = (58, 60, 66)     # 进料斗金属
    hopper_rim = (44, 46, 52)
    # 下部石磨身（y 8..15，占 8 像素）——横向拉丝
    for y in range(8, 16):
        for x in range(16):
            n = noise(d, x, y, 3)
            base = lerp(stone, stone_dark, (n + 1) * 0.15)
            # 横向拉丝：x 方向渐变增加细节
            stripe = 1 if (x * 7) % 5 < 1 else 0
            base = lerp(base, stone_dark, stripe * 0.18)
            img.putpixel((x, y), base)
    # 磨身竖向磨痕（左右边缘更暗，中间几道垂直暗线）
    d.line([0, 8, 0, 15], fill=(70, 72, 78))
    d.line([15, 8, 15, 15], fill=(70, 72, 78))
    for vx in (3, 7, 11):
        d.line([vx, 8, vx, 15], fill=(88, 90, 96))
    # 上下分界
    d.line([0, 8, 15, 8], fill=(44, 46, 52))
    # 上部进料斗（y 1..7）——梯形斗口
    for y in range(1, 8):
        inset = max(1, int((7 - y) / 2))   # 越靠上越窄 -> 斗口朝上
        for x in range(inset, 16 - inset):
            n = noise(d, x, y, 5)
            img.putpixel((x, y), lerp(hopper, hopper_rim, (n + 1) * 0.2))
        # 斗壁描边
        d.line([inset, y, 15 - inset, y], fill=(38, 40, 44))
    # 顶口边缘金属亮边
    d.line([1, 1, 14, 1], fill=(120, 122, 128))
    d.line([1, 1, 1, 2], fill=(120, 122, 128))
    d.line([14, 1, 14, 2], fill=(120, 122, 128))
    # lit 版：斗口内部透出橙光（研磨火花）
    if lit:
        glow = (255, 148, 48)
        for y in (2, 3):
            inset = max(1, int((7 - y) / 2))
            for x in range(inset + 1, 15 - inset):
                img.putpixel((x, y), lerp((255, 120, 30), (255, 176, 80),
                                          noise(d, x, y, 9) * 0.3))
        d.line([3, 2, 12, 2], fill=(255, 168, 64))
        d.line([4, 3, 11, 3], fill=(255, 130, 36))
    img.save(os.path.join(OUT, "rotary_mill_side_lit.png" if lit else "rotary_mill_side.png"))
    print("rotary_mill_side" + ("_lit" if lit else "") + ".png")


def make_bottom():
    img = Image.new("RGB", (16, 16))
    d = ImageDraw.Draw(img)
    metal = (66, 68, 74)
    metal_dark = (48, 50, 56)
    for y in range(16):
        for x in range(16):
            n = noise(d, x, y, 7)
            img.putpixel((x, y), lerp(metal, metal_dark, (n + 1) * 0.25))
    # 外框
    d.rectangle([0, 0, 15, 15], outline=(40, 42, 46))
    d.rectangle([1, 1, 14, 14], outline=(88, 90, 96))
    # 四角铆钉
    for (rx, ry) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.ellipse([rx - 1, ry - 1, rx + 1, ry + 1], fill=(104, 106, 112))
        d.ellipse([rx, ry, rx + 1, ry + 1], fill=(140, 142, 148))
    # 中央轴孔
    d.ellipse([6.5, 6.5, 9.5, 9.5], outline=(36, 38, 42))
    img.save(os.path.join(OUT, "rotary_mill_bottom.png"))
    print("rotary_mill_bottom.png")


if __name__ == "__main__":
    make_top()
    make_side(False)
    make_side(True)
    make_bottom()
    print("done ->", OUT)
