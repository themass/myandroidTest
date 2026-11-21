# 图片编辑美化工具升级设计（加强版编辑器）

> 状态：**方向已获用户批准；经独立 builder 审阅一轮，修订见文内（§5 裁量记录）**。日期 2026-11-22。
> 范围：`:mediaview`（edit 子系统）、`:feature-chat` 接入、新模块 `:media-beauty`（P1）。
> 许可策略：**用户拍板**——本 app 为实验性质，开源代码可自由拷贝、裁剪、修改使用，
> 不受上游协议顾虑约束（正式商用前需另行合规复核，见 §7 风险）。

## 1. 意图

把现有图片编辑器（旋转/调色/画笔/文字/马赛克）升级为"体验特别好、功能特别强"的图片
编辑美化工具：对标美图/醒图的核心体验——**实时预览、裁剪、一键滤镜、自然美颜、贴纸**，
并与角色扮演场景打通（编辑完直接发给她）。

为什么现在做：图片消息 P1（`2026-11-21-image-messages-design.md`）打通"发图给她"的
链路后，"先编辑再发"是同一入口的自然的下一步；编辑器骨架已经正确，缺的是能力块。

## 2. 现状事实（已核实，2026-11-22，经 builder 复核）

| 能力 | 位置 | 现状 |
|---|---|---|
| 数据模型 | `mediaview/…/edit/PhotoEdits.kt` | 不可变 `EditOp` 列表 + undo/redo 快照栈；**标注坐标归一化于「原图（未旋转未镜像）」空间**；旋转/镜像在 ops 头部累积 |
| 渲染 | `PhotoRenderer.kt` | **按类型挑选的一次性预处理**（旋转取最后累积→镜像→Adjust 取最后→降采样）+ 标注逐条重放；纯 CPU Canvas；render 失败回退返回无标注原图 |
| 坐标系 | `EditCoords.kt` | 纯 0..1↔0..1（source↔全幅显示），无窗口概念；触摸坐标由 PhotoEditor 从**渲染图** fit 反算 |
| 编辑器 UI | `PhotoEditor.kt`（715 行） | 无 ViewModel；预览每次 ops 变化**全量重放 1280 长边含标注位图**（L137-139）；拖动中的笔走 Canvas 叠加层；`allowCrop` 参数是空占位 |
| 导出 | `PhotoEditExporter.kt` | 解码 2048 → 渲染 4096 → 上传兜底 2048；`estimatedSize`/`outputSize` **全仓零调用点** |
| 接入 | `ChatRoute.kt:249`（唯一调用点） | 条件叠加 Box；编辑结果同名写回草稿暂存文件，失败提示"已使用原图" |
| 缺口 | — | **无裁剪、无滤镜、无美颜、无贴纸、无 GPU 路径**（minSdk 28；AGSL/RuntimeShader 全仓零使用） |
| 约束 | `mediaview/build.gradle:20` | 模块自我约束「不带原生库、只依赖 Compose 和 Coil」 |
| 测试 | `PhotoEditsTest`（纯 JVM）、`PhotoRendererTest`（Robolectric NATIVE 真位图逐像素） | 两条测试路现成；**Robolectric 无真实 GPU，AGSL 在其上跑不了** |

## 3. 方案与被否掉的备选

- **选定：自有骨架 + 分阶段引地基**。保留 EditOp 模型，往预处理链里加"按类型挑选"的
  能力（Crop/Filter），美颜原生动机（OpenCV/MediaPipe）放新模块 `:media-beauty`，
  `:mediaview` 经端口接口消费，不破"不带原生库"约束。
- 备选 1：整个换开源编辑器——否。接入、ops 模型、undo/redo、像素测试全部重做，
  且没有一家带自然美颜。
- 备选 2：原生库塞进 `:mediaview`——否。chatkit 连带拖大，违背既有模块决定。
- 备选 3：全云端编辑——否。实时跟手是体验核心。

## 4. 分期与能力设计

### P0 裁剪 + 一键滤镜（本计划实现）

**渲染管线定案**（替代旧版"顺序"叙述——现有机制是按类型挑选，不靠 ops 列表位置）：

```
source → Rotate(累积) → Mirror(奇偶) → Crop(唯一，见裁量 1) → Adjust(最后一条)
       → Filter(最后一条，CPU 三线性 LUT) → downscale(maxLongEdge) → 标注逐条重放
```

**裁剪**
- 新 `EditOp.Crop(left, top, right, bottom: Float)`：**归一化于「当前旋转/镜像后的显示画幅」**
  （所见即所得，见裁量 1）；全列表**至多一条**，二次裁剪替换旧值。
