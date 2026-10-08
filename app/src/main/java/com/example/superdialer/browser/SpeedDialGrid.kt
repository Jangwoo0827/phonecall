package com.example.superdialer.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.superdialer.browser.data.SpeedDial

private const val MAX_TILES = 24

/** What the edit dialog is working on: a new tile (or folder), or an existing one. */
private sealed interface EditTarget {
    data class New(val parentId: Long) : EditTarget
    data class Existing(val item: SpeedDial) : EditTarget
}

/**
 * The browser start page: link tiles like a speed dial. Long-press a tile and drag to reorder, long-press without
 * moving for edit / move / delete. Folders (top level only) open to show their tiles.
 */
@Composable
internal fun SpeedDialGrid(
    dials: List<SpeedDial>,
    onOpen: (SpeedDial) -> Unit,
    onAdd: (title: String, url: String, parentId: Long) -> Unit,
    onAddFolder: (title: String) -> Unit,
    onUpdate: (item: SpeedDial, title: String, url: String) -> Unit,
    onDelete: (SpeedDial) -> Unit,
    onMove: (item: SpeedDial, parentId: Long) -> Unit,
    onReorder: (List<SpeedDial>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openFolderId by rememberSaveable { mutableStateOf(0L) }
    val folder = dials.firstOrNull { it.id == openFolderId && it.isFolder }
    val levelId = folder?.id ?: 0L
    val level = dials.filter { it.parentId == levelId }.sortedWith(compareBy({ it.position }, { it.id }))
    val levelKey = level.map { Triple(it.id, it.position, it.title + it.url) }
    var order by remember(levelKey) { mutableStateOf(level) }
    val folders = dials.filter { it.isFolder }

    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var moving by remember { mutableStateOf<SpeedDial?>(null) }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val gridState = rememberLazyGridState()

    BackHandler(enabled = folder != null) { openFolderId = 0L }

    fun onDragStart(id: Long) {
        draggingId = id
        dragOffset = Offset.Zero
    }

    fun onDrag(id: Long, delta: Offset) {
        dragOffset += delta
        val infos = gridState.layoutInfo.visibleItemsInfo
        val me = infos.firstOrNull { it.key == id } ?: return
        val cx = me.offset.x + me.size.width / 2f + dragOffset.x
        val cy = me.offset.y + me.size.height / 2f + dragOffset.y
        val target = infos.firstOrNull {
            it.key is Long && it.key != id &&
                cx >= it.offset.x && cx <= it.offset.x + it.size.width &&
                cy >= it.offset.y && cy <= it.offset.y + it.size.height
        } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        order = SpeedDialTree.moved(order, from, to)
        // Keep the tile under the finger: its slot jumped to the target's position.
        dragOffset += Offset((me.offset.x - target.offset.x).toFloat(), (me.offset.y - target.offset.y).toFloat())
    }

    fun onDragEnd(moved: Boolean) {
        draggingId = null
        dragOffset = Offset.Zero
        if (moved) onReorder(order)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (folder != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                IconButton(onClick = { openFolderId = 0L }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
                Text(folder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(order, key = { _, item -> item.id }) { _, item ->
                val dragging = draggingId == item.id
                SpeedDialTile(
                    item = item,
                    children = if (item.isFolder) dials.filter { it.parentId == item.id }.sortedBy { it.position } else emptyList(),
                    hasFolders = folders.isNotEmpty(),
                    inFolder = folder != null,
                    modifier = if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.1f
                                scaleY = 1.1f
                            }
                    } else {
                        Modifier.animateItem()
                    },
                    onOpen = { if (item.isFolder) openFolderId = item.id else onOpen(item) },
                    onEdit = { editing = EditTarget.Existing(item) },
                    onMove = { moving = item },
                    onDelete = { onDelete(item) },
                    onDragStart = { onDragStart(item.id) },
                    onDrag = { onDrag(item.id, it) },
                    onDragEnd = ::onDragEnd,
                )
            }
            if (order.size < MAX_TILES) {
                item(key = "add") { AddTile(onClick = { editing = EditTarget.New(levelId) }) }
            }
        }
    }

    editing?.let { target ->
        EditDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { title, url, isFolder ->
                when (target) {
                    is EditTarget.New -> if (isFolder) onAddFolder(title) else onAdd(title, url, target.parentId)
                    is EditTarget.Existing -> onUpdate(target.item, title, url)
                }
                editing = null
            },
        )
    }

    moving?.let { item ->
        MoveDialog(
            item = item,
            folders = folders,
            onDismiss = { moving = null },
            onPick = { parentId -> onMove(item, parentId); moving = null },
        )
    }
}

