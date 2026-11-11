package com.openapi.ks.myapp.task

import android.content.Context
import com.openapi.core.app.AppContext
import com.openapi.network.http.HttpClient
import com.openapi.network.http.NetError
import com.openapi.network.http.Response
import com.openapi.network.request.MultipartRequest
import com.openapi.common.util.lang.BeanUtil
import com.openapi.common.util.log.LogUtil
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.ks.myapp.bean.form.LoginForm
import com.openapi.core.model.UserInfoVo
import com.openapi.core.config.Constants
import com.openapi.core.account.UserLoginUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 启动时用上次保存的账号密码自动登录。
 * 原来是 Java 类，用阻塞的 SyncFuture 等结果；现在用挂起函数，请求结果回来后再保存登录信息。
 */
object LoginTask {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic
    fun start(context: Context) {
        scope.launch {
            try {
                val name = PreferenceUtils.getPrefString(AppContext.get(), Constants.LOGIN_USER_LAST, null)
                if (StringUtils.isEmpty(name)) return@launch
                val pwd = PreferenceUtils.getPrefString(AppContext.get(), Constants.LOGIN_USER_PW_LAST, null)
                if (StringUtils.isEmpty(pwd)) return@launch
                val score = PreferenceUtils.getPrefInt(AppContext.get(), Constants.SCORE_TMP, 0)
                val form = LoginForm(name, pwd, score)
                val user = requestLogin(form)
                if (user != null) {
                    UserLoginUtil.login(AppContext.get(), user)
                } else {
                    PreferenceUtils.remove(AppContext.get(), com.openapi.network.http.NetConfig.tokenHeader())
                }
            } catch (e: Exception) {
                LogUtil.e(e)
            }
        }
    }

    /** 失败时抛出 ExecutionException（与原来 future.get() 的行为一致）。 */
    private suspend fun requestLogin(form: LoginForm): UserInfoVo? = suspendCancellableCoroutine { cont ->
        val map: Map<String, String> = BeanUtil.transBean2Map(form)
        val request = MultipartRequest(
            AppContext.get(),
            map,
            Constants.getUrl(Constants.API_LOGIN_URL),
            null,
            object : Response.Listener<UserInfoVo> {
                override fun onResponse(response: UserInfoVo) {
                    if (cont.isActive) cont.resume(response)
                }
            },
            object : Response.ErrorListener {
                override fun onErrorResponse(error: NetError) {
                    if (cont.isActive) cont.resumeWithException(ExecutionException(error))
                }
            },
            UserInfoVo::class.java,
        )
        request.setTag("login")
        HttpClient.addRequest(request)
    }
}
