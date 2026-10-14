# -*- coding: utf-8 -*-
"""生成 GT 管道风格导体的 blockstate（multipart）+ 模型 JSON。

对应 GTOConduitBlock 的六方向连接体系：
  blockstates/<name>.json            multipart：中心体 + 6 方向连接段按位拼装
  models/block/energy/<name>_center.json   中心接头（6..10 小方块）
  models/block/energy/<name>_stub.json     管段（默认指北，含外端喇叭口）
  models/block/energy/<name>_inventory.json 物品栏展示（中心体 + 全 6 向管段）
"""
import json
import os

A = os.path.join(os.path.dirname(__file__), "..",
                 "src", "main", "resources", "assets", "gtoecore")
# 六种导体：原四 + 激光管（直线不能拐弯）+ 量子管（荧光）
TYPES = ["rotation_axle", "heat_pipe", "cold_pipe", "kinetic_rod",
         "laser_pipe", "quantum_pipe"]
# 有荧光变体的（blockstate 里叠加 active=true 的发光子模型）
ACTIVE_TYPES = {"quantum_pipe", "laser_pipe"}


def tex(name):
    # 激光管：直接引用 GT 官方标准激光管道贴图（外观完全对齐）
    if name == "laser_pipe":
        return {
            "side": "gtceu:block/pipe/pipe_laser_side",
            "end": "gtceu:block/pipe/pipe_laser_in",
            "particle": "gtceu:block/pipe/pipe_laser_side",
        }
    return {
        "side": f"gtoecore:block/energy/{name}_side",
        "end": f"gtoecore:block/energy/{name}_end",
        "particle": f"gtoecore:block/energy/{name}_side",
    }


# 贴图取区（16×16 像素坐标系）：
#  - 杆身贴图 _side：中央 x=6..9 是材质色条（4px），所有侧面统一取它 → 管身均匀无斑驳
#  - 端面贴图 _end：中央 5..11 是金属接头（用于 4px/6px 宽的短面）
# ⚠ 只有"全图同材质"的老四管（热/冷/动/旧轴）才裁剪；新风格贴图
#   （GT 激光管壳+芯、Mek 量子管壳+芯、Create 金色轴）必须整张平铺
#   否则壳被裁掉只剩色带 → 显示错误。用 FULL_TEXTURES 集合关裁剪。
UV_SIDE = [6, 0, 10, 16]   # 杆身长面：中央竖条
UV_END = [5, 5, 11, 11]    # 端面接头

# 整张贴图平铺的类型（壳+芯/花纹贴图，裁剪会丢壳）
FULL_TEXTURES = {"laser_pipe", "quantum_pipe", "rotation_axle"}


def el(box, faces, name=None):
    """faces: {面名: (贴图键, cullface)}；自动补 uv（除非是整铺类型）。"""
    e = {"from": box[0], "to": box[1], "faces": {}}
    full = name in FULL_TEXTURES
    for f, (u, cull) in faces.items():
        face = {"texture": u}
        if not full:
            if u == "#end":
                face["uv"] = UV_END
            elif u == "#side":
                face["uv"] = UV_SIDE
        if cull:
            face["cullface"] = cull
        e["faces"][f] = face
    return e


# 管段（默认指北：z=0 外端）。端帽占 z 0..1，杆身从 z=1 起
# ⚠ 杆身绝不能也到 z=0 —— 否则与端帽的 north 面共面同朝向 → z-fighting 闪烁杂色
# ⚠⚠ multipart 模型铁律：端帽朝外面的 cullface 必须为 None！
#    multipart 是"每连接位一个子模型"，若写 cullface: north，旁边有方块
#    （包括另一节管道）时该面被判遮挡剔除 → 外凸喇叭口环"消失"。
#    原版栅栏/墙的所有面都没有 cullface，照抄。
def stub_elements(name=None):
    # 旋转轴（起重机杆风格）：端帽加大成 6x6 轴套 + 中段轴环（每 3px 一个）
    if name == "rotation_axle":
        out = [
            el([[6, 6, 1], [10, 10, 6]], {
                "north": ("#side", None),
                "south": ("#side", None),
                "up": ("#side", None),
                "down": ("#side", None),
                "east": ("#side", None),
                "west": ("#side", None),
            }, name),
            # 轴套：6x6x2 突缘（比 4x4 杆身粗一圈）
            el([[5, 5, 0], [11, 11, 2]], {
                "north": ("#end", None),
                "south": ("#side", None),
                "up": ("#side", None),
                "down": ("#side", None),
                "east": ("#side", None),
                "west": ("#side", None),
            }, name),
        ]
        return out
    return [
        el([[6, 6, 1], [10, 10, 6]], {
            "north": ("#side", None),
            "south": ("#side", None),
            "up": ("#side", None),
            "down": ("#side", None),
            "east": ("#side", None),
            "west": ("#side", None),
        }, name),
        el([[5, 5, 0], [11, 11, 1]], {
            "north": ("#end", None),   # 不能用 cullface，见上方铁律
            "up": ("#side", None),
            "down": ("#side", None),
            "east": ("#side", None),
            "west": ("#side", None),
        }, name),
    ]


