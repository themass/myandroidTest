package com.openapi.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * `/api/chat/messages.json` 的分页结果（对应服务端 InfoListVo 的 voList / hasMore）。
 * voList 按 id 正序；hasMore 为 true 表示还有更早的消息，用最早一条的 id 作为 beforeId 继续拉。
 */
public class ChatMessagePageVo {
    public List<ChatMessageVo> voList = new ArrayList<>();
    public boolean hasMore;
}
