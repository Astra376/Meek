package com.example.aichat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import com.example.aichat.feature.home.SearchRoute
import com.example.aichat.feature.profile.EditProfileRoute
import com.example.aichat.feature.profile.ProfileRoute
import com.example.aichat.feature.profile.SettingsRoute
import com.example.aichat.feature.signin.SignInRoute

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

    data object Search : MainDestination(
        route = "search",
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
    MainDestination.Search,
    MainDestination.Studio,
    MainDestination.Chats,
    MainDestination.Profile
)

private val subpageRoutes = setOf(
    "chat/{conversationId}",
    "search",
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route !in subpageRoutes
    val density = LocalDensity.current
    val bottomBarOffset by animateFloatAsState(
        targetValue = if (showBottomBar) {
            0f
        } else {
            with(density) { (AppChrome.bottomBarHeight + 20.dp).toPx() }
        },
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarOffset"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomIconBar(
                modifier = Modifier.graphicsLayer { translationY = bottomBarOffset },
                currentRoute = currentDestination?.route,
                profileName = profileName,
                profileAvatarUrl = profileAvatarUrl,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { paddingValues ->
        val routePaddingValues = if (showBottomBar) paddingValues else PaddingValues()
        NavHost(
            navController = navController,
            startDestination = MainDestination.Home.route,
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
            composable(MainDestination.Home.route) {
                HomeRoute(
                    paddingValues = routePaddingValues,
                    onOpenSearch = { navController.navigate("search") },
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
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
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
                )
            }
            composable(MainDestination.Profile.route) {
                ProfileRoute(
                    paddingValues = routePaddingValues,
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    },
                    onOpenEditProfile = { navController.navigate("edit-profile") },
                    onOpenSettings = { navController.navigate("settings") }
                )
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
                    paddingValues = routePaddingValues,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "search",
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
                SearchRoute(
                    paddingValues = routePaddingValues,
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
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
                    paddingValues = routePaddingValues,
                    onBack = { navController.popBackStack() }
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
                    paddingValues = routePaddingValues,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
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
    val clickAnims = remember { bottomDestinations.map { Animatable(0f) } }

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
                            val anim = clickAnims[index]
                            if (anim.value > 0f) {
                                val progress = anim.value
                                val timeMs = progress * 350f
                                val expandProgress = (timeMs / 300f).coerceIn(0f, 1f)
                                val fraction = 0.5f + (expandProgress * 0.5f)
                                val alpha = when {
                                    timeMs < 40f -> (timeMs / 40f) * 0.2f
                                    timeMs < 250f -> 0.2f
                                    else -> (1f - (timeMs - 250f) / 100f) * 0.2f
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(0.5.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
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
                        onClick = {
                            coroutineScope.launch {
                                clickAnims[index].snapTo(0f)
                                clickAnims[index].animateTo(1f, tween(350))
                            }
                            onNavigate(destination.route)
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
            .padding(outlineWidth)
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(avatarModifier),
        contentAlignment = Alignment.Center
    ) {
        CircleAvatar(
            name = name,
            avatarUrl = avatarUrl,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BottomBarItem(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(AppChrome.bottomBarTapHeight)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
