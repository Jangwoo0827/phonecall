package com.example.superdialer.games

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.superdialer.account.AccountManager
import com.example.superdialer.account.LeaderRow
import com.example.superdialer.ui.ScreenHeader

/** Top scores per game among signed-in players. Needs an account; your own best scores are sent automatically. */
@Composable
fun LeaderboardScreen(games: List<GameInfo>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(games.firstOrNull()?.id) }
    var rows by remember { mutableStateOf<List<LeaderRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var myId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(selected, AccountManager.email, AccountManager.nicknameVersion) {
        val game = selected ?: return@LaunchedEffect
        rows = null
        error = null
        if (!AccountManager.signedIn) return@LaunchedEffect
        loading = true
        AccountManager.loadLeaderboard(game) { result, message, me ->
            rows = result
            error = message
            myId = me
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(str(R.string.t_leaderboard_2), onBack)
        if (!AccountManager.signedIn) {
            Text(
                str(R.string.t_sign_in_under_settings_account_to),
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games, key = { it.id }) { game ->
                FilterChip(selected = selected == game.id, onClick = { selected = game.id }, label = { Text(game.shownTitle) }, colors = chipColors)
            }
        }
        Text(
            str(R.string.t_my_nickname_change_it_in_settings, AccountManager.nickname),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(error.orEmpty(), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                rows.isNullOrEmpty() -> Text(str(R.string.t_no_scores_yet_be_the_first), modifier = Modifier.padding(24.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows.orEmpty().withIndex().toList(), key = { it.index }) { (index, row) ->
                        val mine = row.userId == myId
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ),
                            leadingContent = {
                                Text(
                                    when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            headlineContent = { Text(row.nickname + if (mine) str(R.string.t_me) else "", fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal) },
                            trailingContent = { Text("${row.score}", fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }
            }
        }
    }
}
