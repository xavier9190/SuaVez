package xavier.pedro.suavez.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import xavier.pedro.suavez.data.repository.SuaVezDataSource
import xavier.pedro.suavez.notifications.NoOpTaskReminderScheduler
import xavier.pedro.suavez.notifications.TaskReminderScheduler

/**
 * Container de escopo compartilhado em nível de processo.
 * Mantém uma única instância de [SuaVezStateHolder] para `LoginActivity`, `MainActivity`
 * e seus `Fragments`, preservando os observadores do Firestore durante a navegação.
 */
class SuaVezAppContainer(
    val dataSource: SuaVezDataSource,
    appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    val reminderScheduler: TaskReminderScheduler = NoOpTaskReminderScheduler,
) {
    val stateHolder: SuaVezStateHolder = SuaVezStateHolder(
        dataSource = dataSource,
        scope = appScope,
        reminderScheduler = reminderScheduler,
    )
}

interface SuaVezContainerProvider {
    val container: SuaVezAppContainer
}
