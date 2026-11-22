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
