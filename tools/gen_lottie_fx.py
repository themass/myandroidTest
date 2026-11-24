#!/usr/bin/env python3
"""Generate the stage-fx Lottie JSONs (repo-owned, self-authored).

资产口径（lottie-fx-plan-20261123.md §3）：透明背景、无 AE 表达式、无内嵌位图、
无 matte、单文件 <=100KB、循环类首尾同态。全部由本脚本生成——许可=仓库自制，
不引第三方素材，商用无障碍（见 feature-companion/src/main/assets/fx/README.md）。

用法:
    python3 -I tools/gen_lottie_fx.py [asset ...]     # 全部或指定几条
生成到 feature-companion/src/main/assets/fx/。
"""
import json
import math
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), "..",
                   "feature-companion", "src", "main", "assets", "fx")
FPS = 30


# ---- Lottie 构件小工具 ----------------------------------------------------

def static(value, dim=1):
    return {"a": 0, "k": [value] * dim if dim > 1 and not isinstance(value, list) else value}


def anim(times, values, dim=1, ease=1 / 3.0):
    """AE 风格关键帧（Lottie v5）。ease 为贝塞尔手柄归一化位置。"""
    kfs = []
    n = len(times)
    for idx, t in enumerate(times):
        v = values[idx]
        if not isinstance(v, list):
            v = [v] * dim
        kf = {"s": v, "t": t}
        if idx < n - 1:
            kf["o"] = {"x": [ease] * dim, "y": [0.0] * dim}
        if idx > 0:
            kf["i"] = {"x": [1.0 - ease] * dim, "y": [1.0] * dim}
        kfs.append(kf)
    return {"a": 1, "k": kfs}


def transform(p, o=None, s=None, r=None, a=None):
    return {
        "o": o if o is not None else static(100),
        "r": r if r is not None else static(0),
        "p": p if p is not None else static([0, 0], 2),
        "a": a if a is not None else static([0, 0], 2),
        "s": s if s is not None else static([100, 100], 2),
    }


def shape_layer(name, shapes, ks, ip, op, ind=1):
    return {
        "ddd": 0, "ind": ind, "ty": 4, "nm": name, "sr": 1,
        "ks": ks, "ao": 0, "shapes": shapes, "ip": ip, "op": op, "st": 0,
    }


def path_shape(vertices, in_tan, out_tan, closed=True):
    return {"ty": "sh", "ks": {"a": 0, "k": {
        "i": in_tan, "o": out_tan, "v": vertices, "c": closed}}}


def fill(color, opacity=100):
    return {"ty": "fl", "c": static([*color, 1]), "o": static(opacity), "r": 1}


def stroke(color, width, opacity=100):
    return {"ty": "st", "c": static([*color, 1]), "o": static(opacity),
            "w": static(width), "lc": 2, "lj": 2, "ml": 4}


def group(items):
    return {"ty": "gr", "it": items + [
        {"ty": "tr", "p": static([0, 0], 2), "a": static([0, 0], 2),
         "s": static([100, 100], 2), "r": static(0), "o": static(100)}]}


def comp(w, h, ip, op, layers, name="fx"):
    return {"v": "5.7.4", "fr": FPS, "ip": ip, "op": op, "w": w, "h": h,
            "nm": name, "ddd": 0, "assets": [], "layers": layers}


# ---- fx_pat_head：爱心弹跳（头部锚点，事件响应类） --------------------------

def heart_path(scale=1.0):
    """手绘心形贝塞尔（居中于原点），6 顶点 6 段。"""
    verts = [
        (0.0, 182), (-196, -58), (-71, -183),
        (0, -118), (71, -183), (196, -58),
    ]
    ins = [
        (106, -70), (0, 100), (-65, 0),
        (-6, -30), (-45, 0), (0, -75),
    ]
    outs = [
        (-106, -70), (0, -75), (45, 0),
        (6, -30), (65, 0), (0, 100),
    ]
    f = lambda pts: [[round(x * scale, 1), round(y * scale, 1)] for x, y in pts]
    return path_shape(f(verts), f(ins), f(outs))


