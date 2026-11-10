package com.openapi.ks.myapp.ui.channel

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.openapi.core.net.BaseService
import com.openapi.core.util.ToastUtil
import com.openapi.feature.channel.R
import com.openapi.ks.myapp.bean.vo.RecommendVo
import com.openapi.ks.myapp.data.UserLoginUtil
import com.openapi.ks.myapp.ui.base.app.BaseFragment
import com.openapi.ks.myapp.constant.Routes

/**
 * Host fragment for the channel body screens (text / video / movie / image channels).
 * Replaces RecommendFragment for these screens: the list is drawn with Compose, loading and
 * paging are handled by [ChannelBodyController].
 */
abstract class ChannelBodyFragment : BaseFragment() {
    private var controller: ChannelBodyController? = null

    abstract fun getUrl(start: Int): String

    abstract fun getNetTag(): String

    /** Called after the level check passed (legacy onCustomerItemClick body). */
    abstract fun onChannelItemClick(vo: RecommendVo)

    open fun getSpanCount(): Int = 3

    /** true for the image channel: 6 columns with 2/3-wide cells (legacy irregular grid). */
    open fun isIrregularGrid(): Boolean = false

    override fun getRootViewId(): Int = 0

    override fun setContentView(inflater: LayoutInflater): View {
        val activity = requireActivity()
        // Legacy BasePullLoadbleFragment.setupViews side effect.
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        val ctl = controller ?: ChannelBodyController(
            appContext = activity.applicationContext,
            service = BaseService(),
            netTag = getNetTag(),
            urlFor = { start -> getUrl(start) },
        ).also { controller = it }
        ctl.service.setup(activity)
        val spanCount = getSpanCount()
        val irregular = isIrregularGrid()
        return ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ChannelBodyScreen(
                    controller = ctl,
                    spanCount = spanCount,
                    irregularGrid = irregular,
                    onItemClick = { vo -> handleItemClick(vo) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller?.ensureLoaded(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        controller?.service?.cancelRequest(getNetTag())
        super.onDestroyView()
    }

    private fun handleItemClick(vo: RecommendVo) {
        if (!checkUserLevel(vo.type)) {
            return
        }
        onChannelItemClick(vo)
    }

    /** Legacy RecommendFragment.checkUserLevel. */
    protected fun checkUserLevel(type: Int): Boolean {
        if (type > 0) {
            val user = UserLoginUtil.getUserCache()
            if (user == null) {
                startActivity(Routes.login(requireActivity()))
                return false
            }
            if (user.level < type) {
                ToastUtil.showShort(R.string.vip_tips)
                return false
            }
        }
        return true
    }
}
