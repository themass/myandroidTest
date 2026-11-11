package com.openapi.uikit.share

import androidx.core.content.FileProvider

/**
 * 分享库自己的 FileProvider。
 *
 * 必须是独立的子类：同一个 app 里如果有两个 `<provider>` 都直接用 `androidx.core.content.FileProvider`，
 * 系统按类名只保留一个实例，第二个 authority 的地址会被第一个的路径配置解析，
 * 接收方（微信、钉钉等）打开时就会提示「无法获取资源」。
 *
 * 在本模块的 manifest 里声明（authority 为 `${applicationId}.sharekit`，路径见 `@xml/sharekit_paths`），
 * 使用方不需要再写。
 */
class ShareKitFileProvider : FileProvider()
