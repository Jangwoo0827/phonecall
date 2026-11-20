package com.example.superdialer.notes

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.superdialer.R
import com.example.superdialer.calllog.CallNotes
import com.example.superdialer.contacts.ContactMemos
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.i18n.str
import com.example.superdialer.ui.groupShape

/**
 * The notepad section of the Apps tab: a list of the user's own notes (search, pin) with a full-screen editor, plus a
 * read-only overview of the call memos and contact memos written elsewhere in the app. Everything stays on this phone.
 */
@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableLongStateOf(-1L) }
    if (editing) {
        NoteEditor(
            noteId = editId.takeIf { it >= 0 },
            onClose = { editing = false },
            modifier = modifier,
        )
    } else {
        NotesList(
            onOpen = { editId = it; editing = true },
            onNew = { editId = -1L; editing = true },
            modifier = modifier,
        )
    }
}

private enum class Source { Mine, Calls, Contacts }

@Composable
private fun NotesList(onOpen: (Long) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    var source by rememberSaveable { mutableStateOf(Source.Mine) }
    var query by rememberSaveable { mutableStateOf("") }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("notes_search"),
                placeholder = { Text(str(R.string.notes_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = str(R.string.t_clear_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceChip(Source.Mine, source, str(R.string.notes_mine)) { source = it }
                SourceChip(Source.Calls, source, str(R.string.notes_calls)) { source = it }
                SourceChip(Source.Contacts, source, str(R.string.notes_contacts)) { source = it }
            }
            when (source) {
                Source.Mine -> MyNotes(query, onOpen)
                Source.Calls -> CallMemos(query)
                Source.Contacts -> ContactMemoList(query)
            }
        }
        if (source == Source.Mine) {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("notes_new"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = str(R.string.notes_new))
            }
        }
    }
}

@Composable
private fun SourceChip(value: Source, selected: Source, label: String, onSelect: (Source) -> Unit) {
    FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
}

@Composable
private fun MyNotes(query: String, onOpen: (Long) -> Unit) {
    val notes = Notes.all().filter { Notes.matches(it.text, query) }
    if (notes.isEmpty()) {
        EmptyText(if (query.isBlank()) str(R.string.notes_empty) else str(R.string.notes_no_match))
        return
    }
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(notes, key = { _, n -> n.id }) { index, note ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(note.id) }.testTag("note_row"),
                shape = groupShape(index, notes.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Notes.titleOf(note.text), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val preview = Notes.previewOf(note.text)
                        if (preview.isNotEmpty()) {
                            Text(
                                preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            whenText(context, note.updatedAt), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin, contentDescription = str(R.string.notes_pinned),
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp).size(20.dp),
                        )
                    }
                }
            }
        }
        item { Box(Modifier.size(88.dp)) } // room for the + button
    }
}

@Composable
private fun CallMemos(query: String) {
    val context = LocalContext.current
    val memos = CallNotes.all().filter {
        Notes.matches(it.text, query) || PhoneNumberFormatter.format(it.numberKey).contains(query.trim())
    }
    if (memos.isEmpty()) {
        EmptyText(if (query.isBlank()) str(R.string.notes_empty_calls) else str(R.string.notes_no_match))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(memos) { index, memo ->
            MemoRow(PhoneNumberFormatter.format(memo.numberKey), memo.text, whenText(context, memo.atMillis), index, memos.size)
        }
    }
}

@Composable
private fun ContactMemoList(query: String) {
    val memos = ContactMemos.all().filter {
        Notes.matches(it.second, query) || PhoneNumberFormatter.format(it.first).contains(query.trim())
    }
    if (memos.isEmpty()) {
        EmptyText(if (query.isBlank()) str(R.string.notes_empty_contacts) else str(R.string.notes_no_match))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(memos) { index, memo ->
            MemoRow(PhoneNumberFormatter.format(memo.first), memo.second, null, index, memos.size)
        }
    }
}

@Composable
private fun MemoRow(number: String, text: String, whenLabel: String?, index: Int, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                listOfNotNull(number, whenLabel).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
            Text(text, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(
                text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Full-screen editor. Leaving (back arrow, system back, or the app going to the background) saves; blank text removes the
 * note (or creates nothing), the same as the call memos.
 */
@Composable
private fun NoteEditor(noteId: Long?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var id by rememberSaveable { mutableLongStateOf(noteId ?: -1L) }
    var text by rememberSaveable { mutableStateOf(noteId?.let { Notes.get(it)?.text }.orEmpty()) }
    var pinned by rememberSaveable { mutableStateOf(noteId?.let { Notes.get(it)?.pinned } ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun commit() {
        // Unchanged text is not saved again, so merely opening a note does not move it to the top.
        val old = id.takeIf { it >= 0 }?.let { Notes.get(it) }
        if (old != null && old.text == text.trim() && old.pinned == pinned) return
        id = Notes.save(id.takeIf { it >= 0 }, text, pinned) ?: -1L
    }

    fun close() { commit(); onClose() }

    BackHandler { close() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) commit() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }, modifier = Modifier.testTag("note_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.notes_back))
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = { pinned = !pinned }) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = str(if (pinned) R.string.notes_unpin else R.string.notes_pin),
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.trim())
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = str(R.string.notes_share))
            }
            IconButton(
                onClick = { if (id >= 0) confirmDelete = true else { text = ""; close() } },
                modifier = Modifier.testTag("note_delete"),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = str(R.string.notes_delete))
            }
        }
        TextField(
            value = text,
            onValueChange = { text = it.take(Notes.MAX_LENGTH) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).testTag("note_text"),
            placeholder = { Text(str(R.string.notes_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.notes_delete_title)) },
            text = { Text(str(R.string.notes_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Notes.delete(id)
                    text = ""
                    id = -1L
                    onClose()
                }) { Text(str(R.string.notes_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
        )
    }
}

private fun whenText(context: android.content.Context, millis: Long): String {
    val flags = if (DateUtils.isToday(millis)) DateUtils.FORMAT_SHOW_TIME else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(context, millis, flags)
}
