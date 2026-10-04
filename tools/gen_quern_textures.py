#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""旋转石磨（群峦 Quern 形态）贴图生成器 —— 16×16 像素。

形态：矮石柱底座（下磨盘）+ 顶部圆盘（上磨盘，由 BER 旋转）。
贴图：
- quern_base_side   底座侧面：石质矮柱，竖向拉丝 + 苔缝
- quern_base_top    底座顶面：环形碾槽（中间凹、边缘高）
- quern_wheel_side  磨盘侧边：圆盘边缘，齿纹
- quern_wheel_top   磨盘顶面：中央轴孔 + 推柄凹槽
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "gtoecore", "textures", "block", "energy")
os.makedirs(OUT, exist_ok=True)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(x, y, seed):
    h = (x * 928371 + y * 413 + seed * 13) % 100
    return -1 if h < 42 else (1 if h > 58 else 0)


def fill_base(img, base, dark, seed, stripe_axis="v", stripe_step=4):
    """底色+噪点+可选拉丝。stripe_axis: v=竖向拉丝 / h=横向"""
    d = ImageDraw.Draw(img)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c = lerp(base, dark, (n + 1) * 0.18)
            s = 1 if (x * stripe_step) % 5 < 1 else 0
            if stripe_axis == "v":
                if s: c = lerp(c, dark, 0.15)
            else:
                if (y * stripe_step) % 5 < 1: c = lerp(c, dark, 0.15)
            img.putpixel((x, y), c)


def make_base_side():
    img = Image.new("RGB", (16, 16))
    fill_base(img, (116, 112, 104), (78, 74, 66), 3, "v")
    d = ImageDraw.Draw(img)
    # 上下缘倒角
    d.line([0, 0, 15, 0], fill=(140, 136, 126))
    d.line([0, 15, 15, 15], fill=(64, 60, 54))
    # 竖向砖缝（两道）
    d.line([5, 1, 5, 14], fill=(70, 66, 60))
    d.line([11, 1, 11, 14], fill=(70, 66, 60))
    # 苔藓斑点
    for (x, y) in ((2, 9), (13, 5), (8, 12)):
        img.putpixel((x, y), (98, 110, 84))
        img.putpixel((x + 1, y), (90, 104, 78))
    img.save(os.path.join(OUT, "quern_base_side.png"))


def make_base_top():
    img = Image.new("RGB", (16, 16))
    fill_base(img, (120, 116, 108), (84, 80, 72), 5, "h")
    d = ImageDraw.Draw(img)
    # 环形碾槽：外环高、内环低（中心凹槽）
    d.ellipse([1, 1, 14, 14], outline=(96, 92, 84))
    d.ellipse([2, 2, 13, 13], fill=(110, 106, 98))     # 外圈磨道
    d.ellipse([5, 5, 10, 10], fill=(72, 70, 66))       # 内凹槽
    d.ellipse([6, 6, 9, 9], outline=(92, 88, 80))      # 槽底亮环
    # 磨道径向纹
    for ang in range(0, 360, 30):
        import math
        for rr in (3.5, 5.5):
            x0 = 8 + rr * math.cos(math.radians(ang))
            y0 = 8 + rr * math.sin(math.radians(ang))
            x1 = 8 + (rr + 0.8) * math.cos(math.radians(ang))
            y1 = 8 + (rr + 0.8) * math.sin(math.radians(ang))
            d.line([x0, y0, x1, y1], fill=(88, 84, 76))
    img.save(os.path.join(OUT, "quern_base_top.png"))


def make_wheel_side():
    img = Image.new("RGB", (16, 16))
    fill_base(img, (104, 100, 92), (66, 62, 56), 7, "h")
    d = ImageDraw.Draw(img)
    # 上下缘
    d.line([0, 0, 15, 0], fill=(128, 124, 114))
    d.line([0, 15, 15, 15], fill=(52, 48, 44))
    # 边缘齿纹（横向凸条）
    for y in (4, 8, 12):
        d.line([0, y, 15, y], fill=(92, 88, 80))
        d.line([0, y + 1, 15, y + 1], fill=(56, 52, 48))
    img.save(os.path.join(OUT, "quern_wheel_side.png"))


def make_wheel_top():
    img = Image.new("RGB", (16, 16))
    fill_base(img, (112, 108, 100), (74, 70, 64), 9, "h")
    d = ImageDraw.Draw(img)
    # 圆盘外缘
    d.ellipse([0.5, 0.5, 15.5, 15.5], outline=(96, 92, 84))
    # 磨道环
    d.ellipse([2, 2, 13, 13], outline=(88, 84, 76))
    # 中央轴孔
    d.ellipse([6.5, 6.5, 9.5, 9.5], fill=(40, 38, 36))
    d.ellipse([7, 7, 9, 9], fill=(24, 22, 20))
    # 推柄凹槽（十字刻痕）
    d.line([8, 1, 8, 3], fill=(70, 66, 60))
    d.line([8, 12, 8, 14], fill=(70, 66, 60))
    d.line([1, 8, 3, 8], fill=(70, 66, 60))
    d.line([12, 8, 14, 8], fill=(70, 66, 60))
    img.save(os.path.join(OUT, "quern_wheel_top.png"))


if __name__ == "__main__":
    make_base_side()
    make_base_top()
    make_wheel_side()
    make_wheel_top()
    print("done ->", OUT)
