package com.openapi.myapp.ui.proton

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.openapi.myapp.constant.BaseRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.util.EventBusUtil
import com.openapi.common.util.PreferenceUtils
import com.openapi.ks.free1.R
import com.openapi.myapp.bean.vo.LocationVo
import com.openapi.myapp.bean.vo.VipLocationVo
import com.openapi.myapp.constant.Constants
import com.openapi.myapp.data.BaseService
import com.openapi.myapp.data.LocationFilter
import com.openapi.myapp.data.LocationUtil
import com.openapi.myapp.data.UserLoginUtil
import com.openapi.myapp.data.config.LocationChooseEvent
import com.openapi.myapp.ui.user.LoginActivity
import com.openapi.yewu.net.HttpUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * ProtonVPN 风格的节点列表页：顶部搜索、线路分层标签、节点卡片（序号、名称、类别、延迟胶囊）。
 * 选择逻辑与旧的 LocationItemFragment 保持一致（VIP 检查、保存选中、发送 LocationChooseEvent）。
 */
class ProtonServerListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProtonServerList"

        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, ProtonServerListActivity::class.java))
        }
    }

    private val baseService by lazy { BaseService().also { it.setup(this) } }
    private val groups = mutableStateOf<List<VipLocationVo>>(emptyList())
    private val loading = mutableStateOf(true)
    private val pings = mutableStateOf<Map<Int, Int>>(emptyMap())
    private val pinging = mutableStateOf(false)
    private val selectedId = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedId.value = LocationUtil.getSelectLocationId(this)
        setContent {
            ProtonServerListScreen(
                groups = groups.value,
                loading = loading.value,
                pings = pings.value,
                pinging = pinging.value,
                selectedId = selectedId.value,
                onBack = { finish() },
                onPing = { pingAll() },
                onSelect = { choose(it) },
                onBest = { chooseBest() },
            )
        }
        loadGroups()
    }

    private fun loadGroups() {
        Thread {
            try {
                val info = baseService.getInfoListData(
                    Constants.getUrl(Constants.API_LOCATION_VIP_URL), VipLocationVo::class.java, TAG)
                runOnUiThread {
                    groups.value = info.voList ?: emptyList()
                    loading.value = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading.value = false
                    Toast.makeText(this, R.string.error_network_no_connection, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun allNodes(): List<LocationVo> = groups.value.flatMap { it.list ?: emptyList() }

    /** 对所有带网关的节点并发测延迟；结果一次性回到主线程。超时记为 -1。 */
    private fun pingAll() {
        if (pinging.value) return
        val nodes = allNodes().filter { !it.gateway.isNullOrBlank() }
        pinging.value = true
        Thread {
            val results = ConcurrentHashMap<Int, Int>()
            val pool = Executors.newFixedThreadPool(8)
            nodes.forEach { vo ->
                pool.execute {
                    val ms = HttpUtils.pingVal(vo.gateway)
                    results[vo.id] = if (ms > 0) ms else -1
                }
            }
            pool.shutdown()
            pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)
            runOnUiThread {
                pings.value = HashMap(results)
                pinging.value = false
            }
        }.start()
    }

    /** 最佳线路：测过速的节点里延迟最低的；没有测速结果时提示先 Ping。 */
    private fun bestNode(): LocationVo? {
        val measured = allNodes().filter { (pings.value[it.id] ?: -1) > 0 }
        return measured.minByOrNull { pings.value[it.id] ?: Int.MAX_VALUE }
    }

    private fun choose(vo: LocationVo) {
        val user = UserLoginUtil.getUserCache()
        if (user == null) {
            if (vo.type != 0) {
                Toast.makeText(this, R.string.password_need_login, Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                return
            }
        } else if (user.level < vo.type) {
            val group = groups.value.firstOrNull { it.type == vo.type }
            Toast.makeText(this, group?.desc ?: "", Toast.LENGTH_LONG).show()
            return
        }
        PreferenceUtils.setPrefBoolean(this, Constants.LOCATION_FLAG, true)
        LocationUtil.setLocation(this, vo)
        EventBusUtil.getEventBus().postSticky(LocationChooseEvent())
        finish()
    }

    /** 供“最佳线路”行调用：选中测速最快的节点。 */
    private fun chooseBest() {
        val best = bestNode()
        if (best == null) {
            Toast.makeText(this, R.string.ping_wait, Toast.LENGTH_SHORT).show()
            pingAll()
        } else {
            choose(best)
        }
    }
}

/** 服务端 img 形如 "timeline://img/flag_us.png"，取文件名去映射表找本地国旗；找不到返回 null。 */
internal fun flagResId(img: String?): Int? {
    if (img.isNullOrBlank()) return null
    return BaseRes.img[img.substringAfterLast('/').substringBeforeLast('.')]
}

private val PvBg = Color(0xFF1C1B24)
private val PvCard = Color(0xFF292733)
private val PvSelected = Color(0xFF6D697D)
private val PvText = Color(0xFFE8E8EE)
private val PvTextSecondary = Color(0xFFA7A4B5)
private val PvGreen = Color(0xFF1C9C7C)
private val PvAmber = Color(0xFFFF9900)
private val PvOrange = Color(0xFFFF7A00)
private val PvAlert = Color(0xFFDC3251)
private val PvAccent = Color(0xFF6D4AFF)

@Composable
fun ProtonServerListScreen(
    groups: List<VipLocationVo>,
    loading: Boolean,
    pings: Map<Int, Int>,
    pinging: Boolean,
    selectedId: Int,
    onBack: () -> Unit,
    onPing: () -> Unit,
    onSelect: (LocationVo) -> Unit,
    onBest: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val current = groups.getOrNull(tab)
    val nodes = (current?.list ?: emptyList()).filter { vo ->
        query.isBlank() || listOf(vo.ename, vo.name, vo.cityName, vo.category)
            .any { it?.contains(query, ignoreCase = true) == true }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = PvBg, surface = PvCard, primary = PvAccent)) {
        Column(modifier = Modifier.fillMaxSize().background(PvBg).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) {
                    Text(text = "←", color = PvText, fontSize = 20.sp)
                }
                Text(
                    text = stringResource(R.string.proton_servers_title),
                    color = PvText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPing, enabled = !pinging) {
                    Text(
                        text = if (pinging) stringResource(R.string.proton_pinging) else stringResource(R.string.proton_ping),
                        color = PvAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.proton_search_hint), color = PvTextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (groups.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEachIndexed { index, group ->
                        val active = index == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) PvSelected else PvCard)
                                .clickable { tab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = group.name ?: group.ename ?: "",
                                color = if (active) PvText else PvTextSecondary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (loading) {
                Text(text = stringResource(R.string.proton_loading), color = PvTextSecondary, modifier = Modifier.padding(16.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (tab == 0 && query.isBlank()) {
                    item(key = "best") {
                        ServerRow(
                            index = null,
                            flag = null,
                            name = stringResource(R.string.proton_best_route),
                            subtitle = stringResource(R.string.proton_best_route_hint),
                            ping = null,
                            selected = false,
                            onClick = onBest,
                        )
                    }
                }
                itemsIndexed(nodes, key = { _, vo -> vo.id }) { index, vo ->
                    ServerRow(
                        index = index + 1,
                        flag = flagResId(vo.img),
                        name = vo.ename ?: vo.name ?: "",
                        subtitle = vo.category ?: vo.cityName ?: "",
                        ping = pings[vo.id],
                        selected = vo.id == selectedId,
                        onClick = { onSelect(vo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    index: Int?,
    flag: Int?,
    name: String,
    subtitle: String,
    ping: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PvCard)
            .border(1.5.dp, if (selected) PvGreen else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flag != null) {
            Image(
                painter = painterResource(flag),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 28.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(PvSelected),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = name.firstOrNull()?.uppercase() ?: "•", color = PvText, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index != null) {
                    Text(text = "#$index  ", color = PvGreen, fontSize = 12.sp)
                }
                Text(text = name, color = PvText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(text = subtitle, color = PvTextSecondary, fontSize = 12.sp)
        }
        PingPill(ping)
    }
}

@Composable
private fun PingPill(ping: Int?) {
    val (label, color) = when {
        ping == null -> stringResource(R.string.proton_no_ping) to PvTextSecondary
        ping < 0 -> stringResource(R.string.proton_timeout) to PvAlert
        ping <= 150 -> "$ping ms" to PvGreen
        ping <= 300 -> "$ping ms" to PvAmber
        else -> "$ping ms" to PvOrange
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PvBg).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
