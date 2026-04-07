package com.example.aichat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIconGlyph
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.CircleAvatar
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.LoadingScreen
import com.example.aichat.feature.character.CharacterStudioRoute
import com.example.aichat.feature.chat.ChatRoute
import com.example.aichat.feature.chatlist.ChatListRoute
import com.example.aichat.feature.home.HomeRoute
import com.example.aichat.feature.home.NewHomeRoute
import com.example.aichat.feature.profile.EditProfileRoute
import com.example.aichat.feature.profile.ProfileRoute
import com.example.aichat.feature.profile.SettingsRoute
import com.example.aichat.feature.signin.SignInRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private sealed class MainDestination(
    val route: String,
    val contentDescription: String,
    val outlinedIcon: AppIconGlyph,
    val filledIcon: AppIconGlyph
) {
    data object Home : MainDestination(
        route = "home",
        contentDescription = "Home",
        outlinedIcon = AppIcons.homeNavOutline,
        filledIcon = AppIcons.homeNav
    )

    data object Discover : MainDestination(
        route = "discover",
        contentDescription = "Discover",
        outlinedIcon = AppIcons.discoverOutline,
        filledIcon = AppIcons.discover
    )

    data object Studio : MainDestination(
        route = "studio",
        contentDescription = "Create",
        outlinedIcon = AppIcons.createOutline,
        filledIcon = AppIcons.create
    )

    data object Chats : MainDestination(
        route = "chats",
        contentDescription = "Chats",
        outlinedIcon = AppIcons.chatsOutline,
        filledIcon = AppIcons.chats
    )

    data object Profile : MainDestination(
        route = "profile",
        contentDescription = "Profile",
        outlinedIcon = AppIcons.profileOutline,
        filledIcon = AppIcons.profile
    )
}

private val bottomDestinations = listOf(
    MainDestination.Home,
    MainDestination.Discover,
    MainDestination.Studio,
    MainDestination.Chats,
    MainDestination.Profile
)

private val subpageRoutes = setOf(
    "chat/{conversationId}",
    "edit-profile",
    "settings"
)

@Composable
fun AiChatApp(appViewModel: AppViewModel) {
    val session by appViewModel.sessionState.collectAsStateWithLifecycle()
    val profile by appViewModel.profile.collectAsStateWithLifecycle()
    val activeProfile = profile ?: session.profile

    when {
        session.isLoading -> LoadingScreen()
        !session.isSignedIn -> SignInRoute()
        else -> MainShell(
            profileName = activeProfile?.displayName.orEmpty(),
            profileAvatarUrl = activeProfile?.avatarUrl
        )
    }
}