def center_elements(name=None):
    return [
        el([[6, 6, 6], [10, 10, 10]], {
            "north": ("#side", None),
            "south": ("#side", None),
            "up": ("#side", None),
            "down": ("#side", None),
            "east": ("#side", None),
            "west": ("#side", None),
        }, name),
    ]


def inventory_elements(name=None):
    """中心体 + 全 6 向管段（管段按 blockstate 同款旋转矩阵手工换算）。"""
    elems = list(center_elements(name))
    n = stub_elements(name)
    # north：原样
    elems += n
    # south（y=180）：x -> 16-x, z -> 16-z
    elems += [mirror(e, sx=True, sz=True) for e in n]
    # west（y=270）：north->west： (x,z) -> (z, 16-x)
    elems += [rot_y(e, deg=270) for e in n]
    # east（y=90）：(x,z) -> (16-z, x)
    elems += [rot_y(e, deg=90) for e in n]
    # up（x=270）：north->up： (y,z) -> (z, 16-y)
    elems += [rot_x(e, deg=270) for e in n]
    # down（x=90）：(y,z) -> (16-z, y)
    elems += [rot_x(e, deg=90) for e in n]
    return elems


def mirror(e, sx=False, sy=False, sz=False):
    def m(v):
        return [16 - v[0] if sx else v[0],
                16 - v[1] if sy else v[1],
                16 - v[2] if sz else v[2]]
    out = {"from": m(e["from"]), "to": m(e["to"]),
           "faces": {k: dict(v) for k, v in e["faces"].items()}}
    # 镜像后 north/south 互换、east/west 互换，uv 简单起见沿用
    f = out["faces"]
    if sx:
        f["north"], f["south"] = f.get("south"), f.get("north")
        f["east"], f["west"] = f.get("west"), f.get("east")
        f = {k: v for k, v in f.items() if v is not None}
        out["faces"] = f
    return normalize(out)


