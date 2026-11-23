#!/usr/bin/env python3
"""
生成 6 张内置滤镜的 33³ .cube 文件（与 FilterPresets.ids 同序）：
creamy / coolwhite / retro / moody / airy / mono。

每张表由一组简单参数程序化采样出来：色温偏移、饱和度、对比曲线、暗部抬升。
只用标准库；输出的值域 0..1、r 变化最快，直接可被 CubeLut.parse 读取。

用法：python3 tools/gen_luts.py <输出目录>
"""

import sys

SIZE = 33

# id: (色温(r-,b+ 偏移), 饱和度 0..1, 对比度, 暗部抬升 0..1)
PRESETS = {
    "creamy":    (0.010, 0.92, 1.02, 0.05),  # 奶油：微暖、降饱和、轻轻抬暗部
    "coolwhite": (-0.030, 0.97, 1.00, 0.00), # 冷白：偏冷、饱和度基本不动
    "retro":     (0.045, 0.75, 0.95, 0.12),  # 复古：暖黄、低饱和、暗部发灰
    "moody":     (-0.015, 0.88, 1.25, 0.00), # 情绪：高对比、略冷、压暗部
    "airy":      (0.005, 0.90, 0.92, 0.15),  # 通透：提暗部、降对比，画面发亮
    "mono":      (0.000, 0.02, 1.05, 0.02),  # 黑白：去饱和 + 轻微对比
}


def clamp(v):
    return 0.0 if v < 0.0 else 1.0 if v > 1.0 else v


def curve(v, contrast, lift):
    """对比曲线 + 暗部抬升：先把对比以 0.5 为中心拉开，再把暗部整体抬一点。"""
    v = 0.5 + (v - 0.5) * contrast
    v = clamp(v)
    # 抬升在暗部更明显：权重随亮度下降而增大
    v = clamp(v + lift * 0.15 * (1.0 - v))
    return v


def desaturate(r, g, b, sat):
    """sat=1 原色，sat=0 灰度（亮度加权）。"""
    luma = 0.299 * r + 0.587 * g + 0.114 * b
    return (luma + (r - luma) * sat,
            luma + (g - luma) * sat,
            luma + (b - luma) * sat)


def sample(r, g, b, temp, sat, contrast, lift):
    # 色温偏移：先做线性偏移，再做饱和度，最后过曲线
    r += temp
    b -= temp
    rr, gg, bb = desaturate(r, g, b, sat)
    return (clamp(curve(rr, contrast, lift)),
            clamp(curve(gg, contrast, lift)),
            clamp(curve(bb, contrast, lift)))


def write_cube(path, name, temp, sat, contrast, lift):
    last = SIZE - 1
    lines = [
        "TITLE \"%s\"" % name,
        "LUT_3D_SIZE %d" % SIZE,
        "DOMAIN_MIN 0.000000 0.000000 0.000000",
        "DOMAIN_MAX 1.000000 1.000000 1.000000",
        "",
    ]
    for bi in range(SIZE):
        b = bi / last
        for gi in range(SIZE):
            g = gi / last
            for ri in range(SIZE):
                r = ri / last
                vr, vg, vb = sample(r, g, b, temp, sat, contrast, lift)
                lines.append("%f %f %f" % (vr, vg, vb))
    with open(path, "w", encoding="ascii") as f:
        f.write("\n".join(lines) + "\n")


def main():
    if len(sys.argv) != 2:
        print("用法：python3 tools/gen_luts.py <输出目录>", file=sys.stderr)
        return 1
    out = sys.argv[1]
    import os
    os.makedirs(out, exist_ok=True)
    for name, params in PRESETS.items():
        path = "%s/%s.cube" % (out, name)
        write_cube(path, name, *params)
        print("wrote %s" % path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
