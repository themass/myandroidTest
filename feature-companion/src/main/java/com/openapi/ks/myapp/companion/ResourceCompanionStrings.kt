package com.openapi.ks.myapp.companion

import android.content.Context
import com.openapi.feature.companion.R

/**
 * [CompanionStrings] 的资源实现（cleanup 项 2）：文字在 feature-companion 资源里
 * （values/ 英文、values-zh/ 中文），这里只按当前系统语言取值。
 *
 * app 在 Application.onCreate 里注入（`CompanionDependencies.installStrings`）。
 * 只应传应用级 Context——它会被一直持有，Activity 传进来就是泄漏。
 */
class ResourceCompanionStrings(private val context: Context) : CompanionStrings {
    override fun uploadPartialFailed() = context.getString(R.string.companion_upload_partial_failed)
    override fun uploadUnsupported() = context.getString(R.string.companion_upload_unsupported)
    override fun attachmentDeliveryFailed() = context.getString(R.string.companion_attachment_delivery_failed)
    override fun uploadInProgress() = context.getString(R.string.companion_upload_in_progress)
    override fun imageLimitReached(max: Int) = context.getString(R.string.companion_attachment_limit_reached, max)
}
