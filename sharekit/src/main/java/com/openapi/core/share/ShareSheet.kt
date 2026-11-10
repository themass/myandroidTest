package com.openapi.core.share

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil

/**
 * 应用内分享面板：底部弹层，列出能处理分享的应用（图标 + 名字），点哪个就直接用哪个应用打开。
 *
 * 分组和顺序由我们定：[COMMON] 里的应用排在「常用」组最前面，其余按名字排在「更多应用」。
 * 系统选择器的样式和顺序不可控，所以不用它。
 */
internal object ShareSheet {

    /** 常用应用（包名），按此顺序排列；装了才显示。 */
    private val COMMON = listOf(
        "com.tencent.mm",              // 微信
        "com.tencent.mobileqq",        // QQ
        "com.alibaba.android.rimet",   // 钉钉
        "com.android.mms",             // 短信
        "com.google.android.apps.messaging",
        "com.android.email",           // 邮件
        "com.google.android.gm",
        "com.android.bluetooth",       // 蓝牙
    )

    private const val COLUMNS = 4

    class Target(
        val label: String,
        val appLabel: String,
        val icon: Drawable,
        val packageName: String,
        val className: String,
    )

    /**
     * 显示面板。返回 false 表示无法显示（上下文里找不到 Activity），调用方应退回系统选择器。
     * 找到 Activity 但没有任何可用应用时提示并返回 true（已处理）。
     */
    fun show(context: Context, intent: Intent, uri: Uri?, title: String?): Boolean {
        val activity = context.findActivity() ?: return false
        val all = targets(activity, intent)
        if (all.isEmpty()) {
            Toast.makeText(activity, "没有可以分享的应用", Toast.LENGTH_SHORT).show()
            return true
        }
        // 常用组：按 COMMON 的顺序；同一个应用的多个入口（微信：发送给朋友、朋友圈、收藏）都放进来
        val common = COMMON.flatMap { pkg -> all.filter { it.packageName == pkg } }
        val others = all.filter { t -> t.packageName !in COMMON }

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(
            buildContent(
                activity = activity,
                title = title ?: "分享到",
                common = common,
                others = others,
                onPick = { target ->
                    dialog.dismiss()
                    launch(activity, intent, uri, target)
                },
                onCancel = { dialog.dismiss() },
            ),
        )
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.4f)
        }
        dialog.show()
        return true
    }

    /**
     * 能处理这个分享 intent 的入口（去掉本应用）。每个入口一项：微信会有「发送给朋友」「朋友圈」「收藏」三项，
     * 名字用入口自己的名字（没有时就是应用名）。
     */
    private fun targets(context: Context, intent: Intent): List<Target> {
        val pm = context.packageManager
        val query = Intent(intent).apply { component = null }
        val infos: List<ResolveInfo> = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
        return infos
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .map { info ->
                Target(
                    label = info.loadLabel(pm).toString(),
                    appLabel = info.activityInfo.applicationInfo.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm),
                    packageName = info.activityInfo.packageName,
                    className = info.activityInfo.name,
                )
            }
            .sortedWith(compareBy({ it.appLabel }, { it.label }))
    }

    /**
     * 直接启动选中的入口。读取权限给两次：Intent 上的标志（随 ClipData 生效），
     * 再按包名显式授权（接收方在别的 Activity 里读文件时也能读到）。
     */
    private fun launch(context: Context, base: Intent, uri: Uri?, target: Target) {
        val intent = Intent(base).apply {
            component = ComponentName(target.packageName, target.className)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (uri != null) {
            context.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "无法打开${target.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildContent(
        activity: Activity,
        title: String,
        common: List<Target>,
        others: List<Target>,
        onPick: (Target) -> Unit,
        onCancel: () -> Unit,
    ): View {
        val metrics = activity.resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), metrics).toInt()

        val cellWidth = (metrics.widthPixels - dp(16) * 2) / COLUMNS
        val cellHeight = dp(88)
        val headerHeight = dp(32)

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        if (common.isNotEmpty()) {
            column.addView(sectionHeader(activity, "常用"))
            column.addView(grid(activity, common, cellWidth, cellHeight, onPick))
        }
        if (others.isNotEmpty()) {
            column.addView(sectionHeader(activity, "更多应用"))
            column.addView(grid(activity, others, cellWidth, cellHeight, onPick))
        }

        // 应用多时限制高度，内容区可滚动；少时按内容高度包裹
        val rows = ceil(common.size / COLUMNS.toDouble()).toInt() + ceil(others.size / COLUMNS.toDouble()).toInt()
        val estimated = rows * cellHeight + (if (common.isNotEmpty()) headerHeight else 0) + (if (others.isNotEmpty()) headerHeight else 0)
        val maxHeight = (metrics.heightPixels * 0.55f).toInt()
        val scroll = ScrollView(activity).apply {
            addView(column)
        }
        val scrollParams = if (estimated > maxHeight) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val radius = dp(16).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        root.addView(TextView(activity).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#1A1A1A"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(scroll, scrollParams)
        root.addView(TextView(activity).apply {
            text = "取消"
            textSize = 16f
            setTextColor(Color.parseColor("#1A1A1A"))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { onCancel() }
            val outValue = TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        return root
    }

    private fun sectionHeader(activity: Activity, text: String): View = TextView(activity).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#8A8A8E"))
        setPadding(dp(activity, 4), dp(activity, 12), 0, dp(activity, 4))
    }

    private fun grid(
        activity: Activity,
        items: List<Target>,
        cellWidth: Int,
        cellHeight: Int,
        onPick: (Target) -> Unit,
    ): GridLayout = GridLayout(activity).apply {
        columnCount = COLUMNS
        items.forEach { target ->
            addView(cell(activity, target, cellWidth, cellHeight, onPick))
        }
    }

    private fun cell(
        activity: Activity,
        target: Target,
        width: Int,
        height: Int,
        onPick: (Target) -> Unit,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val outValue = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        setBackgroundResource(outValue.resourceId)
        setPadding(0, dp(activity, 10), 0, dp(activity, 6))
        addView(
            ImageView(activity).apply { setImageDrawable(target.icon) },
            LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52)),
        )
        addView(
            TextView(activity).apply {
                text = target.label
                textSize = 12f
                setTextColor(Color.parseColor("#333333"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
            },
            LinearLayout.LayoutParams(width - dp(activity, 8), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(activity, 6)
            },
        )
        setOnClickListener { onPick(target) }
        layoutParams = ViewGroup.LayoutParams(width, height)
    }

    private fun dp(activity: Activity, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
