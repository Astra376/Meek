package com.example.aichat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val icon: @Composable () -> Unit
) {
    data object Home : MainDestination(
        route = "home",
        icon = { Icon(Icons.Outlined.Home, contentDescription = null) }
    )

    data object Studio : MainDestination(
        route = "studio",
        icon = { Icon(Icons.Outlined.AddCircle, contentDescription = null) }
    )

    data object Chats : MainDestination(
        route = "chats",
        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) }
    )

    data object Profile : MainDestination(
        route = "profile",
        icon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) }
    )
}

private val bottomDestinations = listOf(
    MainDestination.Home,
    MainDestination.Studio,
    MainDestination.Chats,
    MainDestination.Profile
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
    val hiddenBottomBarRoutes = setOf(
        "chat/{conversationId}",
        "search",
        "edit-profile",
        "settings"
    )
    val showBottomBar = currentDestination?.route !in hiddenBottomBarRoutes

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
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
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
            composable("chat/{conversationId}") {
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
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
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
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
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
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                    ) {
                        androidx.compose.material3.IconButton(
                            onClick = { onNavigate(destination.route) }
                        ) {
                            destination.icon()
                        }
                    }
                }
            }
        }
    }
}
