package com.chatwaifu.mobile.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.openai.CommonConstants
import com.openai.commonutil.StaticDataUtil


val Int.dp : Int
get() {
    if (this <= 0) return 0
    val scale = StaticDataUtil.get(CommonConstants.APPLICATION, Application::class.java).resources?.displayMetrics?.density ?: 0f
    return (this * scale + 0.5f).toInt()
}