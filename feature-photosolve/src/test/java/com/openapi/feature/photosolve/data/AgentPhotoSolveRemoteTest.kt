package com.openapi.feature.photosolve.data

import com.openapi.feature.photosolve.presentation.PhotoSolveError
import com.openapi.feature.photosolve.presentation.PhotoSolveException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AgentPhotoSolveRemoteTest {
    private lateinit var server: MockWebServer
    private lateinit var remote: AgentPhotoSolveRemote
    private val valid = """{"requestId":"client-1","providerUsed":"vision","subject":"math","questionFound":true,"questions":[{"text":"1+1=?","steps":[{"text":"相加","latex":"1+1=2"}],"finalAnswer":"2"}]}"""

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        remote = AgentPhotoSolveRemote(server.url("/").toString(), { "login-token" })
    }
    @After fun teardown() { server.shutdown() }

    @Test fun rawImageUsesAgentBearerAndPlainJson() = runBlocking {
        server.enqueue(MockResponse().setBody(valid))
        val bytes = byteArrayOf(1, 2, 3)
        val result = SolveResultMapper.map(remote.solve(bytes, "client/1 &x=2"))
        val sent = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("/v1/photosolve/solve", sent.requestUrl!!.encodedPath)
        assertEquals("client/1 &x=2", sent.requestUrl!!.queryParameter("client_request_id"))
        assertEquals("math", sent.requestUrl!!.queryParameter("subject"))
        assertEquals("Bearer login-token", sent.getHeader("Authorization"))
        assertEquals("image/jpeg", sent.getHeader("Content-Type"))
        assertArrayEquals(bytes, sent.body.readByteArray())
        assertEquals("2", result.finalAnswer)
        assertEquals("math", result.subject)
        assertEquals("vision", result.providerUsed)
    }

    @Test fun streamingEndpointEmitsOnlyCompleteStepsAndUsesDoneAsFinal() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                ": keepalive\n\n" +
                    "event: meta\ndata: {\"questionFound\":true}\n\n" +
                    "event: question_text_delta\ndata: {\"questionIndex\":0,\"text\":\"1+1=?\"}\n\n" +
                    "event: thinking_delta\ndata: {\"questionIndex\":0,\"text\":\"先相加\"}\n\n" +
                    "event: step_start\ndata: {\"questionIndex\":0,\"stepIndex\":0}\n\n" +
                    "event: step_delta\ndata: {\"questionIndex\":0,\"stepIndex\":0,\"field\":\"text\",\"text\":\"两个数相加\"}\n\n" +
                    "event: step_delta\ndata: {\"questionIndex\":0,\"stepIndex\":0,\"field\":\"latex\",\"text\":\"$1+1=2$\"}\n\n" +
                    "event: step_end\ndata: {\"questionIndex\":0,\"stepIndex\":0,\"step\":{\"index\":1,\"text\":\"两个数相加\",\"latex\":\"$1+1=2$\"}}\n\n" +
                    "event: answer\ndata: {\"questionIndex\":0,\"finalAnswer\":\"2\",\"finalAnswerLatex\":\"\"}\n\n" +
                    "event: done\ndata: $valid\n\n".replace("$valid", valid),
            ),
        )
        var firstFrame = false
        var thinking = ""
        val questions = mutableListOf<String>()
        val steps = mutableListOf<String>()
        var answer = ""
        val result = remote.solveStreaming(
            byteArrayOf(1), "stream-id", { firstFrame = true }, { thinking += it },
            { questions += it }, { steps += it }, { answer = it },
        )
        val sent = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("/v1/photosolve/solve/stream", sent.requestUrl!!.encodedPath)
        assertEquals("Bearer login-token", sent.getHeader("Authorization"))
        assertEquals("text/event-stream", sent.getHeader("Accept"))
        assertTrue(firstFrame)
        assertEquals("先相加", thinking)
        assertEquals(listOf("1+1=?"), questions)
        assertEquals("两个数相加\n\n\$1+1=2\$", steps.single())
        assertEquals("2", answer)
        assertEquals("2", result.questions.first().finalAnswer)
    }

    @Test fun missingLoginDoesNotSendRequest() = runBlocking {
        val unauthenticated = AgentPhotoSolveRemote(server.url("/").toString(), { null })
        try { unauthenticated.solve(byteArrayOf(1), "id"); fail("must reject login") }
        catch (e: PhotoSolveException) { assertEquals(PhotoSolveError.LOGIN_REQUIRED, e.code) }
        assertEquals(0, server.requestCount)
    }

    @Test fun errorsUseStatusAndKnownCodesWithoutExposingProviderDetails() {
        for ((status, code) in listOf(401 to 40101, 413 to 40003, 422 to 40003, 429 to 42901, 502 to 50001)) {
            assertFailure(code) { remote.decode(status, "<html>private gateway details</html>") }
        }
        assertFailure(42902) { remote.decode(429, """{"detail":{"code":42902,"message":"private provider secret"}}""") }
        assertFailure(40101) { remote.decode(401, """{"detail":{"code":50001}}""") }
    }

    @Test fun malformedSuccessIsRetryableRatherThanCrashingMapper() {
        for (body in listOf("not JSON", "{}", """{"questionFound":true,"questions":[]}""", valid.replace("\"text\":\"1+1=?\"", "\"text\":null"), valid.replace("\"finalAnswer\":\"2\"", "\"finalAnswer\":null"))) {
            assertFailure(50001) { remote.decode(200, body) }
        }
    }

    @Test fun noQuestionResponseMapsToRetake() {
        val response = remote.decode(200, """{"questionFound":false,"questions":[]}""")
        try { SolveResultMapper.map(response); fail("expected retake") }
        catch (e: PhotoSolveException) { assertEquals(PhotoSolveError.NOT_A_QUESTION, e.code) }
    }

    @Test fun cancellingPendingRecognitionCancelsHttpCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task = async { remote.solve(byteArrayOf(1), "cancel") }
        // Let the async request enqueue before waiting for server receipt.
        kotlinx.coroutines.yield()
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
    }

    private fun assertFailure(expected: Int, action: () -> Any) {
        try { action(); fail("expected $expected") }
        catch (e: PhotoSolveException) {
            assertEquals(expected, e.code)
            assertEquals(PhotoSolveError.userMessage(expected), e.message)
        }
    }
}
