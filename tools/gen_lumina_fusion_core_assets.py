# -*- coding: utf-8 -*-
"""生成聚变光源核心（luv/zpm/uv_lumina_fusion_core）的 blockstate + 模型 + 物品模型。

照抄 GT 官方 luv_fusion_reactor 的格式：
- blockstate：24 个 facing/upwards_facing 变体（含 gtceu:z 旋转）
- 模型：gtceu:machine loader + texture_overrides(fusion casing) + 8 个
  is_formed/recipe_logic_status 变体 + dynamic_renders(fusion_ring)
- 覆层贴图直接引用 GT 官方 fusion overlay（外观与 GT 聚变同款，不紫黑）
"""
import json, os

A = os.path.join(os.path.dirname(__file__), "..",
                 "src", "main", "resources", "assets", "gtoecore")

TIERS = ["luv", "zpm", "uv"]
# 每级对应 GT fusion casing 贴图
CASING = {"luv": "gtceu:block/casings/fusion/fusion_casing",
          "zpm": "gtceu:block/casings/fusion/fusion_casing_mk2",
          "uv": "gtceu:block/casings/fusion/fusion_casing_mk3"}

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
    print("wrote", os.path.relpath(path, A))

# ---------------- blockstate：24 变体 ----------------
def blockstate(id_):
    model = f"gtoecore:block/machine/{id_}"
    variants = {}
    for facing in ("down", "east", "north", "south", "up", "west"):
        for upf in ("east", "north", "south", "west"):
            key = f"facing={facing},upwards_facing={upf}"
            v = {"model": model}
            # x/y/z 旋转（照抄 GT）
            if facing == "down":
                v["x"] = 90
                zrot = {"east": 90, "north": 0, "south": 180, "west": 270}[upf]
                if zrot:
                    v["gtceu:z"] = zrot
            elif facing == "up":
                v["x"] = 270
                zrot = {"east": 90, "north": 180, "south": 0, "west": 270}[upf]
                if zrot:
                    v["gtceu:z"] = zrot
            else:
                yrot = {"north": 0, "east": 90, "south": 180, "west": 270}[facing]
                if yrot:
                    v["y"] = yrot
                zrot = {"east": 270, "north": 0, "south": 180, "west": 90}[upf]
                if zrot:
                    v["gtceu:z"] = zrot
            variants[key] = v
    return {"variants": variants}

# ---------------- 模型：gtceu:machine loader ----------------
def model(id_, casing_tex):
    def base(active):
        overlay = ("gtceu:block/multiblock/fusion_reactor/overlay_front_active"
                   if active else "gtceu:block/multiblock/fusion_reactor/overlay_front")
        emissive = ("gtceu:block/multiblock/fusion_reactor/overlay_front_active_emissive"
                    if active else "gtceu:block/multiblock/fusion_reactor/overlay_front_emissive")
        return {
            "parent": "gtceu:block/machine/template/cube_all/sided",
            "textures": {
                "all": casing_tex,
                "overlay_front": overlay,
                "overlay_front_emissive": emissive,
            }
        }
    status_variants = {}
    for formed in ("false", "true"):
        for status in ("idle", "suspend", "waiting", "working"):
            status_variants[f"is_formed={formed},recipe_logic_status={status}"] = {
                "model": base(status in ("waiting", "working"))
            }
    return {
        "parent": "minecraft:block/block",
        "dynamic_renders": [{"type": "gtceu:fusion_ring"}],
        "loader": "gtceu:machine",
        "machine": f"gtoecore:{id_}",
        "texture_overrides": {"all": casing_tex},
        "variants": status_variants,
    }

def main():
    for t in TIERS:
        id_ = f"{t}_lumina_fusion_core"
        write(os.path.join(A, "blockstates", f"{id_}.json"), blockstate(id_))
        write(os.path.join(A, "models", "block", "machine", f"{id_}.json"),
              model(id_, CASING[t]))
        write(os.path.join(A, "models", "item", f"{id_}.json"),
              {"parent": f"gtoecore:block/machine/{id_}"})
        print("   ->", id_)

if __name__ == "__main__":
    main()
