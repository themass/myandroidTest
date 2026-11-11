package com.openapi.ks.myapp.data.chat

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.openapi.chat.domain.auth.LoginGate
import com.openapi.ks.chat.R
import com.openapi.core.account.UserLoginUtil
import com.openapi.ks.myapp.ui.user.login.LoginActivity

/** 用现有的账号缓存和登录页实现 [LoginGate]。 */
class LegacyLoginGate : LoginGate {

    override fun ensureLoggedIn(context: Context): Boolean {
        if (UserLoginUtil.getUserCache() != null) return true
        Toast.makeText(context, R.string.need_login, Toast.LENGTH_SHORT).show()
        context.startActivity(Intent(context, LoginActivity::class.java))
        return false
    }
}
