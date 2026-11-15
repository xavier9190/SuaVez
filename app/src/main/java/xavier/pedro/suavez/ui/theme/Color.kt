package xavier.pedro.suavez.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light palette tokens (Identidade Visual v1 + Spec v3)
val VerdeCasa = Color(0xFF0E8C7F)
val VerdeFundo = Color(0xFF0A6B61)
val Nevoa = Color(0xFFE3F1EE)
val AmbarVez = Color(0xFFF5A524)
val AmbarVezSuave = Color(0xFFFEF1D8)
val AmbarVezTexto = Color(0xFF8A5A00)
val AmbarTraco = Color(0xFFB97800)

val Tinta = Color(0xFF13222E)
val TintaSuave = Color(0xFF5A6B77)
val TintaApagada = Color(0xFF8A979D)
val Linha = Color(0xFFDAE5E3)
val Parede = Color(0xFFF4F8F7)
val Superficie = Color(0xFFFFFFFF)

val PrefOdeio = Color(0xFFE2574C)
val PrefOdeioTexto = Color(0xFFC23D32)
val PrefTantoFaz = Color(0xFF8796A5)
val PrefGosto = Color(0xFF2E9E5B)

val RingTrack = Color(0xFFFBD9A0)
val CheckboxBorder = Color(0xFFB4C4C1)
val DashedBorder = Color(0xFFB9CFCB)

val AvatarPaletteLight = listOf(
    Color(0xFFD9F2EC),
    Color(0xFFDDEBFA),
    Color(0xFFFDEFD3),
    Color(0xFFF2E3F7),
)

// Dark palette tokens (Identidade noturna)
val VerdeCasaNight = Color(0xFF2FC2AE)
val VerdeFundoNight = Color(0xFF8CE6D8)
val NevoaNight = Color(0xFF16332F)
val AmbarVezNight = Color(0xFFF5A524)
val AmbarVezSuaveNight = Color(0xFF3A2A10)
val AmbarVezTextoNight = Color(0xFFFFD384)
val AmbarTracoNight = Color(0xFFF5A524)

val TintaNight = Color(0xFFE7F1EF)
val TintaSuaveNight = Color(0xFF9BB0B8)
val TintaApagadaNight = Color(0xFF6E838C)
val LinhaNight = Color(0xFF243945)
val ParedeNight = Color(0xFF0E1A22)
val SuperficieNight = Color(0xFF15252F)

val PrefOdeioNight = Color(0xFFE2574C)
val PrefOdeioTextoNight = Color(0xFFFF8F86)
val PrefTantoFazNight = Color(0xFF8796A5)
val PrefGostoNight = Color(0xFF3CC272)

val RingTrackNight = Color(0xFF56411B)
val CheckboxBorderNight = Color(0xFF3E5964)
val DashedBorderNight = Color(0xFF2E4D59)

val AvatarPaletteDark = listOf(
    Color(0xFF1D4942),
    Color(0xFF1F3B52),
    Color(0xFF4B381B),
    Color(0xFF3E284A),
)

@Immutable
data class SuaVezColors(
    val teal: Color,
    val tealDeep: Color,
    val mist: Color,
    val sol: Color,
    val solSoft: Color,
    val solInk: Color,
    val solStroke: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val bg: Color,
    val surface: Color,
    val coral: Color,
    val coralInk: Color,
    val leaf: Color,
    val slate: Color,
    val ringTrack: Color,
    val checkboxBorder: Color,
    val dashedBorder: Color,
    val avatarColors: List<Color>,
    val isDark: Boolean,
)

val LightSuaVezColors = SuaVezColors(
    teal = VerdeCasa,
    tealDeep = VerdeFundo,
    mist = Nevoa,
    sol = AmbarVez,
    solSoft = AmbarVezSuave,
    solInk = AmbarVezTexto,
    solStroke = AmbarTraco,
    ink = Tinta,
    muted = TintaSuave,
    faint = TintaApagada,
    line = Linha,
    bg = Parede,
    surface = Superficie,
    coral = PrefOdeio,
    coralInk = PrefOdeioTexto,
    leaf = PrefGosto,
    slate = PrefTantoFaz,
    ringTrack = RingTrack,
    checkboxBorder = CheckboxBorder,
    dashedBorder = DashedBorder,
    avatarColors = AvatarPaletteLight,
    isDark = false,
)

val DarkSuaVezColors = SuaVezColors(
    teal = VerdeCasaNight,
    tealDeep = VerdeFundoNight,
    mist = NevoaNight,
    sol = AmbarVezNight,
    solSoft = AmbarVezSuaveNight,
    solInk = AmbarVezTextoNight,
    solStroke = AmbarTracoNight,
    ink = TintaNight,
    muted = TintaSuaveNight,
    faint = TintaApagadaNight,
    line = LinhaNight,
    bg = ParedeNight,
    surface = SuperficieNight,
    coral = PrefOdeioNight,
    coralInk = PrefOdeioTextoNight,
    leaf = PrefGostoNight,
    slate = PrefTantoFazNight,
    ringTrack = RingTrackNight,
    checkboxBorder = CheckboxBorderNight,
    dashedBorder = DashedBorderNight,
    avatarColors = AvatarPaletteDark,
    isDark = true,
)

val LocalSuaVezColors = staticCompositionLocalOf { LightSuaVezColors }
