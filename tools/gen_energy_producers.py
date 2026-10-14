"""GTOE 电动产能机 15 压资源生成（CU/KU/MU/LU/QU × ULV~MAX = 75 台）。

外壳 = 融合 GT 电压机械方块元素：模型六面贴图直接引用 gtceu 的
block/casings/voltage/<vname>/{top,side,bottom}（各电压原生色调/图案），
正面 = GTOE 能量主题面板（5 种机器各 1 张，不随电压变色，保持辨识度）。
每台机器一个独立模型 JSON（textures 指向对应电压 casing + 共享 front）。

blockstate / item 模型按 "<电压小写>_<机器id>" 独立生成。
高级能量流体贴图保留（激光能/量子能，32x32 still/flow）。
"""
import json
import os
import random

from PIL import Image, ImageDraw

AS = r'C:\Users\23737\WorkBuddy\2026-09-21-20-07-18\gtn-core\src\main\resources\assets\gtoecore'
MODEL_DIR = os.path.join(AS, 'models', 'block', 'machine')
BS_DIR = os.path.join(AS, 'blockstates')
ITEM_DIR = os.path.join(AS, 'models', 'item')
TEX_BLOCK = os.path.join(AS, 'textures', 'block', 'energy')
TEX_FLUID = os.path.join(AS, 'textures', 'block', 'fluid')

for d in (MODEL_DIR, BS_DIR, ITEM_DIR, TEX_BLOCK, TEX_FLUID):
    os.makedirs(d, exist_ok=True)

FACINGS = ['north', 'south', 'west', 'east']
ROT = {'north': None, 'east': 90, 'south': 180, 'west': 270}

# GT VN 电压名（小写）—— 必须与 GTValues.VN 前 15 个一致（0=ULV 14=MAX）
VOLTAGE_NAMES = ['ulv', 'lv', 'mv', 'hv', 'ev', 'iv', 'luv', 'zpm', 'uv', 'uhv',
                 'uev', 'uiv', 'uxv', 'opv', 'max']


def front_texture(name, front_fn):
    """生成正面主题面板：中性 GT 机械底色 + 机器主题元素（不随电压变色）。"""
    base = (62, 66, 70, 255)
    accent = (96, 102, 108, 255)
    im = Image.new('RGBA', (16, 16), base)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=accent)
    for y in range(1, 15, 3):
        d.line([(1, y), (14, y)], fill=(50, 53, 56, 255))
    for y in (4, 6):
        d.line([(3, y), (12, y)], fill=(28, 28, 30, 255))
    front_fn(ImageDraw.Draw(im))
    im.save(os.path.join(TEX_BLOCK, name + '_front.png'))


# ---------------- 5 台机器的主题贴图（正面元素，与单机版一致） ----------------

def front_cryo(d):
    # 冷凝机正面：冰晶窗口
    d.rectangle([4, 4, 11, 11], fill=(150, 205, 230, 255))
    d.rectangle([4, 4, 11, 11], outline=(95, 140, 170, 255))
    d.line([(6, 6), (9, 9)], fill=(220, 245, 255, 255))
    d.line([(9, 6), (6, 9)], fill=(220, 245, 255, 255))


def front_pneumatic(d):
    # 压缩机正面：压力表
    d.ellipse([4, 4, 11, 11], fill=(60, 66, 60, 255))
    d.ellipse([4, 4, 11, 11], outline=(30, 34, 30, 255))
    d.line([(8, 8), (10, 6)], fill=(120, 220, 130, 255))
    d.point((8, 8), fill=(220, 255, 220, 255))


def front_magnetic(d):
    # 线圈机正面：铜线圈绕组
    for y in range(3, 13, 2):
        d.rectangle([3, y, 12, y + 1], fill=(190, 115, 60, 255))
        d.rectangle([3, y, 12, y + 1], outline=(150, 85, 40, 255))


def front_laser(d):
    # 激光聚束器正面：聚焦透镜 + 发射口
    d.rectangle([4, 4, 11, 11], fill=(20, 34, 40, 255))
    d.rectangle([4, 4, 11, 11], outline=(40, 70, 80, 255))
    d.rectangle([6, 6, 9, 9], fill=(60, 235, 235, 255))
    d.point((7, 7), fill=(240, 255, 255, 255))


def front_quantum(d):
    # 量子凝聚器正面：量子核心
    d.ellipse([3, 3, 12, 12], fill=(46, 22, 66, 255))
    d.ellipse([3, 3, 12, 12], outline=(90, 40, 130, 255))
    d.ellipse([6, 6, 9, 9], fill=(200, 120, 255, 255))
    d.point((7, 7), fill=(250, 230, 255, 255))


