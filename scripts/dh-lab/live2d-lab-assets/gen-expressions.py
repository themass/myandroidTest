#!/usr/bin/env python3
"""生成 live2d-lab-assets 的组合表情（exp3）与 ATRI.model3.json 表情注册表。

demo #2b：触点反应有四个持久 toggle 维度——
  服装: none / bikini(Param17=30) / pajamas(Param18=30)   （demo #2 语义：点躯干循环）
  脸红: ParamCheek=1                                        （摸头）
  手:   Param38 kani=30                                     （#2b：点手）
  脚:   Param19 凉鞋=30                                     （#2b：点脚）

表情名 = [服装_]blush?_crab?_sandal?，全空为 "base"；与 Kotlin 侧
Live2dTouchReaction.expressionFor 必须同序同名（Live2dTouchReactionTest 钉死）。
3×2×2×2 = 24 个组合，vendored 表情管理器同时只激活一个表情，所以每个组合
必须自带全部已激活维度的参数。

数值纪律：只允许上游 expression1–16.exp3.json 里对同一 atri_8.moc3 已验证过的
(Id,Value)——这里用到的 (17,30)(18,30)(19,30)(38,30)(Cheek,1) 全部在列，无发明值。

淡入淡出：exp3 不写 FadeInTime/FadeOutTime 时 CubismExpressionMotion 用默认
DefaultFadeTime=1.0s（CubismExpressionMotion.cpp:24），触点反馈「按了像没按」。
每个组合显式写 FADE_SECONDS（0.2s）：按下即变，观感从「交叉淡化的慢切换」变成
「响应」；换装同值（要的就是立刻看到，不用另开一档）。
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
EXP_DIR = os.path.join(HERE, "expressions")
MODEL3 = os.path.join(HERE, "ATRI.model3.json")

FADE_SECONDS = 0.2

COMBOS = [
    (costume, blush, crab, sandal)
    for costume in (None, "bikini", "pajamas")
    for blush in (False, True)
    for crab in (False, True)
    for sandal in (False, True)
]

PARAMS = {
    "bikini": ("Param17", 30),
    "pajamas": ("Param18", 30),
    "blush": ("ParamCheek", 1),
    "crab": ("Param38", 30),
    "sandal": ("Param19", 30),
}


def expression_name(costume, blush, crab, sandal):
    parts = [p for p in (costume, "blush" if blush else None,
                         "crab" if crab else None, "sandal" if sandal else None) if p]
    return "_".join(parts) if parts else "base"


def main():
    os.makedirs(EXP_DIR, exist_ok=True)
    names = []
    for costume, blush, crab, sandal in COMBOS:
        name = expression_name(costume, blush, crab, sandal)
        names.append(name)
        activated = [c for c in (costume, "blush" if blush else None,
                                 "crab" if crab else None, "sandal" if sandal else None) if c]
        params = [{"Id": PARAMS[k][0], "Value": PARAMS[k][1], "Blend": "Add"} for k in activated]
        doc = {
            "Type": "Live2D Expression",
            "Comment": "lab 触点互动组合表情（生成器 gen-expressions.py，勿手改）："
                       + (name if activated else "初始态（参数清空）"),
            "FadeInTime": FADE_SECONDS,
            "FadeOutTime": FADE_SECONDS,
            "Parameters": params,
        }
        # 保持与手写资产一致的 tab 缩进风格
        with open(os.path.join(EXP_DIR, f"{name}.exp3.json"), "w") as f:
            json.dump(doc, f, ensure_ascii=False, indent="\t")
            f.write("\n")

    # 重写 model3.json 的 Expressions 注册段（必须嵌在 FileReferences 下，
    # vendored CubismModelSettingJson.cpp:186 只认这个位置）。
    with open(MODEL3) as f:
        model3 = json.load(f)
    model3["FileReferences"]["Expressions"] = [
        {"Name": n, "File": f"expressions/{n}.exp3.json"} for n in names
    ]
    with open(MODEL3, "w") as f:
        json.dump(model3, f, ensure_ascii=False, indent="\t")
        f.write("\n")
    print(f"生成 {len(names)} 个组合表情 + 重写 {os.path.basename(MODEL3)} 注册段")


if __name__ == "__main__":
    main()
