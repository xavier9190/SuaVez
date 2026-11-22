package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xavier.pedro.suavez.ui.components.ActivityFormSheet
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezCard
import xavier.pedro.suavez.ui.components.SuaVezIcon
import xavier.pedro.suavez.ui.components.SuaVezIconType
import xavier.pedro.suavez.ui.components.SuaVezMark
import xavier.pedro.suavez.ui.components.SuaVezOpenTaskCard
import xavier.pedro.suavez.ui.components.SuaVezSubBar
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun OpenTasksScreen(
    state: SuaVezStateHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val uiState by state.uiState.collectAsState()
    val visibleOpenTasks = uiState.openScreenAssignments
    val trulyOpenTasks = uiState.openAssignments
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingOpenTask by remember { mutableStateOf<UiAssignment?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SuaVezSubBar(
            title = "Tarefas abertas",
            onBack = onBack,
            onAddClick = { showCreateSheet = true },
            addContentDescription = "Criar atividade",
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = SuaVezDimens.ScreenHorizontal,
                    top = SuaVezDimens.ScreenTop,
                    end = SuaVezDimens.ScreenHorizontal,
                    bottom = SuaVezDimens.ScreenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(SuaVezDimens.SectionGap),
        ) {
            Text(
                text = "Tarefas avulsas desta semana. Quem pegar soma os pontos; o que sobrar entra no sorteio justo.",
                style = typo.body,
                color = colors.muted,
            )

            Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
                if (visibleOpenTasks.isEmpty()) {
                    SuaVezCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SuaVezMark(size = 40.dp)
                            Text(
                                text = "Nenhuma tarefa aberta",
                                style = typo.itemTitle,
                                color = colors.ink,
                            )
                            Text(
                                text = "Todas as tarefas da semana já foram atribuídas.",
                                style = typo.meta,
                                color = colors.muted,
                            )
                        }
                    }
                } else {
                    visibleOpenTasks.forEach { task ->
                        SuaVezOpenTaskCard(
                            task = task,
                            onClaim = { state.claimOpenTask(task.id) },
                            onEdit = if (uiState.isAdmin && !task.justClaimedInSession) {
                                { editingOpenTask = task }
                            } else {
                                null
                            },
                        )
                    }
                }

                SuaVezButton(
                    text = "Criar atividade",
                    onClick = { showCreateSheet = true },
                    variant = SuaVezButtonVariant.Dashed,
                    leadingContent = {
                        SuaVezIcon(
                            type = SuaVezIconType.PLUS,
                            tint = colors.tealDeep,
                            size = 18.dp,
                        )
                    },
                )
            }

            if (uiState.isAdmin) {
                Spacer(modifier = Modifier.weight(1f).heightIn(min = 12.dp))
                SuaVezButton(
                    text = "Distribuir pendentes",
                    onClick = { state.distributePendingTasks() },
                    variant = SuaVezButtonVariant.Tonal,
                    enabled = trulyOpenTasks.isNotEmpty(),
                )
            }
        }
    }

    if (showCreateSheet) {
        ActivityFormSheet(
            isAdmin = uiState.isAdmin,
            onDismiss = { showCreateSheet = false },
            onCreateWeeklyTask = { title, category, points, deadline ->
                showCreateSheet = false
                state.addWeeklyTask(title, category, points, deadline)
            },
            onSaveRecurringTask = { title, category, points, frequency, openInCurrentWeek, deadline ->
                showCreateSheet = false
                state.saveRecurringTask(
                    editingId = null,
                    title = title,
                    category = category,
                    points = points,
                    frequency = frequency,
                    openInCurrentWeek = openInCurrentWeek,
                    deadline = deadline,
                )
            },
        )
    }

    val assignmentToEdit = editingOpenTask
    if (assignmentToEdit != null) {
        ActivityFormSheet(
            isAdmin = uiState.isAdmin,
            editingAssignment = assignmentToEdit,
            onDismiss = { editingOpenTask = null },
            onUpdateWeeklyAssignment = { id, title, category, points, deadline ->
                editingOpenTask = null
                state.updateWeeklyAssignment(id, title, category, points, deadline)
            },
            onDeleteWeeklyAssignment = { toDelete ->
                editingOpenTask = null
                state.deleteWeeklyAssignment(toDelete.id)
            },
        )
    }
}
