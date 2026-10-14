"""燃烧引擎 GUI 背景贴图生成器（192x166，右侧 16px 为火焰素材区）。

布局约定（与 CombustionEngineMenu 的 Slot 坐标一一对应）：
  Slot 的 (x, y) = 槽位 18x18 区域左上角，贴图装饰框画在 (x-1, y-1)-(x+16, y+16)。

  y=6          标题文字（左对齐 x=8）
  y=19..37     燃料槽 slot(79,20) + 火焰条框 (101,19)-(117,36)
  y=26 / y=40  转速 / 燃料余量读数（左对齐 x=8）
  y=72         分界线
  y=84..139    玩家背包 3x9（slot x=8+col*18, y=84+row*18）
  y=142..159   快捷栏 9（slot x=8+col*18, y=142）
"""
from PIL import Image, ImageDraw
import os

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "gtoecore", "textures", "gui")
os.makedirs(OUT, exist_ok=True)

BGc = (62, 62, 68)
BORDERc = (98, 98, 106)
INNERc = (80, 80, 88)
SLOTc = (30, 30, 34)
SLOT_EDGEc = (112, 112, 120)
DARKc = (44, 44, 50)

W, H = 192, 166
im = Image.new("RGB", (W, H), BGc)
d = ImageDraw.Draw(im)

# 面板 + 双层边框
d.rectangle([0, 0, 175, 165], outline=BORDERc)
d.rectangle([1, 1, 174, 164], outline=INNERc)


def slot(x, y):
    """标准槽位：外框 1px + 内凹深色 16x16（与 MC Slot 渲染完全对齐）。"""
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=SLOT_EDGEc)
    d.rectangle([x, y, x + 15, y + 15], fill=SLOTc)


# 标题区分隔线
d.line([(7, 18), (168, 18)], fill=INNERc)

# 燃料槽（slot 坐标 79,20）+ 火焰条框
slot(79, 20)
d.rectangle([100, 18, 118, 38], fill=DARKc)
d.rectangle([100, 18, 118, 38], outline=SLOT_EDGEc)

# 背包与上方功能区的分隔线
d.line([(7, 72), (168, 72)], fill=INNERc)

# 玩家背包 3x9
for row in range(3):
    for col in range(9):
        slot(8 + col * 18, 84 + row * 18)

# 快捷栏 9（与背包之间的分隔线）
d.line([(7, 138), (168, 138)], fill=INNERc)
for col in range(9):
    slot(8 + col * 18, 142)

# ---- 火焰素材 14x14 @ (176, 0)，供进度条 blit ----
d.rectangle([176, 0, 191, 15], fill=(28, 22, 16))
d.polygon([(183, 1), (178, 6), (182, 6), (177, 12), (189, 12), (184, 6), (188, 6)],
          fill=(255, 152, 32))
d.polygon([(183, 8), (180, 11), (187, 11)], fill=(255, 226, 130))

im.save(os.path.join(OUT, "combustion_engine.png"))
print("GUI bg written:", os.path.getsize(os.path.join(OUT, "combustion_engine.png")), "bytes")