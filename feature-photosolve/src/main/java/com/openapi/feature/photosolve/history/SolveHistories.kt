package com.openapi.feature.photosolve.history

import android.content.Context

/**
 * 单例持有 [SolveHistoryStore]，供 Activity 与 Application 共用（不引 DI 框架）。
 *
 * Application 启动时可以用 [install] 换一个实现（例如以后换成 Room 或加加密）；不装就用默认的文件实现。
 */
object SolveHistories {

    @Volatile
    private var store: SolveHistoryStore? = null

    fun install(store: SolveHistoryStore) {
        this.store = store
    }

    fun current(context: Context): SolveHistoryStore =
        store ?: synchronized(this) {
            store ?: FsSolveHistoryStore(context.applicationContext).also { store = it }
        }
}
