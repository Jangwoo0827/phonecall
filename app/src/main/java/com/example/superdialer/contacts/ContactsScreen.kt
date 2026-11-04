package com.example.superdialer.contacts

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import com.example.superdialer.ui.theme.starColor
import com.example.superdialer.ui.groupShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.lazy.itemsIndexed
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.PermissionGate

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onOpenContact: (Long) -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionGate(
        required = listOf(Manifest.permission.READ_CONTACTS),
        rationale = str(R.string.t_showing_contacts_needs_the_contacts_pe),
        modifier = modifier,
    ) {
        ContactsList(viewModel, onOpenContact, onOpenBlocked, onOpenGroups, onOpenDuplicates, modifier)
    }
}

@Composable
private fun ContactsList(
    viewModel: ContactsViewModel,
    onOpenContact: (Long) -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val favorites = viewModel.favorites
    val sections = viewModel.sections

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = viewModel.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, str(R.string.t_can_t_find_the_contacts_app), Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Filled.Add, contentDescription = str(R.string.t_add_contact)) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = str(R.string.t_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(str(R.string.t_manage_groups)) }, onClick = { menuOpen = false; onOpenGroups() })
                    DropdownMenuItem(text = { Text(str(R.string.t_clean_up_duplicate_contacts)) }, onClick = { menuOpen = false; onOpenDuplicates() })
                    DropdownMenuItem(text = { Text(str(R.string.t_blocked_numbers)) }, onClick = { menuOpen = false; onOpenBlocked() })
                }
            }
        }

        if (ContactGroups.groups.isNotEmpty()) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = viewModel.selectedGroupId == null, onClick = { viewModel.selectGroup(null) }, label = { Text(str(R.string.t_all)) }, colors = chipColors) }
                items(ContactGroups.groups.toList(), key = { it.id }) { group ->
                    FilterChip(
                        selected = viewModel.selectedGroupId == group.id,
                        onClick = { viewModel.selectGroup(group.id) },
                        label = { Text(group.name) },
                        colors = chipColors,
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isEmpty) {
                if (viewModel.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = if (viewModel.query.isBlank()) str(R.string.t_no_contacts) else str(R.string.t_no_results),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (favorites.isNotEmpty()) {
                        item(key = "header-favorites") { SectionHeader(str(R.string.t_favorite)) }
                        itemsIndexed(favorites, key = { _, c -> "fav-${c.id}" }) { index, contact ->
                            ContactRow(contact, groupShape(index, favorites.size), onClick = { onOpenContact(contact.id) })
                        }
                    }
                    sections.forEach { section ->
                        item(key = "header-${section.header}") { SectionHeader(section.header) }
                        itemsIndexed(section.contacts, key = { _, c -> "c-${c.id}" }) { index, contact ->
                            ContactRow(contact, groupShape(index, section.contacts.size), onClick = { onOpenContact(contact.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(str(R.string.t_search_name_initials_number)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ContactRow(contact: Contact, shape: Shape, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { InitialAvatar(contact.name, size = 44.dp) },
        headlineContent = { Text(contact.name, maxLines = 1, fontWeight = FontWeight.SemiBold) },
        trailingContent = {
            if (contact.starred) {
                Icon(Icons.Filled.Star, contentDescription = str(R.string.t_favorite), tint = starColor())
            }
        },
    )
}
