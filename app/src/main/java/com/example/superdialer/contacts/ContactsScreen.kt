package com.example.superdialer.contacts

import android.Manifest
import androidx.compose.foundation.clickable
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

private val StarColor = Color(0xFFF9A825)

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onOpenContact: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionGate(
        required = listOf(Manifest.permission.READ_CONTACTS),
        rationale = "연락처를 보려면 연락처 권한이 필요합니다.",
        modifier = modifier,
    ) {
        ContactsList(viewModel, onOpenContact, modifier)
    }
}

@Composable
private fun ContactsList(
    viewModel: ContactsViewModel,
    onOpenContact: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val favorites = viewModel.favorites
    val sections = viewModel.sections

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = viewModel.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isEmpty) {
                if (viewModel.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = if (viewModel.query.isBlank()) "연락처가 없습니다." else "검색 결과가 없습니다.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (favorites.isNotEmpty()) {
                        item(key = "header-favorites") { SectionHeader("즐겨찾기") }
                        items(favorites, key = { "fav-${it.id}" }) { contact ->
                            ContactRow(contact, onClick = { onOpenContact(contact.id) })
                        }
                    }
                    sections.forEach { section ->
                        item(key = "header-${section.header}") { SectionHeader(section.header) }
                        items(section.contacts, key = { "c-${it.id}" }) { contact ->
                            ContactRow(contact, onClick = { onOpenContact(contact.id) })
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
        placeholder = { Text("이름, 초성, 번호 검색") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "검색어 지우기")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SectionHeader(title: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { InitialAvatar(contact.name) },
        headlineContent = { Text(contact.name, maxLines = 1) },
        trailingContent = {
            if (contact.starred) {
                Icon(Icons.Filled.Star, contentDescription = "즐겨찾기", tint = StarColor)
            }
        },
    )
}
