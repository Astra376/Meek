package com.example.aichat.core.design

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import com.github.yohannestz.iconsax_compose.iconsax.Iconsax

typealias AppIconGlyph = ImageVector

@Composable
fun AppIcon(
    icon: AppIconGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

object AppIcons {
    val back = Iconsax.Linear.ArrowLeft

    val homeOutline = Iconsax.Linear.Home
    val home = Iconsax.Bold.Home
    val homeNavOutline = Iconsax.Linear.HomeThree
    val homeNav = Iconsax.Bold.HomeThree

    val createOutline = Iconsax.Linear.AddCircle
    val create = Iconsax.Bold.AddCircle
    val createAction = Iconsax.Linear.AddCircle

    val chatsOutline = Iconsax.Linear.MessagesTwo
    val chats = Iconsax.Bold.MessagesTwo

    val profileOutline = Iconsax.Linear.Profile
    val profile = Iconsax.Bold.Profile

    val search = Iconsax.Linear.SearchNormalOne
    val searchFilled = Iconsax.Bold.SearchNormalOne
    val discoverOutline = Iconsax.Linear.Discover
    val discover = Iconsax.Bold.Discover
    val sparkle = Iconsax.Linear.MagicStar
    val clear = Iconsax.Linear.Trash
    val public = Iconsax.Linear.Global
    val lock = Iconsax.Linear.LockOne
    val send = Iconsax.Linear.Send
    val stop = Iconsax.Bold.Stop
    val previous = Iconsax.Linear.ArrowLeft
    val next = Iconsax.Linear.ArrowRight

    val themeSystem = Iconsax.Linear.Monitor
    val themeDark = Iconsax.Linear.Moon
    val themeLight = Iconsax.Linear.SunOne

    val logout = Iconsax.Linear.LogoutCurve
    val edit = Iconsax.Linear.Edit
    val settings = Iconsax.Linear.SettingTwo

    val owned = Iconsax.Linear.GridFour
    val ownedFilled = Iconsax.Bold.GridFour
    val liked = Iconsax.Linear.Heart
    val likedFilled = Iconsax.Bold.Heart
}
