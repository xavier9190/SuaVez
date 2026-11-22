package xavier.pedro.suavez.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xavier.pedro.suavez.data.repository.FirestoreSuaVezDataSource
import xavier.pedro.suavez.ui.components.SuaVezAppBar
import xavier.pedro.suavez.ui.components.SuaVezTabBar
import xavier.pedro.suavez.ui.screens.HouseScreen
import xavier.pedro.suavez.ui.screens.LoginScreen
import xavier.pedro.suavez.ui.screens.NoHouseScreen
import xavier.pedro.suavez.ui.screens.OpenTasksScreen
import xavier.pedro.suavez.ui.screens.PreferencesScreen
import xavier.pedro.suavez.ui.screens.ProfileScreen
import xavier.pedro.suavez.ui.screens.TasksScreen
import xavier.pedro.suavez.ui.state.AppDestination
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.state.SnackbarFeedback
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.TestProfile
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun SuaVezApp(
    state: SuaVezStateHolder? = null,
) {
    val colors = SuaVezTheme.colors
    val scope = rememberCoroutineScope()
    val holder = state ?: remember(scope) {
        SuaVezStateHolder(
            dataSource = FirestoreSuaVezDataSource(),
            scope = scope,
        )
    }

    val uiState by holder.uiState.collectAsState()

    BackHandler(enabled = uiState.destination != AppDestination.Login) {
        holder.navigateBack()
    }

    val activeSnackbar = uiState.snackbar
    LaunchedEffect(activeSnackbar?.id) {
        if (activeSnackbar != null) {
            delay(3000)
            holder.clearSnackbar()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        when (uiState.destination) {
            AppDestination.Login -> {
                LoginScreen(
                    isLoading = uiState.isAuthenticating,
                    onSignInAsAdmin = { scope.launch { holder.signInAs(TestProfile.ADMIN) } },
                    onSignInAsMember = { scope.launch { holder.signInAs(TestProfile.MEMBER) } },
                )
            }

            AppDestination.NoHouse -> {
                val user = uiState.currentUser
                Column(modifier = Modifier.fillMaxSize()) {
                    SuaVezAppBar(
                        houseName = null,
                        userName = if (uiState.activeTab != MainTab.PROFILE) user?.nome else null,
                        userId = if (uiState.activeTab != MainTab.PROFILE) user?.uid else null,
                        onAvatarClick = { holder.selectTab(MainTab.PROFILE) },
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.activeTab) {
                            MainTab.PROFILE -> ProfileScreen(state = holder)
                            else -> NoHouseScreen(
                                onCreateHouse = { name -> holder.createHouse(name) },
                                onJoinHouse = { code -> holder.joinHouseByCode(code) },
                            )
                        }
                        SnackbarBannerOverlay(
                            feedback = activeSnackbar,
                            onDismiss = { holder.clearSnackbar() },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    SuaVezTabBar(
                        activeTab = uiState.activeTab,
                        hasHouse = false,
                        onSelectTab = { holder.selectTab(it) },
                    )
                }
            }

            AppDestination.Main -> {
                val user = uiState.currentUser
                val house = uiState.currentHouse
                Column(modifier = Modifier.fillMaxSize()) {
                    SuaVezAppBar(
                        houseName = house?.nome,
                        userName = if (uiState.activeTab != MainTab.PROFILE) user?.nome else null,
                        userId = if (uiState.activeTab != MainTab.PROFILE) user?.uid else null,
                        onAvatarClick = { holder.selectTab(MainTab.PROFILE) },
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.activeTab) {
                            MainTab.TASKS -> TasksScreen(
                                state = holder,
                                onOpenTasksClick = { holder.openOpenTasks() },
                            )
                            MainTab.PREFERENCES -> PreferencesScreen(state = holder)
                            MainTab.HOUSE -> HouseScreen(state = holder)
                            MainTab.PROFILE -> ProfileScreen(state = holder)
                        }
                        SnackbarBannerOverlay(
                            feedback = activeSnackbar,
                            onDismiss = { holder.clearSnackbar() },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    SuaVezTabBar(
                        activeTab = uiState.activeTab,
                        hasHouse = true,
                        onSelectTab = { holder.selectTab(it) },
                    )
                }
            }

            AppDestination.OpenTasks -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        OpenTasksScreen(
                            state = holder,
                            onBack = { holder.navigateBack() },
                        )
                        SnackbarBannerOverlay(
                            feedback = activeSnackbar,
                            onDismiss = { holder.clearSnackbar() },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    SuaVezTabBar(
                        activeTab = MainTab.TASKS,
                        hasHouse = true,
                        onSelectTab = { holder.selectTab(it) },
                    )
                }
            }
        }

        if (uiState.destination == AppDestination.Login) {
            SnackbarBannerOverlay(
                feedback = activeSnackbar,
                onDismiss = { holder.clearSnackbar() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SnackbarBannerOverlay(
    feedback: SnackbarFeedback?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SuaVezDimens.ScreenHorizontal, vertical = 12.dp),
    ) {
        if (feedback != null) {
            Surface(
                shape = RoundedCornerShape(SuaVezDimens.RadiusControl),
                color = colors.ink,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = feedback.message,
                        style = typo.label,
                        color = if (colors.isDark) colors.bg else Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (feedback.actionLabel != null && feedback.onAction != null) {
                        Text(
                            text = feedback.actionLabel,
                            style = typo.buttonSm.copy(fontWeight = FontWeight.Bold),
                            color = colors.sol,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    feedback.onAction.invoke()
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
