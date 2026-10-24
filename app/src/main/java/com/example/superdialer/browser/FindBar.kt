package com.example.superdialer.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/** "2/7" while there are matches, "결과 없음" when the page has none, nothing while the box is empty. [active] counts from 1. */
internal fun findLabel(query: String, active: Int, total: Int): String = when {
    query.isBlank() -> ""
    total <= 0 -> "결과 없음"
    else -> "${active.coerceIn(1, total)}/$total"
}

/** Find-in-page bar: type to highlight every match in the page, the arrows jump between them. */
@Composable
internal fun FindBar(
    active: Int,
    total: Int,
    onQuery: (String) -> Unit,
    onNext: (forward: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(modifier = modifier.fillMaxWidth().testTag("find_bar"), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "찾기 닫기") }
            TextField(
                value = query,
                onValueChange = { query = it; onQuery(it) },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text("페이지에서 찾기") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext(true) }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Text(
                findLabel(query, active, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = { onNext(false) }, enabled = total > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "이전 결과") }
            IconButton(onClick = { onNext(true) }, enabled = total > 0) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "다음 결과") }
        }
    }
}
