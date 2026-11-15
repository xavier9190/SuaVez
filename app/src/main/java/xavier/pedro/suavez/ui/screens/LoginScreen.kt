package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.ui.components.CreateAccountSheet
import xavier.pedro.suavez.ui.components.GoogleLogoIcon
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezDividerWithText
import xavier.pedro.suavez.ui.components.SuaVezInput
import xavier.pedro.suavez.ui.components.SuaVezWordmark
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun LoginScreen(
    onSignInEmail: (String) -> Unit,
    onSignInGoogle: () -> Unit,
    onCreateAccount: (name: String, email: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showCreateAccountSheet by remember { mutableStateOf(false) }

    fun attemptLogin() {
        val cleanEmail = email.trim()
        var valid = true
        if (!cleanEmail.contains("@") || cleanEmail.length < 4) {
            emailError = "Informe um e-mail válido"
            valid = false
        }
        if (password.isBlank()) {
            passwordError = "Informe sua senha"
            valid = false
        }
        if (valid) {
            onSignInEmail(cleanEmail)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
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

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SuaVezInput(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
                },
                label = "E-mail",
                placeholder = "voce@email.com",
                error = emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            SuaVezInput(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                },
                label = "Senha",
                placeholder = "Senha",
                isPassword = true,
                error = passwordError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { attemptLogin() }),
            )
            SuaVezButton(
                text = "Entrar",
                onClick = { attemptLogin() },
                variant = SuaVezButtonVariant.Primary,
            )
        }

        SuaVezDividerWithText("ou")

        Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
            SuaVezButton(
                text = "Entrar com o Google",
                onClick = onSignInGoogle,
                variant = SuaVezButtonVariant.Neutral,
                leadingContent = { GoogleLogoIcon(size = 18.dp) },
            )
            SuaVezButton(
                text = "Criar conta",
                onClick = { showCreateAccountSheet = true },
                variant = SuaVezButtonVariant.Neutral,
            )
        }
    }

    if (showCreateAccountSheet) {
        CreateAccountSheet(
            onDismiss = { showCreateAccountSheet = false },
            onCreateAccount = { name, accEmail ->
                showCreateAccountSheet = false
                onCreateAccount(name, accEmail)
            },
        )
    }
}
