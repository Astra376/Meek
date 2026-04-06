package com.example.aichat.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.DesignMetrics
import com.example.aichat.core.model.CharacterSummary

private object CharacterCardMetrics {
    val cardAspectRatio = 0.82f
    val portraitWeight = 0.60f
    val contentWeight = 0.40f
    val contentPadding = 12.dp
    val gapSmall = 2.dp
    val gapStandard = 6.dp
    val badgeGap = 5.dp
    val badgeIconSize = 14.dp
}

@Composable
fun CharacterSummaryCard(
    character: CharacterSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CharacterCardMetrics.cardAspectRatio)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(CharacterCardMetrics.portraitWeight)
            ) {
                CharacterPortrait(
                    name = character.name,
                    avatarUrl = character.avatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(
                        topStart = DesignMetrics.portraitCorner,
                        topEnd = DesignMetrics.portraitCorner,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(CharacterCardMetrics.contentWeight)
                    .padding(CharacterCardMetrics.contentPadding)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${character.authorUsername.ifBlank { "creator" }}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(CharacterCardMetrics.gapStandard))
                
                Text(
                    text = character.tagline,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CharacterCardMetrics.badgeGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        icon = AppIcons.chats,
                        contentDescription = null,
                        size = CharacterCardMetrics.badgeIconSize,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                    Text(
                        text = character.publicChatCount.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}
