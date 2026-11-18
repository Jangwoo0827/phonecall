package com.example.superdialer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.Track
import com.example.superdialer.music.TrackSource
import com.example.superdialer.music.ui.MusicUi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Music section of the Apps tab. Uses the on-phone library only (no network), so it passes offline too. */
@RunWith(AndroidJUnit4::class)
class MusicTabTest : AppTestBase() {
    private val song = Track("dz:1", "테스트곡", "테스트가수", 2, "앨범", 3, null, 200, "deezer://track/1", TrackSource.DEEZER)

    // MusicUi and MusicStore live for the whole process: start every test from a clean state.
    private fun reset() {
        MusicStore.clearAll()
        MusicUi.stack.clear()
        MusicUi.tab = 0
        MusicUi.searchQuery = ""
        MusicUi.searchSubmitted = ""
    }

    @Before
    fun startClean() = reset()

    @After
    fun cleanUp() = reset()

    private fun openMusic() {
        tab("앱")
        waitFor(hasText("음악"))
        compose.onNodeWithText("음악").performClick()
    }

    @Test
    fun theThreeMusicPagesAreThere() {
        openMusic()
        waitFor(hasText("내 라이브러리"))
        compose.onNodeWithTag("music_tab_home").assertIsDisplayed()
        compose.onNodeWithTag("music_tab_search").assertIsDisplayed()
        compose.onNodeWithTag("music_tab_library").performClick()
        waitFor(hasText("플레이리스트 만들기"))
        compose.onNodeWithText("좋아요 표시한 곡").assertIsDisplayed()
    }

    @Test
    fun aLikedSongShowsInTheLibraryAndOpens() {
        compose.runOnIdle { MusicStore.toggleLike(song) }
        openMusic()
        compose.onNodeWithTag("music_tab_library").performClick()
        waitFor(hasText("좋아요 표시한 곡"))
        compose.onNodeWithText("1곡").assertIsDisplayed()
        compose.onNodeWithText("좋아요 표시한 곡").performClick()
        waitFor(hasText("테스트곡"))
        compose.onNodeWithText("테스트가수").assertIsDisplayed()
    }

    @Test
    fun aPlaylistCanBeCreatedFromTheLibrary() {
        openMusic()
        compose.onNodeWithTag("music_tab_library").performClick()
        waitFor(hasText("플레이리스트 만들기"))
        compose.onNodeWithText("플레이리스트 만들기").performClick()
        waitFor(hasText("만들기"))
        compose.onNodeWithText("만들기").performClick()
        waitFor(hasText("비어 있는 플레이리스트예요", substring = true))
        compose.runOnIdle { assert(MusicStore.playlists.size == 1) }
    }
}
