package com.openapi.feature.user.avatar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openapi.common.media.MediaStager
import com.openapi.common.media.MediaUploadException
import com.openapi.common.media.MediaUploader
import com.openapi.common.net.http.NetConfig
import com.openapi.common.ui.AppFileProvider
import com.openapi.common.ui.permission.PermissionGateway
import com.openapi.common.ui.permission.PermissionOutcome
import com.openapi.common.ui.permission.Permissions
import com.openapi.common.ui.theme.AppColors
import com.openapi.common.ui.theme.AppMaterialTheme
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.core.config.Constants
import com.openapi.feature.user.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 二次元头像生成页（MVP 闭环见 `docs/design/2026-11-24-anime-avatar-mvp.md`）。
 *
 * 入口在「我的」页头像（只属于「我」，不进陪伴角色选择器）。
 * 流程状态全部走 [AvatarFlow]（转移表在 JVM 单测钉死），这里只做三件事：
 * 选图/拍照的 Activity Result、`/v1` 调用、把状态渲染出来。
 *
 * 失败话术全部来自本地字符串表——服务端原始 body 一个字节都不透传
 * （[AvatarGenRemote] 已把 status/code 收敛成 [AvatarUserReason]）。
 */
class AvatarStudioActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppMaterialTheme {
                AvatarStudioScreen(onSaved = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AvatarStudioActivity::class.java)
            if (context !is ComponentActivity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@Composable
fun AvatarStudioScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val host = LocalActivity.current
    val scope = rememberCoroutineScope()

    // 转移表在 AvatarFlow（纯逻辑），UI 只镜像它的 state。
    val flow = remember { AvatarFlow() }
    var state by remember { mutableStateOf(flow.state) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    val uploader = remember {
        val token = { PreferenceUtils.getPrefString(context, NetConfig.tokenHeader(), null) }
        MediaUploader("https://${Constants.BASE_IP}", token)
    }
    val remote = remember {
        val token = { PreferenceUtils.getPrefString(context, NetConfig.tokenHeader(), null) }
        AvatarGenRemote("https://${Constants.BASE_IP}", token)
    }

    /**
     * 拍照输出地址：必须在 files 目录下——`file_provider_paths.xml` 只授权
     * `files-path`/`external-path`，cacheDir 会让 FileProvider 抛 IllegalArgumentException
     * （聊天侧同款：AttachmentStager 的 cameraDir 也在 files 下）。
     */
    fun cameraFile(): File {
        val dir = File(context.filesDir, "avatar_camera")
        dir.mkdirs()
        return File(dir, "shot_${System.currentTimeMillis()}.jpg")
    }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun setStage(event: AvatarEvent) { state = flow.on(event) }

    /** Generating 态续跑（Retry 断点在此时不重复上传）。调用方保证 state 已是 Generating。 */
    fun generateFrom(sourceUploadId: String) {
        scope.launch {
            try {
                val genId = remote.generate(sourceUploadId, "anime")
                setStage(AvatarEvent.GenerateDone(genId))
                previewUrl = uploader.signedUrl(genId)
                // 预览换不到签名 URL 不判失败：Apply 内部会再换一次（signedUrl 失败返回 null 语义）。
            } catch (e: Exception) {
                setStage(AvatarEvent.Failure(AvatarFailureStage.GENERATE, reasonFor(e)))
            }
        }
    }

    /** 上传 + 生成一口气跑完（同步语义 MVP），停在 Previewing 等用户确认。 */
    fun uploadAndGenerate(stagedPath: String) {
        scope.launch {
            try {
                setStage(AvatarEvent.UploadStarted)
                val srcId = uploader.upload(File(stagedPath), "image/jpeg")
                setStage(AvatarEvent.UploadDone(srcId))
                generateFrom(srcId)
            } catch (e: Exception) {
                setStage(AvatarEvent.Failure(failureStageFor(e, flow.state), reasonFor(e)))
            }
        }
    }

    fun stageAndRun(uri: Uri) {
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                MediaStager.stageImage(
                    context, uri,
                    File(context.cacheDir, "avatar_stage"),
                    System.currentTimeMillis(),
                )
            }
            if (staged == null) {
                setStage(AvatarEvent.Failure(AvatarFailureStage.STAGE, AvatarUserReason.BAD_IMAGE))
                return@launch
            }
            setStage(AvatarEvent.ImageStaged(staged.absolutePath))
            uploadAndGenerate(staged.absolutePath)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) stageAndRun(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) stageAndRun(uri)
    }