- 渲染：镜像之后插一步 `createBitmap(交集)`；`displayWidth/Height` 按 crop 交集计算。
- **标注坐标随裁剪重映射**：提交裁剪时把所有 Stroke/Text/Mosaic 的归一化坐标一次性
  线性重映射到裁后画幅（落在裁掉区域的按现有 hitTest/渲染自然出界，不删——用户撤销
  裁剪时还原）。重映射是纯函数，单独测。
- 裁剪后**禁用旋转/镜像按钮**（置灰，WeChat 编辑器同款约束），从根上消掉
  "裁后再旋、rect 解释漂移"问题。
- `EditCoords` 集中加 crop 窗口变换（`toSourceNorm/toDisplayNorm` 增 crop-aware 重载，
  HitTest 与叠加层通过它拿换算，不各自发明）；`editsWithoutOps` 选中框路径同步保 Crop。
- 90° 自由旋转（透视）不做，记 P2 可选。

**一键滤镜（3D LUT）**
- 新 `EditOp.Filter(id: String, intensity: Int /*0..100*/)`；面板互斥选择，
  语义 =「替换最后一条 Filter」。Adjust 与 Filter 是两个独立 tab，顺序固定 Adjust→Filter。
- 资产：**程序化生成** 6–8 张 33³ `.cube`（曲线+色调参数合成，离线脚本产出进
  `assets/filters/`；解析器就绪后设计手调 `.cube` 可直接替换文件，见裁量 6）。
- 运行时：**P0 纯 CPU 三线性查表，不引入 AGSL**（见裁量 5）。预览时 LUT 只作用于
  **640 长边底图**（滑杆拖动期间），提交后底图 1280；导出全尺寸一次 pass。
- 强度合成：`out = lerp(原像素, LUT(像素), intensity/100)`，预览与导出同一实现
  （同一个函数，无两路一致性问题）。
- **预览架构改底图/标注分层**（裁量 4）：预览 = `renderBase`（预处理链到 downscale，
  带缓存）+ 标注在叠加层绘制。滤镜只作用于底图——**标注不过滤镜**（黑色文字仍是黑色），
  导出与预览同序同义，所见即所得。

**预览性能（P0 顺带）**
- 新增 `PhotoRenderer.renderBase(source, edits, maxLongEdge)`：预处理结果
  LruCache（**key 含 maxLongEdge**，容量 4 帧，source 用 identity 弱引用，见裁量 8）。
- 导出**不吃缓存**：export 直调全尺寸管线，失败返回 null（见裁量 11）。

### P1 自然美颜（下一步）

- 新 `:media-beauty` 模块：MediaPipe FaceLandmarker（478 点）+ OpenCV（bilateral 磨皮、
  unsharp 锐化）；`:mediaview` 定义 `BeautyProcessor` 端口注入，无注入不显示「美颜」工具。
- 参数：磨皮（脸内 bilateral + 边缘保护）、美白（Y 通道曲线）、锐化。
  **瘦脸大眼 P1 不做**（mesh warp 自然度需专项打磨）。
- 处理结果 = **烘焙位图（预处理源替换）**，不进 ops 参数化重放（每次重放推理会卡死预览）；
  烘焙结果进 renderBase 缓存链，undo 换回原底图。
- 实时性目标：非相机实时，拍照后一次性处理 2s 内出结果。

### P2 贴纸 / 整形 / AI / GPU 预览（挂账不排期）

- 贴纸：`EditOp.Sticker` 是 ops 模型最便宜一扩；成本在自制角色贴纸资产（她的人设印章、
  聊天气泡框——差异化所在）。
- 瘦脸大眼：landmark mesh warp 专项。
- AI 抠图/背景虚化（MediaPipe Selfie Segmentation）、超分/修复（Real-ESRGAN/GFPGAN，
  **模型权重要逐个核对商用许可**）。
- **GPU 预览增强（AGSL RuntimeShader + 3D LUT 纹理）**：P0 的 CPU 路径真机帧率不达标时
  启动；届时需锁 LUT 纹理布局（建议 1089×53 RGBA8888 切片）与降级策略（builder 创建期
  catch + 机型开关，**驱动 crash 无法靠 try/catch 捕获**），并补 instrumentation 级
  两路一致性测试。

## 5. 裁量记录（builder check 问题的定案，执行者照办不必再问）

1. **Crop 坐标系**：存"当前显示画幅"归一化（非原图空间，非标注空间）。因旋转/镜像在
   预处理链头部且裁剪后禁再旋转（裁量 2），"当前显示画幅"在全生命周期内唯一，不存在
   解释漂移。标注仍存原图空间——两个空间由 EditCoords 的 crop-aware 函数换算。
