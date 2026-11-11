package com.openapi.uikit.dialog

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.openapi.uikit.theme.AppColors

/** Java 可直接用 lambda 实现的无参回调。 */
fun interface DialogAction {
    fun perform()
}

/** Java 可直接用 lambda 实现的列表选中回调。 */
fun interface DialogIndexAction {
    fun onSelected(which: Int)
}

private val AppDialogColors = lightColorScheme(
    primary = AppColors.Primary,
    surface = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
)

/**
 * 与 AlertDialog.Builder 行为对齐的消息对话框。
 * - 按钮：先执行回调，再关闭（AlertDialog 的按钮点击会自动 dismiss）。
 * - cancelable=false：返回键与点击外部都不关闭（等同 setCancelable(false)）。
 * - negativeText 为 null 时不显示第二个按钮（等同不调用 setNegativeButton）。
 */
@Composable
fun AppAlertDialog(
    title: String?,
    message: String?,
    @DrawableRes iconRes: Int?,
    positiveText: String,
    onPositive: DialogAction?,
    negativeText: String?,
    onNegative: DialogAction?,
    cancelable: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = cancelable, dismissOnClickOutside = cancelable),
        icon = if (iconRes != null) {
            { Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(32.dp)) }
        } else null,
        title = if (title != null) {
            { Text(title) }
        } else null,
        text = if (message != null) {
            { Column(Modifier.verticalScroll(rememberScrollState())) { Text(message) } }
        } else null,
        confirmButton = {
            TextButton(onClick = {
                onPositive?.perform()
                onDismiss()
            }) { Text(positiveText) }
        },
        dismissButton = if (negativeText != null) {
            {
                TextButton(onClick = {
                    onNegative?.perform()
                    onDismiss()
                }) { Text(negativeText) }
            }
        } else null,
    )
}

/** 与 AlertDialog.Builder.setItems 对齐的列表对话框：点选某项即回调并关闭。 */
@Composable
private fun AppListDialog(
    title: String?,
    items: List<String>,
    cancelable: Boolean,
    onItem: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = cancelable, dismissOnClickOutside = cancelable),
        title = if (title != null) {
            { Text(title) }
        } else null,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    Text(
                        text = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItem(index) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * Java 调用入口。宿主必须是 ComponentActivity（AppCompatActivity 等），
 * 以提供 Compose 所需的 LifecycleOwner / SavedStateRegistryOwner。
 * 对话框以 ComposeView 挂在 android.R.id.content 上，关闭后移除。
 */
object AppDialogHost {

    @JvmStatic
    fun showMessage(
        activity: Activity,
        title: String?,
        message: String?,
        @DrawableRes iconRes: Int?,
        positiveText: String,
        onPositive: DialogAction?,
        negativeText: String?,
        onNegative: DialogAction?,
        cancelable: Boolean,
    ) {
        attach(activity) { close ->
            AppAlertDialog(
                title = title,
                message = message,
                iconRes = iconRes,
                positiveText = positiveText,
                onPositive = onPositive,
                negativeText = negativeText,
                onNegative = onNegative,
                cancelable = cancelable,
                onDismiss = close,
            )
        }
    }

    @JvmStatic
    fun showItems(
        activity: Activity,
        title: String?,
        items: Array<String>,
        cancelable: Boolean,
        onSelected: DialogIndexAction?,
    ) {
        attach(activity) { close ->
            AppListDialog(
                title = title,
                items = items.toList(),
                cancelable = cancelable,
                onItem = { which ->
                    onSelected?.onSelected(which)
                    close()
                },
                onDismiss = close,
            )
        }
    }

    private fun attach(activity: Activity, content: @Composable (close: () -> Unit) -> Unit) {
        val host = activity as? ComponentActivity
            ?: throw IllegalArgumentException(
                "Compose dialog needs a ComponentActivity host, got ${activity.javaClass.name}"
            )
        val root = host.findViewById<ViewGroup>(android.R.id.content)
        val view = ComposeView(host)
        view.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) {
                MaterialTheme(colorScheme = AppDialogColors) {
                    content {
                        open = false
                        view.post { (view.parent as? ViewGroup)?.removeView(view) }
                    }
                }
            }
        }
        root.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
}
