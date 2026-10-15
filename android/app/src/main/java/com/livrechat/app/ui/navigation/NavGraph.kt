package com.livrechat.app.ui.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.livrechat.app.LivreChatApp
import com.livrechat.app.ui.auth.AuthViewModel
import com.livrechat.app.ui.auth.LoginScreen
import com.livrechat.app.ui.auth.RecoveryScreen
import com.livrechat.app.ui.auth.RegisterScreen
import com.livrechat.app.ui.auth.VirtualNumberAuthScreen
import com.livrechat.app.ui.auth.WelcomeScreen
import com.livrechat.app.ui.call.ActiveCallScreen
import com.livrechat.app.ui.call.CallViewModel
import com.livrechat.app.ui.call.IncomingCallScreen
import com.livrechat.app.ui.chat.ChatScreen
import com.livrechat.app.ui.chat.ChatViewModel
import com.livrechat.app.ui.contacts.QRCodeScreen
import com.livrechat.app.ui.contacts.SearchContactScreen
import com.livrechat.app.ui.home.HomeScreen
import com.livrechat.app.ui.settings.BackupRestoreScreen
import com.livrechat.app.ui.settings.SettingsScreen
import com.livrechat.app.ui.settings.SettingsViewModel
import com.livrechat.app.webrtc.CallState

object Routes {
    const val WELCOME = "welcome"
    const val VIRTUAL_NUMBER_AUTH = "virtual_number_auth"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RECOVERY = "recovery"
    const val HOME = "home"
    const val CHAT = "chat/{convId}/{userId}/{name}"
    const val ACTIVE_CALL = "active_call"
    const val INCOMING_CALL = "incoming_call"
    const val SEARCH_CONTACT = "search_contact"
    const val QR_CODE = "qr_code"
    const val SETTINGS = "settings"
    const val BACKUP_RESTORE = "backup_restore"
}

@Composable
fun LivreChatNavGraph(
    navController: NavHostController,
    pendingIntent: Intent? = null,
    onIntentHandled: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    callViewModel: CallViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val isLoggedIn = LivreChatApp.instance.preferencesManager.isLoggedIn()
    val startDestination = if (isLoggedIn) Routes.HOME else Routes.WELCOME

    val callState by callViewModel.callState.collectAsState()
    val callInfo by callViewModel.callInfo.collectAsState()

    // Handle background notification click or incoming call intent
    LaunchedEffect(pendingIntent) {
        if (pendingIntent != null) {
            val isIncomingCall = pendingIntent.getBooleanExtra("incomingCall", false)
            if (isIncomingCall) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != Routes.INCOMING_CALL && currentRoute != Routes.ACTIVE_CALL) {
                    navController.navigate(Routes.INCOMING_CALL)
                }
                onIntentHandled()
            } else {
                val convId = pendingIntent.getStringExtra("conversationId")
                val senderName = pendingIntent.getStringExtra("senderName") ?: "Contato"
                if (!convId.isNullOrEmpty()) {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    val targetRoute = "chat/$convId/unknown/$senderName"
                    if (currentRoute != targetRoute) {
                        navController.navigate(targetRoute)
                    }
                    onIntentHandled()
                }
            }
        }
    }

    LaunchedEffect(callState, callInfo) {
        if (callState == CallState.RINGING && callInfo != null && !callInfo!!.isOutgoing) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Routes.INCOMING_CALL && currentRoute != Routes.ACTIVE_CALL) {
                navController.navigate(Routes.INCOMING_CALL)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(240)
            ) + fadeIn(animationSpec = tween(200))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(240)
            ) + fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(240)
            ) + fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(240)
            ) + fadeOut(animationSpec = tween(200))
        }
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onNavigateToVirtualNumber = { navController.navigate(Routes.VIRTUAL_NUMBER_AUTH) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.VIRTUAL_NUMBER_AUTH) {
            VirtualNumberAuthScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onAuthSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToRecovery = { navController.navigate(Routes.RECOVERY) },
                onNavigateToVirtualNumber = { navController.navigate(Routes.VIRTUAL_NUMBER_AUTH) },
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRegisterSuccessNav = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.RECOVERY) {
            RecoveryScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRecoverySuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.RECOVERY) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                chatViewModel = chatViewModel,
                callViewModel = callViewModel,
                onNavigateToChat = { convId, userId, name ->
                    navController.navigate("chat/$convId/$userId/$name")
                },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH_CONTACT) },
                onNavigateToQRCode = { navController.navigate(Routes.QR_CODE) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onStartCall = { userId, displayName, username, avatar ->
                    callViewModel.startOutgoingCall(userId, displayName, username, avatar)
                    navController.navigate(Routes.ACTIVE_CALL)
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("convId") { type = NavType.StringType },
                navArgument("userId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStack ->
            val convId = backStack.arguments?.getString("convId") ?: ""
            val userId = backStack.arguments?.getString("userId") ?: ""
            val name = backStack.arguments?.getString("name") ?: ""

            ChatScreen(
                conversationId = convId,
                otherUserId = userId,
                otherDisplayName = name,
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = { targetId, targetName, targetUsername, targetAvatar ->
                    callViewModel.startOutgoingCall(targetId, targetName, targetUsername, targetAvatar)
                    navController.navigate(Routes.ACTIVE_CALL)
                }
            )
        }

        composable(Routes.ACTIVE_CALL) {
            ActiveCallScreen(
                viewModel = callViewModel,
                onCallFinished = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute == Routes.ACTIVE_CALL) {
                        val popped = navController.popBackStack()
                        if (!popped || navController.currentBackStackEntry == null) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable(Routes.INCOMING_CALL) {
            IncomingCallScreen(
                viewModel = callViewModel,
                onCallAnswered = {
                    navController.navigate(Routes.ACTIVE_CALL) {
                        popUpTo(Routes.INCOMING_CALL) { inclusive = true }
                    }
                },
                onCallRejected = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute == Routes.INCOMING_CALL) {
                        val popped = navController.popBackStack()
                        if (!popped || navController.currentBackStackEntry == null) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable(Routes.SEARCH_CONTACT) {
            SearchContactScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { convId, userId, name ->
                    navController.navigate("chat/$convId/$userId/$name")
                },
                onNavigateToQRCode = { navController.navigate(Routes.QR_CODE) }
            )
        }

        composable(Routes.QR_CODE) {
            QRCodeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { convId, userId, name ->
                    navController.navigate("chat/$convId/$userId/$name")
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP_RESTORE) },
                onLogout = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.BACKUP_RESTORE) {
            BackupRestoreScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
