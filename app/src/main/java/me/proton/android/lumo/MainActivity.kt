package me.proton.android.lumo

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.android.lumo.chat.viewmodel.ChatViewModel
import me.proton.android.lumo.navigation.NavRoutes
import me.proton.android.lumo.notification.LumoNotifier
import me.proton.android.lumo.permission.rememberSinglePermission
import me.proton.android.lumo.ui.components.chat.ChatScreen
import me.proton.android.lumo.ui.components.chat.ConversationsDrawer
import me.proton.android.lumo.ui.components.dialog.PermissionDialog
import me.proton.android.lumo.ui.components.lumos.LumoEditorScreen
import me.proton.android.lumo.ui.components.lumos.LumoManagerScreen
import me.proton.android.lumo.ui.components.settings.SettingsScreen
import me.proton.android.lumo.ui.components.speech.SpeechSheet
import me.proton.android.lumo.ui.theme.AppStyle
import me.proton.android.lumo.ui.theme.LumoTheme
import me.proton.android.lumo.utils.openSettings
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notifier: LumoNotifier

    private val chatViewModel: ChatViewModel by viewModels()
    private val themeViewModel: MainThemeViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — non-fatal */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("UNUSED_EXPRESSION")
        notifier // force-init channels
        requestNotificationPermissionIfNeeded()
        setContent {
            MainScreen()
        }
    }

    @Composable
    private fun MainScreen() {
        val theme by themeViewModel.theme.collectAsStateWithLifecycle()
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val isDarkTheme by remember {
            derivedStateOf {
                theme?.let {
                    when (it) {
                        is AppStyle.System -> isSystemInDarkTheme
                        is AppStyle.Dark -> true
                        is AppStyle.Light -> false
                    }
                } ?: isSystemInDarkTheme
            }
        }

        val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

        val navController = rememberNavController()
        val audioPermission = rememberSinglePermission(
            permission = Manifest.permission.RECORD_AUDIO,
            onGrant = { navController.navigate(NavRoutes.SpeechToText) },
            onDeny = {
                navController.navigate(NavRoutes.MissingPermission(Manifest.permission.RECORD_AUDIO))
            },
        )

        LaunchedEffect(isDarkTheme) {
            enableEdgeToEdge(
                statusBarStyle = systemBarStyle(isDarkTheme),
                navigationBarStyle = systemBarStyle(isDarkTheme),
            )
        }

        LumoTheme(darkTheme = isDarkTheme) {
            AppNavigation(
                navController = navController,
                chatState = chatState,
                onSelectConversation = { chatViewModel.selectConversation(it) },
                onNewConversation = { chatViewModel.newConversation() },
                onTogglePin = { chatViewModel.togglePin(it) },
                onDeleteConversation = { chatViewModel.deleteConversation(it) },
                onOpenSpeech = { audioPermission.request() },
            )
        }
    }

    @Composable
    private fun AppNavigation(
        navController: androidx.navigation.NavHostController,
        chatState: ChatViewModel.ChatUiState,
        onSelectConversation: (Long) -> Unit,
        onNewConversation: () -> Unit,
        onTogglePin: (me.proton.android.lumo.data.db.entity.ConversationEntity) -> Unit,
        onDeleteConversation: (me.proton.android.lumo.data.db.entity.ConversationEntity) -> Unit,
        onOpenSpeech: () -> Unit,
    ) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ConversationsDrawer(
            drawerState = drawerState,
            lumo = chatState.currentLumo,
            conversations = chatState.conversations,
            currentConversationId = chatState.currentConversation?.id,
            onSelectConversation = {
                onSelectConversation(it)
                scope.launch { drawerState.close() }
            },
            onNewConversation = {
                onNewConversation()
                scope.launch { drawerState.close() }
            },
            onTogglePin = onTogglePin,
            onDeleteConversation = onDeleteConversation,
            onSwitchLumo = {
                scope.launch { drawerState.close() }
                navController.navigate(NavRoutes.LumoManager)
            },
            onOpenSettings = {
                scope.launch { drawerState.close() }
                navController.navigate(NavRoutes.Settings)
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.Chat,
            ) {
                composable<NavRoutes.Chat> {
                    ChatScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSettings = { navController.navigate(NavRoutes.Settings) },
                        onOpenLumoManager = { navController.navigate(NavRoutes.LumoManager) },
                        onOpenSpeech = onOpenSpeech,
                        viewModel = chatViewModel,
                    )
                }
                composable<NavRoutes.LumoManager> {
                    LumoManagerScreen(
                        onBack = { navController.popBackStack() },
                        onEditLumo = { id -> navController.navigate(NavRoutes.LumoEditor(id)) },
                    )
                }
                composable<NavRoutes.LumoEditor> { backStack ->
                    val args = backStack.toRoute<NavRoutes.LumoEditor>()
                    LumoEditorScreen(
                        lumoId = args.lumoId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<NavRoutes.Settings> {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable<NavRoutes.SpeechToText> {
                    SpeechSheet(onDismiss = { navController.popBackStack() })
                }
                composable<NavRoutes.MissingPermission> {
                    PermissionDialog(
                        openSettings = {
                            openSettings()
                            navController.popBackStack()
                        },
                        onDismiss = { navController.popBackStack() },
                    )
                }
            }
        }
    }

    private fun systemBarStyle(isDarkTheme: Boolean): SystemBarStyle =
        if (isDarkTheme) {
            SystemBarStyle.dark(Color.Transparent.toArgb())
        } else {
            SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
        }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
