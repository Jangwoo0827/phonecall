package com.example.superdialer.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Six VALORANT Champions songs (Deezer track ids), shown as their own shelf on the Music home. */
object ValorantChampions {
    private val ids = listOf(
        3495742731L, // Die For You (Champions 2021 anthem)
        3498004761L, // Ticking Away
        3495742841L, // Fire Again
        3497814911L, // Die For You (Zedd Remix)
        3497814951L, // Die For You (Dabin Remix)
        4210492372L, // If The Sun Burns Out Tonight (feat. Oli Sykes & Courtney LaPlante)
    )

    suspend fun load(): List<Track> = coroutineScope {
        ids.map { id -> async(Dispatchers.IO) { runCatching { DeezerApi.track(id) }.getOrNull() } }
            .mapNotNull { it.await() }
    }
}
