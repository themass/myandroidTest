#!/usr/bin/env python3
"""离线重放拍照解题的端上流水线（不联网、不耗额度）。

对一张照片依次重放真机上会发生的每一步，输出每一步的关键数值：

 1. DocQuadNet256 检测（与 DocQuadNetCropAssistProvider 完全一致：
    黑底 letterbox 256、RGB 0..1 NCHW；四角峰值先 sigmoid，**四个角都算**
    ——端上规则是任一角 < 0.5 整次判负，这里把四角置信度全部打出来）；
    再按 QuadNorm 的 ordered()/isValid 规则（MIN_AREA 0.08、MAX_AREA 0.985、
    自交）判定是否采纳。
 2. 检测采纳时用检测框，否则用 defaultQuad（x .10–.90, y .15–.85）。
 3. QuadWarp.warp（透视矫正，maxLongEdge 2048，输出尺寸同 outputSizePx）。
 4. 质量闸门 qualityOf：缩到 512 长边、整数灰度、3x3 拉普拉斯（Kotlin 同款
    溢出算术），阈值：长边>=800、像素>=250k、lap512>=60、曝光四项。
 5. solve() 输入：resizeSmart(1344/2M/160) + enhanceForDocument
    （Lab CLAHE 2.0 8x8 + 高斯 sigma3 addWeighted 1.5/-0.5）。
 6. ImageCompressor：长边 1600 起，q82 阶梯 -6 到 60，超 800KB 长边 *0.8 再试
    （JPEG 编码质量阶梯与 Android Bitmap.compress 近似一致）。

注意（历史教训）：
 - letterbox 底色是**黑**（drawColor(BLACK)），不是灰；
 - 四角 sigmoid 必须全算完再判，不能提前退出（早前脚本在这错过）；
 - warp 的目标点是 (0,0)(ow,0)(ow,oh)(0,oh)，不是对角线两点（早前 dst 写错
   导致输出全黑）。

用法：python3 scripts/probe/photosolve_offline_chain.py a.jpg b.jpg ...
"""
import sys
import cv2
import numpy as np
import onnxruntime as ort

MODEL = "app/src/main/assets/docquad/docquadnet256_trained_opset17.ort"
INPUT_SIZE, HS = 256, 64
MIN_CONF, MIN_AREA, MAX_AREA = 0.5, 0.08, 0.985


def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))


def letterbox(bgr):
    h, w = bgr.shape[:2]
    scale = min(INPUT_SIZE / w, INPUT_SIZE / h)
    sw, sh = max(int(w * scale), 1), max(int(h * scale), 1)
    ox, oy = (INPUT_SIZE - sw) // 2, (INPUT_SIZE - sh) // 2
    canvas = np.zeros((INPUT_SIZE, INPUT_SIZE, 3), np.uint8)
    canvas[oy:oy + sh, ox:ox + sw] = cv2.resize(bgr, (sw, sh), interpolation=cv2.INTER_AREA)
    return canvas, ox, oy, scale


def detect(bgr):
    canvas, ox, oy, scale = letterbox(bgr)
    rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB)
    chw = (rgb.astype(np.float32) / 255.0).transpose(2, 0, 1)[None]
    sess = ort.InferenceSession(MODEL, providers=["CPUExecutionProvider"])
    hm = sess.run(["corner_heatmaps"], {sess.get_inputs()[0].name: chw})[0][0]  # 4x64x64
    h, w = bgr.shape[:2]
    corners, sigs = [], []
    for ch in range(4):
        idx = int(np.argmax(hm[ch]))
        v = float(hm[ch].flat[idx])
        sigs.append(sigmoid(v))
        hx, hy = idx % HS, idx // HS
        cx, cy = (hx + 0.5) / HS * INPUT_SIZE, (hy + 0.5) / HS * INPUT_SIZE
        oxp = min(max((cx - ox) / scale, 0.0), float(w)) / w
        oyp = min(max((cy - oy) / scale, 0.0), float(h)) / h
        corners.append((oxp, oyp))
    return sigs, corners


def order(pts):
    # QuadNorm.ordered()：TL=min(x+y)、BR=max(x+y)，其余按 x-y 分 TR/BL
    tl = min(pts, key=lambda p: p[0] + p[1])
    br = max(pts, key=lambda p: p[0] + p[1])
    rest = [p for p in pts if p is not tl and p is not br]
    if len(rest) != 2:
        return pts
    tr, bl = (rest[0], rest[1]) if (rest[0][0] - rest[0][1]) > (rest[1][0] - rest[1][1]) else (rest[1], rest[0])
    return [tl, tr, br, bl]


def area(pts):
    s = 0.0
    for i in range(4):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % 4]
        s += ax * by - bx * ay
    return abs(s) / 2.0


def convex(pts):
    sign = 0
    for i in range(4):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % 4]
        cx, cy = pts[(i + 2) % 4]
        c = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
        s = (c > 0) - (c < 0)
        if s:
            if not sign:
                sign = s
            elif sign != s:
                return False
    return True


def output_size(pts, w, h, max_long=2048):
    tl, tr, br, bl = [[p[0] * w, p[1] * h] for p in pts]
    d = lambda a, b: np.hypot(a[0] - b[0], a[1] - b[1])
    ww, hh = max(d(tl, tr), d(bl, br)), max(d(tl, bl), d(tr, br))
    long_e = max(ww, hh)
    if long_e > max_long:
        r = max_long / long_e
        ww, hh = max(int(ww * r), 1), max(int(hh * r), 1)
    return int(max(ww, 1)), int(max(hh, 1))


