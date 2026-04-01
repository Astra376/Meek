package com.example.aichat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.aichat.core.util.avatarPalette

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(54.dp),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text = text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.height(50.dp),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text = text)
    }
}

@Composable
fun SelectionButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            modifier = modifier.height(50.dp),
            onClick = onClick,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(text = text)
        }
    } else {
        OutlinedButton(
            modifier = modifier.height(50.dp),
            onClick = onClick,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(text = text)
        }
    }
}

@Composable
fun CharacterPortrait(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier
) {
    val palette = avatarPalette(avatarUrl ?: name)
    val shape = RoundedCornerShape(22.dp)
    when {
        avatarUrl?.startsWith("http") == true -> {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(brush = Brush.linearGradient(palette))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), shape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CircleAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier
) {
    val palette = avatarPalette(avatarUrl ?: name)
    when {
        avatarUrl?.startsWith("http") == true -> {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        else -> {
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .background(brush = Brush.linearGradient(palette))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun StatRow(
    left: String,
    right: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(left, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(right, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PortraitBadge(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterPortrait(
            name = name,
            avatarUrl = avatarUrl,
            modifier = Modifier
                .size(52.dp)
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
