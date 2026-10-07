package com.example.superdialer.navigation

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
    Dialer("dialer", "키패드", Icons.Filled.Dialpad),
    CallLog("calllog", "최근기록", Icons.Filled.History),
    Contacts("contacts", "연락처", Icons.Filled.Contacts),

    /** Built-in browser and games share one tab. */
    Hub("hub", "웹·게임", Icons.Filled.Apps),
    Settings("settings", "설정", Icons.Filled.Settings),
}
