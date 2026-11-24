package xavier.pedro.suavez.ui

import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiRecurringTask

data class PreferenceTaskItemUiModel(
    val task: UiRecurringTask,
    val selectedPreference: PreferenceLevel,
)

data class PreferencesUiModel(
    val items: List<PreferenceTaskItemUiModel>,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

fun SuaVezUiState.toPreferencesUiModel(): PreferencesUiModel? {
    if (currentUser == null || currentHouse == null) return null
    val items = recurringTasks.map { task ->
        PreferenceTaskItemUiModel(
            task = task,
            selectedPreference = getMyPreference(task.id),
        )
    }
    return PreferencesUiModel(items = items)
}
