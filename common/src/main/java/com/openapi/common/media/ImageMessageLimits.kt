package com.openapi.common.media

/**
 * 一条消息最多带几张图片。
 *
 * 放在 :common 是因为这条规矩**同时**管着两个页面和两端：「对话」页（:feature-chat）与
 * 陪伴页（:feature-companion）在同一条 `/v1` 发送接口上，而 feature 之间不许互相依赖
 * （:app 的 ModuleGraphTest 第 1 条），所以两边唯一能共用的只有第 0 层。
 *
 * **与服务端同源**：`companion/api/http/routers/chat.py` 的 `SendBody.attachments`
 * `max_length` 取的是 `service/uploads/policy.MAX_MESSAGE_ATTACHMENTS`，就是这个数。
 * 两边各写一份迟早漂开——客户端按 9 裁、服务端按别的数字裁，用户看到的就是一次 422。
 * 改这里必须同时改服务端那两处。
 *
 * 拦截点有两道，缺一不可：
 * - 选择器 `PickMultipleVisualMedia(maxItems = …)`：把超选挡在系统界面里；
 * - 加入草稿时再挡一次：`maxItems` 只是**建议**，厂商 ROM 的选择器可以不认。
 */
const val MAX_MESSAGE_IMAGES: Int = 9

/**
 * 产品上限：一条消息最多带 4 张图片（图片+视频合计的暂存闸也用它，计数语义与
 * [MAX_MESSAGE_IMAGES] 那道闸相同）。
 *
 * **刻意与 9 分开**：9 是服务端传输硬上限（`MAX_MESSAGE_ATTACHMENTS`，改它要两端同步），
 * 4 是产品要的用户体验上限——选择器一次最多给 4 张、草稿托盘最多压 4 个图/视频。
 * 4 ≤ 9，所以客户端永远够不到服务端那道 422 闸；服务端值保持原样不动。
 */
const val MAX_IMAGES_PER_MESSAGE_PICK: Int = 4
