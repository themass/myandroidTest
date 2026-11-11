package com.openapi.feature.user.register

import com.openapi.feature.user.login.LoginActivity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.uikit.theme.AppColors
import com.openapi.network.http.BaseService
import com.openapi.network.request.CommonResponse
import com.openapi.common.util.lang.StringUtils
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.user.R
import com.openapi.feature.user.data.form.RegForm
import com.openapi.core.model.NullReturnVo
import com.openapi.core.config.Constants

/**
 * 注册页。原来是 BaseSingleActivity + XML，现在是 Compose。
 *
 * 行为与旧页面一致：提交前校验；成功后提示、关闭本页并跳到登录页；失败时恢复表单。
 * 旧页面的"获取验证码"按钮没有对接任何接口，这里保留按钮但不做任何事（旧版会把整个表单锁死，那是个 bug，没有保留）。
 */
class RegActivity : ComponentActivity() {

    private val baseService = BaseService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baseService.setup(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Surface) {
                    RegScreen(
                        onBack = { finish() },
                        onSubmit = ::submit,
                        onLogin = {
                            finish()
                            startActivity(Intent(this, LoginActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    /** [onDone] 在校验失败或请求失败时调用，用来恢复表单的可编辑状态。 */
    private fun submit(name: String, pwd: String, repwd: String, female: Boolean, onDone: () -> Unit) {
        if (StringUtils.isEmpty(name) || StringUtils.isEmpty(pwd) || StringUtils.isEmpty(repwd)) {
            ToastUtil.showShort(R.string.empty_name_pwd)
            onDone()
            return
        }
        if (pwd != repwd) {
            ToastUtil.showShort(R.string.error_repwd)
            onDone()
            return
        }
        val sex = if (female) Constants.SEX_F else Constants.SEX_M
        val form = RegForm(name, pwd, repwd, sex, "test@gmail.com", "")
        baseService.postData(
            Constants.getUrl(Constants.API_REG_URL),
            form,
            object : CommonResponse.ResponseOkListener<NullReturnVo>() {
                override fun onResponse(vo: NullReturnVo) {
                    ToastUtil.showShort(R.string.reg_success)
                    finish()
                    startActivity(Intent(this@RegActivity, LoginActivity::class.java))
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    onDone()
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
        private const val TAG = "login_tag"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegScreen(
    onBack: () -> Unit,
    onSubmit: (name: String, pwd: String, repwd: String, female: Boolean, onDone: () -> Unit) -> Unit,
    onLogin: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var repwd by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var female by remember { mutableStateOf(false) }
    var sexChosen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.reg)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FormField(name, { name = it }, R.string.username_hint, enabled = !busy)
                FormField(pwd, { pwd = it }, R.string.password_hint, enabled = !busy, password = true)
                FormField(repwd, { repwd = it }, R.string.re_password_hint, enabled = !busy, password = true)
                FormField(email, { email = it }, R.string.email_hint, enabled = !busy)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.re_code)) },
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {}, enabled = !busy) {
                        Text(stringResource(R.string.re_bt_code))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sex), fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = sexChosen && !female, onClick = { female = false; sexChosen = true }, enabled = !busy)
                    Text(stringResource(R.string.male))
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = sexChosen && female, onClick = { female = true; sexChosen = true }, enabled = !busy)
                    Text(stringResource(R.string.female))
                }
                Button(
                    onClick = {
                        if (!sexChosen) {
                            ToastUtil.showShort(R.string.empty_name_pwd)
                            return@Button
                        }
                        busy = true
                        onSubmit(name, pwd, repwd, female) { busy = false }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.reg)) }
                TextButton(onClick = onLogin, enabled = !busy) { Text(stringResource(R.string.login)) }
                Text(stringResource(R.string.remark), fontSize = 12.sp, color = AppColors.TextSecondary)
            }
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    labelRes: Int,
    enabled: Boolean,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}

