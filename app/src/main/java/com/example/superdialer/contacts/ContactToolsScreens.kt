package com.example.superdialer.contacts

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.hasPermission

private val transparentItem
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Manage the app's contact groups: create, rename, delete and pick the members. */
@Composable
fun ContactGroupsScreen(viewModel: ContactsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val editing = ContactGroups.get(editingId)

    if (editing != null) {
        GroupEditor(viewModel, editing, onBack = { editingId = null }, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("연락처 그룹", onBack) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "새 그룹") }
        }
        if (ContactGroups.groups.isEmpty()) {
            Text(
                "그룹이 없습니다. 오른쪽 위 +로 만들고, 연락처 화면 위의 그룹 칩으로 모아 볼 수 있습니다. 그룹은 이 앱 안에서만 쓰이고 폰 연락처는 바뀌지 않습니다.",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(ContactGroups.groups.toList(), key = { it.id }) { group ->
                ListItem(
                    modifier = Modifier.clickable { editingId = group.id },
                    colors = transparentItem,
                    headlineContent = { Text(group.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${group.members.size}명") },
                )
            }
        }
    }

    if (creating) {
        NameDialog(title = "새 그룹", initial = "", onDismiss = { creating = false }) { name ->
            ContactGroups.create(name)?.let { editingId = it.id }
        }
    }
}

@Composable
private fun GroupEditor(viewModel: ContactsViewModel, group: ContactGroup, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val contacts = viewModel.contacts.filter { query.isBlank() || ContactSorting.matches(it, query) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(group.name, onBack) {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "이름 변경") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "그룹 삭제") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("연락처 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            "${group.members.size}명 선택됨",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(contacts, key = { it.id }) { contact ->
                val member = contact.id in group.members
                ListItem(
                    modifier = Modifier.clickable { ContactGroups.setMember(group.id, contact.id, !member) },
                    colors = transparentItem,
                    leadingContent = { InitialAvatar(contact.name, size = 40.dp) },
                    headlineContent = { Text(contact.name) },
                    trailingContent = { Checkbox(checked = member, onCheckedChange = { ContactGroups.setMember(group.id, contact.id, it) }) },
                )
            }
        }
    }

    if (renaming) {
        NameDialog(title = "그룹 이름", initial = group.name, onDismiss = { renaming = false }) { ContactGroups.rename(group.id, it) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("그룹 삭제") },
            text = { Text("'${group.name}' 그룹을 삭제합니다. 연락처는 삭제되지 않습니다.") },
            confirmButton = { TextButton(onClick = { ContactGroups.delete(group.id); confirmDelete = false; onBack() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("이름") }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text); onDismiss() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Contacts that look like the same person: merge them into one contact (undoable in the system contacts app) or delete one. */
@Composable
fun DuplicateContactsScreen(viewModel: ContactsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val groups = remember(viewModel.contacts) { findDuplicates(viewModel.contacts) }

    var pendingMerge by remember { mutableStateOf<DuplicateGroup?>(null) }
    var pendingDelete by remember { mutableStateOf<Contact?>(null) }
    // The action waiting for the contacts write permission.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterPermission
        afterPermission = null
        if (granted) action?.invoke()
        else Toast.makeText(context, "연락처 수정 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }
    fun withWritePermission(action: () -> Unit) {
        if (context.hasPermission(Manifest.permission.WRITE_CONTACTS)) action()
        else { afterPermission = action; writePermission.launch(Manifest.permission.WRITE_CONTACTS) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("중복 연락처 정리", onBack)
        if (groups.isEmpty()) {
            Text("중복된 연락처가 없습니다.", modifier = Modifier.padding(24.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { g -> g.contacts.joinToString("-") { it.id.toString() } }) { group ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            group.reason.label,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        group.contacts.forEach { contact ->
                            ListItem(
                                colors = transparentItem,
                                leadingContent = { InitialAvatar(contact.name, size = 40.dp) },
                                headlineContent = { Text(contact.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(contact.numbers.joinToString(", ") { PhoneNumberFormatter.formatLoose(it) }) },
                                trailingContent = {
                                    IconButton(onClick = { pendingDelete = contact }) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
                                },
                            )
                        }
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Button(onClick = { pendingMerge = group }) { Text("하나로 합치기") }
                        }
                    }
                }
            }
        }
    }

    pendingMerge?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("하나로 합치기") },
            text = { Text("${group.contacts.size}개 연락처를 하나로 합칩니다. 번호와 정보는 모두 남고, 필요하면 폰의 연락처 앱에서 다시 나눌 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingMerge = null
                    withWritePermission {
                        viewModel.mergeContacts(group.contacts.map { it.id }) { ok ->
                            Toast.makeText(context, if (ok) "합쳤습니다." else "합치지 못했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("합치기") }
            },
            dismissButton = { TextButton(onClick = { pendingMerge = null }) { Text("취소") } },
        )
    }
    pendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("연락처 삭제") },
            text = { Text("'${contact.name}' 연락처를 폰에서 삭제합니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    withWritePermission {
                        viewModel.deleteContact(contact.id) { ok ->
                            Toast.makeText(context, if (ok) "삭제했습니다." else "삭제하지 못했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}
