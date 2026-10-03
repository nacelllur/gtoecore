# -*- coding: utf-8 -*-
"""
生成 gtncore 无人机物品贴图（16x16 RGBA PNG，纯标准库）。

布局参考 GTO 四旋翼无人机：四角旋翼(5x1 桨叶+桨毂) + 机臂 + 中央机身。
机身颜色 = 官方 GTValues.VC 电压等级色（LV~MAX 共 14 级）；
探测无人机(survey) 下挂相机（青色镜头），开采无人机(mining) 下挂钻头。

用法：python tools/gen_drone_textures.py
输出：src/main/resources/assets/gtncore/textures/item/{survey,mining}_drone_<tier>.png
      tools/drone_preview.png（2x14 拼图预览，放大 6 倍）
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "gtncore", "textures", "item")

# 官方 GTValues.VC（ULV=0 不用；无人机从 LV=1 开始）
VC = [0xC80000, 0xDCDCDC, 16737280, 0xFFFF1E, 0x808080, 0xF0F0F5, 0xE99797,
      8307652, 8302718, 12547264, 744702, 9522833, 0x488748, 0x8C0000, 2631925]
VN = ["ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv",
      "uev", "uiv", "uxv", "opv", "max"]
TIERS = list(range(1, 15))  # LV..MAX


def rgb(c):
    return ((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF)


def shade(c, f):
    r, g, b = rgb(c)
    return (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)))


def mix(c, other, t):
    r1, g1, b1 = rgb(c)
    r2, g2, b2 = rgb(other)
    return (int(r1 + (r2 - r1) * t), int(g1 + (g2 - g1) * t), int(b1 + (b2 - b1) * t))


# 固定调色板
BLADE = (62, 67, 75)       # 桨叶深灰
HUB = (201, 206, 214)      # 桨毂亮灰
ARM = (84, 90, 99)         # 机臂
MOUNT = (58, 63, 70)       # 挂载连接件
CAM_DARK = (32, 36, 42)    # 相机外壳
CAM_LENS = (47, 216, 242)  # 相机镜头（青）
STEEL = (110, 110, 120)    # 钻杆
STEEL_HI = (140, 140, 150) # 钻杆亮面
COPPER = (192, 120, 48)    # 钻头尖（铜色）


def blank(w=16, h=16):
    return [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]


def put(img, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y][x] = (c[0], c[1], c[2], a)


def draw_drone(tier_color, kind):
    img = blank()
    # ---- 旋翼 ×4：5x1 桨叶 + 中心桨毂 ----
    rotors = [(2, 2), (13, 2), (2, 13), (13, 13)]
    for cx, cy in rotors:
        for dx in (-2, -1, 1, 2):
            put(img, cx + dx, cy, BLADE)
        put(img, cx, cy, HUB)
    # ---- 机臂：桨毂斜向连到机身四角 ----
    arms = [
        [(3, 3), (4, 4), (5, 5)],      # 左上
        [(12, 3), (11, 4), (10, 5)],   # 右上
        [(3, 12), (4, 11), (5, 10)],   # 左下
        [(12, 12), (11, 11), (10, 10)] # 右下
    ]
    for arm in arms:
        for x, y in arm:
            put(img, x, y, ARM)
    # ---- 机身：x5..10, y6..9，描边 + 顶高光 + 底阴影 ----
    outline = shade(tier_color, 0.42)
    hi = mix(tier_color, 0xFFFFFF, 0.28)
    lo = shade(tier_color, 0.72)
    for x in range(5, 11):
        put(img, x, 6, outline)
        put(img, x, 9, outline)
    for y in range(6, 10):
        put(img, 5, y, outline)
        put(img, 10, y, outline)
    for x in range(6, 10):
        put(img, x, 7, hi)
        put(img, x, 8, lo)
    # 机身前缘小灯（任务色：探测青 / 开采橙）
    put(img, 9, 7, CAM_LENS if kind == "survey" else COPPER)
    # ---- 挂载 ----
    if kind == "survey":
        put(img, 7, 10, MOUNT)
        put(img, 8, 10, MOUNT)
        put(img, 7, 11, CAM_DARK)
        put(img, 8, 11, CAM_LENS)
    else:
        put(img, 7, 10, STEEL_HI)
        put(img, 8, 10, STEEL)
        put(img, 7, 11, STEEL)
        put(img, 8, 11, STEEL_HI)
        put(img, 7, 12, COPPER)
        put(img, 8, 12, COPPER)
    return img


def write_png(path, img, scale=1):
    h, w = len(img), len(img[0])
    if scale > 1:
        img = [[px for px in row for _ in range(scale)] for row in img for _ in range(scale)]
        h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *px) for px in row) for row in img)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    os.makedirs(OUT, exist_ok=True)
    sheets = {"survey": [], "mining": []}
    for tier in TIERS:
        color = VC[tier]
        name = VN[tier]
        for kind in ("survey", "mining"):
            img = draw_drone(color, kind)
            path = os.path.join(OUT, f"{kind}_drone_{name}.png")
            write_png(path, img)
            sheets[kind].append(img)
    # 预览拼图：2 行（survey / mining）x 14 列，棋盘底，放大 6 倍
    s = 6
    W, H = 16 * 14, 16 * 2
    sheet = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]
    for y in range(H):
        for x in range(W):
            sheet[y][x] = (48, 48, 48, 255) if (x // 4 + y // 4) % 2 else (32, 32, 32, 255)
    for row, kind in enumerate(("survey", "mining")):
        for col, img in enumerate(sheets[kind]):
            for y in range(16):
                for x in range(16):
                    px = img[y][x]
                    if px[3]:
                        sheet[row * 16 + y][col * 16 + x] = px
    write_png(os.path.join(ROOT, "tools", "drone_preview.png"), sheet, scale=s)
    print(f"OK: 28 textures -> {OUT}")
    print(f"preview -> tools/drone_preview.png")


if __name__ == "__main__":
    main()