@Composable
private fun SpeedDialTile(
    item: SpeedDial,
    children: List<SpeedDial>,
    hasFolders: Boolean,
    inFolder: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (moved: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            val gestures = Modifier
                .clickable(onClick = onOpen)
                .pointerInput(item.id) {
                    var travelled = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { travelled = 0f; onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            travelled += amount.getDistance()
                            onDrag(amount)
                        },
                        // A long press that never moved opens the menu instead of reordering.
                        onDragEnd = { val moved = travelled > 12f; onDragEnd(moved); if (!moved) menuOpen = true },
                        onDragCancel = { onDragEnd(false) },
                    )
                }
            if (item.isFolder) {
                FolderIcon(children, gestures)
            } else {
                SiteIcon(url = item.url, title = item.title, size = 64.dp, corner = 20.dp, modifier = gestures)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("수정") }, onClick = { menuOpen = false; onEdit() })
                if (!item.isFolder && (hasFolders || inFolder)) {
                    DropdownMenuItem(
                        text = { Text(if (hasFolders) "폴더로 이동" else "맨 위로 이동") },
                        onClick = { menuOpen = false; onMove() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (item.isFolder) "폴더와 안의 링크 삭제" else "삭제") },
                    onClick = { menuOpen = false; onDelete() },
                )
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

/** A folder: a tonal tile showing up to four of its sites. */
@Composable
private fun FolderIcon(children: List<SpeedDial>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val shown = children.filter { !it.isFolder }.take(4)
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 2) {
                        val child = shown.getOrNull(row * 2 + col)
                        if (child != null) {
                            SiteIcon(child.url, child.title, 24.dp, corner = 7.dp)
                        } else {
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        }
                    }
                }
            }
        }
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
            Icon(Icons.Filled.Add, contentDescription = "추가", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("추가", modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp)
    }
}

@Composable
private fun EditDialog(target: EditTarget, onDismiss: () -> Unit, onSave: (title: String, url: String, isFolder: Boolean) -> Unit) {
    val initial = (target as? EditTarget.Existing)?.item
    val canChooseFolder = target is EditTarget.New && target.parentId == 0L
    var asFolder by remember { mutableStateOf(initial?.isFolder == true) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    val resolved = UrlResolver.resolveAddress(url)
    val urlError = !asFolder && url.isNotBlank() && resolved == null
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) (if (asFolder) "폴더 추가" else "링크 추가") else (if (asFolder) "폴더 수정" else "링크 수정")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canChooseFolder) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !asFolder, onClick = { asFolder = false }, label = { Text("링크") }, colors = chipColors)
                        FilterChip(selected = asFolder, onClick = { asFolder = true }, label = { Text("폴더") }, colors = chipColors)
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("이름") },
                    singleLine = true,
                )
                if (!asFolder) {
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (asFolder || resolved != null),
                onClick = { onSave(title.trim(), if (asFolder) "" else resolved!!, asFolder) },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun MoveDialog(item: SpeedDial, folders: List<SpeedDial>, onDismiss: () -> Unit, onPick: (parentId: Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'${item.title}' 이동") },
        text = {
            Column {
                if (item.parentId != 0L) {
                    TextButton(onClick = { onPick(0L) }) { Text("맨 위 (폴더 밖)") }
                }
                folders.filter { it.id != item.parentId }.forEach { folder ->
                    TextButton(onClick = { onPick(folder.id) }) { Text(folder.title) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
