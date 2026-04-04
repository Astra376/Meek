package com.example.aichat.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.R

enum class AppIconWeight {
    Regular,
    Fill
}

data class AppIconGlyph(
    val value: String,
    val weight: AppIconWeight = AppIconWeight.Regular
)

private val PhosphorRegularFontFamily = FontFamily(
    Font(R.font.phosphor_regular, weight = FontWeight.Normal)
)

private val PhosphorFillFontFamily = FontFamily(
    Font(R.font.phosphor_fill, weight = FontWeight.Normal)
)

@Composable
fun AppIcon(
    icon: AppIconGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon.value,
            color = tint,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = when (icon.weight) {
                    AppIconWeight.Regular -> PhosphorRegularFontFamily
                    AppIconWeight.Fill -> PhosphorFillFontFamily
                },
                fontWeight = FontWeight.Normal,
                fontSize = size.value.sp,
                lineHeight = size.value.sp
            )
        )
    }
}

object AppIcons {
    private fun regular(value: String) = AppIconGlyph(value = value)
    private fun fill(value: String) = AppIconGlyph(value = value, weight = AppIconWeight.Fill)

    val back = regular("\uE058")

    val homeOutline = regular("\uE2C6")
    val home = fill("\uE2C6")

    val createOutline = regular("\uE3D6")
    val create = fill("\uE3D6")
    val createAction = regular("\uE34C")

    val chatsOutline = regular("\uE17E")
    val chats = fill("\uE17E")

    val profileOutline = regular("\uEE58")
    val profile = fill("\uEE58")

    val search = regular("\uE30C")
    val searchFilled = fill("\uE30C")
    val sparkle = regular("\uE6A2")
    val clear = regular("\uE4F8")
    val public = regular("\uE28E")
    val lock = regular("\uE308")
    val send = regular("\uE398")
    val stop = fill("\uE46E")
    val previous = regular("\uE058")
    val next = regular("\uE06C")

    val themeSystem = regular("\uE560")
    val themeDark = regular("\uE330")
    val themeLight = regular("\uE472")

    val logout = regular("\uE42A")
    val edit = regular("\uE3B4")
    val settings = regular("\uE272")

    val owned = regular("\uE0F8")
    val ownedFilled = fill("\uE0F8")
    val liked = regular("\uE2AA")
    val likedFilled = fill("\uE2AA")
}
