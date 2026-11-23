package com.openapi.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.core.model.ChatLog;
import com.openapi.core.model.ChatMessageHit;

import java.util.Collections;
import java.util.List;

@Dao
public interface ChatLogDao {

    /** 同一条消息（chatId 相同）再次保存时覆盖旧记录，而不是抛唯一约束异常。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(ChatLog log);

    /**
     * 批量 upsert：整页历史一次写入。Room 会把整个 @Insert 列表放进同一个事务，
     * 代替历史刷新里「每条消息一个隐式事务」的逐条 save（切换会话慢的三处之一，见整改清单 P0-6.3）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveAll(List<ChatLog> logs);

    @Query("SELECT * FROM ChatLog WHERE sessionId = :sessionId ORDER BY id ASC")
    List<ChatLog> bySession(long sessionId);

    @Query("DELETE FROM ChatLog WHERE chatId = :chatId")
    void deleteByChatId(String chatId);

    /** 批量删除旧临时 id 行（历史刷新时与 saveAll 同事务提交）。调用方保证列表非空。 */
    @Query("DELETE FROM ChatLog WHERE chatId IN (:chatIds)")
    void deleteByChatIds(List<String> chatIds);

    /** 删除某个会话的全部缓存（会话被删除，或整页刷新前清掉旧缓存）。 */
    @Query("DELETE FROM ChatLog WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);

    /**
     * 只删某个会话的纯文字缓存行。刷新历史页时用这个代替 deleteBySession：
     * 语音/图片/文件（msgType != 0）与本地临时行（local- 前缀）必须活过刷新——
     * 附件的本机路径与语音时长只存在这些行里，服务端历史里没有。
     */
    @Query("DELETE FROM ChatLog WHERE sessionId = :sessionId AND msgType = 0 AND chatId NOT LIKE 'local-%'")
    void deleteSessionTextOnly(long sessionId);

    @Query("DELETE FROM ChatLog")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM ChatLog")
    long count();

    /**
     * 每个会话各自保留最近 [keep] 条纯文字缓存：某一行要删，当且仅当**同一会话**里
     * id 比它新的文字行不少于 [keep] 条。
     * 以前是全表共享一个额度（id <= MAX(id) - keep），多切几个会话后每个会话断网时只剩几行
     * （整改清单 P1-7）。语音、图片、文件（msgType != 0）和本地临时消息（chatId 以 local- 开头）
     * 不删：服务端历史里只有文字，这些记录里的附件信息只存在本机，删了就找不回来了。
     * 用相关子查询而不是窗口函数：低版本 Android 的 SQLite（<3.25）没有 ROW_NUMBER()。
     */
    @Query("DELETE FROM ChatLog WHERE msgType = 0 AND chatId NOT LIKE 'local-%' "
            + "AND (SELECT COUNT(*) FROM ChatLog AS newer "
            + "     WHERE newer.sessionId = ChatLog.sessionId "
            + "       AND newer.msgType = 0 AND newer.chatId NOT LIKE 'local-%' "
            + "       AND newer.id > ChatLog.id) >= :keep")
    void trimToLatest(long keep);

    @Query("SELECT * FROM ChatLog WHERE chatId = :chatId LIMIT 1")
    ChatLog byChatId(String chatId);

    /**
     * 全部本地临时行（`local-` 前缀，服务端历史里没有这些消息），跨会话。
     *
     * 用途只有一个：附件暂存目录做清扫之前，先问一遍「哪些字节还有人引用」。
     * 一条没到 SENT 的本地行（上传一次都没成功过）手里只有本机字节——
     * 服务端那张直传票据 10 分钟就废（ADR-011），重发只能重新上传文件。
     * 按年龄把这些字节删了，那条消息就永久发不出去（复审 Important）。
     * 投递状态在 content 的 JSON 里，SQL 判不了，所以这里只按前缀取行，状态由调用方映射后再筛。
     */
    @Query("SELECT * FROM ChatLog WHERE chatId LIKE 'local-%' ORDER BY id ASC")
    List<ChatLog> localRows();

    /**
     * 跨会话搜消息正文（整改清单 P2-15）。只搜纯文字行（msgType = 0）：
     * 语音/图片/文件行的 content 是本机路径或元数据，搜出来没有意义（同 deleteSessionTextOnly 的理由）。
     * 联 ChatSessionLog 带出会话名，结果才能显示「在哪段对话里」。
     * preview 取正文开头一段就够列表用；最近命中排前（id 单调递增），有上限防止全表扫输出过长。
     */
    @Query("SELECT c.sessionId AS sessionId, s.name AS sessionName, substr(c.content, 1, 120) AS preview "
            + "FROM ChatLog c JOIN ChatSessionLog s ON s.id = c.sessionId "
            + "WHERE c.msgType = 0 AND c.content LIKE '%' || :pattern || '%' ESCAPE '\\' "
            + "ORDER BY c.id DESC LIMIT :limit")
    List<ChatMessageHit> searchContentPattern(String pattern, int limit);

    default List<ChatMessageHit> searchContent(String query, int limit) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        return searchContentPattern(LikeEscapes.escape(query), limit);
    }
}
