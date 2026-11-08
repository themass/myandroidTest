package com.openapi.chat.presentation.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKitColors
import com.openapi.chatkit.theme.ChatKitDefaults
import com.openapi.core.design.theme.AppColors
import com.openapi.ks.chat.R
import java.io.File

/** 对话页用到的平台能力：剪贴板、分享、打开文件、拍照文件、语音样式偏好。 */
internal object ChatPlatform {

    private const val FILE_PROVIDER = "com.openapi.ks.chat.fileprovider"

    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("chat", text))
        Toast.makeText(context, context.getString(R.string.ui_copied), Toast.LENGTH_SHORT).show()
    }

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** 用系统应用打开附件（附件在应用私有目录，经 FileProvider 授权）。 */
    fun openFile(context: Context, path: String, name: String) {
        try {
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER, File(path))
            val extension = name.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.chat_open_file_failed), Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, context.getString(R.string.chat_open_file_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /** 拍照用的输出地址：放在 files/camera 下（FileProvider 已授权 files 根目录）。 */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.filesDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, FILE_PROVIDER, file)
    }

    private const val PREFS = "chat_voice_style"
    /** v2：默认改为豆包式（Ripple）。换了键名，老的手动选择不再覆盖默认值。 */
    private const val KEY = "style_v2"

    fun loadVoiceStyle(context: Context): VoiceStyle {
        val ordinal = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, VoiceStyle.Ripple.ordinal)
        return VoiceStyle.entries.getOrElse(ordinal) { VoiceStyle.Ripple }
    }

    fun saveVoiceStyle(context: Context, style: VoiceStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, style.ordinal).apply()
        val label = when (style) {
            VoiceStyle.Floating -> R.string.chat_voice_style_floating
            VoiceStyle.Ripple -> R.string.chat_voice_style_ripple
            VoiceStyle.Bar -> R.string.chat_voice_style_bar
        }
        Toast.makeText(context, context.getString(label), Toast.LENGTH_SHORT).show()
    }

    /** 用 App 自己的 token 构造组件库配色：用户气泡 ChatOutgoing，助手气泡 ChatIncoming，页面 ChatBackground。 */
    fun chatKitColors(): ChatKitColors = ChatKitDefaults.colors(
        accent = AppColors.Primary,
        background = AppColors.ChatBackground,
        surface = AppColors.Surface,
        surfaceVariant = AppColors.SurfaceVariant,
        userBubble = AppColors.ChatOutgoing,
        assistantBubble = AppColors.ChatIncoming,
        userBubbleText = AppColors.ChatOutgoingText,
        assistantBubbleText = AppColors.TextPrimary,
        secondaryText = AppColors.TextSecondary,
        divider = AppColors.Divider,
        error = AppColors.Error,
    )
}
