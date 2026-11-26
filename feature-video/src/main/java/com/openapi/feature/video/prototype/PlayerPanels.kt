package com.openapi.feature.video.prototype

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.openapi.feature.video.R

/**
 * 新播放页的子面板。用原生对话框承载，因为它们在竖屏、横屏、锁定三种状态下都要能弹出，
 * 而 Compose 的 BottomSheet 需要宿主自己处理 inset；原生对话框由系统负责。
 */
internal object PlayerPanels {

    interface SubtitleHost {
        fun enabled(): Boolean
        fun hasTrack(): Boolean
        fun trackName(): String?
        fun delayMs(): Int
        fun setEnabled(enabled: Boolean)
        fun shiftDelay(deltaMs: Int)
        fun resetDelay()
        fun importFromDevice()
        fun clearTrack()
    }

    fun showSubtitles(context: Context, host: SubtitleHost) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 8))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.np_subtitle_panel_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        fun addRow(
            title: String,
            hint: String?,
            checked: Boolean,
            onClick: () -> Unit,
        ) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val p = dp(context, 12)
                setPadding(p, p, p, p)
                isClickable = true
                setOnClickListener { onClick() }
                if (checked) {
                    background = ColorDrawable(0xFFE7F6EF.toInt())
                }
            }
            row.addView(TextView(context).apply {
                text = if (checked) "$title  ✓" else title
                setTextColor(0xFF16231E.toInt())
                textSize = 16f
            })
            if (!hint.isNullOrBlank()) {
                row.addView(TextView(context).apply {
                    text = hint
                    setTextColor(0xFF5B6B65.toInt())
                    textSize = 12f
                })
            }
            content.addView(row)
        }

        addRow(
            context.getString(R.string.np_subtitle_off),
            context.getString(R.string.np_subtitle_off_hint),
            !host.enabled(),
        ) {
            host.setEnabled(false)
            dialog.dismiss()
        }
        addRow(
            context.getString(R.string.np_subtitle_embedded),
            context.getString(R.string.np_subtitle_none_selected),
            false,
        ) {
            host.setEnabled(true)
        }
        val localTitle = buildString {
            append(context.getString(R.string.np_subtitle_local))
            host.trackName()?.let { append("  ·  ").append(it) }
        }
        addRow(
            localTitle,
            context.getString(R.string.np_subtitle_local_hint),
            host.enabled() && host.hasTrack(),
        ) {
            host.importFromDevice()
            dialog.dismiss()
        }
        addRow(
            context.getString(R.string.np_subtitle_delay, formatDelay(host.delayMs())),
            context.getString(R.string.np_subtitle_delay_hint),
            false,
        ) {
            host.shiftDelay(-500)
            dialog.dismiss()
            showSubtitles(context, host)
        }
        addRow(
            context.getString(R.string.np_subtitle_delay_reset),
            null,
            host.delayMs() == 0,
        ) {
            host.resetDelay()
            dialog.dismiss()
        }
        if (host.hasTrack()) {
            addRow(context.getString(R.string.np_subtitle_clear), null, false) {
                host.clearTrack()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun showSpeeds(context: Context, speeds: FloatArray, current: Float, onPick: (Float) -> Unit) {
        val labels = speeds.map { speedLabel(it) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.np_speed_panel_title)
            .setSingleChoiceItems(labels, speeds.indexOfFirst { it == current }) { dialog, which ->
                onPick(speeds[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 「更多」面板：只列真件（清单与理由在 [MoreMenuPolicy]）。
     * 分享调用由宿主注入——shareVideo 在 local/ 里属于别的会话在动的文件，
     * 这里不直接引用它，只接一个 `(MoreAction)->Unit`。
     */
    fun showMore(context: Context, actions: List<MoreAction>, onPick: (MoreAction) -> Unit) {
        if (actions.isEmpty()) return
        val labels = actions.map { context.getString(labelOf(it)) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.np_more_panel_title)
            .setItems(labels) { dialog, which ->
                onPick(actions[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun labelOf(action: MoreAction): Int = when (action) {
        MoreAction.Share -> R.string.np_more_share
        MoreAction.Cast -> R.string.np_more_cast
        MoreAction.DecoderSettings -> R.string.np_more_decoder_settings
        MoreAction.Pip -> R.string.np_more_pip
    }

    private fun speedLabel(speed: Float): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "$speed×"

    private fun formatDelay(ms: Int): String = if (ms > 0) "+$ms ms" else "$ms ms"

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
