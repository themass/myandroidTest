package com.openapi.myapp.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.data.local.UserNodeStore
import com.openapi.data.remote.ShareLinkParser
import com.openapi.ks.free1.R

/** 我的节点：粘贴分享链接添加自己的节点（VMess / Trojan），只保存在本机。 */
class UserNodesActivity : AppCompatActivity() {

    private var nodes by mutableStateOf<List<UserNodeStore.UserNode>>(emptyList())
    private var selectedId by mutableStateOf<String?>(null)
    private var input by mutableStateOf("")
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val compose = ComposeView(this)
        compose.setContent {
            UserNodesScreen(
                nodes = nodes,
                selectedId = selectedId,
                input = input,
                message = message,
                onInput = { input = it },
                onAdd = ::addNode,
                onSelect = ::select,
                onDelete = ::delete,
                onUseServer = { select(null) },
            )
        }
        setContentView(compose)
        reload()
    }

    private fun reload() {
        nodes = UserNodeStore.list(this)
        selectedId = UserNodeStore.selected(this)?.id
    }

    private fun addNode() {
        val link = input.trim()
        if (link.isEmpty()) return
        try {
            val node = UserNodeStore.addFromLink(this, link)
            input = ""
            message = getString(R.string.user_node_added, node.name)
            reload()
        } catch (e: ShareLinkParser.ParseException) {
            message = getString(R.string.user_node_invalid, e.message ?: "")
        }
    }

    private fun select(id: String?) {
        UserNodeStore.select(this, id)
        reload()
        Toast.makeText(
            this,
            if (id == null) R.string.user_node_switched else R.string.user_node_selected,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun delete(id: String) {
        UserNodeStore.remove(this, id)
        reload()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, UserNodesActivity::class.java))
        }
    }
}

private val Bg = Color(0xFF1C1B24)
private val Card = Color(0xFF292733)
private val TextMain = Color(0xFFE8E8EE)
private val TextSub = Color(0xFFA7A4B5)
private val Accent = Color(0xFF6D4AFF)

@Composable
private fun UserNodesScreen(
    nodes: List<UserNodeStore.UserNode>,
    selectedId: String?,
    input: String,
    message: String,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUseServer: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent)) {
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
            Text(
                text = stringResource(R.string.user_node_title),
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.user_node_hint), color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.user_node_paste_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.user_node_add))
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(text = message, color = TextSub, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onUseServer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        if (selectedId == null) R.string.user_node_using_server else R.string.user_node_use_server,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(nodes, key = { it.id }) { node ->
                    NodeRow(
                        node = node,
                        selected = node.id == selectedId,
                        onSelect = { onSelect(node.id) },
                        onDelete = { onDelete(node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    node: UserNodeStore.UserNode,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Card)
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (if (selected) "● " else "") + node.name,
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = "${node.protocol} · ${node.server}:${node.port}", color = TextSub, fontSize = 12.sp)
        }
        Text(
            text = stringResource(R.string.user_node_delete),
            color = Accent,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onDelete).padding(8.dp),
        )
    }
}
