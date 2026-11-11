package com.openapi.cast

/** 投屏操作结果回调，供 Java 调用点使用（见 [JavaCaster]）。 */
fun interface CastCallback {
    /** @param error 成功时为 null */
    fun onDone(error: String?)
}