def rot_y(e, deg):
    """绕 Y 轴旋转（90 度步进，每步 north->east，同 blockstate y 旋转）。"""
    m = {"north": "east", "east": "south", "south": "west", "west": "north"}
    def r(v):
        x, y, z = v
        return [16 - z, y, x]
    out = e
    for _ in range(int(deg) // 90 % 4):
        out = {"from": r(out["from"]), "to": r(out["to"]),
               "faces": {m.get(k, k): dict(v) for k, v in out["faces"].items()}}
    return normalize(out)


def rot_x(e, deg):
    """绕 X 轴旋转（90 度步进，每步 north->down，同 blockstate x 旋转）。"""
    m = {"north": "down", "down": "south", "south": "up", "up": "north"}
    def r(v):
        x, y, z = v
        return [x, z, 16 - y]
    out = e
    for _ in range(int(deg) // 90 % 4):
        out = {"from": r(out["from"]), "to": r(out["to"]),
               "faces": {m.get(k, k): dict(v) for k, v in out["faces"].items()}}
    return normalize(out)


def normalize(e):
    """from/to 逐轴归一化（旋转/镜像后可能出现 from>to，原版会判非法元素）。"""
    f, t = list(e["from"]), list(e["to"])
    for i in range(3):
        if f[i] > t[i]:
            f[i], t[i] = t[i], f[i]
    e = dict(e)
    e["from"], e["to"] = f, t
    return e


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
    print("wrote", os.path.relpath(path, A))


def main():
    bs = os.path.join(A, "blockstates")
    md = os.path.join(A, "models", "block")
    it = os.path.join(A, "models", "item")

    for name in TYPES:
        # multipart blockstate
        parts = [{"apply": {"model": f"gtoecore:block/energy/{name}_center"}}]
        for when, rot in [
            ({"north": "true"}, {}),
            ({"south": "true"}, {"y": 180}),
            ({"west": "true"}, {"y": 270}),
            ({"east": "true"}, {"y": 90}),
            ({"up": "true"}, {"x": 270}),
            ({"down": "true"}, {"x": 90}),
        ]:
            apply_ = {"model": f"gtoecore:block/energy/{name}_stub"}
            apply_.update(rot)
            parts.append({"when": when, "apply": apply_})
        # 荧光变体：lit=true 时叠发光子模型（同款位置，亮贴图）
        # ⚠ Java 侧 ACTIVE = BlockStateProperties.LIT（属性名 "lit"），JSON when 必须写 lit！
        if name in ACTIVE_TYPES:
            for when, rot in [
                ({"lit": "true"}, {}),
            ]:
                apply_ = {"model": f"gtoecore:block/energy/{name}_center_active"}
                parts.append({"when": when, "apply": apply_})
            for when, rot in [
                ({"lit": "true", "north": "true"}, {}),
                ({"lit": "true", "south": "true"}, {"y": 180}),
                ({"lit": "true", "west": "true"}, {"y": 270}),
                ({"lit": "true", "east": "true"}, {"y": 90}),
                ({"lit": "true", "up": "true"}, {"x": 270}),
                ({"lit": "true", "down": "true"}, {"x": 90}),
            ]:
                apply_ = {"model": f"gtoecore:block/energy/{name}_stub_active"}
                apply_.update(rot)
                parts.append({"when": when, "apply": apply_})
        write(os.path.join(bs, f"{name}.json"), {"multipart": parts})

        # models
        base = {"parent": "minecraft:block/block",
                "textures": tex(name),
                "elements": center_elements(name)}
        write(os.path.join(md, "energy", f"{name}_center.json"), base)

        stub = {"parent": "minecraft:block/block",
                "textures": tex(name),
                "elements": stub_elements(name)}
        write(os.path.join(md, "energy", f"{name}_stub.json"), stub)

        inv = {"parent": "minecraft:block/block",
               "textures": tex(name),
               "elements": inventory_elements(name)}
        write(os.path.join(md, "energy", f"{name}_inventory.json"), inv)

        # 荧光变体模型（复用同元素，换 active 贴图）
        if name in ACTIVE_TYPES:
            if name == "laser_pipe":
                # 激光管：active = GT 官方 emissive overlay（发光）
                tex_active = {
                    "side": "gtceu:block/pipe/pipe_laser_side_overlay_emissive",
                    "end": "gtceu:block/pipe/pipe_laser_in",
                    "particle": "gtceu:block/pipe/pipe_laser_side_overlay_emissive",
                }
            else:
                tex_active = {
                    "side": f"gtoecore:block/energy/{name}_side_active",
                    "end": f"gtoecore:block/energy/{name}_end_active",
                    "particle": f"gtoecore:block/energy/{name}_side_active",
                }
            base_a = {"parent": "minecraft:block/block",
                      "textures": tex_active,
                      "elements": center_elements(name)}
            write(os.path.join(md, "energy", f"{name}_center_active.json"), base_a)
            stub_a = {"parent": "minecraft:block/block",
                      "textures": tex_active,
                      "elements": stub_elements(name)}
            write(os.path.join(md, "energy", f"{name}_stub_active.json"), stub_a)

        # item model -> inventory
        write(os.path.join(it, f"{name}.json"),
              {"parent": f"gtoecore:block/energy/{name}_inventory"})

    # 清理旧的单文件杆模型（已被 multipart 取代）
    for name in TYPES:
        p = os.path.join(md, f"{name}.json")
        if os.path.exists(p):
            os.remove(p)
            print("removed legacy", os.path.relpath(p, A))


if __name__ == "__main__":
    main()
