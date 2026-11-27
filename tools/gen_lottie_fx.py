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


# ---- 通用构件（扩词表新增） ------------------------------------------------
# 全部走同一套口径：512×512 给锚点类（HEAD/BODY/HAND 在渲染层是正方形画位），
# 1080×1920 给全屏类；形状只用 el/sh + fl/st，不用位图、matte、表达式。

def poly_path(pts, closed=True):
    """直边多边形（切线全零，Lottie 要求 i/o 与 v 同数）。"""
    f = lambda v: [[round(x, 1), round(y, 1)] for x, y in pts]
    zero = [(0.0, 0.0)] * len(pts)
    return path_shape(pts, zero, zero, closed=closed)


def star_path(outer, inner, points=5, rot_deg=-90):
    """尖角星（外/内半径交替），直边。-90 让一个角朝上。"""
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        a = math.radians(rot_deg + i * 180.0 / points)
        pts.append((r * math.cos(a), r * math.sin(a)))
    return poly_path(pts)


def ellipse_shape(rx, ry, color, opacity=100, stroke_w=None):
    """椭圆（rx/ry 是半径，Lottie 的 el.s 是全宽高）。"""
    el = {"ty": "el", "p": static([0, 0], 2), "s": static([2 * rx, 2 * ry], 2)}
    deco = stroke(color, stroke_w, opacity) if stroke_w else fill(color, opacity)
    return group([el, deco])


def line_path(x0, y0, x1, y1):
    return poly_path([(x0, y0), (x1, y1)], closed=False)


# ---- fx_cat_ears：猫耳弹出（HEAD，事件响应） --------------------------------
# 耳朵长在头顶画位上沿，弹入带一点回弹，之后轻微摆动——「戴上」的反馈，不是常驻。

def ear_shape(scale=1.0):
    pts = [(-58, 62), (0, -74), (58, 62)]
    f = lambda v: [[round(x * scale, 1), round(y * scale, 1)] for x, y in v]
    zero = [[0.0, 0.0]] * 3
    return group([path_shape(f(pts), zero, zero), fill((0.24, 0.22, 0.30), 96)])


def fx_cat_ears():
    dur = 46
    layers = []
    for ind, (x, tilt) in enumerate([(172, -8), (340, 8)], start=1):
        layers.append(shape_layer(
            "ear-%d" % ind, [ear_shape()],
            transform(
                p=static([x, 168], 2),
                o=anim([0, 6, dur - 10, dur], [0, 100, 100, 0]),
                s=anim([0, 10, 16, 22, dur], [[10, 10], [118, 118], [92, 92], [104, 104], [100, 100]], 2),
                r=anim([0, 10, 22, 34, dur], [tilt * 2.2, tilt, -tilt * 0.6, tilt * 0.3, 0]),
            ),
            ip=0, op=dur, ind=ind))
    return comp(512, 512, 0, dur, layers, "fx_cat_ears")


# ---- fx_star_eyes：星星眼（HEAD，事件响应） --------------------------------
# 两只星在眼部画位上转 + 呼吸；被夸/被逗到时的即时反馈。

def fx_star_eyes():
    dur = 48
    layers = []
    for ind, (x, phase) in enumerate([(196, 0), (316, 6)], start=1):
        layers.append(shape_layer(
            "star-%d" % ind, [group([star_path(56, 23), fill((1.0, 0.85, 0.35))])],
            transform(
                p=static([x, 262], 2),
                o=anim([phase, phase + 6, dur - 8, dur], [0, 100, 100, 0]),
                s=anim([phase, phase + 8, phase + 20, phase + 32, dur],
                       [[10, 10], [120, 120], [96, 96], [112, 112], [100, 100]], 2),
                r=anim([phase, dur], [0, 150]),
            ),
            ip=phase, op=dur, ind=ind))
    return comp(512, 512, 0, dur, layers, "fx_star_eyes")


# ---- fx_blush：脸红（HEAD，事件响应） --------------------------------------
# 两团腮红淡入、微微扩散再退——调戏/害羞的反馈，particles 关也要有。

