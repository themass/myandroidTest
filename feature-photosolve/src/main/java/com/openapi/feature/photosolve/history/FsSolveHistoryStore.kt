package com.openapi.feature.photosolve.history

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用文件实现解题记录：一个索引 JSON + 每条记录的图片文件。
 *
 * 为什么不用 Room（项目里已经有）：
 * - 记录里最大的数据是题目图，图片本来就得放文件系统，数据库只能存路径；索引本身很小（几百条也就几十 KB）。
 * - 写一条记录 = 写两张图 + 改一次索引，本来就不是单个事务能覆盖的；用 Room 反而要自己处理
 *   "数据库有记录但图片文件丢了"的情况。
 * - `:feature-photosolve` 现在没有 ksp 插件，引入 Room 要改构建配置（见设计文档）。
 *
 * 并发：所有写操作在同一把 [Mutex] 后面串行。索引先写临时文件再 rename，避免中断留下半个 JSON。
 * 索引损坏时**不抛异常**：备份损坏文件、从空索引开始，用户最多丢历史，不会一进页面就崩。
 *
 * 布局：
 * ```
 * filesDir/photosolve_history/
 *   index.json
 *   img/<id>.jpg     长边 ≤ 1280
 *   thumb/<id>.jpg   长边 ≤ 240
 * ```
 */
class FsSolveHistoryStore(context: Context) : SolveHistoryStore {

    private val root = File(context.applicationContext.filesDir, DIR)
    private val indexFile = File(root, "index.json")
    private val imageDir = File(root, "img")
    private val thumbDir = File(root, "thumb")
    private val lock = Mutex()

    init {
        imageDir.mkdirs()
        thumbDir.mkdirs()
    }

    override suspend fun recent(limit: Int): List<SolveRecord> = withContext(Dispatchers.IO) {
        lock.withLock { readIndex().sortedByDescending { it.updatedAt }.take(limit.coerceAtLeast(1)) }
    }

    override suspend fun get(id: Long): SolveRecord? = withContext(Dispatchers.IO) {
        lock.withLock { readIndex().firstOrNull { it.id == id } }
    }

    override suspend fun save(draft: SolveDraft, image: Bitmap?): SolveRecord = withContext(Dispatchers.IO) {
        lock.withLock {
            val all = readIndex().toMutableList()
            val existingIndex = draft.requestId
                ?.takeIf { it.isNotBlank() }
                ?.let { rid -> all.indexOfFirst { it.requestId == rid } }
                ?.takeIf { it >= 0 }
            val now = System.currentTimeMillis()
            val existing = existingIndex?.let { all[it] }

            val id = existing?.id ?: nextId(all)
            // 图片只在第一次保存时写；重试保存同一题不重复压图（除非上次没存上）
            val paths = if (existing?.imagePath != null && File(existing.imagePath).exists()) {
                existing.imagePath to existing.thumbPath
            } else {
                writeImages(id, image)
            }

            val record = SolveRecord(
                id = id,
                requestId = draft.requestId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                // 用户改过标题就保留，不拿题干覆盖
                title = existing?.title?.takeIf { it.isNotBlank() && it != SolveRecordCodec.defaultTitle(existing.question) }
                    ?: SolveRecordCodec.defaultTitle(draft.question),
                question = draft.question,
                steps = draft.steps,
                answer = draft.answer,
                thinking = draft.thinking,
                subject = draft.subject,
                favorite = existing?.favorite ?: false,
                imagePath = paths.first,
                thumbPath = paths.second,
            )
            if (existingIndex != null) all[existingIndex] = record else all.add(record)
            writeIndex(all)
            record
        }
    }

