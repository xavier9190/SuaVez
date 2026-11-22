package xavier.pedro.suavez.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel compartilhado para Activities e Fragments acessarem o mesmo [SuaVezStateHolder]
 * sem recriar observadores do Firestore entre navegações.
 */
class SuaVezSharedViewModel(
    val stateHolder: SuaVezStateHolder,
) : ViewModel() {

    val uiState: StateFlow<SuaVezUiState> = stateHolder.uiState

    companion object {
        fun factory(containerProvider: () -> SuaVezAppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SuaVezSharedViewModel(containerProvider().stateHolder) as T
                }
            }

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "Application não encontrada em CreationExtras para SuaVezSharedViewModel"
                }
                val provider = app as? SuaVezContainerProvider
                    ?: error("${app::class.java.simpleName} deve implementar SuaVezContainerProvider")
                return SuaVezSharedViewModel(provider.container.stateHolder) as T
            }
        }
    }
}
