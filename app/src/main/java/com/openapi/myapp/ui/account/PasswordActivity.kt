package com.openapi.myapp.ui.account

import com.openapi.myapp.ui.base.ComposeBackButton

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.util.LogUtil
import com.openapi.ks.free1.R
import com.openapi.myapp.bean.vo.NullReturnVo
import com.openapi.myapp.constant.Constants
import com.openapi.myapp.data.BaseService
import com.openapi.myapp.data.UserLoginUtil
import com.openapi.yewu.net.request.CommonResponse

/**
 * 忘记密码 / 修改密码页面，视觉参考 ProtonVPN 的账号页：深色背景、大标题、圆角输入框、主按钮。
 * 与服务端 /user/findpass.json、/user/resetpass.json、/user/changepass.json 对接。
 */
class PasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_FORGOT = "forgot"
        const val MODE_CHANGE = "change"
        private const val TAG = "PasswordActivity"
    }

    private val mode by lazy { intent.getStringExtra(EXTRA_MODE) ?: MODE_FORGOT }
    private val baseService by lazy { BaseService().also { it.setup(this) } }
    private val busy = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mode == MODE_CHANGE && UserLoginUtil.getUserCache() == null) {
            Toast.makeText(this, R.string.password_need_login, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            PasswordScreen(
                isChange = mode == MODE_CHANGE,
                busy = busy.value,
                onBack = { finish() },
                onSendCode = { email, onSent -> sendCode(email, onSent) },
                onReset = { email, code, pwd, rePwd -> resetPassword(email, code, pwd, rePwd) },
                onChange = { oldPwd, newPwd, rePwd -> changePassword(oldPwd, newPwd, rePwd) },
            )
        }
    }

    private fun sendCode(email: String, onSent: () -> Unit) {
        busy.value = true
        baseService.postData(
            Constants.getUrl(Constants.API_FINDPASS_URL),
            EmailForm(email),
            object : CommonResponse.ResponseOkListener<NullReturnVo>() {
                override fun onResponse(o: NullReturnVo?) {
                    busy.value = false
                    Toast.makeText(this@PasswordActivity, R.string.password_code_sent_toast, Toast.LENGTH_SHORT).show()
                    onSent()
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    busy.value = false
                }
            },
            TAG,
            NullReturnVo::class.java,
        )
    }

    private fun resetPassword(email: String, code: String, pwd: String, rePwd: String) {
        busy.value = true
        baseService.postData(
            Constants.getUrl(Constants.API_RESETPASS_URL),
            ResetForm(email, code, pwd, rePwd),
            object : CommonResponse.ResponseOkListener<NullReturnVo>() {
                override fun onResponse(o: NullReturnVo?) {
                    busy.value = false
                    Toast.makeText(this@PasswordActivity, R.string.password_done, Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    busy.value = false
                }
            },
            TAG,
            NullReturnVo::class.java,
        )
    }

    private fun changePassword(oldPwd: String, newPwd: String, rePwd: String) {
        busy.value = true
        baseService.postData(
            Constants.getUrl(Constants.API_CHANGEPASS_URL),
            ChangeForm(oldPwd, newPwd, rePwd),
            object : CommonResponse.ResponseOkListener<NullReturnVo>() {
                override fun onResponse(o: NullReturnVo?) {
                    busy.value = false
                    Toast.makeText(this@PasswordActivity, R.string.password_done, Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    busy.value = false
                }
            },
            TAG,
            NullReturnVo::class.java,
        )
    }
}

// 字段必须是 public（BeanUtil 通过反射读取 declaredFields），所以用 @JvmField。
class EmailForm(@JvmField val email: String)
class ResetForm(
    @JvmField val email: String,
    @JvmField val code: String,
    @JvmField val pwd: String,
    @JvmField val rePwd: String,
)
class ChangeForm(
    @JvmField val oldPwd: String,
    @JvmField val newPwd: String,
    @JvmField val rePwd: String,
)

private val PvBg = Color(0xFF1C1B24)
private val PvCard = Color(0xFF292733)
private val PvText = Color(0xFFE8E8EE)
private val PvTextSecondary = Color(0xFFA7A4B5)
private val PvAccent = Color(0xFF6D4AFF)

@Composable
fun PasswordScreen(
    isChange: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSendCode: (String, () -> Unit) -> Unit,
    onReset: (String, String, String, String) -> Unit,
    onChange: (String, String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var rePwd by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun notFilled(vararg values: String) = values.any { it.isBlank() }

    MaterialTheme(colorScheme = darkColorScheme(background = PvBg, surface = PvCard, primary = PvAccent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PvBg)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            ComposeBackButton(onBack = onBack, color = PvTextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (isChange) R.string.password_title_change else R.string.password_title_forgot),
                color = PvText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))

            if (isChange) {
                PvField(oldPwd, { oldPwd = it }, stringResource(R.string.password_old), password = true)
                Spacer(Modifier.height(12.dp))
                PvField(newPwd, { newPwd = it }, stringResource(R.string.password_new), password = true)
                Spacer(Modifier.height(12.dp))
                PvField(rePwd, { rePwd = it }, stringResource(R.string.password_confirm), password = true)
                Spacer(Modifier.height(24.dp))
                PvButton(
                    text = stringResource(R.string.password_change_btn),
                    busy = busy,
                    onClick = {
                        if (notFilled(oldPwd, newPwd, rePwd)) {
                            android.widget.Toast.makeText(context, R.string.password_fill, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            onChange(oldPwd, newPwd, rePwd)
                        }
                    },
                )
            } else if (!codeSent) {
                Text(
                    text = stringResource(R.string.password_email_hint),
                    color = PvTextSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                PvField(email, { email = it }, stringResource(R.string.password_email), keyboard = KeyboardType.Email)
                Spacer(Modifier.height(24.dp))
                PvButton(
                    text = stringResource(R.string.password_send_code),
                    busy = busy,
                    onClick = {
                        if (email.isBlank()) {
                            android.widget.Toast.makeText(context, R.string.password_fill, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            onSendCode(email) { codeSent = true }
                        }
                    },
                )
            } else {
                Text(
                    text = stringResource(R.string.password_code_sent, email),
                    color = PvTextSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                PvField(code, { code = it }, stringResource(R.string.password_code), keyboard = KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                PvField(newPwd, { newPwd = it }, stringResource(R.string.password_new), password = true)
                Spacer(Modifier.height(12.dp))
                PvField(rePwd, { rePwd = it }, stringResource(R.string.password_confirm), password = true)
                Spacer(Modifier.height(24.dp))
                PvButton(
                    text = stringResource(R.string.password_reset_btn),
                    busy = busy,
                    onClick = {
                        if (notFilled(code, newPwd, rePwd)) {
                            android.widget.Toast.makeText(context, R.string.password_fill, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            onReset(email, code, newPwd, rePwd)
                        }
                    },
                )
                TextButton(onClick = { codeSent = false }) {
                    Text(text = stringResource(R.string.password_change_email), color = PvTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PvField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
    )
}

@Composable
private fun PvButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PvAccent),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
        } else {
            Text(text = text, fontWeight = FontWeight.Bold)
        }
    }
}
