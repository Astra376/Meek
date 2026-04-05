package com.example.aichat.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.appOutlineSurface

object AppChrome {
    val screenHorizontalPadding = 20.dp
    val screenTopPadding = 16.dp
    val screenBottomPadding = 24.dp
    val sectionSpacing = 14.dp
    val gridSpacing = 12.dp
    val compactControlSize = 42.dp
    val compactControlGap = 10.dp
    val compactHeaderVerticalPadding = 8.dp
    val headerActionIconSize = 24.dp
    val bottomBarHeight = 45.dp
    val bottomBarTapHeight = 45.dp
    val bottomBarHorizontalPadding = 16.dp
    val bottomBarVerticalPadding = 5.dp
    val bottomBarItemHorizontalPadding = 4.dp
    val bottomBarIconSize = 28.dp
    val listRowGap = 20.dp
}

@Composable
fun screenContentPadding(paddingValues: PaddingValues): PaddingValues {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(
        start = AppChrome.screenHorizontalPadding,
        top = statusBarPadding + paddingValues.calculateTopPadding() + AppChrome.screenTopPadding,
        end = AppChrome.screenHorizontalPadding,
        bottom = paddingValues.calculateBottomPadding() + AppChrome.screenBottomPadding
    )
}

fun Modifier.pageContentFrame(
    paddingValues: PaddingValues = PaddingValues(),
    imeAware: Boolean = false
): Modifier = composed {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var framedModifier = this
        .fillMaxSize()
        .padding(top = statusBarPadding + paddingValues.calculateTopPadding())
    if (imeAware) {
        framedModifier = framedModifier.imePadding()
    }
    framedModifier.padding(
        horizontal = AppChrome.screenHorizontalPadding,
        vertical = AppChrome.screenTopPadding
    )
}

fun Modifier.clearFocusOnTap(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    this.clickable(
        interactionSource = interactionSource,
        indication = null
    ) {
        focusManager.clearFocus(force = true)
    }
}

@Composable
fun ScreenBackgroundBox(
    snackbarHostState: SnackbarHostState? = null,
    clearFocusOnTap: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val topGlow = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f).compositeOver(background)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clearFocusOnTap(enabled = clearFocusOnTap)
            .background(background)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            topGlow,
                            background,
                            background
                        )
                    )
                )
        )
        snackbarHostState?.let {
            SnackbarHost(hostState = it)
        }
        content()
    }
}

@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(AppChrome.compactControlSize)
            .appOutlineSurface(shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            icon = AppIcons.back,
            contentDescription = "Back",
            size = AppChrome.headerActionIconSize
        )
    }
}

@Composable
fun SimplePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (trailing != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
    }
}
