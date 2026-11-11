package com.openapi.chat.data.remote

import android.content.Context
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.network.http.BaseService
import com.openapi.network.awaitInfoList
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.core.config.Constants
import com.openapi.core.model.CharacterVo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 角色：当前选中的角色（本地偏好）、角色列表和增删改。 */
internal class CharacterRemote(
    private val context: Context,
    private val baseService: BaseService,
    private val remote: ChatRemote,
) {

    private companion object {
        const val PREF_SELECTED_CHARACTER_ID = "chat_selected_character_id"
    }

    fun selectedCharacterId(): Int? {
        val id = PreferenceUtils.getPrefInt(context, PREF_SELECTED_CHARACTER_ID, -1)
        return id.takeIf { it > 0 }
    }

    fun setSelectedCharacterId(characterId: Int?) {
        PreferenceUtils.setPrefInt(context, PREF_SELECTED_CHARACTER_ID, characterId ?: -1)
    }

    suspend fun loadCharacters(): List<ChatCharacterOption> = withContext(Dispatchers.IO) {
        runCatching {
            baseService.awaitInfoList(
                Constants.getUrl(Constants.API_CHARACTER_URL),
                CharacterVo::class.java,
                CHAT_TAG,
            )
        }.getOrNull()?.voList.orEmpty().map { it.toOption() }
    }

    suspend fun createCharacter(title: String, content: String, avatarUrl: String?): ChatCharacterOption? {
        // BeanUtil.transBean2Map 用 String.valueOf() 序列化每个字段，null 会变成字面量字符串
        // "null" 发给服务端——用空串兜底，避免这个坑
        val param = CharacterCreateParam(title = title, content = content, url = avatarUrl.orEmpty())
        val vo = remote.postForResult(Constants.API_CHARACTER_CREATE_URL, param, CharacterVo::class.java) ?: return null
        return vo.toOption()
    }

    suspend fun updateCharacter(characterId: Int, title: String, content: String, avatarUrl: String?): Boolean {
        val param = CharacterUpdateParam(id = characterId, title = title, content = content, url = avatarUrl.orEmpty())
        return remote.postForResult(Constants.API_CHARACTER_UPDATE_URL, param, CharacterVo::class.java) != null
    }

    suspend fun deleteCharacter(characterId: Int): Boolean {
        val param = CharacterDeleteParam(id = characterId)
        return remote.postForResult(Constants.API_CHARACTER_DELETE_URL, param, Any::class.java) != null
    }

    private fun CharacterVo.toOption() = ChatCharacterOption(
        id = (id ?: 0L).toInt(),
        title = title.orEmpty(),
        avatarUrl = url,
        preview = content.orEmpty().take(40),
        mine = isMine,
        content = content.orEmpty(),
    )
}