def heart_shape(scale=1.0):
    return group([heart_path(scale), fill((1.0, 0.31, 0.38))])


def fx_pat_head():
    big = shape_layer(
        "heart", [heart_shape()],
        transform(
            p=anim([0, 12, 26, 44], [[256, 286], [256, 260], [256, 238], [256, 160]], 2),
            o=anim([0, 4, 34, 44], [0, 100, 100, 0]),
            s=anim([0, 12, 22, 32, 44],
                   [[20, 20], [115, 115], [95, 95], [105, 105], [100, 100]], 2),
        ),
        ip=0, op=45, ind=2)
    small = shape_layer(
        "heart-sm", [heart_shape(0.45)],
        transform(
            p=anim([8, 20, 44], [[186, 290], [170, 242], [152, 178]], 2),
            o=anim([8, 14, 36, 44], [0, 100, 100, 0]),
            s=anim([8, 18, 28, 44], [[20, 20], [110, 110], [100, 100], [92, 92]], 2),
        ),
        ip=8, op=45, ind=1)
    return comp(512, 512, 0, 45, [big, small], "fx_pat_head")


# ---- fx_wave：光弧划过（手部锚点，事件响应类） ------------------------------

def arc_path(radius, a0, a1, segs=4):
    """圆弧（原点为心），按立方贝塞尔逼近；角度制，y 轴向下。"""
    step = (a1 - a0) / segs
    pts, ins, outs = [], [], []
    k = (4.0 / 3.0) * math.tan(math.radians(abs(step) / 4.0)) * radius
    sign = 1.0 if step >= 0 else -1.0
    for i in range(segs + 1):
        a = math.radians(a0 + step * i)
        x, y = radius * math.cos(a), radius * math.sin(a)
        pts.append((x, y))
        # 切线方向（沿扫掠方向）
        tx, ty = -math.sin(a) * sign, math.cos(a) * sign
        ins.append((-tx * k, -ty * k))
        outs.append((tx * k, ty * k))
    f = lambda v: [[round(x, 1), round(y, 1)] for x, y in v]
    sh = {"ty": "sh", "ks": {"a": 0, "k": {
        "i": f(ins), "o": f(outs), "v": f(pts), "c": False}}}
    return sh


def fx_wave():
    trim = {"ty": "tm", "s": anim([0, 26], [0, 55]),
            "e": anim([0, 18], [2, 100]), "o": static(0), "m": 1}
    arc = group([
        arc_path(170, 205, -25),
        stroke((0.55, 0.82, 1.0), 26),
        trim,
    ])
    layer = shape_layer(
        "arc", [arc],
        transform(
            p=static([256, 256], 2),
            o=anim([0, 24, 39], [100, 100, 0]),
            s=anim([0, 39], [[92, 92], [106, 106]], 2),
            r=anim([0, 39], [-8, 10]),
        ),
        ip=0, op=40, ind=1)
    return comp(512, 512, 0, 40, [layer], "fx_wave")


# ---- fx_connected：花瓣飘落（全屏氛围类） ----------------------------------

PETALS = [
    # (x, drift, size, delay, color)
    (140, 120, 0.9, 0, (1.0, 0.55, 0.62)),
    (300, -90, 0.6, 10, (0.78, 0.62, 1.0)),
    (455, 140, 1.0, 20, (1.0, 0.68, 0.75)),
    (620, -130, 0.7, 6, (0.85, 0.72, 1.0)),
    (760, 80, 0.85, 30, (1.0, 0.5, 0.6)),
    (900, -110, 0.65, 16, (0.9, 0.6, 1.0)),
    (1010, 60, 0.75, 38, (1.0, 0.62, 0.7)),
]


def petal_shape(scale, color):
    # 花瓣 = 椭圆压扁，组内缩放控制大小（画布 1080x1920）
    ellipse = {"ty": "el", "p": static([0, 0], 2),
               "s": static([52 * scale, 84 * scale], 2)}
    return group([ellipse, fill(color, 90)])


