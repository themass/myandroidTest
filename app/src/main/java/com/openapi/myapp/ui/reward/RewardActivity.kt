package com.openapi.myapp.ui.reward

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R
import com.openapi.myapp.bean.vo.RewardStatusVo
import com.openapi.myapp.constant.Constants
import com.openapi.myapp.data.BaseService
import com.openapi.myapp.data.UserLoginUtil
import com.openapi.myapp.ui.user.LoginActivity
import com.openapi.yewu.ads.admob.AdmobRewardAds
import com.openapi.yewu.ads.base.RewardInterface
import com.openapi.yewu.net.request.CommonResponse

/**
 * 观看广告赚 VIP 时长。打开时若可以观看，会自动弹出一次激励视频。
 * 奖励由服务端（AdMob 回调）发放，这里只展示进度，并在兑换时调用兑换接口。
 */
class RewardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RewardActivity"
        private const val REFRESH_DELAY_MS = 4000L

        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, RewardActivity::class.java))
        }
    }

    private val baseService by lazy { BaseService().also { it.setup(this) } }
    private val status = mutableStateOf<RewardStatusVo?>(null)
    private val busy = mutableStateOf(false)
    private val message = mutableStateOf("")
    private var rewardAds: AdmobRewardAds? = null
    private var autoShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RewardScreen(
                loggedIn = UserLoginUtil.getUserCache() != null,
                status = status.value,
                busy = busy.value,
                message = message.value,
                onBack = { finish() },
                onWatch = { watch() },
                onRedeem = { redeem() },
                onSignIn = { startActivity(Intent(this, LoginActivity::class.java)) },
            )
        }
        if (UserLoginUtil.getUserCache() != null) {
            loadStatus { s ->
                // 第一次进入时自动弹一次激励视频（达到每日上限或冷却中则不弹）
                if (!autoShown && s.todayCount < s.dailyLimit) {
                    autoShown = true
                    watch()
                }
            }
        }
    }

    override fun onDestroy() {
        rewardAds?.onAdDestroy()
        rewardAds = null
        super.onDestroy()
    }

    private fun loadStatus(onLoaded: (RewardStatusVo) -> Unit = {}) {
        baseService.getData(
            Constants.getUrl(Constants.API_REWARD_STATUS_URL),
            object : CommonResponse.ResponseOkListener<RewardStatusVo>() {
                override fun onResponse(o: RewardStatusVo?) {
                    o?.let {
                        status.value = it
                        onLoaded(it)
                    }
                }
            },
            object : CommonResponse.ResponseErrorListener() {},
            TAG,
            RewardStatusVo::class.java,
        )
    }

    private fun watch() {
        val user = UserLoginUtil.getUserCache() ?: return
        val s = status.value
        if (s != null && s.todayCount >= s.dailyLimit) {
            message.value = getString(R.string.reward_daily_limit)
            return
        }
        if (rewardAds == null) {
            rewardAds = AdmobRewardAds(
                this,
                object : RewardInterface.OnAdmobRewardListener {
                    override fun onNoRewardAD() {
                        message.value = getString(R.string.reward_ad_unavailable)
                    }
                },
                user.name,
                object : AdmobRewardAds.OnRewardEarnedListener {
                    override fun onRewardEarned() {
                        message.value = getString(R.string.reward_pending)
                        window.decorView.postDelayed({ loadStatus() }, REFRESH_DELAY_MS)
                    }
                },
            )
        }
        rewardAds?.showAd()
    }

    private fun redeem() {
        busy.value = true
        baseService.postData(
            Constants.getUrl(Constants.API_REWARD_REDEEM_URL),
            null,
            object : CommonResponse.ResponseOkListener<RewardStatusVo>() {
                override fun onResponse(o: RewardStatusVo?) {
                    busy.value = false
                    o?.let {
                        status.value = it
                        message.value = getString(R.string.reward_redeemed)
                        Toast.makeText(this@RewardActivity, R.string.reward_redeemed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    busy.value = false
                }
            },
            TAG,
            RewardStatusVo::class.java,
        )
    }
}

private val RwBg = Color(0xFF1C1B24)
private val RwCard = Color(0xFF292733)
private val RwText = Color(0xFFE8E8EE)
private val RwTextSecondary = Color(0xFFA7A4B5)
private val RwAccent = Color(0xFF6D4AFF)
private val RwGreen = Color(0xFF1C9C7C)

@Composable
private fun RewardScreen(
    loggedIn: Boolean,
    status: RewardStatusVo?,
    busy: Boolean,
    message: String,
    onBack: () -> Unit,
    onWatch: () -> Unit,
    onRedeem: () -> Unit,
    onSignIn: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(background = RwBg, surface = RwCard, primary = RwAccent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RwBg)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) {
                    Text(text = "←  " + stringResource(R.string.password_back), color = RwTextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reward_title),
                color = RwText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.reward_subtitle),
                color = RwTextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(28.dp))

            if (!loggedIn) {
                Text(text = stringResource(R.string.reward_sign_in_hint), color = RwTextSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(R.string.reward_sign_in), enabled = true, onClick = onSignIn)
                return@Column
            }

            val tickets = status?.tickets ?: 0
            val needed = status?.redeemTickets ?: 3
            // 进度圈：每张券一个点
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .border(4.dp, if (tickets >= needed) RwGreen else RwAccent, CircleShape)
                    .background(RwCard),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.reward_progress, tickets, needed),
                        color = RwText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = stringResource(R.string.reward_tickets_label), color = RwTextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RwCard)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.reward_rule_exchange,
                        status?.redeemTickets ?: 3,
                        status?.redeemHours ?: 2,
                    ),
                    color = RwText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reward_rule_daily, status?.dailyLimit ?: 10),
                    color = RwTextSecondary,
                    fontSize = 14.sp,
                )
                Text(text = stringResource(R.string.reward_rule_expire), color = RwTextSecondary, fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.reward_today, status?.todayCount ?: 0, status?.dailyLimit ?: 10),
                    color = RwTextSecondary,
                    fontSize = 14.sp,
                )
                status?.vipEnd?.let {
                    Text(
                        text = stringResource(R.string.reward_vip_until, it),
                        color = RwGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = stringResource(R.string.reward_redeem_btn, status?.redeemHours ?: 2),
                enabled = !busy && tickets >= needed,
                onClick = onRedeem,
            )
            if (tickets < needed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.reward_need_more, needed - tickets),
                    color = RwTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onWatch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.reward_watch_btn), color = RwText, fontWeight = FontWeight.Bold)
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(text = message, color = RwTextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RwAccent),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}
