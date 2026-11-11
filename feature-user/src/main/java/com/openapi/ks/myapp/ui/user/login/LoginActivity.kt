package com.openapi.ks.myapp.ui.user.login

import com.openapi.ks.myapp.ui.user.password.FindPassActivity
import com.openapi.ks.myapp.ui.user.register.RegActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.design.theme.AppColors
import com.openapi.common.net.BaseService
import com.openapi.common.net.request.CommonResponse
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.user.R
import com.openapi.ks.myapp.bean.form.LoginForm
import com.openapi.core.model.UserInfoVo
import com.openapi.core.config.Constants
import com.openapi.core.account.UserLoginUtil
import com.openapi.common.design.theme.AppMaterialTheme

/**
 * 登录页（Compose）。网络和本地偏好与原 Java 实现保持一致：
 * 记住上次的账号密码，登录成功后交给 [UserLoginUtil] 落库并关闭页面。
 */
class LoginActivity : ComponentActivity() {

    private val baseService = BaseService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baseService.setup(this)
        val lastName = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_LAST, null).orEmpty()
        val lastPassword = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_PW_LAST, null).orEmpty()
        setContent {
            AppMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Surface) {
                    LoginScreen(
                        initialName = lastName,
                        initialPassword = lastPassword,
                        onLogin = ::login,
                        onRegister = {
                            finish()
                            startActivity(Intent(this, RegActivity::class.java))
                        },
                        onForgotPassword = {
                            startActivity(Intent(this, FindPassActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    /** [onDone] 在请求失败或输入不完整时调用，用来恢复表单的可编辑状态。 */
    private fun login(name: String, pwd: String, onDone: () -> Unit) {
        if (StringUtils.isEmpty(name) || StringUtils.isEmpty(pwd)) {
            ToastUtil.showShort(R.string.empty_name_pwd)
            onDone()
            return
        }
        PreferenceUtils.setPrefString(this, Constants.LOGIN_USER_LAST, name)
        PreferenceUtils.setPrefString(this, Constants.LOGIN_USER_PW_LAST, pwd)
        val score = PreferenceUtils.getPrefInt(this, Constants.SCORE_TMP, 0)
        val form = LoginForm(name, pwd, score)
        baseService.postData(
            Constants.getUrl(Constants.API_LOGIN_URL),
            form,
            object : CommonResponse.ResponseOkListener<UserInfoVo>() {
                override fun onResponse(vo: UserInfoVo) {
                    PreferenceUtils.setPrefInt(this@LoginActivity, Constants.SCORE_TMP, 0)
                    UserLoginUtil.login(this@LoginActivity, vo)
                    finish()
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    onDone()
                }
            },
            TAG,
            UserInfoVo::class.java,
        )
    }

    override fun onDestroy() {
        baseService.cancelRequest(TAG)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "login_tag"

        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}

@Composable
private fun LoginScreen(
    initialName: String,
    initialPassword: String,
    onLogin: (name: String, pwd: String, onDone: () -> Unit) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var password by remember { mutableStateOf(initialPassword) }
    var loading by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        keyboard?.hide()
        loading = true
        onLogin(name, password) { loading = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 25.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.username_hint)) },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_hint)) },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Button(
                onClick = ::submit,
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp),
            ) {
                Text(stringResource(R.string.login))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onForgotPassword, enabled = !loading) {
                    Text(stringResource(R.string.findpass), color = AppColors.TextSecondary)
                }
                TextButton(onClick = onRegister, enabled = !loading) {
                    Text(stringResource(R.string.reg), color = AppColors.TextSecondary)
                }
            }
            Text(
                text = stringResource(R.string.remark),
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Scrim),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