def fx_connected():
    dur = 96
    layers = []
    for ind, (x, drift, size, delay, color) in enumerate(PETALS, start=1):
        end = delay + dur
        layers.append(shape_layer(
            "petal-%d" % ind, [petal_shape(size, color)],
            transform(
                p=anim([delay, delay + dur], [[x, -140], [x + drift, 2060]], 2),
                o=anim([delay, delay + 8, end - 16, end], [0, 95, 95, 0]),
                r=anim([delay, end], [0, 300 if drift > 0 else -300]),
                s=static([100, 100], 2),
            ),
            ip=delay, op=end, ind=len(PETALS) - ind + 1))
    return comp(1080, 1920, 0, 134, layers, "fx_connected")


# ---- fx_swipe：流光横扫（全屏锚点，事件响应类） -----------------------------
# 对应用户「手指滑动」cue（CameraSwipeCue）。中性流光（两端对称淡出、中间亮起）：
# gesture 词表载荷没有方向字段（协议不碰），素材不指方向——见 CameraSwipeCue 注释。

def streak_path(x0, x1):
    """水平线段，居中于原点（图层 p 再放到画布位置，与 fx_wave 同口径）。
    i/o 切线组必须与 v 顶点数一致（Lottie 要求），直线全零切线。"""
    return path_shape([(x0, 0), (x1, 0)], [(0.0, 0.0), (0.0, 0.0)],
                      [(0.0, 0.0), (0.0, 0.0)], closed=False)


def fx_swipe():
    dur = 30
    layers = []
    # 三条圆头条带：主条最长、最快；上下两条短、延迟——制造速度感。
    for ind, (y, width, delay, x0, x1) in enumerate(
        [(256, 30, 0, -172, 172), (214, 16, 3, -126, 124), (292, 16, 5, -106, 94)],
        start=1,
    ):
        ip = delay
        op = dur + 4
        trim = {"ty": "tm",
                "s": anim([ip, ip + 12, op], [0, 0, 100]),
                "e": anim([ip, ip + 12, ip + 20, op], [2, 100, 100, 0]),
                "o": static(0), "m": 1}
        band = group([
            streak_path(x0, x1),
            stroke((0.55, 0.82, 1.0), width),
            trim,
        ])
        layers.append(shape_layer(
            "streak-%d" % ind, [band],
            transform(
                p=static([256, y], 2),
                o=anim([ip, ip + 4, op - 6, op], [0, 95, 95, 0]),
                s=static([100, 100], 2),
            ),
            ip=ip, op=op, ind=ind))
    return comp(512, 512, 0, dur + 4, layers, "fx_swipe")


ASSETS = {
    "fx_pat_head": fx_pat_head,
    "fx_wave": fx_wave,
    "fx_connected": fx_connected,
    "fx_swipe": fx_swipe,
}


def main():
    names = sys.argv[1:] or list(ASSETS)
    os.makedirs(OUT, exist_ok=True)
    for name in names:
        data = ASSETS[name]()
        def validate_static_vectors(node):
            if isinstance(node, dict):
                if node.get("a") == 0 and isinstance(node.get("k"), list):
                    assert all(isinstance(v, (int, float)) for v in node["k"]), \
                        "Lottie static vectors must be flat numeric arrays"
                for value in node.values():
                    validate_static_vectors(value)
            elif isinstance(node, list):
                for value in node:
                    validate_static_vectors(value)
        validate_static_vectors(data)
        text = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
        path = os.path.join(OUT, name + ".json")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
        size = len(text.encode("utf-8"))
        assert size <= 100_000, "%s: %d bytes > 100KB" % (name, size)
        # 规格自检：无内嵌位图、无 matte、无表达式
        blob = text
        for banned in ('"ty":"img"', '"ty":"tt"', '"ty":"kd"', '"ty":"grp"'):
            assert banned not in blob, "%s contains %s" % (name, banned)
        print("%s: %.1f KB -> %s" % (name, size / 1024.0, path))


if __name__ == "__main__":
    main()