def front_concentrator(d):
    # 聚光阵列正面：同心聚光镜阵列 + 焦点光束（聚变光源 → 定向强光）
    d.ellipse([1, 1, 14, 14], fill=(38, 40, 46, 255))
    d.ellipse([2, 2, 13, 13], outline=(112, 118, 126, 255))
    d.ellipse([4, 4, 11, 11], fill=(70, 74, 82, 255))
    d.ellipse([5, 5, 10, 10], outline=(150, 156, 164, 255))
    d.ellipse([6, 6, 9, 9], fill=(255, 236, 168, 255))
    d.point((7, 7), fill=(255, 255, 245, 255))
    # 四向辐条（阵列支撑）
    for p in ((7, 2), (7, 13), (2, 7), (13, 7)):
        d.point(p, fill=(190, 150, 70, 255))


# 机器 id 列表（顺序固定：CU/KU/MU/LU/QU/SC）
MACHINE_IDS = ['cryogenic_condenser', 'pneumatic_compressor',
               'magnetic_coil', 'laser_focus', 'quantum_condenser',
               'solar_concentrator']
FRONT_FNS = [front_cryo, front_pneumatic, front_magnetic, front_laser, front_quantum,
             front_concentrator]

# 生成 5 张共享正面面板（无电压前缀，模型 texture 引用这个）
for mid, front_fn in zip(MACHINE_IDS, FRONT_FNS):
    front_texture(mid, front_fn)

# 生成 75 个模型 JSON：外壳纹理引用 GT 电压机械方块，正面引用 GTOE 共享面板
count = 0
for mid in MACHINE_IDS:
    for vname in VOLTAGE_NAMES:
        rid = vname + '_' + mid
        model = {
            'parent': 'minecraft:block/block',
            'textures': {
                'top': 'gtceu:block/casings/voltage/%s/top' % vname,
                'bottom': 'gtceu:block/casings/voltage/%s/bottom' % vname,
                'side': 'gtceu:block/casings/voltage/%s/side' % vname,
                'front': 'gtoecore:block/energy/%s_front' % mid,
                'particle': 'gtceu:block/casings/voltage/%s/side' % vname,
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
        with open(os.path.join(MODEL_DIR, rid + '.json'), 'w', encoding='utf-8') as fp:
            json.dump(model, fp, indent=2)
        # blockstate：NON_Y_AXIS = 4 facing 变体
        variants = {}
        for f in FACINGS:
            entry = {'model': 'gtoecore:block/machine/' + rid}
            if ROT[f]:
                entry['y'] = ROT[f]
            variants['facing=' + f] = entry
        with open(os.path.join(BS_DIR, rid + '.json'), 'w', encoding='utf-8') as fp:
            json.dump({'variants': variants}, fp, indent=2)
        # item 模型
        with open(os.path.join(ITEM_DIR, rid + '.json'), 'w', encoding='utf-8') as fp:
            json.dump({'parent': 'gtoecore:block/machine/' + rid}, fp, indent=2)
        count += 1
    print('models written:', mid, 'x', len(VOLTAGE_NAMES))

# ---------------- 高级能量流体贴图（32x32 still/flow） ----------------

def fluid_texture(name, rgb, seed):
    rng = random.Random(seed)
    im = Image.new('RGBA', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            n = rng.random()
            r = ((x - 16) ** 2 + (y - 16) ** 2) ** 0.5
            glow = max(0.0, 1.0 - r / 18.0)
            v = 0.55 + 0.45 * n
            c = tuple(min(255, int(c * (0.5 + 0.6 * glow) * v)) for c in rgb) + (int(200 + 55 * glow),)
            px[x, y] = c
    im.save(os.path.join(TEX_FLUID, name + '_still.png'))
    im2 = Image.new('RGBA', (32, 32))
    px2 = im2.load()
    for y in range(32):
        band = rng.random()
        for x in range(32):
            n = rng.random() * 0.4 + band * 0.6
            v = 0.5 + 0.5 * n
            c = tuple(min(255, int(c * v)) for c in rgb) + (225,)
            px2[x, y] = c
    im2.save(os.path.join(TEX_FLUID, name + '_flow.png'))
    print('fluid textures written:', name)


fluid_texture('laser_energy', (48, 210, 215), 1)
fluid_texture('quantum_energy', (168, 84, 220), 2)
print('done, total machines =', count)