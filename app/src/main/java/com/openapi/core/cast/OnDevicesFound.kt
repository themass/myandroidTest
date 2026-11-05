package com.openapi.core.cast

/**
 * 设备搜索回调。
 *
 * 用显式的 SAM 接口而不是 Kotlin 的 `(List<DlnaDevice>) -> Unit`：后者编译成
 * `Function1<T, Unit>`，Java 侧的方法引用（void 方法）对不上返回类型，调用点只能写
 * 成笨拙的匿名类 + `return Unit.INSTANCE`。一个专门的接口让 Java 调用点就是
 * `dialog::onDevicesFound` 这样一行。
 */
fun interface OnDevicesFound {
    fun onFound(devices: List<DlnaDevice>)
}
