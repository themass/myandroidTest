#!/usr/bin/env python3
"""Generate the camera **sticker** Lottie JSONs (repo-owned, self-authored).

贴纸线与舞台特效线是两套词表（用户 2026-11-28：「贴纸只是挪用其中 3 个……贴纸你也没搞啊」）：
特效是对「发生了什么」的一次性反馈，贴纸是挂在人已跟踪部位上的常驻装饰。
因此本脚本只产 `sticker_*`，写进 :feature-camera-fx 的 assets，与 tools/gen_lottie_fx.py
的 `fx_*` 在文件名上就能分开。

画位口径（与 CameraStickerAnchors.kt 一一对应，改一处必须改另一处）：
  - 画布 512×512，**画布中心 = 该贴纸的锚点**（头顶 / 两眼连线中点 / 唇 / 手掌中心）。
    所以头饰的「底边」画在 y≈270（略低于中心），墨镜画在 y≈256，胡子画在 y≈225
    （挂在唇锚点上方而不是压在唇上）。
  - 相机侧 `iterations = Int.MAX_VALUE`：贴纸循环播放，因此**首尾同态**（seamless loop），
    只做轻微摆动/呼吸，不做入场退场。
规格同特效（lottie-fx-plan-20261123.md §3）：透明背景、无 AE 表达式、无内嵌位图、
无 matte、单文件 <=100KB。全部程序化生成——许可=仓库自制。

用法:
    python3 -I tools/gen_lottie_stickers.py [sticker ...]   # 全部或指定几条
生成到 feature-camera-fx/src/main/assets/fx/。
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_lottie_fx import (  # 共用构件，不复制一份（同一套口径只有一处定义）
    anim,
    comp,
    ellipse_shape,
    fill,
    group,
    heart_path,
    line_path,
    poly_path,
    shape_layer,
    static,
    stroke,
    transform,
)

OUT = os.path.join(os.path.dirname(__file__), "..",
                   "feature-camera-fx", "src", "main", "assets", "fx")
W = H = 512


# ---- 头顶类：底边压在画布中心线（= 头顶锚点）上 ----------------------------

def sticker_cat_ears():
    """猫耳：两只三角耳立在头顶，轻轻摆动（首尾同态）。"""
    dur = 60
    layers = []
    for ind, (x, tilt) in enumerate([(168, -10), (344, 10)], start=1):
        pts = [(-72, 66), (0, -96), (72, 66)]
        inner = [(-40, 60), (0, -56), (40, 60)]
        layers.append(shape_layer(
            "ear-%d" % ind,
            [group([poly_path(pts), fill((0.20, 0.19, 0.26), 100)]),
             group([poly_path(inner), fill((1.0, 0.62, 0.70), 90)])],
            transform(
                p=static([x, 262], 2),
                o=static(100),
                s=static([100, 100], 2),
                # 60 帧一摆：0 → +a → 0 → -a → 0，首尾同值 = 无缝循环。
                r=anim([0, 15, 30, 45, 60], [0, tilt, 0, -tilt * 0.6, 0]),
            ),
            ip=0, op=dur, ind=ind))
    return comp(W, H, 0, dur, layers, "sticker_cat_ears")


def sticker_crown():
    """皇冠：五尖王冠 + 三颗宝石，坐在头顶，轻微上下呼吸。"""
    dur = 72
    base = 66  # 冠底相对画布中心（270）的偏移：底边在 y≈302
    body = [(-150, base), (-150, -46), (-76, 2), (0, -78), (76, 2), (150, -46), (150, base)]
    layers = [
        shape_layer(
            "crown",
            [group([poly_path(body), fill((0.98, 0.79, 0.28), 100), stroke((0.80, 0.58, 0.12), 8)])],
            transform(
                p=static([256, 264], 2),
                o=static(100),
                s=static([100, 100], 2),
                r=anim([0, 36, 72], [-1.5, 1.5, -1.5]),
            ),
            ip=0, op=dur, ind=3),
        shape_layer(
            "gems",
            [group([{"ty": "el", "p": static([x, -74], 2), "s": static([26, 26], 2)},
                    fill(color, 100)])
             for x, color in [(-150, (1.0, 0.36, 0.44)), (0, (0.42, 0.72, 1.0)), (150, (0.55, 0.90, 0.65))]],
            transform(
                p=static([256, 264], 2),
                o=static(100),
                s=static([100, 100], 2),
                r=anim([0, 36, 72], [-1.5, 1.5, -1.5]),
            ),
            ip=0, op=dur, ind=2),
    ]
    return comp(W, H, 0, dur, layers, "sticker_crown")


def sticker_flower_wreath():
    """花环：沿头顶弧线排 7 朵小花，逐朵轻微缩放呼吸（相位错开 → 整体不死板）。"""
    dur = 84
    layers = []
    # 弧：圆心在画布中心略下、半径盖过头顶，花排在弧的上半段（-152°..-28°）。
    cx, cy, radius = 256, 330, 236
    r, pr = 26, 20
    for ind, frac in enumerate([0.0, 0.16, 0.33, 0.5, 0.67, 0.84, 1.0], start=1):
        deg = -152 + 124 * frac
        a = math.radians(deg)
        x, y = cx + radius * math.cos(a), cy + radius * math.sin(a)
        # 一朵花 = 五个花瓣圆（同组共填充） + 一个花心圆（另一组）
        petals = [
            {"ty": "el",
             "p": static([round(r * math.cos(math.radians(72 * k)), 1),
                          round(r * math.sin(math.radians(72 * k)), 1)], 2),
             "s": static([2 * pr, 2 * pr], 2)}
            for k in range(5)
        ]
        phase = 8 * ind
        # 呼吸：同一组关键帧时间、按花朵序号**轮转取值** → 每朵相位不同，
        # 且首值=末值，循环处不跳（不后移时间轴：t 超过 op 的关键帧不可靠）。
        cycle = [[100, 100], [114, 114], [100, 100], [88, 88]]
        shift = ind % 4
        scale_kfs = cycle[shift:] + cycle[:shift] + [cycle[shift]]
        layers.append(shape_layer(
            "flower-%d" % ind,
            [group(petals + [fill((1.0, 0.55, 0.66), 95)]),
             group([{"ty": "el", "p": static([0, 0], 2), "s": static([22, 22], 2)},
                    fill((1.0, 0.90, 0.45), 100)])],
            transform(
                p=static([round(x, 1), round(y, 1)], 2),
                o=static(100),
                s=anim([0, dur // 4, dur // 2, 3 * dur // 4, dur], scale_kfs, 2, ease=0.5),
            ),
            ip=0, op=dur, ind=ind))
    return comp(W, H, 0, dur, layers, "sticker_flower_wreath")


# ---- 脸部类：挂在眼睛 / 唇的锚点上 -----------------------------------------

def sticker_sunglasses():
    """墨镜：两片圆角镜 + 鼻梁 + 镜腿；一道高光斜扫过镜片（循环）。"""
    dur = 96
    lens = [
        {"ty": "rc", "p": static([x, 0], 2), "s": static([196, 150], 2), "r": static(46)}
        for x in (-112, 112)
    ]
    frame = shape_layer(
        "lenses",
        [group(lens + [fill((0.12, 0.14, 0.20), 92), stroke((0.94, 0.96, 1.0), 10, 85)]),
         group([poly_path([(-14, -6), (14, -6)], closed=False), stroke((0.94, 0.96, 1.0), 10, 85)]),
         group([poly_path([(-206, -18), (-196, 6), (196, 6), (206, -18)], closed=False),
                stroke((0.94, 0.96, 1.0), 10, 70)])],
        transform(
            p=static([256, 256], 2),
            o=static(100),
            s=static([100, 100], 2),
        ),
        ip=0, op=dur, ind=2)
    shine = shape_layer(
        "shine",
        [group([line_path(-120, 90, 120, -90), stroke((1.0, 1.0, 1.0), 22, 30)])],
        transform(
            p=anim([0, dur], [[130, 256], [430, 256]], 2),
            o=anim([0, 10, 26, 36, dur], [0, 60, 60, 0, 0]),
            r=static(0),
            s=static([100, 100], 2),
        ),
        ip=0, op=dur, ind=1)
    return comp(W, H, 0, dur, [frame, shine], "sticker_sunglasses")


def sticker_mustache():
    """胡子：八字胡（两条对称弧 + 中线），挂在唇锚点**上方**。"""
    dur = 60
    def arc(sign):
        pts, ins, outs = [], [], []
        steps = 4
        for i in range(steps + 1):
            t = i / steps
            x = sign * (10 + 190 * t)
            y = -52 * math.sin(math.pi * t) + 8 * t
            pts.append((x, y))
        zero = [[0.0, 0.0]] * len(pts)
        return group([poly_path(pts, closed=False), stroke((0.24, 0.16, 0.12), 34, 100)])

    layer = shape_layer(
        "mustache",
        [arc(-1), arc(1),
         group([{"ty": "el", "p": static([0, -6], 2), "s": static([44, 30], 2)},
                fill((0.24, 0.16, 0.12), 100)])],
        transform(
            p=static([256, 214], 2),
            o=static(100),
            s=anim([0, 30, 60], [[100, 100], [104, 100], [100, 100]], 2),
        ),
        ip=0, op=dur, ind=1)
    return comp(W, H, 0, dur, [layer], "sticker_mustache")


# ---- 手持类：挂在手掌中心，闪光循环 ----------------------------------------

def sticker_hand_heart():
    """手持比心：一颗心 + 外圈四颗闪光交替明灭（首尾同态）。"""
    dur = 72
    heart = shape_layer(
        "heart",
        [group([heart_path(0.62), fill((1.0, 0.36, 0.46), 95), stroke((1.0, 0.86, 0.90), 10, 90)])],
        transform(
            p=static([256, 256], 2),
            o=static(100),
            s=anim([0, 18, 36, 54, 72], [[92, 92], [110, 110], [96, 96], [108, 108], [92, 92]], 2),
        ),
        ip=0, op=dur, ind=2)
    sparkles = []
    for ind, deg in enumerate([40, 130, 220, 310], start=1):
        a = math.radians(deg)
        x, y = 256 + 178 * math.cos(a), 256 + 178 * math.sin(a)
        pts = []
        for i in range(8):
            r = 34 if i % 2 == 0 else 9
            ang = math.radians(-90 + i * 45)
            pts.append((r * math.cos(ang), r * math.sin(ang)))
        sparkles.append(shape_layer(
            "spark-%d" % ind,
            [group([poly_path(pts), fill((1.0, 0.94, 0.60), 100)])],
            transform(
                p=static([round(x, 1), round(y, 1)], 2),
                # 相位错开且首尾同值：0 → 100 → 0 → 100 → 0 会跳，用 0→100→0 半个周期对称。
                o=anim([0, dur // 4, dur // 2, 3 * dur // 4, dur],
                       [20, 100, 20, 100, 20]),
                s=static([100, 100], 2),
                r=anim([0, dur], [0, 360]),
            ),
            ip=0, op=dur, ind=ind))
    return comp(W, H, 0, dur, sparkles + [heart], "sticker_hand_heart")


ASSETS = {
    "sticker_cat_ears": sticker_cat_ears,
    "sticker_crown": sticker_crown,
    "sticker_flower_wreath": sticker_flower_wreath,
    "sticker_sunglasses": sticker_sunglasses,
    "sticker_mustache": sticker_mustache,
    "sticker_hand_heart": sticker_hand_heart,
}


def main():
    names = sys.argv[1:] or list(ASSETS)
    os.makedirs(OUT, exist_ok=True)
    for name in names:
        data = ASSETS[name]()
        # 与 gen_lottie_fx 同一套自检：静态向量必须是扁平数字数组。
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
        for banned in ('"ty":"img"', '"ty":"tt"', '"ty":"kd"', '"tx":"ex"'):
            assert banned not in text, "%s contains %s" % (name, banned)
        # 贴纸循环：首尾同态自检（每个图层 ip=0/op=dur，画布 op 即循环长度）。
        assert all(layer["ip"] == 0 for layer in data["layers"]), \
            "%s: 贴纸图层必须整程在场（循环装饰不做入场）" % name
        print("%s: %.1f KB -> %s" % (name, size / 1024.0, path))


if __name__ == "__main__":
    main()
