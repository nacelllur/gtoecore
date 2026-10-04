#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gtoecore 资源 / 注册一致性校验器  —— 新增方块或物品后【必跑】

用法:
    <python> tools/validate_assets.py

它把"造一点修一点"最容易踩的坑一次全查出来（编译前就能发现，不用进游戏）:

  [A] blockstate 覆盖: 每个 GTNBlocks 注册的方块都有 blockstates/<name>.json
  [B] 模型引用:        blockstate 引用的每个 model 文件真实存在
  [C] 模型递归解析:    parent 链 + textures 键 + faces 的 #引用，
                       最终每个 gtoecore 贴图 .png 存在（跨 GTM jar 解析）
  [D] item 模型:       每个 GTNItems 注册的物品都有 models/item/<name>.json
  [E] lang:            zh_cn / en_us 都有对应名称
  [F] 创造栏:          GTNCoreGT 的 displayItems 覆盖 GTNItems 全部物品字段
  [G] 变体合法性:      含 upwards_facing 的机器只能是 4x4=16 变体（不许 facing=up/down）

退出码 0 = 无 ERROR，1 = 有 ERROR。
"""
import glob
import json
import os
import re
import sys
import zipfile

WORK = os.environ.get(
    "GTO_WORK", r"C:\Users\23737\WorkBuddy\2026-09-21-20-07-18\gtn-core")
MODS_DIR = os.environ.get("GTO_MODS", r"D:\GT-New\mods")
MODID = "gtoecore"

ASSETS = os.path.join(WORK, "src", "main", "resources", "assets", MODID)
JAVA_DIR = os.path.join(WORK, "src", "main", "java", "com", "gtoecore")

ERRORS, WARNS, INFOS = [], [], []


def err(m):
    ERRORS.append(m)


def warn(m):
    WARNS.append(m)


def info(m):
    INFOS.append(m)


# ---------------------------------------------------------------- 资源访问
_JARS = {}


def _jar(path):
    if path not in _JARS:
        _JARS[path] = zipfile.ZipFile(path)
    return _JARS[path]


JAR_ROOTS = {}


def _init_jars():
    cands = sorted(glob.glob(os.path.join(MODS_DIR, "gtceu-*.jar")))
    if cands:
        JAR_ROOTS["gtceu"] = cands[-1]
    else:
        warn("未找到 gtceu-*.jar，GTM 资源无法交叉校验（仅本地资源会检查）")


def split_ref(ref):
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = "minecraft", ref
    return ns, path


def read_asset(ns, kind, rel):
    """rel 为含扩展名的相对路径，如 'block/energy/heat_pipe_side.png'"""
    if ns == MODID:
        fp = os.path.join(ASSETS, kind, *rel.split("/"))
        if os.path.isfile(fp):
            return open(fp, "rb").read()
        return None
    jp = JAR_ROOTS.get(ns)
    if jp:
        try:
            return _jar(jp).read("assets/%s/%s/%s" % (ns, kind, rel))
        except KeyError:
            return None
    return None


def asset_exists(ns, kind, rel):
    if ns == "minecraft":
        return True          # 原版资源不做存在性校验
    if ns == MODID:
        return os.path.isfile(os.path.join(ASSETS, kind, *rel.split("/")))
    jp = JAR_ROOTS.get(ns)
    if jp:
        try:
            _jar(jp).getinfo("assets/%s/%s/%s" % (ns, kind, rel))
            return True
        except KeyError:
            return False
    return True              # 未知外部 mod：不报错


# ---------------------------------------------------------------- 模型解析
def resolve_tex_val(val, ctx, depth=0):
    if not isinstance(val, str) or depth > 8:
        return None
    if val.startswith("#"):
        nxt = ctx.get(val[1:])
        if nxt is None:
            return None
        return resolve_tex_val(nxt, ctx, depth + 1)
    return val


def gather_chain(node, seen, out):
    """收集 parent 链（child -> root），out 追加每层 data dict。
    外部读不到的模型（如 minecraft 模板）跳过不断链，父层上下文保持。"""
    if "ref" in node:
        ref = node["ref"]
        if ref in seen:
            return
        seen.add(ref)
        ns, path = split_ref(ref)
        raw = read_asset(ns, "models", path + ".json")
        if raw is None:
            return
        try:
            data = json.loads(raw.decode("utf-8"))
        except Exception:                                     # noqa: BLE001
            return
    else:
        data = node["inline"]
    out.append(data)
    parent = data.get("parent")
    if parent:
        gather_chain({"ref": parent}, seen, out)


def check_model(node, tex_ctx, seen, trail, needed):
    """解析一个模型（ref 或 inline）。按 MC 语义：
    parent 链逐层合并 textures，child 覆盖 parent；合并完成后统一解析所有面的 #ref。"""
    chain = []
    gather_chain(node, seen, chain)          # child -> root

    if not chain:
        # 完全读不到：只对 gtoecore 自己报错
        if node.get("ref", "").startswith(MODID + ":"):
            err("模型文件缺失: %s   (来自 %s)" % (node["ref"], trail))
        return tex_ctx

    merged = dict(tex_ctx)
    for data in reversed(chain):             # root -> child（child 覆盖）
        merged.update(data.get("textures") or {})

    for data in chain:
        ref = data.get("__ref") or "<inline>"
        for el in data.get("elements") or []:
            for fname, face in (el.get("faces") or {}).items():
                t = face.get("texture")
                rv = resolve_tex_val(t, merged)
                if rv is None:
                    if isinstance(t, str) and t.startswith("#"):
                        warn("贴图键无法解析: %s face=%s   (%s)" % (t, fname, ref))
                    else:
                        warn("贴图引用为空: %s face=%s   (%s)" % (t, fname, ref))
                else:
                    needed.add(rv)

        # GTM machine loader 的嵌套 variants（inline dict 已在链内，这里只处理 ref 型）
        for vname, v in (data.get("variants") or {}).items():
            if not isinstance(v, dict):
                continue
            mm = v.get("model")
            if isinstance(mm, str):
                check_model({"ref": mm}, dict(merged), seen,
                            "%s -> variant %s" % (ref, vname), needed)
            elif isinstance(mm, dict):
                check_model({"inline": mm}, dict(merged), seen,
                            "%s -> variant %s" % (ref, vname), needed)

    return merged


