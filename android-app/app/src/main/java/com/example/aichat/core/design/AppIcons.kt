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

@JvmInline
value class AppIconGlyph(val value: String)

private val MingCuteFontFamily = FontFamily(
    Font(R.font.mingcute, weight = FontWeight.Normal)
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
                fontFamily = MingCuteFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = size.value.sp,
                lineHeight = size.value.sp
            )
        )
    }
}

object AppIcons {
    val back = AppIconGlyph("\uE98B")

    val homeOutline = AppIconGlyph("\uEF43")
    val home = AppIconGlyph("\uEF42")

    val createOutline = AppIconGlyph("\uE90D")
    val create = AppIconGlyph("\uE90C")
    val createAction = AppIconGlyph("\uE90D")

    val chatsOutline = AppIconGlyph("\uEB75")
    val chats = AppIconGlyph("\uEB74")

    val profileOutline = AppIconGlyph("\uF523")
    val profile = AppIconGlyph("\uF522")

    val search = AppIconGlyph("\uF303")
    val sparkle = AppIconGlyph("\uF3B7")
    val clear = AppIconGlyph("\uEC93")
    val public = AppIconGlyph("\uED15")
    val lock = AppIconGlyph("\uF047")
    val send = AppIconGlyph("\uF321")
    val stop = AppIconGlyph("\uF3E5")
    val previous = AppIconGlyph("\uE98B")
    val next = AppIconGlyph("\uE997")

    val themeSystem = AppIconGlyph("\uEAAD")
    val themeDark = AppIconGlyph("\uF0E1")
    val themeLight = AppIconGlyph("\uF40B")

    val logout = AppIconGlyph("\uED6D")
    val edit = AppIconGlyph("\uED33")
    val settings = AppIconGlyph("\uF32D")

    val owned = AppIconGlyph("\uEEC9")
    val ownedFilled = AppIconGlyph("\uEEC8")
    val liked = AppIconGlyph("\uEF13")
    val likedFilled = AppIconGlyph("\uEF0E")
}
