package com.example.superdialer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Dialer("dialer", "키패드", Icons.Filled.Dialpad),
    CallLog("calllog", "최근기록", Icons.Filled.History),
    Contacts("contacts", "연락처", Icons.Filled.Contacts),
    Browser("browser", "브라우저", Icons.Filled.Language),
    Games("games", "게임", Icons.Filled.SportsEsports),
}
