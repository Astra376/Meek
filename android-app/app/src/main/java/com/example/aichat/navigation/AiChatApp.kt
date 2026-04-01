package com.example.aichat.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.aichat.core.ui.LoadingScreen
import com.example.aichat.feature.character.CharacterStudioRoute
import com.example.aichat.feature.chat.ChatRoute
import com.example.aichat.feature.chatlist.ChatListRoute
import com.example.aichat.feature.home.HomeRoute
import com.example.aichat.feature.profile.ProfileRoute
import com.example.aichat.feature.signin.SignInRoute

private sealed class MainDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
) {
    data object Home : MainDestination(
        route = "home",
        label = "Home",
        icon = { Icon(Icons.Outlined.Home, contentDescription = null) }
    )

    data object Studio : MainDestination(
        route = "studio",
        label = "Studio",
        icon = { Icon(Icons.Outlined.AddCircle, contentDescription = null) }
    )

    data object Chats : MainDestination(
        route = "chats",
        label = "Chats",
        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) }
    )

    data object Profile : MainDestination(
        route = "profile",
        label = "Profile",
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
    val showBottomBar = currentDestination?.route != "chat/{conversationId}"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Home.route
        ) {
            composable(MainDestination.Home.route) {
                HomeRoute(
                    paddingValues = paddingValues,
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
                )
            }
            composable(MainDestination.Studio.route) {
                CharacterStudioRoute(
                    paddingValues = paddingValues,
                    onOpenConversation = { conversationId ->
                        navController.navigate("chat/$conversationId")
                    }
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
                ProfileRoute(paddingValues = paddingValues)
            }
            composable("chat/{conversationId}") {
                ChatRoute(
                    paddingValues = paddingValues,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

