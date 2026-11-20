package com.example.superdialer

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Map section opens from the Apps tab's drawer and shows its own native search bar over the map. It does not depend on
 * the T map key (a build without one shows a notice instead of the map, the search bar is there either way).
 */
@RunWith(AndroidJUnit4::class)
class MapScreenTest : AppTestBase() {
    @Test
    fun theMapOpensFromTheDrawerWithItsSearchBarAndLocationButton() {
        tab("앱")
        waitFor(hasTag("hub_expand"))
        compose.onNodeWithTag("hub_expand").performClick()
        waitFor(hasTag("hub_tile_4"))
        compose.onNodeWithTag("hub_tile_4").performClick()
        waitFor(hasTag("map_search"))
        waitFor(hasTag("map_my_location"))
        waitFor(androidx.compose.ui.test.hasText("지도"))
    }
}
