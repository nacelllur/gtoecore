#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gtoecore 机器控制器外壳贴图生成器

**为什么需要它（重要）**：
GTM 的 `gtceu:machine` 模型 loader 在烘焙 variants 里的 `textures` 时，
**不解析 `minecraft:` 命名空间** —— 日志表现是
`Missing textures in model gtoecore:<机器>#<变体>`，游戏里直接显示
紫黑品红 "missing model" 棋盘格（本项目 deep_space_hub + 6 台深空模块全中）。

所以 GTM addon 的机器外壳贴图 **必须放在自己 mod 命名空间下**
（`gtoecore:block/casing/...`），不能用 `minecraft:block/xxx` 或
`gtceu:block/...` 以外的跨 mod 引用。改外观只需改这里重跑。

用法：
    <python> tools/gen_casing_textures.py
"""
import os
import random

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "src", "main", "resources", "assets", "gtoecore",
                   "textures", "block", "casing")


def _clamp(v):
    return max(0, min(255, int(v)))


def _speckle(d, base, count, amp, seed):
    rnd = random.Random(seed)
    for _ in range(count):
        x, y = rnd.randrange(16), rnd.randrange(16)
        v = rnd.randint(-amp, amp)
        d.point((x, y), fill=tuple(_clamp(c + v) for c in base))


def deep_space_hub():
    """平滑石英质感（深空枢纽主壳）：近白、极淡横向条带、少量噪点。"""
    im = Image.new("RGB", (16, 16))
    d = ImageDraw.Draw(im)
    base = (236, 235, 229)
    d.rectangle([0, 0, 15, 15], fill=base)
    rnd = random.Random(7)
    for y in range(16):
        v = rnd.randint(-3, 3)
        d.line([(0, y), (15, y)], fill=tuple(_clamp(c + v) for c in base))
    _speckle(d, base, 14, 6, 7)
    return im


def deep_space_module():
    """浅灰混凝土质感（深空模块外壳）：明显颗粒。"""
    im = Image.new("RGB", (16, 16))
    d = ImageDraw.Draw(im)
    base = (210, 210, 210)
    d.rectangle([0, 0, 15, 15], fill=base)
    _speckle(d, base, 70, 12, 3)
    return im


def main():
    out = os.path.normpath(OUT)
    os.makedirs(out, exist_ok=True)
    for name, fn in [("deep_space_hub", deep_space_hub),
                     ("deep_space_module", deep_space_module)]:
        p = os.path.join(out, name + ".png")
        fn().save(p)
        im = Image.open(p)
        px = list(im.getdata())
        avg = tuple(sum(c[i] for c in px) // len(px) for i in range(3))
        print("生成 %-20s %s size=%s avg=%s"
              % (name + ".png", os.path.getsize(p), im.size, avg))


if __name__ == "__main__":
    main()