def bs_model_refs(bs):
    """从 blockstate 里收集所有模型引用（字符串或内联 dict）"""
    out = []

    def add(v):
        if v is None:
            return
        if isinstance(v, list):
            for x in v:
                add(x)
        elif isinstance(v, dict):
            m = v.get("model")
            if isinstance(m, str):
                out.append({"ref": m})
            elif isinstance(m, dict):
                out.append({"inline": m})

    for v in (bs.get("variants") or {}).values():
        add(v)
    # multipart（单数键名）与 multiparts 都兼容
    for part in (bs.get("multipart") or bs.get("multiparts") or []):
        if isinstance(part, dict):
            add(part.get("apply"))
    return out


# ---------------------------------------------------------------- Java 侧
def read_java(name):
    fp = os.path.join(JAVA_DIR, name)
    return open(fp, encoding="utf-8").read() if os.path.isfile(fp) else ""


def main():
    _init_jars()
    print("=" * 68)
    print("gtoecore 资源校验   assets=%s" % ASSETS)
    print("=" * 68)

    java_blocks = read_java("GTNBlocks.java")
    java_items = read_java("GTNItems.java")
    java_tab = read_java(os.path.join("gt", "GTNCoreGT.java"))

    block_names = sorted(set(re.findall(r'BLOCKS\.register\("([a-z0-9_]+)"', java_blocks)))
    item_names = sorted(set(re.findall(r'ITEMS\.register\("([a-z0-9_]+)"', java_items)))
    dynamic = sorted(set(re.findall(r'ITEMS\.register\("([a-z0-9_]+)"\s*\+', java_items)))
    # 动态物品前缀（后面拼 + 电压名）不是字面注册名，剔除
    item_names = [n for n in item_names if n not in dynamic]

    # 1.20.1 GTValues 电压名小写（与 GTNItems.java 静态块一致）
    DRONE_VN = ["lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv",
                "uev", "uiv", "uxv", "opv", "max"]

    print("\n[A] blockstate 覆盖  (GTNBlocks 注册 %d 个方块)" % len(block_names))
    bs_dir = os.path.join(ASSETS, "blockstates")
    bs_files = {f[:-5] for f in os.listdir(bs_dir)} if os.path.isdir(bs_dir) else set()
    for n in block_names:
        if n in bs_files:
            print("    OK   %s" % n)
        else:
            err("缺少 blockstate: blockstates/%s.json" % n)

    print("\n[B/C] 模型引用与贴图递归解析  (blockstates %d 个)" % len(bs_files))
    needed = set()
    for name in sorted(bs_files):
        raw = read_asset(MODID, "blockstates", name + ".json")
        if raw is None:
            err("blockstate 读取失败: %s" % name)
            continue
        try:
            bs = json.loads(raw.decode("utf-8"))
        except Exception as e:                                    # noqa: BLE001
            err("blockstate JSON 解析失败: %s -> %s" % (name, e))
            continue
        refs = bs_model_refs(bs)
        if not refs:
            err("blockstate 无任何模型引用: %s" % name)
        for r in refs:
            seen = set()
            check_model(r, {}, seen, "blockstates/%s.json" % name, needed)

    miss_tex = 0
    for t in sorted(needed):
        ns, path = split_ref(t)
        if not asset_exists(ns, "textures", path + ".png"):
            miss_tex += 1
            if ns == MODID:
                err("贴图缺失: %s.png" % t)
            else:
                warn("外部贴图缺失: %s.png" % t)
    print("    引用贴图 %d 个，其中缺失 %d 个" % (len(needed), miss_tex))

    print("\n[D] item 模型  (GTNItems 注册 %d 个物品%s)"
          % (len(item_names),
             ("，动态注册 %d 组：[%s]" % (len(dynamic), ",".join(dynamic))) if dynamic else ""))
    item_dir = os.path.join(ASSETS, "models", "item")
    for n in item_names:
        fp = os.path.join(item_dir, n + ".json")
        if not os.path.isfile(fp):
            err("缺少 item 模型: models/item/%s.json" % n)
    for d in dynamic:
        cnt = 0
        for v in DRONE_VN:
            if os.path.isfile(os.path.join(item_dir, d + v + ".json")):
                cnt += 1
        if cnt == 0:
            err("动态物品缺少 item 模型: models/item/%s<tier>.json" % d)
        else:
            print("    动态组 %s 匹配到 %d 个等级模型" % (d, cnt))
    print("    本地 item 模型文件 %d 个" % (len(os.listdir(item_dir))
                                            if os.path.isdir(item_dir) else 0))

    print("\n[E] lang 名称")
    langs = {}
    for lf in ("zh_cn", "en_us"):
        raw = read_asset(MODID, "lang", lf + ".json")
        if raw is None:
            err("lang 文件缺失: %s.json" % lf)
            langs[lf] = {}
        else:
            try:
                langs[lf] = json.loads(raw.decode("utf-8"))
            except Exception as e:                                    # noqa: BLE001
                err("lang JSON 解析失败 %s.json: %s" % (lf, e))
                langs[lf] = {}
    for n in block_names:
        if not any(("block.%s.%s" % (MODID, n)) in langs[lf] for lf in langs):
            err("lang 缺少方块名: block.%s.%s" % (MODID, n))
    for n in item_names:
        if any(("block.%s.%s" % (MODID, n)) in langs[lf] for lf in langs):
            continue
        if not all(("item.%s.%s" % (MODID, n)) in langs[lf] for lf in langs):
            err("lang 缺少物品名: item.%s.%s" % (MODID, n))
    for d in dynamic:
        missing = [v for v in DRONE_VN
                   if not all(("item.%s.%s%s" % (MODID, d, v)) in langs[lf]
                              for lf in langs)]
        if missing:
            err("lang 缺少动态物品名（%s<tier>，缺 %d 个，如 %s）: %s"
                % (d, len(missing), missing[0], d))
    if "itemGroup.%s" % MODID not in langs["zh_cn"]:
        err("lang 缺少创造栏标题: itemGroup.%s" % MODID)
    print("    zh_cn %d 条 / en_us %d 条" % (len(langs["zh_cn"]), len(langs["en_us"])))

    print("\n[F] 创造栏注入")
    fields = set(re.findall(r'RegistryObject<Item>(?:\[\])?\s+([A-Z][A-Z0-9_]*)\s*[;=]',
                            java_items))
    for f in sorted(fields):
        if ("GTNItems.%s" % f) not in java_tab:
            err("创造栏未注入物品字段: GTNItems.%s" % f)
    print("    GTNItems 物品字段 %d 个，创造栏引用 %d 个"
          % (len(fields), sum(1 for f in fields if ("GTNItems.%s" % f) in java_tab)))

    print("\n[G] blockstate 变体合法性")
    for name in sorted(bs_files):
        raw = read_asset(MODID, "blockstates", name + ".json")
        if raw is None:
            continue
        bs = json.loads(raw.decode("utf-8"))
        keys = list((bs.get("variants") or {}).keys())
        n = len(keys)
        if not n:
            continue
        facing_vals, up_vals = set(), set()
        for k in keys:
            kv = {x.split("=")[0]: x.split("=")[1] for x in k.split(",") if "=" in x}
            if "facing" in kv:
                facing_vals.add(kv["facing"].lower())
            if "upwards_facing" in kv:
                up_vals.add(kv["upwards_facing"].lower())
        # 含 upwards_facing 的机器：
        #   NON_Y_AXIS -> facing 4 值 x upwards 4 值 = 16
        #   ALL        -> facing 6 值 x upwards 4 值 = 24
        # 两者都不许出现"只有 facing 没有配对"的残缺组合
        if up_vals:
            if len(up_vals) != 4:
                err("upwards_facing 值数应为 4，实际 %d: %s"
                    % (len(up_vals), name))
            if facing_vals in ({"north", "south", "west", "east"},) and n != 16:
                err("NON_Y_AXIS 型机器变体数应为 16，实际 %d: %s" % (n, name))
            elif facing_vals in ({"north", "south", "east", "west", "up", "down"},) \
                    and n != 24:
                err("ALL 型机器变体数应为 24，实际 %d: %s" % (n, name))
            elif n != len(facing_vals) * len(up_vals):
                err("变体组合数不匹配: %d 变体 vs %d facing x %d upwards: %s"
                    % (n, len(facing_vals), len(up_vals), name))
        else:
            # 无 upwards_facing：普通方向方块 / 单方块机器
            # GTM 单方块机器（如能量仓）是 RotationState.ALL → facing 6 值（含 up/down）合法；
            # 只有"4 水平 + 零星 up 或 down"才是 HORIZONTAL 型疑误。
            all6 = facing_vals == {"north", "south", "west", "east", "up", "down"}
            bad = [k for k in keys
                   if re.search(r'(^|,)facing=(up|down)(,|$)', k)]
            if bad and not all6:
                err("无 upwards_facing 但含 facing=up/down（HORIZONTAL 型疑误）: %s (%d 个)"
                    % (name, len(bad)))

    print("\n[H] GTM 机器贴图命名空间（本项目血泪坑）")
    mach_dir = os.path.join(ASSETS, "models", "block", "machine")
    mach_files = sorted(glob.glob(os.path.join(mach_dir, "*.json"))) \
        if os.path.isdir(mach_dir) else []
    bad_ns = 0
    for fp in mach_files:
        d = json.load(open(fp, encoding="utf-8"))
        hits = set()

        def walk(o):
            if isinstance(o, dict):
                for k, v in o.items():
                    if k in ("textures", "texture_overrides") and isinstance(v, dict):
                        for tv in v.values():
                            if isinstance(tv, str) and ":" in tv:
                                hits.add(tv)
                    walk(v)
            elif isinstance(o, list):
                for x in o:
                    walk(x)

        walk(d)
        # GTM machine loader 不解析 minecraft: 贴图 -> 游戏里紫黑 missing 格
        mine = sorted(x for x in hits
                      if x.startswith("minecraft:block/")
                      and not x.endswith("block/block"))
        if mine:
            bad_ns += 1
            err("机器模型引用 minecraft: 贴图（GTM loader 不解析 => 紫黑 missing 格）: %s -> %s"
                % (os.path.basename(fp), mine))
    # Java 侧 workableCasingModel(new ResourceLocation("minecraft", ...)) 同理
    java_mach = read_java(os.path.join("gt", "GTNMachines.java"))
    for m in re.finditer(r'workableCasingModel\(\s*new ResourceLocation\(\s*"(\w+)"\s*,\s*"([^"]+)"',
                         java_mach):
        if m.group(1) == "minecraft":
            err("workableCasingModel 用 minecraft: 贴图（紫黑 missing 格）: %s" % m.group(2))
            bad_ns += 1
    print("    机器模型 %d 个，命名空间问题 %d 处" % (len(mach_files), bad_ns))

    # ---------------------------------------------------------- 汇总
    print("\n" + "=" * 68)
    if WARNS:
        print("WARN (%d):" % len(WARNS))
        for w in WARNS:
            print("  ! " + w)
    if INFOS:
        print("INFO (%d):" % len(INFOS))
        for i in INFOS[:15]:
            print("  - " + i)
        if len(INFOS) > 15:
            print("  ... 其余 %d 条省略" % (len(INFOS) - 15))
    print("ERROR: %d    WARN: %d    INFO: %d" % (len(ERRORS), len(WARNS), len(INFOS)))
    if ERRORS:
        print("-" * 68)
        for e in ERRORS:
            print("  X " + e)
        return 1
    print("资源一致性检查通过 ✔")
    return 0


if __name__ == "__main__":
    sys.exit(main())