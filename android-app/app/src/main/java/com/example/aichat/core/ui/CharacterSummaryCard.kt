package com.example.aichat.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.PillChip
import com.example.aichat.core.model.CharacterSummary

@Composable
fun CharacterSummaryCard(
    character: CharacterSummary,
    modifier: Modifier = Modifier,
    actionRow: @Composable RowScope.() -> Unit
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CharacterPortrait(
                    name = character.name,
                    avatarUrl = character.avatarUrl,
                    modifier = Modifier
                        .weight(0.26f)
                        .height(96.dp)
                )
                Column(modifier = Modifier.weight(0.74f)) {
                    Text(text = character.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = character.tagline,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillChip(text = "${character.likeCount} likes")
                        PillChip(text = "${character.publicChatCount} chats")
                    }
                }
            }
            Text(
                text = character.description,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                actionRow()
            }
        }
    }
}
