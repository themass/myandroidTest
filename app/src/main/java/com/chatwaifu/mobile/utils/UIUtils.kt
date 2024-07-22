package com.chatwaifu.mobile.ui

import android.content.Context
import android.widget.Toast
import com.openapi.ks.myapp.base.MyApplication

fun showToast(text: String, context: Context = MyApplication.getInstance(), type: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, text, type).show()
}

val Int.dp : Int
get() {
    if (this <= 0) return 0
    val scale = MyApplication.getInstance().resources?.displayMetrics?.density ?: 0f
    return (this * scale + 0.5f).toInt()
}