2. **裁后禁旋转/镜像**：有 Crop 后旋转/镜像按钮置灰；进裁剪模式二次确认替换旧 Crop。
   由此"多条 Crop 语义/裁后再旋重映射"两个问题不存在。
3. **EditCoords 是唯一换算层**：crop 窗口变换集中加在这里（crop-aware 重载 +
   `displayRect` 概念），PhotoEditor 的 fitRect/toDisplayNorm、HitTest、live 叠加全部
   经它；`editsWithoutOps` 不再滤 Crop。禁止在其他文件发明第二套换算。
4. **预览底图/标注分层**：`renderBase`（无标注）+ 叠加层画标注；Filter 只进底图。
   这同时是 renderBase 缓存的边界（缓存里没有标注，换文字内容不重跑 LUT）。
5. **P0 不引 AGSL**：Robolectric 无 GPU（一致性测试跑不了）、CPU try/catch 罩不住
   RenderThread crash、LUT 纹理布局要另定——三条都指向 GPU 后置。P0 预览 640 底图
   CPU 三线性（0.3MP ≈ 每帧 10ms 量级），真机 ADY-AL00 验帧率，不达标开 GPU 任务。
6. **LUT 资产程序化生成**：离线脚本（Python，仓库 `tools/`）由曲线/色调参数合成
   33³ .cube；解析器测试用内嵌迷你 2³/4³ cube 做 golden；真实资产文件只验解析成功与
   尺寸。设计后续手调 .cube 直接覆盖 assets 即生效。
7. **多条 Filter = 替换最后一条**（面板互斥），与 Adjust 同款；"列表顺序"无实现含义，
   渲染按类型挑选（现有机制），文档与代码注释都这么写。
8. **renderBase 缓存**：key = (source 位图 identity, 预处理 ops 指纹, maxLongEdge)，
   LruCache(4)；640/1280/导出 4096 三档天然分键。滑杆期间 `withOpsUnrecorded` 只换
   Adjust/Filter 参数 → 命中同 key 的底图缓存跳过旋转/镜像/crop pass。
9. （裁量 5 已覆盖原 AGSL 降级问题；此位留空保持编号稳定。）
10. **导出失败策略**：`render` 保留回退（预览必须有图）；`PhotoEditExporter.export`
    改为检测渲染失败 → **返回 null**，ChatRoute 走现有"编辑失败，已使用原图"路径
    （不覆盖暂存文件），杜绝"静默丢标注写回草稿"。
11. 裁剪最小保留区 = **当前画幅**（非原始 source）边长的 1/8。
12. 滤镜 tab 与选中面板互斥：按 Adjust 现例（选中优先抢面板），不再单定。

## 6. 验收

- 单测（全部沿用 PhotoEditsTest/PhotoRendererTest 风格）：
  - LUT：内嵌迷你 cube 解析 golden；identity LUT 像素不变；抬黑 LUT 黑场像素抬升；
    intensity=0 恒等、50 为 lerp 中点。
  - Crop：`displayWidth/Height` 4 旋转×裁剪组合；EditCoords crop-aware 往返互逆；
    标注重映射纯函数（含出界点保号）；render 交集正确（渐变图断言输出尺寸与边缘像素）。
  - 管线：Adjust→Filter 共存输出与分别作用的合成序一致；renderBase 缓存键含
    maxLongEdge、参数滑杆变化只重跑 LUT pass（用计数器 fake 断言）。
- 真机（ADY-AL00）：选图 → 裁剪 → 滤镜滑杆（拖动目视 ≥ 可用，掉帧记录开 GPU 单）→
  画笔文字在滤镜后颜色不变 → 保存发给她；低端机导出耗时 < 3s；断网/超大图无崩溃。

## 7. 风险与挂账

- 实验期许可策略是用户拍板；**若 app 走向正式发布，需对拷贝过的代码做许可清点**
  （GPL 系只可参考不可拷贝进发布物）。
- CPU LUT 在超大图预览可能仍慢 → GPU 增强任务已挂 P2，触发条件写明。
- 美颜"自然度"是调参苦功，P1 按"能看"验收，不承诺对标醒图。
- OpenCV aar 体积（~90MB 全 ABI）：只打 arm64-v8a，实验包可整包。

## 8. 与其他工作的关系

- 与图片消息 P1 无代码冲突（ChatRoute 不同区域；编辑入口与待发条位置在图片消息
  落地后再对位）。
- ADY-AL00 真机窗口与 `video-channel-bugs` 会话协调（同图片消息计划协调条款）。