    fun runApply(avatarUploadId: String) {
        scope.launch {
            try {
                setStage(AvatarEvent.ApplyStarted)
                // 服务端确认在前、本地换指针在后：任何一步失败旧头像都原样在。
                remote.apply(avatarUploadId)
                val url = previewUrl ?: uploader.signedUrl(avatarUploadId)
                val bytes = url?.let { remote.download(it) }
                if (bytes == null || bytes.isEmpty()) {
                    setStage(AvatarEvent.Failure(AvatarFailureStage.APPLY, AvatarUserReason.SERVICE_UNAVAILABLE))
                    return@launch
                }
                if (UserAvatarStore.install(context, bytes)) {
                    setStage(AvatarEvent.ApplyDone)
                } else {
                    setStage(AvatarEvent.Failure(AvatarFailureStage.APPLY, AvatarUserReason.UNKNOWN))
                }
            } catch (e: Exception) {
                setStage(AvatarEvent.Failure(AvatarFailureStage.APPLY, reasonFor(e)))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.avatar_title), color = AppColors.TextPrimary, fontSize = 19.sp)

        when (val s = state) {
            AvatarUiState.Idle -> {
                Text(
                    stringResource(R.string.avatar_privacy),
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.avatar_pick)) }
                OutlinedButton(
                    onClick = {
                        val activity = host ?: return@OutlinedButton
                        scope.launch {
                            when (PermissionGateway.of(activity).request(Permissions.camera("拍照生成头像需要使用相机"))) {
                                PermissionOutcome.Granted -> {
                                    val uri = AppFileProvider.uriFor(context, cameraFile())
                                    pendingCameraUri = uri
                                    takePhoto.launch(uri)
                                }
                                // 拒绝/永久拒绝：PermissionGateway 已经向用户解释过，这里不重复弹。
                                else -> Unit
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.avatar_take)) }
            }

            // Staged 是瞬时态：stageAndRun 落盘后立刻 UploadStarted，这里按上传中渲染兜底。
            is AvatarUiState.Staged -> Progress(stringResource(R.string.avatar_uploading))
            is AvatarUiState.Uploading -> Progress(stringResource(R.string.avatar_uploading))
            is AvatarUiState.Generating -> Progress(stringResource(R.string.avatar_generating))
            is AvatarUiState.Applying -> Progress(stringResource(R.string.avatar_applying))

            is AvatarUiState.Previewing -> {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
                Text(stringResource(R.string.avatar_preview_hint), color = AppColors.TextSecondary, fontSize = 13.sp)
                Button(
                    onClick = { runApply(s.avatarUploadId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.avatar_use)) }
                OutlinedButton(
                    onClick = {
                        previewUrl = null
                        setStage(AvatarEvent.Retake)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.avatar_retake)) }
            }

            AvatarUiState.Saved -> {
                Text(stringResource(R.string.avatar_saved), color = AppColors.TextPrimary, fontSize = 15.sp)
                Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.avatar_done))
                }
            }

            is AvatarUiState.Failed -> {
                Text(reasonText(s.reason), color = AppColors.Error, fontSize = 14.sp, textAlign = TextAlign.Center)
                // 按钮始终给：回到哪一阶段由状态机断点决定（暂存文件丢了才回选图）。
                Button(
                    onClick = {
                        previewUrl = null
                        when (val after = flow.on(AvatarEvent.Retry)) {
                            is AvatarUiState.Uploading -> { state = after; uploadAndGenerate(after.stagedPath) }
                            is AvatarUiState.Generating -> { state = after; generateFrom(after.sourceUploadId) }
                            is AvatarUiState.Applying -> { state = after; runApply(after.avatarUploadId) }
                            else -> state = after
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.avatar_retry)) }
                OutlinedButton(
                    onClick = {
                        previewUrl = null
                        setStage(AvatarEvent.Retake)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.avatar_retake)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Progress(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, color = AppColors.TextSecondary, fontSize = 14.sp)
    }
}

private fun reasonFor(e: Exception): AvatarUserReason = when (e) {
    is AvatarGenException -> e.reason
    is MediaUploadException -> when (e.status) {
        401, 403 -> AvatarUserReason.NOT_LOGGED_IN
        413, 415, 422 -> AvatarUserReason.BAD_IMAGE
        429 -> AvatarUserReason.QUOTA
        else -> AvatarUserReason.SERVICE_UNAVAILABLE
    }
    is IOException -> AvatarUserReason.NETWORK
    else -> AvatarUserReason.UNKNOWN
}

/** 失败发生在哪个阶段：以当前状态为准（状态机 Retry 的断点依据）。 */
private fun failureStageFor(e: Exception, current: AvatarUiState): AvatarFailureStage = when (current) {
    is AvatarUiState.Uploading -> AvatarFailureStage.UPLOAD
    is AvatarUiState.Generating -> AvatarFailureStage.GENERATE
    is AvatarUiState.Applying -> AvatarFailureStage.APPLY
    else -> if (e is MediaUploadException) AvatarFailureStage.UPLOAD else AvatarFailureStage.GENERATE
}

@Composable
private fun reasonText(reason: AvatarUserReason): String = stringResource(
    when (reason) {
        AvatarUserReason.NOT_LOGGED_IN -> R.string.avatar_err_not_logged_in
        AvatarUserReason.BAD_IMAGE -> R.string.avatar_err_bad_image
        AvatarUserReason.QUOTA -> R.string.avatar_err_quota
        AvatarUserReason.SERVICE_UNAVAILABLE -> R.string.avatar_err_service
        AvatarUserReason.NETWORK -> R.string.avatar_err_network
        AvatarUserReason.UNKNOWN -> R.string.avatar_err_unknown
    },
)
