# 换装线：pending 槽的美术/参数需求单（2026-11-24）

单一事实源：`scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json`（6 槽 20 变体：16 active）。
本文只处理 **4 个 pending 变体**：内衣 3（pending-art）+ 头饰 2（pending-param）。
纪律：pending 绝不伪装 active（四处同源测试钉死：gen-expressions.py / wardrobe_layout.py /
Live2dTouchReactionTest 四向钉 / `Live2dWardrobeAxis`）。

## 一、头饰 2 项（ribbon/hat）——pending-param：**参数已在模型里，缺的是「已验证值」**

现状事实（本轮核对）：
- atri_8 的 cdi3 有发饰参数：`Param4–Param7`（发饰）、`Param23–Param30`（hdj 组）。
- 180 个上游 `expressions/*.exp3.json` 引用的参数全集只有：
  `Param9/17/18/19/20/22/36/37/38/39/Cheek` —— **没有任何 Param4–7/23–30**。
- 所以盲发值=发明值（测试禁止）；这就是这两项 pending 的全部原因。

**需要的输入（一个人 + Cubism Editor 一次会话就能做完）：**
1. 在 Cubism Editor 里打开 atri_8 模型；
2. 对 `Param4,5,6,7` 与 `Param23..30` 逐个从 0 缓慢拉到最大，肉眼确认每个参数控制哪个部件
   （蝴蝶结/帽子/其它发饰片）、显隐是否干净（无残影、无穿插）；
3. 为 spec 的两个 token 各记录一组 `(Id, Value)`：
   - `ribbon` = 哪几个参数、什么值时「蝴蝶结显示、其余发饰隐藏」；
   - `hat` = 同上（帽子）。互斥由槽的 `exclusive` 语义保证，参数组必须能全关回基线；
4. 把记录填进 wardrobe-spec.json 对应 variant：`status: active` + `param`/`value` +
   `verifiedFrom: "cubism-editor(atri_8) YYYY-MM-DD"`，并截图存
   `scripts/dh-lab/live2d-lab-assets/evidence/`（开/关两态）。

**填完后代码侧要做的事（顺序）：** gen-expressions.py 重新生成 → wardrobe_layout.py 校验 →
`Live2dWardrobeAxis`/`wardrobeRows()` 补 hair 轴 → 四处同源测试补钉 → lab 真机验收换装托盘。
没有任何模型文件改动，纯参数。

风险：若编辑器里发现 Param4–7/23–30 并不对应「蝴蝶结/帽子」两个可独立开关的部件
（比如都挂在同一个发饰片上），则 ribbon/hat 中至少一个转 pending-art，走下面内衣同一条管线。

## 二、内衣 3 项（lace/sport/cat）——pending-art：**缺的是正式美术，管线已备好**

现状事实：
- atri_8 moc3 **没有** 内衣 ArtMesh，exp3 只能开关已有参数——不可能靠参数变出内衣；
- `productionBinding` 已声明：toggle、`ParamWear_` 前缀、off=0 / on=1——端侧语义就绪；
- 交付管线已存在并带测试：`UNDERWEAR-PIPELINE.md` + `check_delivery_ready.py` +
  `wardrobe_layout.py`（psd2live v1 Layout；证据链见 psd2live-eval/EVAL.md、
  docs/design/psd2live-wardrobe-evidence-20261123.md、Editor 开关三联图）。

**需要美术交付的东西（每套一份，规格细节以 UNDERWEAR-PIPELINE.md 为准）：**
- 一个目录：`source.json` + 真实 PNG 分层（ lace.png / sport.png / cat.png，可多图层共享参数）；
- 每层给 `rect` 坐标与尺寸（美术给，不自动猜）；`wardrobe.slot=underwear` + token 声明；
- 底图/身体/头发等普通层保留（生成新模型，不是在旧 ATRI 上拆图）；
- 合规红线：身体基线用**有授权、已确认成年**的原创角色素材；
  示意/占位图（examples/underwear-delivery-minimal/）永远不算正式交付。

**验收门槛（转 active 前，全部不可跳，摘自管线文档）：**
1. 美术目录跑 `check_delivery_ready.py` 通过（结构就绪，不等于可上架）；
2. `wardrobe_layout.py` 生成 Layout → psd2live 官方应用 MCP：
   `project_import_layout → rig_generate → project_export_target` 出**新模型**（moc3/model3/cdi3/贴图）；
3. `inspect_export.py` 断言 `ParamWear_lace|sport|cat` 同时在 moc3 字符串表与 cdi3；
4. 真实 Cubism runtime 加载新模型：每套 0/1 显隐、遮挡、动作变形、互斥逐一截图；
5. 旧槽（上衣 Param17 等）在新模型上重新验证——**不能沿用旧 ATRI 绑定**；
6. 全部过了才把 spec 转 active、补 verifiedFrom、更新 naming.order 与 Kotlin 绑定、跑四处同源测试。

## 三、优先级与依赖

- 头饰是**最短路径**（无美术、无新模型；一次编辑器会话 + 一轮代码）——先做；
- 内衣卡在美术资源本身；管线与门禁已齐，美术到货即可进入生成-验收循环；
- 两条都不阻塞其它视觉线；本仓侧无需再准备工具。
