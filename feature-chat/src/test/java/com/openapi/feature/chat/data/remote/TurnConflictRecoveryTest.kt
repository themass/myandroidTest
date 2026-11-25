package com.openapi.feature.chat.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 409 恢复策略（任务 2）：先取消服务端那一轮，再重试**一次**。
 * 注入 isConflict 后这条策略不需要 HTTP 桩。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnConflictRecoveryTest {

    private class Conflict : RuntimeException("409")
    private class Other : RuntimeException("500")

    @Test
    fun aConflictCancelsTheServerTurnThenRetriesExactlyOnce() = runTest {
        var attempts = 0
        var cancels = 0
        val result = withTurnConflictRecovery(
            isConflict = { it is Conflict },
            cancelTurn = { cancels++ },
        ) {
            attempts++
            if (attempts == 1) throw Conflict() else "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals("只补一次服务端取消", 1, cancels)
    }

    @Test
    fun aNonConflictRethrowsWithoutCancellingOrRetrying() = runTest {
        var attempts = 0
        var cancels = 0
        val failure = try {
            withTurnConflictRecovery(
                isConflict = { it is Conflict },
                cancelTurn = { cancels++ },
            ) {
                attempts++
                throw Other()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertTrue(failure is Other)
        assertEquals(1, attempts)
        assertEquals(0, cancels)
    }

    @Test
    fun aSecondConflictPropagatesInsteadOfLooping() = runTest {
        // 「别的设备正在跑这一轮」这种 409 重试到底也是 409：只重试一次，异常交出去。
        var attempts = 0
        var cancels = 0
        val second = Conflict()
        val failure = try {
            withTurnConflictRecovery(
                isConflict = { it is Conflict },
                cancelTurn = { cancels++ },
            ) {
                attempts++
                if (attempts == 1) throw Conflict() else throw second
            }
            null
        } catch (e: Exception) {
            e
        }
        assertSame("第二次失败必须原样交出（上层据此报明确文案）", second, failure)
        assertEquals(2, attempts)
        assertEquals(1, cancels)
    }

    @Test
    fun aFailedCancelDoesNotBlockTheRetry() = runTest {
        // 取消接口自己挂了也要重试一次：上一轮可能其实已经结束。
        var attempts = 0
        var logged = 0
        val result = withTurnConflictRecovery(
            isConflict = { it is Conflict },
            cancelTurn = { throw Other() },
            onConflictLogged = { logged++ },
        ) {
            attempts++
            if (attempts == 1) throw Conflict() else "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals("取消失败要留下痕迹", 1, logged)
    }
}
