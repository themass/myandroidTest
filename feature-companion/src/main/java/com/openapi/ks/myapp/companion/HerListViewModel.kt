package com.openapi.ks.myapp.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.ks.myapp.dh.DhLabPreferences
import com.openapi.ks.myapp.dh.StageChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- her 形象列表（OpenDesign 稿 02「她长什么样」） ----

data class HerListState(
    /** 这个列表是为谁列的（会话角色 id）。 */
    val roleId: String = "",
    /** 当前会话角色的展示名，用来标「人格不变，只换呈现」。拿不到就空着。 */
    val roleName: String = "",
    /** 这个角色能证明归属的 her，发布态在前。 */
    val items: List<HerSummary> = emptyList(),
    /** 当前舞台选择——读自 [DhLabPreferences.callStageChoice]，不是列表页自己的一份。 */
    val choice: StageChoice = StageChoice.Unset,
    val loading: Boolean = false,
) {
    /** 哪张卡描边、是不是「跟随服务端默认」——由 [herListSelection] 现算，不另存。 */
    val selection: HerListSelection
        get() = herListSelection(choice = choice, roleId = roleId, hers = items)

    /** 注册表对这个角色为空（服务端没上架任何 her），与「拉取失败」是两件事。 */
    val registryEmpty: Boolean get() = !loading && items.isEmpty()
}

/**
 * her 形象列表页的 ViewModel。
 *
 * 数据只有一条来路：[HerRegistryStore]（拉 `/v1/hers`，失败宽出成过期缓存/空表，永不抛）。
 * 写也只有一条去路：[DhLabPreferences.callStageChoice]——通话页的
 * [com.openapi.ks.myapp.dh.resolveCallStage] 仍是引擎的唯一决定点。这里**不**存
 * 「选中的 her id」这种第二份状态：三态里存的是「原形象 / 某引擎」（见 [StageChoice]），
 * 高亮由 [herListSelection] 从它反推。
 */
class HerListViewModel(
    private val registry: HerRegistryStore,
    private val api: CompanionApi,
    private val prefs: DhLabPreferences,
    /** 会话里正在聊的角色：列表只列能证明属于它的 her。 */
    private val roleId: String,
    /** her 包下载→校验→安装（详情抽屉的下载按钮）。null = 不具备下载能力，按钮不兑现。 */
    private val packSync: HerPackSync? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HerListState(roleId = roleId))
    val state: StateFlow<HerListState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // 线程切换在 `HttpCompanionApi` 内部（withContext(IO)），与 CharacterListViewModel
            // 同一口径，这里不再套一层。注册表永不抛（失败宽出成空表）；角色名单独兜底——
            // 名字拿不到不该挡住形象列表。
            val hers = registry.hers()
            val name = runCatching { api.character(roleId).summary.name }.getOrNull().orEmpty()
            _state.update {
                it.copy(
                    roleName = name,
                    items = hersForRole(hers, roleId),
                    choice = prefs.callStageChoice,
                    loading = false,
                )
            }
        }
    }

    /**
     * 点一张卡 = 写一条 [StageChoice]（原形象 / 某引擎），回 false 表示这张不可选（草稿、
     * 或端上还没接入的 renderer），什么都不写——UI 因此既不动高亮也不换舞台。
     */
    fun pick(her: HerSummary): Boolean {
        val choice = stageChoiceForHer(her) ?: return false
        prefs.callStageChoice = choice
        // 偏好不是可观察对象，自己跟一笔，高亮立刻落到被点的卡上。
        _state.update { it.copy(choice = choice) }
        return true
    }

    /** 回到「跟随服务端默认」：三态清回 [StageChoice.Unset]（键删掉），服务端重新有权决定。 */
    fun followServerDefault() {
        prefs.callStageChoice = StageChoice.Unset
        _state.update { it.copy(choice = StageChoice.Unset) }
    }

    // ---- her 详情抽屉（稿 02 第二层：形象 / 人格 / 介绍 / 装饰 + her 包下载安装） ----

    private val _detail = MutableStateFlow<HerDetailState?>(null)

    /** null = 抽屉没开。选择状态仍只有 [DhLabPreferences.callStageChoice] 那一份。 */
    val detailState: StateFlow<HerDetailState?> = _detail.asStateFlow()

    /**
     * 点卡打开抽屉：先回「读取中」，详情拉取（[CompanionApi.herDetail]）失败**宽出成 null**——
     * 抽屉明说「这份详情还取不到」（老服务端 404 / 断网），列表与当前引擎完全不受影响。
     * 包状态（已装哪版）从 [HerPackSync] 现查，不存第二份。
     */
    fun openDetail(her: HerSummary) {
        _detail.value = HerDetailState(summary = her, loading = true)
        viewModelScope.launch {
            val detail = runCatching { api.herDetail(her.id) }.getOrNull()
            val pack = detail?.let { packSync?.stateFor(it) } ?: HerPackState.NotInstalled
            _detail.update {
                if (it?.summary?.id == her.id) it.copy(detail = detail, loading = false, packState = pack) else it
            }
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    /**
     * 下载 / 更新这个 her 的素材包。走 [HerPackSync]（永不抛）：sha256 不过就是
     * [HerPackState.VerifyFailed]，已装版本原封不动，按钮变「校验失败，重试」。
     */
    fun downloadPack() {
        val sync = packSync ?: return
        val detail = _detail.value?.detail ?: return
        if (!detail.model.downloadable) return   // 「暂不可下载」的卡不兑现下载
        _detail.update { it?.copy(packState = HerPackState.Downloading(null)) }
        viewModelScope.launch {
            val final = sync.sync(detail) { progress ->
                _detail.update { if (it?.detail?.id == detail.id) it.copy(packState = progress) else it }
            }
            _detail.update { if (it?.detail?.id == detail.id) it.copy(packState = final) else it }
        }
    }
}

/**
 * 详情抽屉一份状态：摘要（卡片带来的，永远画得出）+ 详情（可能取不到）+ 包状态。
 * 下载按钮文案 / 可点性由 [herDownloadButtonLabel] / [herDownloadButtonEnabled] 现算。
 */
data class HerDetailState(
    val summary: HerSummary,
    val detail: HerDetail? = null,
    val loading: Boolean = false,
    val packState: HerPackState = HerPackState.NotInstalled,
) {
    /** 链接 + 哈希形状 + 版本号三者合格才谈下载；否则按钮说「暂不可下载」。 */
    val downloadable: Boolean get() = detail?.model?.downloadable ?: false
}
