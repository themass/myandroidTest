package com.openapi.myapp.ui.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R
import com.openapi.myapp.bean.vo.NullReturnVo
import com.openapi.myapp.constant.Constants
import com.openapi.myapp.data.BaseService
import com.openapi.myapp.data.UserLoginUtil
import com.openapi.yewu.net.request.CommonResponse

/**
 * 账号注销。
 * <p>
 * Google Play 从 2024 年起把「应用内删除入口 + 网页删除入口」列为强制项：
 * 只要应用支持创建账号，就必须让用户能自己删掉它，且不能隐藏在客服流程后面。
 * 这个页面就是应用内入口；网页入口见 Constants.ACCOUNT_DELETE_WEB_URL。
 * <p>
 * 要求输入密码二次确认——删除不可恢复，误触的代价是丢号。
 */
class DeleteAccountActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "DeleteAccount"
    }

    private val baseService by lazy { BaseService().also { it.setup(this) } }
    private var busy by mutableStateOf(false)
    private var password by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = UserLoginUtil.getUserCache()
        if (user == null) {
            Toast.makeText(this, R.string.password_need_login, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val userName = user.name
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = PvBg, surface = PvCard, primary = PvAccent,
                ),
            ) {
                DeleteAccountScreen(
                    userName = userName,
                    password = password,
                    busy = busy,
                    onPassword = { password = it },
                    onCancel = { finish() },
                    onConfirm = { doDelete(userName, password) },
                    onWeb = {
                        com.openapi.myapp.ui.base.WebViewActivity.startWebViewActivity(
                            this, Constants.ACCOUNT_DELETE_WEB_URL,
                            getString(R.string.delete_account_web), false, false, null,
                        )
                    },
                )
            }
        }
    }

    private fun doDelete(name: String, pwd: String) {
        if (pwd.isBlank()) return
        busy = true
        baseService.postData(
            Constants.getUrl(Constants.API_DEL_ACCOUNT_URL),
            DelAccountForm(name, pwd),
            object : CommonResponse.ResponseOkListener<NullReturnVo>() {
                override fun onResponse(o: NullReturnVo?) {
                    busy = false
                    // 本地登录态必须同时清掉，否则界面还显示着已删除的账号
                    UserLoginUtil.logout(this@DeleteAccountActivity)
                    Toast.makeText(
                        this@DeleteAccountActivity, R.string.delete_account_done, Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    busy = false
                    Toast.makeText(
                        this@DeleteAccountActivity, R.string.delete_account_failed, Toast.LENGTH_LONG,
                    ).show()
                }
            },
            TAG,
            NullReturnVo::class.java,
        )
    }
}

/** 字段必须 public（BeanUtil 靠反射读 declaredFields），所以用 @JvmField。 */
class DelAccountForm(@JvmField val name: String, @JvmField val pwd: String)

@Composable
private fun DeleteAccountScreen(
    userName: String,
    password: String,
    busy: Boolean,
    onPassword: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onWeb: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PvBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        com.openapi.myapp.ui.base.ComposeBackButton(onBack = onCancel, color = PvText)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.delete_account_title),
            color = PvText, fontSize = 24.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Surface(color = PvCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.delete_account_desc),
                color = PvTextSecondary, fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(text = userName, color = PvText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text(stringResource(R.string.delete_account_password_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(20.dp))
        com.openapi.myapp.ui.account.PvButton(
            text = stringResource(R.string.delete_account_confirm),
            busy = busy,
            enabled = password.isNotBlank(),
            onClick = onConfirm,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_account_cancel), color = PvTextSecondary)
        }
        Spacer(Modifier.height(24.dp))
        // Play 要求：不装 App 的人也能申请删除，这里给网页入口
        TextButton(onClick = onWeb, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_account_web), color = PvAccent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