def warp(bgr, pts):
    h, w = bgr.shape[:2]
    ow, oh = output_size(pts, w, h)
    src = np.float32([[p[0] * w, p[1] * h] for p in pts])
    dst = np.float32([[0, 0], [ow, 0], [ow, oh], [0, oh]])  # 修正过的四点
    M = cv2.getPerspectiveTransform(src, dst)
    return cv2.warpPerspective(bgr, M, (ow, oh), flags=cv2.INTER_LINEAR)


def quality(img):
    h, w = img.shape[:2]
    scale = min(1.0, 512 / max(w, h))
    sw, sh = max(int(w * scale), 1), max(int(h * scale), 1)
    # Bitmap.createScaledBitmap 等价 INTER_AREA（缩小）
    small = cv2.resize(img, (sw, sh), interpolation=cv2.INTER_AREA)
    rgb = cv2.cvtColor(small, cv2.COLOR_BGR2RGB).astype(np.int32)
    gray = (rgb[..., 0] * 299 + rgb[..., 1] * 587 + rgb[..., 2] * 114) // 1000
    g = gray.astype(np.int32)
    lap = g[1:-1, :-2] + g[1:-1, 2:] + g[:-2, 1:-1] + g[2:, 1:-1] - 4 * g[1:-1, 1:-1]
    lapvar = float(np.mean(lap.astype(np.int64) ** 2) - np.mean(lap.astype(np.float64)) ** 2)
    mean_luma = float(np.mean(g))
    dark = float(np.mean(g <= 16))
    bright = float(np.mean(g >= 240))
    issues = []
    if max(w, h) < 800 or w * h < 250_000:
        issues.append("LOW_RESOLUTION")
    if lapvar < 60.0:
        issues.append("BLURRY")
    if mean_luma < 60.0 or dark > 0.5:
        issues.append("TOO_DARK")
    if mean_luma > 215.0 or bright > 0.5:
        issues.append("TOO_BRIGHT")
    return f"{w}x{h} lap512={lapvar:.1f} luma={mean_luma:.0f} dark={dark:.2f} bright={bright:.2f} issues={issues or '无'}"


def resize_smart(img):
    h, w = img.shape[:2]
    scale = min(1344 / max(w, h), 1.0)
    sp = (w * scale) * (h * scale)
    if sp > 2_000_000:
        scale *= np.sqrt(2_000_000 / sp)
    if min(w * scale, h * scale) < 160:
        scale = max(scale, min(160 / min(w, h), 1.0))
    return cv2.resize(img, (max(int(round(w * scale)), 1), max(int(round(h * scale)), 1)),
                      interpolation=cv2.INTER_AREA)


def enhance(img):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2Lab)
    lab[..., 0] = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(lab[..., 0])
    rgb = cv2.cvtColor(lab, cv2.COLOR_Lab2BGR)
    blur = cv2.GaussianBlur(rgb, (0, 0), 3.0)
    return cv2.addWeighted(rgb, 1.5, blur, -0.5, 0.0)


def compress(img):
    edge = 1600
    while True:
        h, w = img.shape[:2]
        long_e = max(w, h)
        cur = img if long_e <= edge else cv2.resize(
            img, (max(int(round(w * edge / long_e)), 1), max(int(round(h * edge / long_e)), 1)),
            interpolation=cv2.INTER_AREA)
        q = 82
        buf = cv2.imencode(".jpg", cur, [cv2.IMWRITE_JPEG_QUALITY, q])[1]
        while len(buf) > 800 * 1024 and q > 60:
            q = max(q - 6, 60)
            buf = cv2.imencode(".jpg", cur, [cv2.IMWRITE_JPEG_QUALITY, q])[1]
        if len(buf) <= 800 * 1024 or edge <= 640:
            return f"{cur.shape[1]}x{cur.shape[0]} q={q} {len(buf) // 1024}KB"
        edge = int(round(edge * 0.8))


def main():
    for path in sys.argv[1:]:
        print(f"\n===== {path}")
        # 样本 EXIF orientation=0（真机读相册时 MediaStore 已给转正后的位图）
        img = cv2.imread(path, cv2.IMREAD_COLOR)
        h, w = img.shape[:2]
        print(f"尺寸(转正后): {w}x{h}")
        sigs, corners = detect(img)
        passed = sum(1 for s in sigs if s >= MIN_CONF)
        o = order(corners)
        a = area(o)
        print(f"四角 sigmoid: {[f'{s:.3f}' for s in sigs]}  过 0.5: {passed}/4")
        accepted = passed == 4 and MIN_AREA <= a <= MAX_AREA and convex(o)
        print(f"ordered 面积: {a:.3f}  凸: {convex(o)}  → 采纳: {accepted}")
        quad = o if accepted else [(0.10, 0.15), (0.90, 0.15), (0.90, 0.85), (0.10, 0.85)]
        quad = order(quad)
        print(f"使用框: {'检测' if accepted else 'defaultQuad'} {[(round(p[0],3), round(p[1],3)) for p in quad]}")
        warped = warp(img, quad)
        print(f"warp(2048): {warped.shape[1]}x{warped.shape[0]} mean={warped.mean():.1f}")
        print(f"质量闸门: {quality(warped)}")
        final = enhance(resize_smart(warped))
        print(f"solve 输入(enhance+resizeSmart): {final.shape[1]}x{final.shape[0]}")
        print(f"上传(IImageCompressor): {compress(final)}")


if __name__ == "__main__":
    main()
