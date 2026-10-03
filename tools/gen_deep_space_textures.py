# -*- coding: utf-8 -*-
"""
生成 gtncore 深空体系的贴图（纯标准库 zlib+struct 写 PNG，16x16）：
  1. 深空枢纽覆层：overlay_front[_emissive|_active|_active_emissive]
  2. 扩展模块覆层：同上
  3. 深空燃料单元物品贴图：item/deep_space_fuel.png

风格约定（照抄 GT 机器覆层惯例）：
  - overlay_front：**透明底** + 中央深色屏幕（贴合机器正面）
  - *_emissive：只保留发光像素，其余全透明（夜晚自发光）
  - *_active：屏幕点亮版
配色照 EARTH-STAR：青（#3FD8D8）为主、白星点、深蓝黑屏幕底（#12161F）。

用法：python tools/gen_deep_space_textures.py
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "gtncore", "textures")

# 调色板 (r, g, b, a)
TRANS = (0, 0, 0, 0)
SCREEN = (18, 22, 31, 255)        # 屏幕底色
SCREEN_EDGE = (36, 44, 58, 255)   # 屏幕边框
CYAN = (63, 216, 216, 255)        # 主青
CYAN_DIM = (30, 110, 118, 255)    # 暗青
WHITE = (235, 245, 255, 255)      # 星点/高光
DARK = (10, 12, 16, 255)          # 深色描边

S = 16


def blank():
    return [[TRANS for _ in range(S)] for _ in range(S)]


def px(img, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        img[y][x] = c


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def frame(img, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        px(img, x, y0, c)
        px(img, x, y1, c)
    for y in range(y0, y1 + 1):
        px(img, x0, y, c)
        px(img, x1, y, c)


def write_png(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in img)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print("write", os.path.relpath(path, ROOT))


# ---------------------------------------------------------------- 枢纽覆层
def hub_overlay(active, emissive_only):
    img = blank()
    if not emissive_only:
        # 中央屏幕（透明底 + 深色屏）
        rect(img, 3, 3, 12, 12, SCREEN)
        frame(img, 3, 3, 12, 12, SCREEN_EDGE)
        rect(img, 4, 4, 11, 11, DARK if not active else SCREEN)

    # 深空图案：中心青色核心 + 光环
    core = CYAN if active else CYAN_DIM
    px(img, 8, 7, core); px(img, 8, 8, core)
    px(img, 7, 8, core); px(img, 9, 8, core)
    px(img, 8, 6, core); px(img, 8, 9, core)
    # 光环（做旧：不点亮时用暗青）
    ring = CYAN if active else CYAN_DIM
    for (x, y) in [(5, 5), (11, 5), (5, 11), (10, 11)]:
        px(img, x, y, ring)
    # 星点（发光部分，emissive 只留这些）
    for (x, y) in [(6, 6), (10, 6), (6, 10), (11, 10), (8, 4), (8, 11)]:
        px(img, x, y, WHITE if active else CYAN_DIM)
    # 底部数据条（3 条青色刻度）
    for i, x in enumerate((5, 7, 9)):
        px(img, x, 10 if not active else 10, ring)
        px(img, x, 9 if not active else 10 - i, ring)
    return img


# ---------------------------------------------------------------- 模块覆层
def module_overlay(active, emissive_only):
    img = blank()
    if not emissive_only:
        rect(img, 3, 3, 12, 12, SCREEN)
        frame(img, 3, 3, 12, 12, SCREEN_EDGE)
        rect(img, 4, 4, 11, 11, DARK if not active else SCREEN)

    c = CYAN if active else CYAN_DIM
    # 电路：三条竖线 + 两条横线（母线）
    for x in (5, 8, 11):
        for y in range(5, 11):
            px(img, x, y, c)
    for y in (6, 9):
        for x in range(5, 12):
            px(img, x, y, c)
    # 节点（亮青，emissive 保留）
    for (x, y) in [(5, 6), (8, 6), (11, 6), (5, 9), (8, 9), (11, 9)]:
        px(img, x, y, WHITE if active else c)
    # 四角螺钉
    for (x, y) in [(4, 4), (11, 4), (4, 11), (11, 11)]:
        px(img, x, y, SCREEN_EDGE if not emissive_only else (0, 0, 0, 0))
    return img


# ---------------------------------------------------------------- 燃料单元
def fuel_item():
    img = blank()
    # 罐体（8x12 居中）
    rect(img, 4, 2, 11, 13, (150, 156, 166, 255))     # 金属灰
    frame(img, 4, 2, 11, 13, (92, 97, 105, 255))      # 描边
    # 顶盖
    rect(img, 5, 2, 10, 3, (196, 202, 212, 255))
    # 青色液面窗口
    rect(img, 5, 6, 10, 11, (24, 30, 40, 255))
    rect(img, 5, 7, 10, 10, CYAN)
    rect(img, 5, 7, 10, 8, (120, 240, 240, 255))      # 高光
    # 上下箍带
    for y in (5, 12):
        for x in range(4, 12):
            px(img, x, y, (92, 97, 105, 255))
    return img


def main():
    hub = os.path.join(TEX, "block", "multiblock", "deep_space_hub")
    write_png(os.path.join(hub, "overlay_front.png"), hub_overlay(False, False))
    write_png(os.path.join(hub, "overlay_front_emissive.png"), hub_overlay(False, True))
    write_png(os.path.join(hub, "overlay_front_active.png"), hub_overlay(True, False))
    write_png(os.path.join(hub, "overlay_front_active_emissive.png"), hub_overlay(True, True))

    mod = os.path.join(TEX, "block", "multiblock", "deep_space_module")
    write_png(os.path.join(mod, "overlay_front.png"), module_overlay(False, False))
    write_png(os.path.join(mod, "overlay_front_emissive.png"), module_overlay(False, True))
    write_png(os.path.join(mod, "overlay_front_active.png"), module_overlay(True, False))
    write_png(os.path.join(mod, "overlay_front_active_emissive.png"), module_overlay(True, True))

    write_png(os.path.join(TEX, "item", "deep_space_fuel.png"), fuel_item())


if __name__ == "__main__":
    main()