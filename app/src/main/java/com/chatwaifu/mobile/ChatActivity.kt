package com.chatwaifu.mobile

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.chatwaifu.mobile.ui.base.ChatWaifuRootView
import com.chatwaifu.mobile.ui.channellist.ChannelListBean
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import com.openapi.commons.common.util.LogUtil
import com.openapi.commons.yewu.ads.base.AdsContext
import com.openapi.ks.chat.R

class ChatActivity : AppCompatActivity() {

    private val chatViewModel: ChatActivityViewModel by lazy {
        ViewModelProvider(this)[ChatActivityViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    ChatWaifu_MobileTheme {
                        ChatWaifuRootView(
                            chatViewModel = chatViewModel,
                            onChannelListClick = {
                                findNavController().navigate(R.id.nav_channel_list)
                            },
                            onChatLogClick = {
                                findNavController().navigate(R.id.nav_chat_log)
                            },
                            onSettingClick = {
                                findNavController().navigate(R.id.nav_setting)
                            }
                        )
                    }
                }
            }
        )
        AdsContext.showNext(this)
        chatViewModel.refreshAllKeys()
        LogUtil.i("进入 mainLoop")
        chatViewModel.mainLoop()
    }

    private fun init(item: ChannelListBean) {
        chatViewModel.currentLive2DModelPath = item.characterPath
        chatViewModel.currentLive2DModelName = item.characterName
        chatViewModel.loadModelSystemSetting(item.characterName)
        chatViewModel.currentVITSModelName = item.characterName
        chatViewModel.loadChatListCache(item.characterName)
        chatViewModel.loadVitsModel(item.characterVitsPath)
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            showToast("no permission...")
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController().navigateUp() || super.onSupportNavigateUp()
    }

    private fun findNavController(): NavController {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }
}