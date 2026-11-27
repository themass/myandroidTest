package com.openapi.ks.myapp.companion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openapi.ks.myapp.dh.DhEngineId
import com.openapi.ks.myapp.dh.DhLabPreferences
import com.openapi.ks.myapp.dh.StageChoice
import com.openapi.ks.myapp.dh.resolveCallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * her 列表页的「点卡 → 换引擎」链（OpenDesign 02 的核心承诺）。钉四条：
 *
 * 1. 列表只列这个角色能证明归属的 her，并带上角色名（「人格不变」那句要用）；
 * 2. 点一张可选卡写的就是 [DhLabPreferences.callStageChoice]，且通话页的
 *    [resolveCallStage] 用这条偏好立刻解出新引擎——列表页没有第二份选择状态；
 * 3. 点草稿 / 端上没接入的 renderer：`pick` 回 false，偏好**一个字节都不动**
 *    （Unset 的用户不该被一次误点变成显式选择，那会永久剥夺服务端决定舞台的权力）；
 * 4. 注册表空（老服务端 / 失败宽出）= 空列表，不是错误，页不崩。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HerListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeApi(private val hers: List<HerSummary>) : CompanionApi {
        override suspend fun hers(limit: Int, offset: Int) = HerPage(items = hers, total = hers.size)
        override suspend fun character(id: String) = CharacterDetail(
            summary = CharacterSummary(id, "林澈", "", emptyList(), true),
            description = "", personality = "", scenario = "", firstMessage = "",
            alternateGreetings = emptyList(), skill = "",
        )
        override suspend fun createSession(characterId: String) = error("unused")
        override suspend fun createScriptSession(characterId: String, caseId: String) = error("unused")
        override suspend fun history(sessionId: Long, limit: Int) = error("unused")
        override suspend fun attachmentUrl(uploadId: String) = error("unused")
        override suspend fun characters(limit: Int, offset: Int) = error("unused")
        override suspend fun memory(roleId: String, limit: Int, offset: Int) = error("unused")
        override suspend fun deleteMemory(roleId: String, id: String) = error("unused")
        override suspend fun profileDoc(roleId: String) = error("unused")
        override suspend fun updateProfileDoc(roleId: String, markdown: String) = error("unused")
        override suspend fun events(roleId: String, includePast: Boolean) = error("unused")
        override suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String) = error("unused")
        override suspend fun setEventStatus(id: String, status: String) = error("unused")
        override suspend fun deleteEvent(id: String) = error("unused")
        override suspend fun pendingActions() = error("unused")
        override suspend fun confirmAction(confirmationId: String, accepted: Boolean) = error("unused")
        override suspend fun preferences() = error("unused")
        override suspend fun updatePreferences(patch: Map<String, Any?>) = error("unused")
        override suspend fun inbox() = error("unused")
    }

    private fun her(id: String, renderer: String, status: String = "published") =
        HerSummary(id = id, name = "", tagline = "", renderer = renderer, status = status)

    /** 在 [runTest] 内部建 VM 并等首次载入落地（`advanceUntilIdle` 驱动 init 里那条 launch）。 */
    private fun TestScope.loaded(vararg hers: HerSummary): Pair<HerListViewModel, DhLabPreferences> {
        val prefs = DhLabPreferences(ApplicationProvider.getApplicationContext<Context>())
        prefs.callStageChoice = StageChoice.Unset
        val api = FakeApi(hers.toList())
        val vm = HerListViewModel(HerRegistryStore(api), api, prefs, roleId = "linche")
        advanceUntilIdle()
        return vm to prefs
    }

    @Test
    fun `loading lists only this role's hers and names the role`() = runTest(dispatcher) {
        val (vm, _) = loaded(
            her("simple-linche-v1", "simple"),
            her("simple-sudai-v1", "simple"),
        )
        val st = vm.state.value
        assertEquals(listOf("simple-linche-v1"), st.items.map { it.id })
        assertEquals("林澈", st.roleName)
        assertFalse(st.loading)
    }

    @Test
    fun `picking a live2d her writes the engine choice and the call stage follows`() = runTest(dispatcher) {
        val prefs = DhLabPreferences(ApplicationProvider.getApplicationContext<Context>())
        prefs.callStageChoice = StageChoice.Unset
        val api = FakeApi(listOf(her("simple-linche-v1", "simple"), her("live2d-linche-v2", "live2d")))
        val vm = HerListViewModel(HerRegistryStore(api), api, prefs, roleId = "linche")
        advanceUntilIdle()

        val card = vm.state.value.items.first { it.id == "live2d-linche-v2" }
        assertTrue(vm.pick(card))
        assertEquals(StageChoice.Engine(DhEngineId.LIVE2D), prefs.callStageChoice)
        // 通话页那条单一选择点用同一条偏好解出新引擎——「回通话页即用新引擎」的实据。
        assertEquals(
            DhEngineId.LIVE2D,
            resolveCallStage(prefs.callStageChoice, "linche", vm.state.value.items),
        )
    }

    @Test
    fun `picking the simple her picks the original look explicitly`() = runTest(dispatcher) {
        val prefs = DhLabPreferences(ApplicationProvider.getApplicationContext<Context>())
        prefs.callStageChoice = StageChoice.Unset
        val api = FakeApi(listOf(her("simple-linche-v1", "simple")))
        val vm = HerListViewModel(HerRegistryStore(api), api, prefs, roleId = "linche")
        advanceUntilIdle()

        assertTrue(vm.pick(vm.state.value.items.first()))
        assertEquals(StageChoice.Original, prefs.callStageChoice)
        assertNull(resolveCallStage(prefs.callStageChoice, "linche", vm.state.value.items))
    }

    @Test
    fun `picking a draft or an unhooked renderer changes nothing at all`() = runTest(dispatcher) {
        val prefs = DhLabPreferences(ApplicationProvider.getApplicationContext<Context>())
        prefs.callStageChoice = StageChoice.Unset
        val api = FakeApi(
            listOf(
                her("live2d-linche-v2", "live2d", status = "draft"),
                her("talkinghead-linche-v1", "talkinghead"),
            ),
        )
        val vm = HerListViewModel(HerRegistryStore(api), api, prefs, roleId = "linche")
        advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        for (card in vm.state.value.items) {
            assertFalse("草稿与端上未接入的卡不能兑现点击", vm.pick(card))
        }
        // 一次误点都没落成显式选择：三态仍是 Unset，服务端默认仍有权决定舞台。
        assertEquals(StageChoice.Unset, prefs.callStageChoice)
    }

    @Test
    fun `followServerDefault clears the choice back to unset`() = runTest(dispatcher) {
        val prefs = DhLabPreferences(ApplicationProvider.getApplicationContext<Context>())
        prefs.callStageChoice = StageChoice.Unset
        val api = FakeApi(listOf(her("live2d-linche-v2", "live2d")))
        val vm = HerListViewModel(HerRegistryStore(api), api, prefs, roleId = "linche")
        advanceUntilIdle()

        assertTrue(vm.pick(vm.state.value.items.first()))
        vm.followServerDefault()
        assertEquals(StageChoice.Unset, prefs.callStageChoice)
        assertEquals(StageChoice.Unset, vm.state.value.choice)
        // 回到 Unset 后舞台重新由服务端默认决定：这角色的默认 her 是 live2d，解出 LIVE2D。
        assertEquals(
            DhEngineId.LIVE2D,
            resolveCallStage(prefs.callStageChoice, "linche", vm.state.value.items),
        )
    }

    @Test
    fun `an empty registry is an empty list, not an error`() = runTest(dispatcher) {
        val (vm, _) = loaded()
        val st = vm.state.value
        assertTrue(st.registryEmpty)
        assertFalse(st.loading)
    }
}
