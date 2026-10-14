# -*- coding: utf-8 -*-
"""GTOE 导体贴图生成（2026-10-05 视觉重做版）。

- rotation_axle：机械动力（Create）传动轴风格 —— 金色亮面金属 + 端面十字节
- heat_pipe / cold_pipe / kinetic_rod：保留原拉丝金属（各自主题色）
- quantum_pipe：Mek 能量管道风格 —— 深灰金属壳 + 红紫能量芯（active 时芯发亮）
- laser_pipe：不在此生成贴图（模型直接引用 gtceu 官方 pipe_laser_side/in/overlay）

输出：{name}_side.png / {name}_end.png；量子管额外 _side_active / _end_active。
"""
from PIL import Image, ImageDraw
import os
import random

OUT = os.path.join(os.path.dirname(__file__), "..",
                   "src", "main", "resources", "assets", "gtoecore", "textures", "block", "energy")


def clamp(v):
    return max(0, min(255, int(v)))


def metal(base, streak, seed, brighten=0):
    """拉丝金属（原版管材）。"""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    row_shift = [rnd.randint(-streak, streak) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-4, 4)
            d = row_shift[y] + n + brighten
            px[x, y] = (clamp(base[0] + d), clamp(base[1] + d), clamp(base[2] + d), 255)
    return img


def create_shaft_side():
    """机械动力传动轴侧面：金色亮面 + 周期性轴环（每 8px 一个暗槽）。"""
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    base = (222, 178, 92)   # 金色
    for y in range(16):
        # 横向渐变亮面（顶部亮、底部稍暗 → 圆轴感）
        shade = 1.0 - 0.25 * (y / 15.0)
        for x in range(16):
            n = random.randint(-3, 3)
            c = clamp(base[0] * shade + n), clamp(base[1] * shade + n), clamp(base[2] * shade + n)
            img.putpixel((x, y), (c[0], c[1], c[2], 255))
    # 轴环：y=7..9 深槽（每节交界）
    for y in range(7, 10):
        for x in range(16):
            img.putpixel((x, y), (120, 92, 44, 255))
    # 高光竖线
    d.line([(4, 0), (4, 15)], fill=(255, 235, 180, 255))
    return img


def create_shaft_end():
    """机械动力传动轴端面：金色圆轴 + 十字节（Create shaft 标志）。"""
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], fill=(70, 62, 48, 255))          # 端板深色
    d.ellipse([3, 3, 12, 12], fill=(222, 178, 92, 255))           # 轴头金色圆
    d.ellipse([3, 3, 12, 12], outline=(150, 118, 60, 255))
    d.rectangle([6, 1, 9, 14], fill=(190, 150, 78, 255))           # 十字节竖臂
    d.rectangle([1, 6, 14, 9], fill=(190, 150, 78, 255))           # 十字节横臂
    d.ellipse([6, 6, 9, 9], fill=(255, 236, 180, 255))             # 中心亮轴心
    d.ellipse([7, 7, 8, 8], fill=(120, 90, 40, 255))
    return img


def create_mek_side(active=False):
    """Mek 能量管道侧面：深灰金属壳（四边）+ 中央红紫能量芯（active 时芯高亮）。"""
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    shell = (52, 56, 62)
    core = (200, 80, 120) if not active else (255, 120, 200)
    # 全底色 = 金属壳（带横向拉丝）
    for y in range(16):
        for x in range(16):
            n = random.randint(-3, 3)
            img.putpixel((x, y), (clamp(shell[0] + n), clamp(shell[1] + n), clamp(shell[2] + n), 255))
    # 中央能量芯：竖向渐变带（宽 6px，x=5..10）
    for y in range(16):
        for x in range(5, 11):
            # 芯从边缘暗到中间亮（能量集中感）
            edge = min(x - 5, 10 - x)
            bright = 0.55 + 0.45 * (3 - edge) / 3.0
            c = (clamp(core[0] * bright), clamp(core[1] * bright), clamp(core[2] * bright))
            img.putpixel((x, y), (c[0], c[1], c[2], 255))
    # 芯与外层之间的金属分隔线
    d.line([(4, 0), (4, 15)], fill=(38, 42, 46, 255))
    d.line([(11, 0), (11, 15)], fill=(38, 42, 46, 255))
    return img


def create_mek_end(active=False):
    """Mek 能量管道端面：方管口 + 中央能量芯圆点（active 时发亮）。"""
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    shell = (52, 56, 62)
    core = (200, 80, 120) if not active else (255, 130, 210)
    d.rectangle([2, 2, 13, 13], fill=shell)                        # 管口金属
    d.rectangle([2, 2, 13, 13], outline=(84, 90, 98, 255))
    d.rectangle([4, 4, 11, 11], outline=(38, 42, 46, 255))          # 内壁
    d.ellipse([6, 6, 9, 9], fill=core)                              # 能量芯圆
    if active:
        d.ellipse([6, 6, 9, 9], outline=(255, 200, 240, 255))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    # 机械动力传动轴
    create_shaft_side().save(os.path.join(OUT, "rotation_axle_side.png"))
    create_shaft_end().save(os.path.join(OUT, "rotation_axle_end.png"))
    print("wrote rotation_axle_side/end.png (Create shaft style)")
    # 热/冷/动三管保留拉丝金属
    for i, (name, (base, streak)) in enumerate([
        ("heat_pipe", ((188, 100, 48), 16)),
        ("cold_pipe", ((64, 130, 198), 16)),
        ("kinetic_rod", ((178, 162, 48), 16)),
    ]):
        metal(base, streak, seed=1000 + i).save(os.path.join(OUT, name + "_side.png"))
        metal(base, max(2, streak // 2), seed=2000 + i, brighten=20).save(
            os.path.join(OUT, name + "_end.png"))
        print("wrote", name + "_side/end.png")
    # Mek 风格量子管
    create_mek_side(False).save(os.path.join(OUT, "quantum_pipe_side.png"))
    create_mek_end(False).save(os.path.join(OUT, "quantum_pipe_end.png"))
    create_mek_side(True).save(os.path.join(OUT, "quantum_pipe_side_active.png"))
    create_mek_end(True).save(os.path.join(OUT, "quantum_pipe_end_active.png"))
    print("wrote quantum_pipe side/end + active (Mek style)")
    # laser_pipe：不生成，模型直接引用 gtceu 官方贴图


if __name__ == "__main__":
    main()