def fx_blush():
    dur = 54
    layers = []
    for ind, x in enumerate([(158, ), (354,)], start=1):
        cx = x[0]
        layers.append(shape_layer(
            "blush-%d" % ind,
            [group([
                {"ty": "el", "p": static([0, 0], 2), "s": static([176, 92], 2)},
                fill((1.0, 0.42, 0.48), 62),
            ])],
            transform(
                p=static([cx, 316], 2),
                o=anim([0, 12, 34, dur], [0, 78, 72, 0]),
                s=anim([0, 12, dur], [[70, 70], [108, 108], [100, 100]], 2),
            ),
            ip=0, op=dur, ind=ind))
    return comp(512, 512, 0, dur, layers, "fx_blush")


# ---- fx_bubbles：气泡上浮（BODY，事件响应） --------------------------------

BUBBLES = [
    # (x, drift, r, delay)
    (150, 34, 30, 0), (238, -28, 18, 7), (318, 40, 24, 14),
    (392, -36, 14, 4), (196, 26, 12, 20), (352, -20, 34, 24),
]


def fx_bubbles():
    span = 62
    layers = []
    for ind, (x, drift, r, delay) in enumerate(BUBBLES, start=1):
        end = delay + span
        layers.append(shape_layer(
            "bubble-%d" % ind,
            [ellipse_shape(r, r, (0.62, 0.86, 1.0), opacity=85, stroke_w=5)],
            transform(
                p=anim([delay, delay + span // 2, end], [[x, 470], [x + drift, 260], [x - drift, 30]], 2),
                o=anim([delay, delay + 8, end - 12, end], [0, 88, 80, 0]),
                s=static([100, 100], 2),
            ),
            ip=delay, op=end, ind=ind))
    return comp(512, 512, 0, 86, layers, "fx_bubbles")


# ---- fx_halo：光环（BODY，事件响应） ---------------------------------------
# 平躺的椭圆环 + 一颗绕环走的光点（旋转整层 → 点绕椭圆公转）。
# 「光环」是对「做了好事」的一次性嘉奖，不常驻。

def fx_halo():
    dur = 58
    ring = shape_layer(
        "ring",
        [ellipse_shape(158, 46, (1.0, 0.86, 0.42), opacity=92, stroke_w=12)],
        transform(
            p=static([256, 132], 2),
            o=anim([0, 8, dur - 10, dur], [0, 95, 92, 0]),
            s=anim([0, 10, dur], [[60, 60], [106, 106], [100, 100]], 2),
        ),
        ip=0, op=dur, ind=2)
    orbit = shape_layer(
        "orbit",
        [group([
            {"ty": "el", "p": static([158, 0], 2), "s": static([26, 26], 2)},
            fill((1.0, 0.95, 0.70)),
        ])],
        transform(
            p=static([256, 132], 2),
            o=anim([0, 8, dur - 10, dur], [0, 90, 88, 0]),
            r=anim([0, dur], [-20, 340]),
            s=static([100, 100], 2),
        ),
        ip=0, op=dur, ind=1)
    return comp(512, 512, 0, dur, [ring, orbit], "fx_halo")


# ---- fx_sparkle：指尖闪光（HAND，事件响应） --------------------------------

SPARKLES = [
    # (x, y, size, delay)
    (148, 214, 1.0, 0), (330, 176, 0.7, 6), (392, 300, 0.9, 12),
    (214, 344, 0.6, 18), (268, 236, 0.45, 24),
]


def sparkle_shape(scale=1.0):
    """四角星（十字拉长），直边。"""
    r_out, r_in = 66.0 * scale, 15.0 * scale
    pts = []
    for i in range(8):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(-90 + i * 45)
        pts.append((r * math.cos(a), r * math.sin(a)))
    return group([poly_path(pts), fill((1.0, 0.92, 0.55))])


def fx_sparkle():
    span = 30
    layers = []
    for ind, (x, y, size, delay) in enumerate(SPARKLES, start=1):
        end = delay + span
        layers.append(shape_layer(
            "sparkle-%d" % ind, [sparkle_shape(size)],
            transform(
                p=static([x, y], 2),
                o=anim([delay, delay + 6, end - 8, end], [0, 100, 95, 0]),
                s=anim([delay, delay + 10, delay + 20, end],
                       [[10, 10], [112, 112], [92, 92], [118, 118]], 2),
                r=anim([delay, end], [0, 90]),
            ),
            ip=delay, op=end, ind=ind))
    return comp(512, 512, 0, 56, layers, "fx_sparkle")


# ---- fx_fireworks：烟花（FULL，事件响应） ----------------------------------
# 两朵：每朵 N 条射线从中心向外炸开（整层缩放 → 射线长度与头一起长），
# 末尾整体淡出。事件反馈（庆祝），particles 关仍播。

def firework_burst(layers, cx, cy, delay, base_hue, rays=9, reach=250):
    for i in range(rays):
        angle = 360.0 * i / rays
        layers.append(shape_layer(
            "fw-%d-%d" % (cx, i),
            [group([line_path(0, -reach * 0.32, 0, -reach),
                    stroke(base_hue, 16, 92)]),
             group([{"ty": "el", "p": static([0, -reach], 2), "s": static([26, 26], 2)},
                    fill(base_hue, 90)])],
            transform(
                p=static([cx, cy], 2),
                o=anim([delay, delay + 4, delay + 26, delay + 34], [0, 100, 92, 0]),
                s=anim([delay, delay + 6, delay + 30, delay + 34],
                       [[8, 8], [72, 72], [120, 120], [128, 128]], 2),
                r=static(angle),
            ),
            ip=delay, op=delay + 34, ind=len(layers) + 1))
    # 中心闪团
    layers.append(shape_layer(
        "fw-flash-%d" % cx,
        [ellipse_shape(56, 56, (1.0, 0.98, 0.86), opacity=90)],
        transform(
            p=static([cx, cy], 2),
            o=anim([delay, delay + 3, delay + 12, delay + 20], [0, 95, 40, 0]),
            s=anim([delay, delay + 4, delay + 20], [[30, 30], [130, 130], [170, 170]], 2),
        ),
        ip=delay, op=delay + 20, ind=len(layers) + 1))


def fx_fireworks():
    layers = []
    firework_burst(layers, 330, 520, 0, (1.0, 0.45, 0.55))
    firework_burst(layers, 760, 760, 12, (0.55, 0.82, 1.0), rays=8, reach=210)
    return comp(1080, 1920, 0, 48, layers, "fx_fireworks")


# ---- fx_coins：金币雨（FULL，事件响应） ------------------------------------
# 金币下落 + 翻面（x 缩放正负交替模拟旋转）。打赏/达成的一次性事件。

COINS = [
    # (x, r, delay, sway)
    (130, 44, 0, 40), (300, 32, 6, -30), (470, 50, 12, 55),
    (640, 36, 3, -45), (810, 42, 18, 35), (950, 30, 9, -25),
    (210, 38, 24, 30), (560, 28, 30, -38), (720, 46, 21, 44),
]


def coin_shape(r):
    return group([
        ellipse_shape(r, r, (0.98, 0.76, 0.24)),
        {"ty": "st", "c": static([0.85, 0.60, 0.12, 1]), "o": static(100),
         "w": static(max(4.0, r * 0.18)), "lc": 2, "lj": 2, "ml": 4},
    ])


def fx_coins():
    span = 90
    layers = []
    for ind, (x, r, delay, sway) in enumerate(COINS, start=1):
        end = delay + span
        mid = delay + span // 2
        layers.append(shape_layer(
            "coin-%d" % ind, [coin_shape(r)],
            transform(
                p=anim([delay, mid, end], [[x, -110], [x + sway, 900], [x - sway, 2030]], 2),
                o=anim([delay, delay + 6, end - 14, end], [0, 100, 100, 0]),
                s=anim([delay, delay + 12, delay + 24, delay + 36, delay + 48, end],
                       [[100, 100], [35, 100], [100, 100], [35, 100], [100, 100], [80, 100]], 2),
            ),
            ip=delay, op=end, ind=ind))
    return comp(1080, 1920, 0, 120, layers, "fx_coins")


# ---- fx_heart_rain：爱心雨（FULL，氛围） -----------------------------------
# 与 fx_connected 同一结构（飘落 + 旋转 + 首尾淡入淡出），形状换成心。

HEART_RAIN = [
    (120, 90, 0.42, 0, (1.0, 0.45, 0.55)),
    (275, -70, 0.30, 12, (1.0, 0.62, 0.70)),
    (430, 110, 0.50, 24, (1.0, 0.36, 0.50)),
    (600, -100, 0.34, 6, (1.0, 0.55, 0.66)),
    (745, 80, 0.44, 18, (1.0, 0.42, 0.52)),
    (890, -85, 0.28, 30, (1.0, 0.66, 0.74)),
    (1010, 60, 0.38, 10, (1.0, 0.48, 0.58)),
]


def fx_heart_rain():
    dur = 104
    layers = []
    for ind, (x, drift, size, delay, color) in enumerate(HEART_RAIN, start=1):
        end = delay + dur
        layers.append(shape_layer(
            "heart-%d" % ind, [group([heart_path(size), fill(color, 88)])],
            transform(
                p=anim([delay, delay + dur // 2, end], [[x, -160], [x + drift, 900], [x - drift, 2080]], 2),
                o=anim([delay, delay + 10, end - 18, end], [0, 92, 92, 0]),
                r=anim([delay, end], [0, 26 if drift > 0 else -26]),
                s=static([100, 100], 2),
            ),
            ip=delay, op=end, ind=len(HEART_RAIN) - ind + 1))
    return comp(1080, 1920, 0, 134, layers, "fx_heart_rain")


# ---- fx_snow：雪花飘落（FULL，氛围） ---------------------------------------

SNOW = [
    # (x, sway, r, delay)
    (90, 60, 9, 0), (210, -50, 6, 14), (330, 70, 11, 28),
    (450, -40, 7, 6), (560, 55, 9, 20), (680, -65, 6, 34),
    (790, 45, 10, 10), (900, -55, 7, 24), (1000, 50, 8, 40),
    (160, -35, 5, 46), (620, 40, 12, 48), (860, -30, 5, 18),
]


def snowflake_shape(r):
    """米字雪花（6 条过心短线）——比圆点更像雪，仍只用 st。"""
    items = []
    for i in range(3):
        a = math.radians(60 * i)
        dx, dy = r * math.cos(a), r * math.sin(a)
        items.append(group([line_path(-dx, -dy, dx, dy), stroke((0.92, 0.97, 1.0), 3, 92)]))
    items.append(group([ellipse_shape(r * 0.34, r * 0.34, (1.0, 1.0, 1.0), 85)]))
    return items


def fx_snow():
    dur = 128
    layers = []
    for ind, (x, sway, r, delay) in enumerate(SNOW, start=1):
        end = delay + dur
        mid = delay + dur // 2
        layers.append(shape_layer(
            "snow-%d" % ind, snowflake_shape(r),
            transform(
                p=anim([delay, delay + dur // 4, mid, delay + 3 * dur // 4, end],
                       [[x, -120], [x + sway, 400], [x - sway, 940], [x + sway, 1500], [x - sway, 2040]], 2),
                o=anim([delay, delay + 12, end - 20, end], [0, 90, 90, 0]),
                r=anim([delay, end], [0, 180 if sway > 0 else -180]),
                s=static([100, 100], 2),
            ),
            ip=delay, op=end, ind=len(SNOW) - ind + 1))
    return comp(1080, 1920, 0, 176, layers, "fx_snow")


# ---- fx_rain：雨滴（FULL，氛围） -------------------------------------------
# 细长圆头条带快速下坠（复用 fx_wave 的 st 口径），加两层更淡的远景雨。

RAIN = [
    # (x, len, delay, w, opacity)
    (80, 190, 0, 6, 60), (190, 150, 9, 5, 50), (300, 210, 18, 7, 66),
    (410, 140, 5, 4, 46), (520, 180, 13, 6, 58), (640, 160, 22, 5, 50),
    (750, 200, 3, 7, 64), (860, 150, 16, 4, 48), (970, 185, 25, 6, 56),
    (1040, 130, 8, 4, 44), (240, 120, 30, 3, 38), (690, 110, 34, 3, 36),
]


def fx_rain():
    dur = 46
    layers = []
    for ind, (x, length, delay, w, op) in enumerate(RAIN, start=1):
        end = delay + dur
        layers.append(shape_layer(
            "rain-%d" % ind,
            [group([line_path(0, -length / 2, 0, length / 2), stroke((0.66, 0.80, 0.95), w, op)])],
            transform(
                p=anim([delay, end], [[x, -220], [x + 26, 2120]], 2),
                o=anim([delay, delay + 6, end - 8, end], [0, 100, 100, 0]),
                s=static([100, 100], 2),
            ),
            ip=delay, op=end, ind=ind))
    return comp(1080, 1920, 0, 80, layers, "fx_rain")


# ---- fx_spotlight：追光（FULL，氛围） --------------------------------------
# 两束从画面上角扫下的光锥（低透明度填充 + 缓慢摆动）+ 底部一块亮区。
# 全屏构图，不跟人物，放大态照播（FxRenderPolicy 里 FULL 类不受放大影响）。

def cone_shape(half_width, height):
    return poly_path([(0, 0), (-half_width, height), (half_width, height)])


def fx_spotlight():
    dur = 150
    layers = [
        shape_layer(
            "cone-left",
            [group([cone_shape(300, 1500), fill((0.78, 0.86, 1.0), 13)])],
            transform(
                p=static([250, -60], 2),
                o=anim([0, 24, dur - 24, dur], [0, 90, 88, 0]),
                r=anim([0, dur // 2, dur], [-16, 10, -16]),
                s=static([100, 100], 2),
            ),
            ip=0, op=dur, ind=3),
        shape_layer(
            "cone-right",
            [group([cone_shape(340, 1560), fill((1.0, 0.90, 0.72), 12)])],
            transform(
                p=static([830, -40], 2),
                o=anim([0, 24, dur - 24, dur], [0, 85, 84, 0]),
                r=anim([0, dur // 2, dur], [14, -8, 14]),
                s=static([100, 100], 2),
            ),
            ip=0, op=dur, ind=2),
        shape_layer(
            "pool",
            [ellipse_shape(300, 130, (0.92, 0.95, 1.0), opacity=16)],
            transform(
                p=static([540, 1560], 2),
                o=anim([0, 30, dur - 30, dur], [0, 90, 90, 0]),
                s=anim([0, dur // 2, dur], [[80, 80], [108, 108], [80, 80]], 2),
            ),
            ip=0, op=dur, ind=1),
    ]
    return comp(1080, 1920, 0, dur, layers, "fx_spotlight")


ASSETS = {
    "fx_pat_head": fx_pat_head,
    "fx_wave": fx_wave,
    "fx_connected": fx_connected,
    "fx_swipe": fx_swipe,
    # 头部锚点（事件响应）
    "fx_cat_ears": fx_cat_ears,
    "fx_star_eyes": fx_star_eyes,
    "fx_blush": fx_blush,
    # 身体锚点（事件响应）
    "fx_bubbles": fx_bubbles,
    "fx_halo": fx_halo,
    # 手部锚点（事件响应）
    "fx_sparkle": fx_sparkle,
    # 全屏（事件响应）
    "fx_fireworks": fx_fireworks,
    "fx_coins": fx_coins,
    # 全屏（氛围）
    "fx_heart_rain": fx_heart_rain,
    "fx_snow": fx_snow,
    "fx_rain": fx_rain,
    "fx_spotlight": fx_spotlight,
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
