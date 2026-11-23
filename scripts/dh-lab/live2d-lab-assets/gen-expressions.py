#!/usr/bin/env python3
"""生成 live2d-lab-assets 的组合表情（exp3）与 ATRI.model3.json 表情注册表。

v2（真机投诉 2026-11-23「点击头和脚只能点一次，再点就没反应了」后重设计）：
触点反应的四根**循环轴**——
  上衣: 日常 / bikini(Param17=30) / pajamas(Param18=30) / pumpkin(Param9=30,
        衣服-睡衣-南瓜裤) / blood(Param36=30, 血衣)                    （点躯干循环）
  鞋:   赤脚 / sandal(Param19=30) / leather(Param20=30, 衣服-皮鞋)      （点脚循环）
  手持: 空手 / kani(Param38=30) / bird(Param37=30) / pillow(Param39=30) （点手循环）
  表情: 正常 / blush(ParamCheek=1) / white(Param22=30, 白眼)           （点头顶循环）

旧语义（toggle 挂上/摘掉）被换掉：第二次点把状态还原，机器看是变化、用户看是
「没反应」。循环语义下每次点击都进一个新的可见状态。

表情名 = top?_shoes?_held?_face?，全空为 "base"；与 Kotlin 侧
Live2dTouchReaction.expressionFor 必须同序同名（Live2dTouchReactionTest 钉死）。
5×3×4×3 = 180 个组合，vendored 表情管理器同时只激活一个表情，所以每个组合
必须自带全部已激活维度的参数。各轴 token 全局互不相同 → 180 个名字两两不同。

数值纪律：只允许上游 expression1–16.exp3.json 里对同一 atri_8.moc3 已验证过的
(Id,Value)（2026-11-23 盘点：16 个文件的参数与 cdi3 中文标签逐一对过）。
头饰类参数（Param4-7 发饰、Param23-30 hdj）上游没有已验证值，不进这里。

**单一事实源**（2026-11-23 换装抽象设计）：槽位/变体/参数绑定全部读
wardrobe-spec.json，本脚本不再自带字典。status=active 才进命名空间并生成资产；
pending-* 只声明不出货。改轴改 spec，不改本脚本。

淡入淡出：exp3 不写 FadeInTime/FadeOutTime 时 CubismExpressionMotion 用默认
DefaultFadeTime=1.0s（CubismExpressionMotion.cpp:24），触点反馈「按了像没按」。
每个组合显式写 FADE_SECONDS（0.2s）：按下即变。
"""
import json
import os
import glob

HERE = os.path.dirname(os.path.abspath(__file__))
EXP_DIR = os.path.join(HERE, "expressions")
MODEL3 = os.path.join(HERE, "ATRI.model3.json")
SPEC = os.path.join(HERE, "wardrobe-spec.json")

FADE_SECONDS = 0.2

with open(SPEC) as f:
    spec = json.load(f)

# 词缀顺序与 Kotlin expressionFor 一致（spec.naming.order）：上衣 → 鞋 → 手持 → 表情。
SLOTS = [s for s in spec["slots"] if s["slot"] in spec["naming"]["order"]]
AXES = [(s["slot"], [v["token"] for v in s["variants"] if v["status"] == "active"])
        for s in SLOTS]
PARAMS = {
    v["token"]: (v["param"], v["value"])
    for s in SLOTS for v in s["variants"]
    if v["status"] == "active" and v["token"] != "none"
}


def expression_name(active):
    return "_".join(active) if active else "base"


def main():
    os.makedirs(EXP_DIR, exist_ok=True)
    # v1 的 24 个老名字（crab 等）不在新名空间里——清掉防止设备上堆死资产。
    for f in glob.glob(os.path.join(EXP_DIR, "*.exp3.json")):
        os.remove(f)

    names = []
    def cross(i, active):
        if i == len(AXES):
            names.append((tuple(active), expression_name(active)))
            return
        for token in AXES[i][1]:
            if token == "none":
                cross(i + 1, active)
            else:
                cross(i + 1, active + [token])
    cross(0, [])

    for active, name in names:
        params = [{"Id": PARAMS[k][0], "Value": PARAMS[k][1], "Blend": "Add"} for k in active]
        doc = {
            "Type": "Live2D Expression",
            "Comment": "lab 触点互动组合表情 v2（生成器 gen-expressions.py，勿手改）："
                       + (name if active else "初始态（参数清空）"),
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
        {"Name": n, "File": f"expressions/{n}.exp3.json"} for _, n in names
    ]
    with open(MODEL3, "w") as f:
        json.dump(model3, f, ensure_ascii=False, indent="\t")
        f.write("\n")
    print(f"生成 {len(names)} 个组合表情 + 重写 {os.path.basename(MODEL3)} 注册段")


if __name__ == "__main__":
    main()