@Composable
private fun MainShell(
    profileName: String,
    profileAvatarUrl: String?
) {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = "main_tabs",
        enterTransition = { EnterTransition.None },
        exitTransition = {
            if (targetState.destination.route in subpageRoutes) {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            } else {
                ExitTransition.None
            }
        },
        popEnterTransition = {
            if (initialState.destination.route in subpageRoutes) {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            } else {
                EnterTransition.None
            }
        },
        popExitTransition = { ExitTransition.None }
    ) {
        composable("main_tabs") {
            MainTabs(profileName, profileAvatarUrl, rootNavController)
        }
        
        composable(
            route = "chat/{conversationId}",
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 220)
                )
            }
        ) {
            ChatRoute(
                paddingValues = androidx.compose.foundation.layout.PaddingValues(),
                onBack = { rootNavController.popBackStack() }
            )
        }

        composable(
            route = "edit-profile",
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 220)
                )
            }
        ) {
            EditProfileRoute(
                paddingValues = androidx.compose.foundation.layout.PaddingValues(),
                onBack = { rootNavController.popBackStack() }
            )
        }

        composable(
            route = "settings",
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 220)
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 220)
                )
            }
        ) {
            SettingsRoute(
                paddingValues = androidx.compose.foundation.layout.PaddingValues(),
                onBack = { rootNavController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainTabs(
    profileName: String,
    profileAvatarUrl: String?,
    rootNavController: NavController
) {
    val bottomNavController = rememberNavController()
    val backStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val density = LocalDensity.current
    val minDragDistance = with(density) { 50.dp.toPx() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentDestination?.route) {
                var swipeDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { swipeDistance = 0f },
                    onDragEnd = {
                        if (swipeDistance.absoluteValue > minDragDistance) {
                            val currentIndex = bottomDestinations.indexOfFirst { it.route == currentDestination?.route }
                            if (currentIndex != -1) {
                                val nextIndex = if (swipeDistance < 0) currentIndex + 1 else currentIndex - 1
                                if (nextIndex in bottomDestinations.indices) {
                                    val nextRoute = bottomDestinations[nextIndex].route
                                    bottomNavController.navigate(nextRoute) {
                                        popUpTo(bottomNavController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeDistance += dragAmount
                    }
                )
            },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomIconBar(
                currentRoute = currentDestination?.route,
                profileName = profileName,
                profileAvatarUrl = profileAvatarUrl,
                onNavigate = { route ->
                    bottomNavController.navigate(route) {
                        popUpTo(bottomNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { routePaddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = MainDestination.Home.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(MainDestination.Home.route) {
                NewHomeRoute(
                    paddingValues = routePaddingValues,
                    onOpenConversation = { id -> rootNavController.navigate("chat/$id") },
                    onOpenStudio = { 
                        bottomNavController.navigate(MainDestination.Studio.route) {
                            popUpTo(bottomNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenChats = {
                        bottomNavController.navigate(MainDestination.Chats.route) {
                            popUpTo(bottomNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(MainDestination.Discover.route) {
                // The old Home feed becomes Discover
                HomeRoute(
                    paddingValues = routePaddingValues,
                    onOpenSearch = { /* No-op, search sub-page deleted */ },
                    onOpenActivity = { /* No-op, not used in old Home */ },
                    onOpenConversation = { id -> rootNavController.navigate("chat/$id") }
                )
            }
            composable(MainDestination.Studio.route) {
                CharacterStudioRoute(
                    paddingValues = routePaddingValues
                )
            }
            composable(MainDestination.Chats.route) {
                ChatListRoute(
                    paddingValues = routePaddingValues,
                    onOpenConversation = { id -> rootNavController.navigate("chat/$id") }
                )
            }
            composable(MainDestination.Profile.route) {
                ProfileRoute(
                    paddingValues = routePaddingValues,
                    onOpenConversation = { id -> rootNavController.navigate("chat/$id") },
                    onOpenEditProfile = { rootNavController.navigate("edit-profile") },
                    onOpenSettings = { rootNavController.navigate("settings") }
                )
            }
        }
    }
}

private class LineAnimState(val tabIndex: Int, val startX: Float) {
    val expand = Animatable(0f)
    val alpha = Animatable(0f)
}

@Composable
private fun BottomIconBar(
    modifier: Modifier = Modifier,
    currentRoute: String?,
    profileName: String,
    profileAvatarUrl: String?,
    onNavigate: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeClicks = remember { mutableStateListOf<LineAnimState>() }
    val interactionSources = remember { bottomDestinations.map { MutableInteractionSource() } }

    bottomDestinations.forEachIndexed { index, _ ->
        val source = interactionSources[index]
        LaunchedEffect(source) {
            source.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    val animState = LineAnimState(index, interaction.pressPosition.x)
                    activeClicks.add(animState)
                    launch {
                        launch {
                            animState.expand.snapTo(0f)
                            animState.expand.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
                        }
                        launch {
                            animState.alpha.snapTo(0.2f)
                            delay(150)
                            animState.alpha.animateTo(0f, tween(150, easing = LinearEasing))
                        }
                        delay(350)
                        activeClicks.remove(animState)
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    bottomDestinations.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            activeClicks.filter { it.tabIndex == index }.forEach { activeClick ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .graphicsLayer {
                                            val tapFraction = if (size.width > 0) activeClick.startX / size.width else 0.5f
                                            transformOrigin = TransformOrigin(tapFraction, 0.5f)
                                            scaleX = activeClick.expand.value
                                        }
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = activeClick.alpha.value),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppChrome.bottomBarHeight)
                    .padding(vertical = AppChrome.bottomBarVerticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomDestinations.forEachIndexed { index, destination ->
                    val selected = currentRoute == destination.route
                    BottomBarItem(
                        contentDescription = destination.contentDescription,
                        interactionSource = interactionSources[index],
                        onClick = {
                            coroutineScope.launch {
                                delay(75) // Grants rendering dispatcher uncontested UI priority for initial ripple frames
                                onNavigate(destination.route)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        if (destination == MainDestination.Profile) {
                            BottomBarProfileAvatar(
                                name = profileName.ifBlank { "User" },
                                avatarUrl = profileAvatarUrl,
                                selected = selected,
                                modifier = Modifier.size(AppChrome.bottomBarIconSize)
                            )
                        } else {
                            AppIcon(
                                icon = if (selected) destination.filledIcon else destination.outlinedIcon,
                                contentDescription = null,
                                size = AppChrome.bottomBarIconSize,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
private fun BottomBarProfileAvatar(
    name: String,
    avatarUrl: String?,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val outlineWidth = 1.5.dp
    val avatarModifier = if (selected) {
        Modifier
            .border(
                width = outlineWidth,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .padding(outlineWidth + 1.dp)
    } else {
        Modifier
    }

    CircleAvatar(
        name = name,
        avatarUrl = avatarUrl,
        modifier = modifier.then(avatarModifier)
    )
}

@Composable
private fun BottomBarItem(
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
