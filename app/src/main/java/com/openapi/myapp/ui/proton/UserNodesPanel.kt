package com.openapi.myapp.ui.proton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.data.local.UserNodeStore
import com.openapi.ks.free1.R

/**
 * "Private" 节点面板：用户自己添加的节点（只存本机）。
 * 节点列表页（新旧两套）共用这一个组件，保证样式和操作一致。
 */

@Composable
internal fun UserNodesPanel(
    nodes: List<UserNodeStore.UserNode>,
    selectedUserId: String?,
    onSelect: (String) -> Unit,
    onSave: (String?, String, String, (String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
) {
    // editing: null = 关闭；EDIT_NEW = 新增；其它 = 编辑该 id
    var editing by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun open(node: UserNodeStore.UserNode?) {
        editing = node?.id ?: EDIT_NEW
        name = node?.name ?: ""
        link = node?.link ?: ""
        error = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { open(null) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.user_node_add))
        }
        Spacer(Modifier.height(8.dp))
        if (nodes.isEmpty()) {
            Text(text = stringResource(R.string.user_node_empty), color = PvTextSecondary, fontSize = 13.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(nodes, key = { it.id }) { node ->
                val selected = node.id == selectedUserId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PvCard)
                        .clickable { onSelect(node.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (if (selected) "● " else "") + node.name,
                            color = PvText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${node.protocol.uppercase()} · ${node.server}:${node.port}",
                            color = PvTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { open(node) }) {
                        Text(stringResource(R.string.user_node_edit), color = PvAccent)
                    }
                    TextButton(onClick = { onDelete(node.id) }) {
                        Text(stringResource(R.string.user_node_delete), color = PvAccent)
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = PvCard,
            title = {
                Text(
                    stringResource(if (current == EDIT_NEW) R.string.user_node_add_title else R.string.user_node_edit_title),
                    color = PvText,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.user_node_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text(stringResource(R.string.user_node_link)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(text = it, color = PvAccent, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saving) return@TextButton
                    saving = true
                    error = null
                    onSave(if (current == EDIT_NEW) null else current, name, link) { result ->
                        saving = false
                        if (result == null) editing = null else error = result
                    }
                }) {
                    Text(stringResource(R.string.user_node_save), color = PvAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.user_node_cancel), color = PvTextSecondary)
                }
            },
        )
    }
}

private const val EDIT_NEW = "__new__"

