package com.example.superdialer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The Apps tab keeps three tabs on top; the arrow under them slides down a drawer with every app. */
@RunWith(AndroidJUnit4::class)
class HubDrawerTest : AppTestBase() {
    @Test
    fun theArrowOpensADrawerWithAllAppsAndPickingOneClosesIt() {
        tab("앱")
        waitFor(hasTag("hub_expand"))
        // Closed: the drawer is not there, and Notes has no tab of its own.
        compose.onAllNodes(hasTag("hub_drawer")).assertCountEquals(0)
        compose.onAllNodes(tabMatcher("메모")).assertCountEquals(0)

        compose.onNodeWithTag("hub_expand").performClick()
        waitFor(hasTag("hub_drawer"))
        compose.onNodeWithTag("hub_tile_3").assertIsDisplayed()
        compose.onNodeWithTag("hub_tile_4").assertIsDisplayed()

        compose.onNodeWithTag("hub_tile_3").performClick()
        waitFor(hasTag("notes_new"))
        waitGone(hasTag("hub_drawer"))
        // The notes app has no tab, so the strip under the tabs names it.
        waitFor(hasText("메모"))
    }
}
