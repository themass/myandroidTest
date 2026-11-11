package com.openapi.feature.video.local

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openapi.uikit.theme.AppColors
import com.openapi.uikit.theme.AppMaterialTheme
import com.openapi.uikit.permission.Permissions
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.video.R
import com.openapi.core.model.RecommendVo
import com.openapi.core.config.Constants
import com.openapi.feature.video.page.VideoShowActivityLazyUrl

@Composable
internal fun VideoRow(video: LocalVideo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val thumb = remember(video.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thumb.value = runCatching {
                context.contentResolver.loadThumbnail(video.uri, Size(240, 160), null)
            }.getOrNull()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            thumb.value?.let { bmp ->
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.name, color = AppColors.TextPrimary, fontSize = 14.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                "${video.folder} · ${formatDuration(video.durationMs)}",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** 用现有播放器播放：本地视频的 content:// 地址直接作为播放地址。 */
internal fun playLocal(context: Context, video: LocalVideo) {
    val vo = RecommendVo().apply {
        title = video.name
        actionUrl = video.uri.toString()
        needLazyUrl = false
        img = ""
    }
    val intent = Intent(context, VideoShowActivityLazyUrl::class.java)
    intent.putExtra(Constants.CONFIG_PARAM, vo)
    context.startActivity(intent)
}

internal fun shareVideo(context: Context, video: LocalVideo) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, video.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

internal fun deleteVideo(context: Context, video: LocalVideo, launch: (IntentSenderRequest) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
        launch(IntentSenderRequest.Builder(pi.intentSender).build())
    } else {
        val ok = runCatching { context.contentResolver.delete(video.uri, null, null) > 0 }.getOrDefault(false)
        if (!ok) ToastUtil.showShort(R.string.localvideo_delete_failed)
    }
}
