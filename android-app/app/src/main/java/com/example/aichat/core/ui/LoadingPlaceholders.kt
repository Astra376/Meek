package com.example.aichat.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aichat.core.design.DesignMetrics

private val PlaceholderBase = Color(0xFF242424)
private val PlaceholderShine = Color(0xFF4B4B4B)

fun Modifier.shimmerPlaceholder(
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "placeholder-shimmer")
    val offset = transition.animateFloat(
        initialValue = -700f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "placeholder-shimmer-offset"
    )
    clip(shape).background(
        Brush.linearGradient(
            colors = listOf(
                PlaceholderBase,
                PlaceholderBase,
                PlaceholderShine,
                PlaceholderBase,
                PlaceholderBase
            ),
            start = Offset(offset.value, 0f),
            end = Offset(offset.value + 360f, 360f)
        )
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(modifier = modifier.shimmerPlaceholder(shape))
}

@Composable
fun ShimmerTextLine(
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp = 14.dp
) {
    ShimmerBox(
        modifier = modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(height / 2)
    )
}

@Composable
fun CharacterSummaryCardPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f),
            shape = RoundedCornerShape(DesignMetrics.portraitCorner)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, top = 8.dp, end = 12.dp, bottom = 12.dp)
        ) {
            ShimmerTextLine(width = 96.dp, height = 20.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerTextLine(width = 108.dp, height = 12.dp)
            Spacer(modifier = Modifier.height(6.dp))
            ShimmerTextLine(width = 74.dp, height = 12.dp)
        }
    }
}

@Composable
fun ChatListRowPlaceholder(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(DesignMetrics.portraitCorner))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 5.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ShimmerTextLine(width = 128.dp, height = 18.dp)
                Spacer(modifier = Modifier.weight(1f))
                ShimmerTextLine(width = 44.dp, height = 11.dp)
            }
            ShimmerTextLine(width = 170.dp, height = 14.dp)
        }
    }
}

@Composable
fun CircleAvatarPlaceholder(
    modifier: Modifier = Modifier,
    size: Dp
) {
    ShimmerBox(modifier = modifier.size(size), shape = CircleShape)
}
