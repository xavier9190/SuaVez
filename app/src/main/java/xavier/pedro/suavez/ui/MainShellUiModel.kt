package xavier.pedro.suavez.ui

import xavier.pedro.suavez.ui.state.AppDestination
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiUser

enum class MainShellDestination {
    NO_HOUSE,
    TASKS,
    PREFERENCES,
    HOUSE,
    PROFILE,
    OPEN_TASKS,
}

data class MainShellUiModel(
    val hasHouse: Boolean,
    val visibleTabs: List<MainTab>,
    val selectedTab: MainTab,
    val destination: MainShellDestination,
    val showTopBar: Boolean,
    val houseTitle: String?,
    val avatarUser: UiUser?,
)

fun SuaVezUiState.toMainShellUiModel(): MainShellUiModel {
    val hasHouse = currentHouse != null
    val visibleTabs = if (hasHouse) {
        listOf(MainTab.TASKS, MainTab.PREFERENCES, MainTab.HOUSE, MainTab.PROFILE)
    } else {
        listOf(MainTab.HOUSE, MainTab.PROFILE)
    }
    val selectedTab = if (!hasHouse && (activeTab == MainTab.TASKS || activeTab == MainTab.PREFERENCES)) {
        MainTab.HOUSE
    } else {
        activeTab
    }
    val shellDestination = when (destination) {
        AppDestination.OpenTasks -> if (hasHouse) MainShellDestination.OPEN_TASKS else MainShellDestination.NO_HOUSE
        AppDestination.Login, AppDestination.NoHouse, AppDestination.Main -> when (selectedTab) {
            MainTab.TASKS -> if (hasHouse) MainShellDestination.TASKS else MainShellDestination.NO_HOUSE
            MainTab.PREFERENCES -> if (hasHouse) MainShellDestination.PREFERENCES else MainShellDestination.NO_HOUSE
            MainTab.HOUSE -> if (hasHouse) MainShellDestination.HOUSE else MainShellDestination.NO_HOUSE
            MainTab.PROFILE -> MainShellDestination.PROFILE
        }
    }
    return MainShellUiModel(
        hasHouse = hasHouse,
        visibleTabs = visibleTabs,
        selectedTab = selectedTab,
        destination = shellDestination,
        showTopBar = destination != AppDestination.OpenTasks || !hasHouse,
        houseTitle = currentHouse?.nome?.takeIf { it.isNotBlank() },
        avatarUser = if (currentUser != null && selectedTab != MainTab.PROFILE) currentUser else null,
    )
}
