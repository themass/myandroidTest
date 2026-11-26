package com.openapi.feature.chat.presentation.ui

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
import com.openapi.chatkit.message.MessageMenuStyle
import com.openapi.chatkit.theme.ChatKitColors
import com.openapi.chatkit.theme.ChatKitDefaults
import com.openapi.common.ui.theme.AppColors
import com.openapi.feature.chat.R
import java.io.File

/** 对话页用到的平台能力：剪贴板、分享、打开文件、拍照文件、语音样式偏好。 */
internal object ChatPlatform {

    /** 收口到 :common（cleanup 项 14）：这一处 + 陪伴页 + :app 导出分享，以前是三份字面量。 */
    private const val FILE_PROVIDER = com.openapi.common.ui.AppFileProvider.AUTHORITY

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

    /**
     * 拍照用的输出地址：放在 files/camera 下（FileProvider 已授权 files 根目录）。
     *
     * 这里**不做超龄清扫**（复审 minor）：这条路由「+」面板的点击 lambda 直接调
     * （`ChatRoute` 的 `launchCamera`），跑在主线程上——`listFiles()` + 逐个删文件在慢存储上
     * 就是点一下拍摄卡一下。清扫挂在
     * [com.openapi.feature.chat.presentation.AttachmentStager.stagingDir]（选图/拍照结果回来时
     * 已经在 IO 线程），`camera/` 与 `chat_attachments` 同一次扫、同一份年龄规矩；
     * 这里只留 FileProvider 要求的 `mkdirs()`（一次系统调用，与整改前基线一致）。
     */
    fun newCameraUri(context: Context): Uri {
        val dir = com.openapi.feature.chat.presentation.AttachmentStager.cameraDir(context)
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, FILE_PROVIDER, file)
    }

    /**
     * 「在地图打开」：系统 `geo:` intent（P2-13）。
     *
     * 不用任何地图 SDK：坐标精度本来就是城市街区级，交给用户设备上任何一个地图应用
     * （Maps、高德、百度都认 geo:）。一个都装不上才提示（车载/纯净系统可能真没有）。
     */
    fun openMap(context: Context, latitude: Double, longitude: Double) {
        try {
            // 不拼 ?z= 缩放参数：各地图应用对它的支持不一致，中心点正确比缩放重要
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude"))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.chat_open_map_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private const val PREFS = "chat_voice_style"
    /** v2：默认改为豆包式（Ripple）。换了键名，老的手动选择不再覆盖默认值。 */
    private const val KEY = "style_v2"

    /** 「生成中发消息」prefs（D1）：与语音样式分开存，键名带版本语义。 */
    private const val SEND_MODE_PREFS = "chat_send_mode"
    private const val SEND_MODE_KEY = "mode_v1"

    /**
     * 「生成中发消息」模式（设计 20261126 §2 D1）：默认 **Immediate**（即时发送，
     * 永不本地排队，服务端裁决）；用户可切到 Queue 保留 A36/A40 的本地排队+徽标。
     * 判定在 ChatTurnGate，这里只存取值。
     */
    fun loadSendMode(context: Context): com.openapi.feature.chat.presentation.SendMode {
        val ordinal = context.getSharedPreferences(SEND_MODE_PREFS, Context.MODE_PRIVATE)
            .getInt(SEND_MODE_KEY, com.openapi.feature.chat.presentation.SendMode.Immediate.ordinal)
        return com.openapi.feature.chat.presentation.SendMode.entries.getOrElse(ordinal) {
            com.openapi.feature.chat.presentation.SendMode.Immediate
        }
    }

    fun saveSendMode(context: Context, mode: com.openapi.feature.chat.presentation.SendMode) {
        context.getSharedPreferences(SEND_MODE_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(SEND_MODE_KEY, mode.ordinal).apply()
    }

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

    /** 长按菜单样式（原型对比）：Popup = 横排卡片（选项 2），Sheet = 底部弹层（选项 1）。 */
    val messageMenuStyle: MessageMenuStyle = MessageMenuStyle.Sheet

    /** 用 App 自己的 token 构造组件库配色：用户气泡 ChatOutgoing（浅橙），助手气泡 ChatIncoming，页面 ChatBackground；footer 芯片 ChatOutgoing + Primary。 */
    fun chatKitColors(): ChatKitColors = ChatKitDefaults.colors(
        accent = AppColors.Primary,
        background = AppColors.ChatBackground,
        surface = AppColors.Surface,
        surfaceVariant = AppColors.SurfaceVariant,
        userBubble = AppColors.ChatOutgoing,
        assistantBubble = AppColors.ChatIncoming,
        userBubbleText = AppColors.ChatOutgoingText,
        assistantBubbleText = AppColors.TextPrimary,
        // footer 芯片：浅蓝底、蓝色图标（与暖色气泡区分）
        chipBackground = Color(0xFFE3F2FD),
        chipIcon = Color(0xFF1565C0),
        menuBackground = AppColors.MenuSurface,
        secondaryText = AppColors.TextSecondary,
        divider = AppColors.Divider,
        error = AppColors.Error,
    )
}