    override suspend fun rename(id: Long, title: String) = withContext(Dispatchers.IO) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return@withContext
        lock.withLock {
            val all = readIndex().toMutableList()
            val i = all.indexOfFirst { it.id == id }
            if (i >= 0) {
                all[i] = all[i].copy(title = trimmed.take(60), updatedAt = System.currentTimeMillis())
                writeIndex(all)
            }
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            val all = readIndex().toMutableList()
            val i = all.indexOfFirst { it.id == id }
            if (i >= 0) {
                // 收藏不动 updatedAt：列表按"最近看过"排序时，点个收藏不该把旧题顶到最前面
                all[i] = all[i].copy(favorite = favorite)
                writeIndex(all)
            }
        }
    }

    override suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val all = readIndex()
                val target = all.firstOrNull { it.id == id }
                writeIndex(all.filterNot { it.id == id })
                target?.let { deleteFiles(it) }
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            readIndex().forEach { deleteFiles(it) }
            writeIndex(emptyList())
        }
    }

    // ------------------------------------------------------------------

    private fun nextId(all: List<SolveRecord>): Long = (all.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun writeImages(id: Long, image: Bitmap?): Pair<String?, String?> {
        if (image == null || image.width <= 0 || image.height <= 0) return null to null
        val full = File(imageDir, "$id.jpg")
        val thumb = File(thumbDir, "$id.jpg")
        val okFull = runCatching {
            full.outputStream().use { image.scaledToLongEdge(IMAGE_LONG_EDGE).compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }.isSuccess
        val okThumb = runCatching {
            thumb.outputStream().use { image.scaledToLongEdge(THUMB_LONG_EDGE).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }.isSuccess
        return (if (okFull) full.absolutePath else null) to (if (okThumb) thumb.absolutePath else null)
    }

    private fun deleteFiles(record: SolveRecord) {
        record.imagePath?.let { runCatching { File(it).delete() } }
        record.thumbPath?.let { runCatching { File(it).delete() } }
    }

    private fun readIndex(): List<SolveRecord> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            List(arr.length()) { i -> arr.getJSONObject(i).toRecord() }
        }.getOrElse { e ->
            Log.e(TAG, "history index is corrupt, starting empty", e)
            runCatching { indexFile.renameTo(File(root, "index.corrupt-${System.currentTimeMillis()}.json")) }
            emptyList()
        }
    }

    private fun writeIndex(all: List<SolveRecord>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        val tmp = File(root, "index.json.tmp")
        runCatching {
            tmp.writeText(arr.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(indexFile)) {
                // 某些文件系统上 rename 到已存在的文件会失败
                indexFile.delete()
                tmp.renameTo(indexFile)
            }
        }.onFailure { Log.e(TAG, "failed to write history index", it) }
    }

    private fun SolveRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("requestId", requestId ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("title", title)
        put("question", question)
        put("steps", SolveRecordCodec.encodeSteps(steps))
        put("answer", answer)
        put("thinking", thinking)
        put("subject", subject)
        put("favorite", favorite)
        put("imagePath", imagePath ?: JSONObject.NULL)
        put("thumbPath", thumbPath ?: JSONObject.NULL)
    }

    private fun JSONObject.toRecord() = SolveRecord(
        id = getLong("id"),
        requestId = if (isNull("requestId")) null else getString("requestId"),
        createdAt = optLong("createdAt", 0L),
        updatedAt = optLong("updatedAt", 0L),
        title = optString("title").ifBlank { SolveRecordCodec.UNTITLED },
        question = optString("question"),
        steps = SolveRecordCodec.decodeSteps(optString("steps")),
        answer = optString("answer"),
        thinking = optString("thinking"),
        subject = optString("subject"),
        favorite = optBoolean("favorite", false),
        imagePath = if (isNull("imagePath")) null else optString("imagePath").takeIf { it.isNotBlank() },
        thumbPath = if (isNull("thumbPath")) null else optString("thumbPath").takeIf { it.isNotBlank() },
    )

    /** 只缩小不放大；长边已经够小就返回原图。 */
    private fun Bitmap.scaledToLongEdge(maxEdge: Int): Bitmap {
        val longEdge = maxOf(width, height)
        if (longEdge <= maxEdge) return this
        val ratio = maxEdge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            this,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val TAG = "PhotoSolveHistory"
        const val DIR = "photosolve_history"
        const val IMAGE_LONG_EDGE = 1280
        const val THUMB_LONG_EDGE = 240
    }
}
