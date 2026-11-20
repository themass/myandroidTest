package com.openapi.feature.photosolve.history

/**
 * 解题记录的持久化接口。实现见 [FsSolveHistoryStore]。
 *
 * 为什么要自己定接口：现在的实现是文件 + 索引（不引 Room），但调用方（ViewModel/UI）
 * 只依赖这个接口，以后换数据库不影响界面。
 */
interface SolveHistoryStore {

    /** 最近 [limit] 条，按更新时间倒序。 */
    suspend fun recent(limit: Int = 100): List<SolveRecord>

    /** 按 id 取一条；不存在返回 null。 */
    suspend fun get(id: Long): SolveRecord?

    /**
     * 保存一条新记录。同一个非空 [SolveDraft.requestId] 只保留一条：
     * 重复保存（例如重试成功）时更新原记录而不是新增。
     *
     * @return 保存后的记录（含 id）
     */
    suspend fun save(draft: SolveDraft, image: android.graphics.Bitmap?): SolveRecord

    /** 重命名。空白标题忽略。 */
    suspend fun rename(id: Long, title: String)

    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** 删除记录（连带图片）。 */
    suspend fun delete(id: Long)

    /** 清空全部记录。 */
    suspend fun clear()
}
