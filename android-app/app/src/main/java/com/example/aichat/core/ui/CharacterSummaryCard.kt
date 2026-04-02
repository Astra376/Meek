package com.example.aichat.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.model.CharacterSummary

@Composable
fun CharacterSummaryCard(
    character: CharacterSummary,
    authorLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.64f)
                .clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CharacterPortrait(
                    name = character.name,
                    avatarUrl = character.avatarUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                lineHeight = 24.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = character.tagline,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        )
                        Text(
                            text = character.publicChatCount.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            text = authorLabel,
            modifier = Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
