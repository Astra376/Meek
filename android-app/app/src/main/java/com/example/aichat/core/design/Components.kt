package com.example.aichat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.aichat.core.util.avatarPalette

private object DesignMetrics {
    val cardCorner = 24.dp
    val buttonCorner = 18.dp
    val primaryButtonHeight = 54.dp
    val secondaryButtonHeight = 50.dp
    val iconGap = 8.dp
    val fieldCorner = 26.dp
    val fieldSingleLineHeight = 46.dp
    val fieldMultiLineHeight = 54.dp
    val fieldHorizontalPadding = 12.dp
    val fieldSingleLineVerticalPadding = 7.dp
    val fieldMultiLineVerticalPadding = 11.dp
    val fieldIconGap = 10.dp
    val iconCircleSize = 48.dp
    val iconPillHeight = 48.dp
    val iconPillHorizontalPadding = 16.dp
    val outlineStrokeWidth = 1.dp
    val outlineStrokeAlpha = 0.25f
    val squareIconButtonSize = 54.dp
    val squareButtonCorner = 18.dp
    val selectionDotSize = 20.dp
    val selectionDotInnerSize = 8.dp
    val portraitCorner = 22.dp
    val chipHorizontalPadding = 12.dp
    val chipVerticalPadding = 6.dp
    val portraitBadgeGap = 12.dp
}

fun Modifier.appOutlineSurface(
    shape: Shape,
    enabled: Boolean = true,
    selected: Boolean = false
): Modifier = composed {
    this
        .alpha(if (enabled) 1f else 0.45f)
        .clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
        .border(
            width = DesignMetrics.outlineStrokeWidth,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = DesignMetrics.outlineStrokeAlpha)
            },
            shape = shape
        )
}

@Composable
private fun OutlineTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(DesignMetrics.buttonCorner),
    height: androidx.compose.ui.unit.Dp = DesignMetrics.secondaryButtonHeight,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(
        horizontal = DesignMetrics.iconPillHorizontalPadding
    ),
    selected: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(height)
            .appOutlineSurface(
                shape = shape,
                enabled = enabled,
                selected = selected
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            leadingIcon?.let {
                it()
                Spacer(modifier = Modifier.width(DesignMetrics.iconGap))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DesignMetrics.cardCorner),
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
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(DesignMetrics.primaryButtonHeight),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(DesignMetrics.buttonCorner),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let {
                it()
                Spacer(modifier = Modifier.width(DesignMetrics.iconGap))
            }
            Text(text = text)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    OutlineTextButton(
        text = text,
        enabled = enabled,
        modifier = modifier,
        leadingIcon = leadingIcon,
        onClick = onClick
    )
}

@Composable
fun SelectionButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    if (selected) {
        OutlineTextButton(
            text = text,
            modifier = modifier,
            selected = true,
            leadingIcon = leadingIcon,
            onClick = onClick
        )
    } else {
        OutlineTextButton(
            text = text,
            modifier = modifier,
            leadingIcon = leadingIcon,
            onClick = onClick
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    shape: RoundedCornerShape = RoundedCornerShape(DesignMetrics.fieldCorner),
    leadingIcon: (@Composable () -> Unit)? = null
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (enabled) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
                        }
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        shape
                    )
                    .defaultMinSize(
                        minHeight = if (singleLine) {
                            DesignMetrics.fieldSingleLineHeight
                        } else {
                            DesignMetrics.fieldMultiLineHeight
                        }
                    )
                    .padding(
                        horizontal = DesignMetrics.fieldHorizontalPadding,
                        vertical = if (singleLine) {
                            DesignMetrics.fieldSingleLineVerticalPadding
                        } else {
                            DesignMetrics.fieldMultiLineVerticalPadding
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(DesignMetrics.fieldIconGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingIcon?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            it()
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (enabled) 0.72f else 0.45f
                            )
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
fun IconCircleButton(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(DesignMetrics.iconCircleSize)
            .appOutlineSurface(
                shape = CircleShape,
                enabled = enabled,
                selected = selected
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            icon()
        }
    }
}

@Composable
fun IconPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    OutlineTextButton(
        text = text,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        height = DesignMetrics.iconPillHeight,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = DesignMetrics.iconPillHorizontalPadding
        ),
        leadingIcon = leadingIcon,
        onClick = onClick
    )
}

@Composable
fun SquareIconButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(DesignMetrics.squareIconButtonSize)
            .appOutlineSurface(
                shape = RoundedCornerShape(DesignMetrics.squareButtonCorner),
                enabled = enabled
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            icon()
        }
    }
}

@Composable
fun SelectionDot(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(DesignMetrics.selectionDotSize)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(DesignMetrics.selectionDotInnerSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
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
    val shape = RoundedCornerShape(DesignMetrics.portraitCorner)
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
                    .background(brush = Brush.linearGradient(palette)),
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
                    .background(brush = Brush.linearGradient(palette)),
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
            modifier = Modifier.padding(
                horizontal = DesignMetrics.chipHorizontalPadding,
                vertical = DesignMetrics.chipVerticalPadding
            ),
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
        Spacer(modifier = Modifier.size(DesignMetrics.portraitBadgeGap))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
