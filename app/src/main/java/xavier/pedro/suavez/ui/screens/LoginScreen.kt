package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezCard
import xavier.pedro.suavez.ui.components.SuaVezWordmark
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun LoginScreen(
    onSignInAsAdmin: () -> Unit,
    onSignInAsMember: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SuaVezDimens.ScreenHorizontal, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(SuaVezDimens.SectionGap),
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        SuaVezWordmark(
            markSize = 48.dp,
            fontSize = 36.sp,
            gap = 10.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        SuaVezCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PERFIS DE TESTE",
                    style = typo.overline,
                    color = colors.muted,
                )
                Text(
                    text = "Escolha um perfil para entrar na casa compartilhada e validar a persistência no Firestore.",
                    style = typo.label,
                    color = colors.muted,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
            SuaVezButton(
                text = if (isLoading) "Entrando..." else "Entrar como Admin",
                onClick = onSignInAsAdmin,
                enabled = !isLoading,
                variant = SuaVezButtonVariant.Primary,
            )
            SuaVezButton(
                text = if (isLoading) "Entrando..." else "Entrar como Membro",
                onClick = onSignInAsMember,
                enabled = !isLoading,
                variant = SuaVezButtonVariant.Neutral,
            )
        }
    }
}
