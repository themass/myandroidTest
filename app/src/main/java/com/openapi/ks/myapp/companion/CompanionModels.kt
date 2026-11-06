package com.openapi.ks.myapp.companion

/**
 * 与服务端 companion 服务的接口约定。
 *
 * 服务端实现在 companion-agent 项目的 src/companion/server/sessions.py：
 * POST /companion/sessions 带 {user_id, character_id}，返回房间名和入场 token。
 */
data class SessionRequest(
    val userId: String,
    val characterId: String,
)

data class SessionResponse(
    val sessionId: String,
    val roomName: String,
    val token: String,
    val livekitUrl: String,
    val expiresAt: String,
)
