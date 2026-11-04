package com.openapi.myapp.ui.proton

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.openapi.common.util.EventBusUtil
import com.openapi.myapp.data.config.LocationChooseEvent

/**
 * 旧版节点列表（LocationPageViewFragment）里的 "Private" 页。
 * 选中后关闭父页面（与服务器节点选中后的行为一致）。
 */
class PrivateNodesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                PrivateNodesHost(onChosen = { requireActivity().finish() })
            }
        }
    }
}
