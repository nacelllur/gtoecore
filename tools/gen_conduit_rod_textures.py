# -*- coding: utf-8 -*-
"""生成 GT 管道风格能量导体贴图（统一材质，无方向性 → 任意 uv 都干净）。

设计修正（2026-10-04）：
  旧版为"暗背板 + 中央 4px 色条"，模型未写 uv 时整张平铺 → 75% 暗色 + 25% 橙条，
  游戏里看起来是"黑褐斑驳"而非细管。现改为**整张 16×16 都是材质本体**
  （底色 + 横向拉丝 + 细噪点），无方向性 ⇒ 无论怎么取 uv、面朝哪都一致。

输出（每种 2 张，路径不变）：
  {name}_side.png  杆身材质
  {name}_end.png   端面材质（略亮，像打磨过的切口/接头）
"""
from PIL import Image
import os
import random

OUT = os.path.join(os.path.dirname(__file__), "..",
                   "src", "main", "resources", "assets", "gtoecore", "textures", "block", "energy")

# name -> (基色, 拉丝强度)
TYPES = {
    "rotation_axle": ((124, 126, 134), 14),
    "heat_pipe":     ((188, 100, 48),  16),
    "cold_pipe":     ((64, 130, 198),  16),
    "kinetic_rod":   ((178, 162, 48),  16),
}


def clamp(v):
    return max(0, min(255, int(v)))


def make_texture(base, streak, seed, brighten=0):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    # 每行一个固定偏移 → 横向拉丝（管材轧制感）
    row_shift = [rnd.randint(-streak, streak) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-4, 4)
            d = row_shift[y] + n + brighten
            px[x, y] = (clamp(base[0] + d), clamp(base[1] + d), clamp(base[2] + d), 255)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, (name, (base, streak)) in enumerate(TYPES.items()):
        make_texture(base, streak, seed=1000 + i).save(os.path.join(OUT, name + "_side.png"))
        make_texture(base, max(2, streak // 2), seed=2000 + i, brighten=20) \
            .save(os.path.join(OUT, name + "_end.png"))
        print("wrote", name + "_side.png /", name + "_end.png")


if __name__ == "__main__":
    main()