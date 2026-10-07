package com.example.superdialer.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GamesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val games = remember { GameCatalog.load(context) }
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }
    val playing = games.firstOrNull { it.id == playingId }

    BackHandler(enabled = playing != null) { playingId = null }

    if (playing != null) {
        GamePlayer(playing, onClose = { playingId = null }, modifier = modifier)
        return
    }

    if (games.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("등록된 게임이 없습니다.")
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games, key = { it.id }) { game ->
            GameCard(game, onClick = { playingId = game.id })
        }
    }
}

@Composable
private fun GameCard(game: GameInfo, onClick: () -> Unit) {
    val accent = runCatching { Color(android.graphics.Color.parseColor(game.color)) }.getOrDefault(Color.Gray)
    val best = GameScores.best(game.id)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(game.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(game.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Text(
                if (best > 0) "최고 점수 $best" else "아직 기록 없음",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
