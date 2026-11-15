package xavier.pedro.suavez.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VerdeCasaNight,
    onPrimary = ParedeNight,
    primaryContainer = NevoaNight,
    onPrimaryContainer = VerdeFundoNight,
    secondary = AmbarVezNight,
    onSecondary = Tinta,
    secondaryContainer = AmbarVezSuaveNight,
    onSecondaryContainer = AmbarVezTextoNight,
    background = ParedeNight,
    onBackground = TintaNight,
    surface = SuperficieNight,
    onSurface = TintaNight,
    surfaceVariant = NevoaNight,
    onSurfaceVariant = TintaSuaveNight,
    outline = LinhaNight,
    error = PrefOdeioNight,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = VerdeCasa,
    onPrimary = Color.White,
    primaryContainer = Nevoa,
    onPrimaryContainer = VerdeFundo,
    secondary = AmbarVez,
    onSecondary = Tinta,
    secondaryContainer = AmbarVezSuave,
    onSecondaryContainer = AmbarVezTexto,
    background = Parede,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    surfaceVariant = Nevoa,
    onSurfaceVariant = TintaSuave,
    outline = Linha,
    error = PrefOdeio,
    onError = Color.White,
)

object SuaVezTheme {
    val colors: SuaVezColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSuaVezColors.current

    val typography: SuaVezTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalSuaVezTypography.current
}

@Composable
fun SuaVezTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val suaVezColors = if (darkTheme) DarkSuaVezColors else LightSuaVezColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val suaVezTypography = SuaVezTypography()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = suaVezColors.bg.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = suaVezColors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalSuaVezColors provides suaVezColors,
        LocalSuaVezTypography provides suaVezTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
