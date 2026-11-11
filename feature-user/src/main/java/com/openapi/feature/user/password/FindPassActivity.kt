package com.openapi.feature.user.password

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.uikit.theme.AppColors
import com.openapi.network.http.BaseService
import com.openapi.network.request.CommonResponse
import com.openapi.feature.user.R
import com.openapi.feature.user.data.form.RegForm
import com.openapi.core.model.NullReturnVo
import com.openapi.core.config.Constants
import com.openapi.uikit.theme.AppMaterialTheme

/**
 * 找回密码。接口沿用原来的 [Constants.API_FINDPASS_URL]（只提交邮箱），这里只改交互：
 * 邮箱格式即时校验、失败有明确提示、发送成功后给出确认页和重发倒计时。
 *
 * 入口在登录页的「忘记密码？」。
 */
class FindPassActivity : ComponentActivity() {

    private val baseService = BaseService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baseService.setup(this)
        setContent {
            AppMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Surface) {
                    FindPassRoute(
                        onBack = { finish() },
                        onSend = ::send,
                    )
                }
            }
        }
    }

    /** [onResult] 参数：null 表示成功，否则为失败原因（用于界面提示）。 */
    private fun send(email: String, onResult: (Boolean) -> Unit) {
        val form = RegForm(null, null, null, null, email, null)
        baseService.postData(
            Constants.getUrl(Constants.API_FINDPASS_URL),
            form,
            object : CommonResponse.ResponseOkListener<NullReturnVo>() {
                override fun onResponse(vo: NullReturnVo) {
                    onResult(true)
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    onResult(false)
                }
            },
            TAG,
            NullReturnVo::class.java,
        )
    }

    override fun onDestroy() {
        baseService.cancelRequest(TAG)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "findpass_tag"
        private const val RESEND_SECONDS = 60
        private val EMAIL_RE = Patterns.EMAIL_ADDRESS

        internal fun isValidEmail(email: String): Boolean =
            email.isNotBlank() && EMAIL_RE.matcher(email.trim()).matches()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindPassRoute(
    onBack: () -> Unit,
    onSend: (email: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }

    // 发送成功后倒计时，倒计时结束前不能重发（防止误点连发邮件）
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            kotlinx.coroutines.delay(1000)
            cooldown -= 1
        }
    }

    fun submit(target: String) {
        val trimmed = target.trim()
        invalid = !FindPassActivity.isValidEmail(trimmed)
        if (invalid) return
        busy = true
        sendFailed = false
        onSend(trimmed) { ok ->
            busy = false
            if (ok) {
                sentTo = trimmed
                cooldown = 60
            } else {
                sendFailed = true
            }
        }
    }

    BackHandler(enabled = sentTo != null) { sentTo = null }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.findpass)) },
            navigationIcon = {
                IconButton(onClick = { if (sentTo != null) sentTo = null else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val sent = sentTo
            if (sent == null) {
                EmailForm(
                    email = email,
                    onEmailChange = {
                        email = it
                        invalid = false
                        sendFailed = false
                    },
                    busy = busy,
                    invalid = invalid,
                    sendFailed = sendFailed,
                    onSubmit = { submit(email) },
                )
            } else {
                SentConfirmation(
                    email = sent,
                    cooldown = cooldown,
                    busy = busy,
                    onResend = { submit(sent) },
                    onBackToLogin = onBack,
                )
            }
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailForm(
    email: String,
    onEmailChange: (String) -> Unit,
    busy: Boolean,
    invalid: Boolean,
    sendFailed: Boolean,
    onSubmit: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.findpass_hint),
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.email_hint)) },
            enabled = !busy,
            singleLine = true,
            isError = invalid || sendFailed,
            supportingText = {
                when {
                    invalid -> Text(stringResource(R.string.findpass_email_invalid), color = AppColors.Error)
                    sendFailed -> Text(stringResource(R.string.findpass_send_failed), color = AppColors.Error)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                keyboard?.hide()
                onSubmit()
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                keyboard?.hide()
                onSubmit()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(R.string.submit)) }
    }
}

@Composable
private fun SentConfirmation(
    email: String,
    cooldown: Int,
    busy: Boolean,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.MarkEmailRead,
            contentDescription = null,
            tint = AppColors.Primary,
            modifier = Modifier.size(64.dp),
        )
        Text(
            stringResource(R.string.findpass_sent_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
        )
        Text(
            stringResource(R.string.findpass_sent_body, email),
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Button(
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(R.string.findpass_back_login)) }
        OutlinedButton(
            onClick = onResend,
            enabled = cooldown == 0 && !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                if (cooldown == 0) stringResource(R.string.findpass_resend)
                else stringResource(R.string.findpass_resend_wait, cooldown),
            )
        }
    }
}
