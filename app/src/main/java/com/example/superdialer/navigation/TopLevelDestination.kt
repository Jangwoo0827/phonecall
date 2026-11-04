package com.example.superdialer.navigation

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Dialer("dialer", str(R.string.t_keypad), Icons.Filled.Dialpad),
    CallLog("calllog", str(R.string.shortcut_calllog), Icons.Filled.History),
    Contacts("contacts", str(R.string.shortcut_contacts), Icons.Filled.Contacts),

    /** Built-in browser and games share one tab. */
    Hub("hub", str(R.string.t_web_games), Icons.Filled.Apps),
    Settings("settings", str(R.string.t_settings), Icons.Filled.Settings),
}
