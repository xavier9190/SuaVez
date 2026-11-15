package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import xavier.pedro.suavez.ui.components.CreateHouseSheet
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezDividerWithText
import xavier.pedro.suavez.ui.components.SuaVezInput
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun NoHouseScreen(
    onCreateHouse: (String) -> Unit,
    onJoinHouse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    var inviteCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var showCreateHouseSheet by remember { mutableStateOf(false) }

    fun attemptJoin() {
        val clean = inviteCode.trim()
        if (clean.length < 4) {
            codeError = "Digite um código válido (ex.: SV-7824)"
        } else {
            onJoinHouse(clean)
        }
    }

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
            Text(text = "Sua casa", style = typo.pageTitle, color = colors.ink)
            Text(
                text = "Crie a casa ou entre com um código de convite.",
                style = typo.body,
                color = colors.muted,
            )
        }

        SuaVezButton(
            text = "Criar casa",
            onClick = { showCreateHouseSheet = true },
            variant = SuaVezButtonVariant.Primary,
        )

        SuaVezDividerWithText("ou")

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SuaVezInput(
                value = inviteCode,
                onValueChange = {
                    inviteCode = it
                    codeError = null
                },
                label = "Código de convite",
                placeholder = "Digite o código",
                error = codeError,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { attemptJoin() }),
            )
            SuaVezButton(
                text = "Entrar na casa",
                onClick = { attemptJoin() },
                variant = SuaVezButtonVariant.Secondary,
            )
        }
    }

    if (showCreateHouseSheet) {
        CreateHouseSheet(
            onDismiss = { showCreateHouseSheet = false },
            onCreateHouse = { name ->
                showCreateHouseSheet = false
                onCreateHouse(name)
            },
        )
    }
}
