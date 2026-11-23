package com.openapi.ks.myapp.companion

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 档案页 ViewModel：角色选择、文档载入、编辑与保存（含失败回滚）。
 * 用可脚本化档案的假 API，不碰网络。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDocViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    /** 只给档案页要用的几个接口留脚本，其余按 error("unused")。 */
    private class DocApi(
        private val doc: ProfileDoc = ProfileDoc(markdown = "", version = null, source = null),
        private val roleIds: List<String> = listOf("linche"),
    ) : CompanionApi {
        var savedMarkdown: String? = null
        var saveCalls = 0
        var saveError: Exception? = null
        override suspend fun profileDoc(roleId: String) = doc
        override suspend fun updateProfileDoc(roleId: String, markdown: String) {
            saveError?.let { throw it }
            saveCalls++
            savedMarkdown = markdown
        }
        override suspend fun characters(limit: Int, offset: Int): CharacterPage =
            CharacterPage(items = roleIds.map { CharacterSummary(it, it, "", emptyList(), true) }, total = roleIds.size)
        override suspend fun createSession(characterId: String): SessionResponse = error("unused")
        override suspend fun createScriptSession(characterId: String, caseId: String): SessionResponse = error("unused")
        override suspend fun history(sessionId: Long, limit: Int): List<CompanionHistoryItem> = error("unused")
        override suspend fun attachmentUrl(uploadId: String): String? = error("unused")
        override suspend fun character(id: String): CharacterDetail = error("unused")
        override suspend fun memory(roleId: String, limit: Int, offset: Int): FactPage = error("unused")
        override suspend fun deleteMemory(roleId: String, id: String) = error("unused")
        override suspend fun profile(roleId: String, includeHistory: Boolean): List<ProfileEntry> = error("unused")
        override suspend fun suppressProfileEntry(roleId: String, id: String) = error("unused")
        override suspend fun deleteProfileEntry(roleId: String, id: String) = error("unused")
        override suspend fun events(roleId: String, includePast: Boolean): List<TimelineEventItem> = error("unused")
        override suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String): TimelineEventItem = error("unused")
        override suspend fun setEventStatus(id: String, status: String) = error("unused")
        override suspend fun deleteEvent(id: String) = error("unused")
        override suspend fun pendingActions(): List<PendingAction> = error("unused")
        override suspend fun confirmAction(confirmationId: String, accepted: Boolean): String = error("unused")
        override suspend fun preferences(): CompanionPreferences = error("unused")
        override suspend fun updatePreferences(patch: Map<String, Any?>): CompanionPreferences = error("unused")
        override suspend fun inbox(): List<InboxMessage> = error("unused")
    }

    @Test
    fun `on load the conversation role is selected and its doc seeded into the draft`() = runTest(dispatcher) {
        val api = DocApi(
            doc = ProfileDoc(markdown = "## 基本情况\n做前端", version = 3, source = "llm"),
            roleIds = listOf("other", "linche"),
        )
        val vm = ProfileDocViewModel(api, currentCharacterId = "linche")
        advanceUntilIdle()
        val st = vm.state.value
        assertEquals("linche", st.roleId)
        assertEquals("## 基本情况\n做前端", st.draft)
        assertEquals("## 基本情况\n做前端", st.doc?.markdown)
        assertFalse(st.docLoading)
    }

    @Test
    fun `when the conversation role is not in the list the first role is selected`() = runTest(dispatcher) {
        val api = DocApi(doc = ProfileDoc("", null, null), roleIds = listOf("a", "b"))
        val vm = ProfileDocViewModel(api, currentCharacterId = "gone")
        advanceUntilIdle()
        assertEquals("a", vm.state.value.roleId)
    }

    @Test
    fun `selecting another role re-seeds the draft and swaps the role`() = runTest(dispatcher) {
        val api = DocApi(doc = ProfileDoc("doc", 1, "llm"), roleIds = listOf("a", "b"))
        val vm = ProfileDocViewModel(api, currentCharacterId = "a")
        advanceUntilIdle()
        assertEquals("a", vm.state.value.roleId)
        vm.selectRole("b")
        advanceUntilIdle()
        assertEquals("b", vm.state.value.roleId)
        assertEquals("doc", vm.state.value.draft)
    }

    @Test
    fun `editing the draft is reflected and clears any saved hint`() = runTest(dispatcher) {
        val api = DocApi(doc = ProfileDoc("old", 1, "llm"))
        val vm = ProfileDocViewModel(api, currentCharacterId = "linche")
        advanceUntilIdle()
        vm.onDraftChange("new")
        assertEquals("new", vm.state.value.draft)
        assertFalse(vm.state.value.savedHint)
    }

    @Test
    fun `saving pushes the draft to the server and shows the saved hint`() = runTest(dispatcher) {
        val api = DocApi(doc = ProfileDoc("old", 1, "llm"))
        val vm = ProfileDocViewModel(api, currentCharacterId = "linche")
        advanceUntilIdle()
        vm.onDraftChange("changed")
        vm.save()
        advanceUntilIdle()
        assertEquals("changed", api.savedMarkdown)
        assertEquals(1, api.saveCalls)
        assertTrue(vm.state.value.savedHint)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a failed save rolls the draft back to the server version and reports the error`() = runTest(dispatcher) {
        val api = DocApi(doc = ProfileDoc("server", 5, "llm"))
        api.saveError = CompanionApiException("boom", 500)
        val vm = ProfileDocViewModel(api, currentCharacterId = "linche")
        advanceUntilIdle()
        vm.onDraftChange("lost edit")
        vm.save()
        advanceUntilIdle()
        assertEquals("server", vm.state.value.draft)      // 回滚
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.savedHint)
        assertEquals(0, api.saveCalls)                      // 没真发出去
    }

    @Test
    fun `save is a no-op when there is no role selected`() = runTest(dispatcher) {
        val api = DocApi(doc = ProfileDoc("", null, null), roleIds = emptyList())
        val vm = ProfileDocViewModel(api, currentCharacterId = "linche")
        advanceUntilIdle()
        assertNull(vm.state.value.roleId)
        vm.save()
        advanceUntilIdle()
        assertEquals(0, api.saveCalls)
    }
}
