package com.example.superdialer.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.rememberDialAction
import com.example.superdialer.ui.sendSms

private val CallGreen = Color(0xFF2E7D32)
private val StarColor = Color(0xFFF9A825)

@Composable
fun ContactDetailScreen(
    viewModel: ContactsViewModel,
    contactId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dial = rememberDialAction()

    LaunchedEffect(contactId) { viewModel.loadDetail(contactId) }
    val detail = viewModel.detail?.takeIf { it.id == contactId }

    Column(modifier = modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
        }

        if (detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (detail.photo != null) {
                Image(
                    bitmap = detail.photo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                InitialAvatar(detail.name, size = 96.dp, fontSize = 40.sp)
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(detail.name, fontSize = 26.sp, style = MaterialTheme.typography.headlineSmall)
                if (detail.starred) {
                    Icon(Icons.Filled.Star, contentDescription = "즐겨찾기", tint = StarColor)
                }
            }
        }

        detail.numbers.forEach { entry ->
            ListItem(
                modifier = Modifier.clickable { dial(entry.number) },
                headlineContent = { Text(PhoneNumberFormatter.formatLoose(entry.number)) },
                supportingContent = { Text(entry.label) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { context.sendSms(entry.number) }) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "문자 보내기")
                        }
                        IconButton(onClick = { dial(entry.number) }) {
                            Icon(Icons.Filled.Call, contentDescription = "발신", tint = CallGreen)
                        }
                    }
                },
            )
        }
    }
}

