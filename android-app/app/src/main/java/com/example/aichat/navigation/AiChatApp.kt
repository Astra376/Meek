package com.example.aichat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
) {
    data object Home : MainDestination(
        route = "home",
        outlinedIcon = Icons.Outlined.Home,
        filledIcon = Icons.Filled.Home
    )

    data object Studio : MainDestination(
        route = "studio",
        outlinedIcon = Icons.Outlined.AddCircle,
        filledIcon = Icons.Filled.AddCircle
    )

    data object Chats : MainDestination(
        route = "chats",
        outlinedIcon = Icons.Outlined.ChatBubbleOutline,
        filledIcon = Icons.Filled.ChatBubble
    )

    data object Profile : MainDestination(
        route = "profile",
        outlinedIcon = Icons.Outlined.PersonOutline,
        filledIcon = Icons.Filled.Person
    )
}

private val bottomDestinations = listOf(
    MainDestination.Home,
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

    when {
        session.isLoading -> LoadingScreen()
        !session.isSignedIn -> SignInRoute()
        else -> MainShell()
    }
}

@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route !in subpageRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                BottomIconBar(
                    currentRoute = currentDestination?.route,
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
        }
    ) { paddingValues ->
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
                    paddingValues = paddingValues,
                    onOpenSearch = { navController.navigate("search") },
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
                )
            }
            composable(MainDestination.Studio.route) {
                CharacterStudioRoute(
                    paddingValues = paddingValues
                )
            }
            composable(MainDestination.Chats.route) {
                ChatListRoute(
                    paddingValues = paddingValues,
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
                )
            }
            composable(MainDestination.Profile.route) {
                ProfileRoute(
                    paddingValues = paddingValues,
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
                    paddingValues = paddingValues,
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
                    paddingValues = paddingValues,
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
                    paddingValues = paddingValues,
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
                    paddingValues = paddingValues,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun BottomIconBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .padding(horizontal = 8.dp)
                            .clickable { onNavigate(destination.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                            }
                        )
                    }
                }
            }
        }
    }
}
