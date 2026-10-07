package com.example.superdialer.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.superdialer.browser.data.SpeedDial

private const val MAX_TILES = 24

private val tileColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA),
    Color(0xFFF4511E), Color(0xFF00897B), Color(0xFF3949AB), Color(0xFF7CB342),
)

/** What the edit dialog is working on: a new tile, or an existing one. */
private sealed interface EditTarget {
    data object New : EditTarget
    data class Existing(val item: SpeedDial) : EditTarget
}

/** The browser start page: editable link tiles, like a speed dial. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpeedDialGrid(
    dials: List<SpeedDial>,
    onOpen: (SpeedDial) -> Unit,
    onAdd: (title: String, url: String) -> Unit,
    onUpdate: (item: SpeedDial, title: String, url: String) -> Unit,
    onDelete: (SpeedDial) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(dials, key = { it.id }) { item ->
            SpeedDialTile(
                item = item,
                onOpen = { onOpen(item) },
                onEdit = { editing = EditTarget.Existing(item) },
                onDelete = { onDelete(item) },
            )
        }
        if (dials.size < MAX_TILES) {
            item(key = "add") { AddTile(onClick = { editing = EditTarget.New }) }
        }
    }

    editing?.let { target ->
        EditDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { title, url ->
                when (target) {
                    EditTarget.New -> onAdd(title, url)
                    is EditTarget.Existing -> onUpdate(target.item, title, url)
                }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(item: SpeedDial, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val color = tileColors[(item.title.hashCode() and Int.MAX_VALUE) % tileColors.size]
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
                    .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.title.trim().take(1).uppercase(), color = Color.White, fontSize = 26.sp)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("수정") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; onDelete() })
            }
        }
        Text(
            item.title,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "링크 추가", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("추가", modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp)
    }
}

@Composable
private fun EditDialog(target: EditTarget, onDismiss: () -> Unit, onSave: (title: String, url: String) -> Unit) {
    val initial = (target as? EditTarget.Existing)?.item
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    val resolved = UrlResolver.resolveAddress(url)
    val urlError = url.isNotBlank() && resolved == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "링크 추가" else "링크 수정") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("이름") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("주소") },
                    singleLine = true,
                    isError = urlError,
                    supportingText = { if (urlError) Text("올바른 주소를 입력하세요") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && resolved != null,
                onClick = { onSave(title.trim(), resolved!!) },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
