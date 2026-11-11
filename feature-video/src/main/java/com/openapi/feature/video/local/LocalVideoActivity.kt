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
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.openapi.common.ui.theme.AppColors
import com.openapi.common.ui.theme.AppMaterialTheme
import com.openapi.common.ui.permission.PermissionGateway
import com.openapi.common.ui.permission.PermissionOutcome
import com.openapi.common.ui.permission.Permissions
import kotlinx.coroutines.launch
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.video.R
import com.openapi.core.model.RecommendVo
import com.openapi.core.config.Constants
import com.openapi.feature.video.page.VideoShowActivityLazyUrl

/** 本地视频（A19）：读取系统相册里的视频，按文件夹筛选，缩略图列表；点按用现有播放器播放，长按分享或删除。 */
class LocalVideoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppMaterialTheme {
                LocalVideoScreen(onBack = { finish() })
            }
        }
    }
}

data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folder: String,
    val durationMs: Long,
    val sizeBytes: Long,
)

internal fun queryLocalVideos(context: Context): List<LocalVideo> {
    val result = mutableListOf<LocalVideo>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
    )
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC",
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val folderCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            result += LocalVideo(
                id = id,
                uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                name = c.getString(nameCol).orEmpty(),
                folder = c.getString(folderCol).orEmpty(),
                durationMs = c.getLong(durCol),
                sizeBytes = c.getLong(sizeCol),
            )
        }
    }
    return result
}

/**
 * 本地视频页。[onBack] 为 null 时不显示标题栏（嵌入到频道页的 tab 里使用）。
 */
@Composable
fun LocalVideoScreen(onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember { Permissions.readVideo() }
    var granted by remember { mutableStateOf(PermissionGateway.isGranted(context, request)) }
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    fun requestPermission() {
        val host = activity as? ComponentActivity ?: return
        scope.launch {
            granted = PermissionGateway.of(host).request(request) == PermissionOutcome.Granted
        }
    }
    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var folder by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<LocalVideo?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    // 删除（Android 11+）需要系统确认：弹出系统的删除确认，成功后刷新列表
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) reloadKey++
    }

    LaunchedEffect(granted, reloadKey) {
        if (granted) videos = queryLocalVideos(context)
    }

    Column(modifier.fillMaxSize().background(AppColors.ChatBackground)) {
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.localvideo_title), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text(stringResource(R.string.localvideo_back), color = AppColors.Primary) }
            }
        }

        if (!granted) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.localvideo_need_permission), color = AppColors.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { requestPermission() }) {
                        Text(stringResource(R.string.localvideo_grant))
                    }
                }
            }
            return@Column
        }

        val folders = remember(videos) { videos.map { it.folder }.filter { it.isNotBlank() }.distinct() }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text(stringResource(R.string.localvideo_all)) })
            }
            items(folders) { name ->
                FilterChip(selected = folder == name, onClick = { folder = name }, label = { Text(name) })
            }
        }
        Spacer(Modifier.height(8.dp))

        val shown = if (folder == null) videos else videos.filter { it.folder == folder }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.localvideo_empty), color = AppColors.TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        onClick = { playLocal(context, video) },
                        onLongClick = { actionTarget = video },
                    )
                }
            }
        }
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(target.name, maxLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    deleteVideo(context, target) { req -> deleteLauncher.launch(req) }
                }) { Text(stringResource(R.string.localvideo_delete), color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    actionTarget = null
                    shareVideo(context, target)
                }) { Text(stringResource(R.string.localvideo_share), color = AppColors.Primary) }
            },
        )
    }
}
