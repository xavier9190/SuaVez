package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xavier.pedro.suavez.ui.components.SuaVezCard
import xavier.pedro.suavez.ui.components.SuaVezMark
import xavier.pedro.suavez.ui.components.SuaVezPreferenceSegmented
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun PreferencesScreen(
    state: SuaVezStateHolder,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val uiState by state.uiState.collectAsState()
    val recurringTasks = uiState.recurringTasks

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = SuaVezDimens.ScreenHorizontal,
                top = SuaVezDimens.ScreenTop,
                end = SuaVezDimens.ScreenHorizontal,
                bottom = SuaVezDimens.ScreenBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(SuaVezDimens.SectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Preferências",
                style = typo.pageTitle,
                color = colors.ink,
            )
            Text(
                text = "Defina como você se sente sobre cada tarefa doméstica para um sorteio justo.",
                style = typo.body,
                color = colors.muted,
            )
        }

        if (recurringTasks.isEmpty()) {
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
                        text = "Nenhuma tarefa recorrente ainda",
                        style = typo.itemTitle,
                        color = colors.ink,
                    )
                    Text(
                        text = "Quando o admin cadastrar tarefas recorrentes na Casa, elas aparecerão aqui.",
                        style = typo.meta,
                        color = colors.muted,
                    )
                }
            }
        } else {
            recurringTasks.forEach { task ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.titulo,
                        style = typo.itemTitle,
                        color = colors.ink,
                    )
                    SuaVezPreferenceSegmented(
                        selected = uiState.getMyPreference(task.id),
                        onSelect = { level -> state.setPreference(task.id, level) },
                    )
                }
            }
        }
    }
}
