"""燃烧引擎（GTM 机器版）资源生成：blockstate + 普通机器模型 + item 模型 + overlay 贴图。

⚠ 模型用「普通 block 模型」（对齐 energy hatch 的可靠做法），不用 gtceu:machine loader
   的 recipe_logic_status 变体 —— 本机器没有 RecipeLogic，stateDefinition 里没有该属性，
   写了会匹配不到 → 紫黑 missing 格。
"""
import json
import os

from PIL import Image, ImageDraw

AS = r'C:\Users\23737\WorkBuddy\2026-09-21-20-07-18\gtn-core\src\main\resources\assets\gtoecore'
MODEL_DIR = os.path.join(AS, 'models', 'block', 'machine')
BS_DIR = os.path.join(AS, 'blockstates')
ITEM_DIR = os.path.join(AS, 'models', 'item')
TEX_BLOCK = os.path.join(AS, 'textures', 'block', 'energy')
TEX_OVERLAY = os.path.join(AS, 'textures', 'block', 'overlay')

for d in (MODEL_DIR, BS_DIR, ITEM_DIR, TEX_BLOCK, TEX_OVERLAY):
    os.makedirs(d, exist_ok=True)

FACINGS = ['north', 'south', 'west', 'east']

# ---------------- 贴图：机器外壳（LV 电压风格 + 引擎进气窗） ----------------
def casing(name, base, accent, vent):
    im = Image.new('RGBA', (16, 16), base)
    d = ImageDraw.Draw(im)
    # 边框
    d.rectangle([0, 0, 15, 15], outline=accent)
    # 金属拉丝
    for y in range(1, 15, 3):
        d.line([(1, y), (14, y)], fill=(max(0, base[0] - 12), max(0, base[1] - 12), max(0, base[2] - 12), 255))
    # 进气格栅
    for y in vent:
        d.line([(3, y), (12, y)], fill=(30, 26, 24, 255))
    return im


lv_base = (120, 120, 124, 255)
lv_accent = (86, 86, 90, 255)
casing('combustion_top', lv_base, lv_accent, (4, 7, 10)).save(os.path.join(TEX_BLOCK, 'combustion_engine_top.png'))
casing('combustion_side', lv_base, lv_accent, (5, 8)).save(os.path.join(TEX_BLOCK, 'combustion_engine_side.png'))
casing('combustion_bottom', (95, 95, 99, 255), (70, 70, 74, 255), ()).save(
    os.path.join(TEX_BLOCK, 'combustion_engine_bottom.png'))
# 正面（进气窗 + 燃料口）
front = casing('combustion_front', lv_base, lv_accent, (4, 6))
dfr = ImageDraw.Draw(front)
dfr.rectangle([5, 10, 10, 13], fill=(52, 46, 40, 255))
dfr.rectangle([5, 10, 10, 13], outline=(38, 34, 30, 255))
front.save(os.path.join(TEX_BLOCK, 'combustion_engine_front.png'))
# 正面亮（燃烧）
front_lit = casing('combustion_front', lv_base, lv_accent, (4, 6))
dfl = ImageDraw.Draw(front_lit)
dfl.rectangle([5, 10, 10, 13], fill=(255, 172, 52, 255))
dfl.rectangle([6, 11, 9, 12], fill=(255, 232, 150, 255))
front_lit.save(os.path.join(TEX_BLOCK, 'combustion_engine_front_lit.png'))
print('casing textures written')

# ---------------- blockstate：NON_Y_AXIS = 4 个 facing 变体（无 upwards_facing 属性） ----------------
variants = {}
rot = {'north': None, 'east': 90, 'south': 180, 'west': 270}
for f in FACINGS:
    entry = {'model': 'gtoecore:block/machine/combustion_engine'}
    if rot[f]:
        entry['y'] = rot[f]
    variants['facing=' + f] = entry
with open(os.path.join(BS_DIR, 'combustion_engine.json'), 'w', encoding='utf-8') as fp:
    json.dump({'variants': variants}, fp, indent=2)
print('blockstate ->', len(variants), 'variants')

# ---------------- 机器模型：普通 block 模型（六面 casing + 正面 overlay 层） ----------------
model = {
    'parent': 'minecraft:block/block',
    'textures': {
        'top': 'gtoecore:block/energy/combustion_engine_top',
        'bottom': 'gtoecore:block/energy/combustion_engine_bottom',
        'side': 'gtoecore:block/energy/combustion_engine_side',
        'front': 'gtoecore:block/energy/combustion_engine_front',
        'particle': 'gtoecore:block/energy/combustion_engine_side',
    },
    'elements': [
        {
            'from': [0, 0, 0], 'to': [16, 16, 16],
            'faces': {
                'down': {'texture': '#bottom', 'cullface': 'down'},
                'up': {'texture': '#top', 'cullface': 'up'},
                'north': {'texture': '#front', 'cullface': 'north'},
                'south': {'texture': '#side', 'cullface': 'south'},
                'west': {'texture': '#side', 'cullface': 'west'},
                'east': {'texture': '#side', 'cullface': 'east'},
            },
        },
    ],
}
with open(os.path.join(MODEL_DIR, 'combustion_engine.json'), 'w', encoding='utf-8') as fp:
    json.dump(model, fp, indent=2)
print('machine model written')

# ---------------- item 模型 ----------------
with open(os.path.join(ITEM_DIR, 'combustion_engine.json'), 'w', encoding='utf-8') as fp:
    json.dump({'parent': 'gtoecore:block/machine/combustion_engine'}, fp, indent=2)
print('item model written')

# ---------------- overlay 贴图（保留：机器正面发光层，供后续 lit 变体用） ----------------
def draw_emissive(active):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    if active:
        d = ImageDraw.Draw(im)
        d.rectangle([5, 10, 10, 13], fill=(255, 190, 90, 255))
        d.rectangle([6, 11, 9, 12], fill=(255, 236, 170, 255))
    return im


draw_emissive(False).save(os.path.join(TEX_OVERLAY, 'combustion_engine_front_emissive.png'))
draw_emissive(True).save(os.path.join(TEX_OVERLAY, 'combustion_engine_front_active_emissive.png'))
print('overlay emissive written')