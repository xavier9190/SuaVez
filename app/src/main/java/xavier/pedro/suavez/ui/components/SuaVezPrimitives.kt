package xavier.pedro.suavez.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.ui.state.avatarIndexFor
import xavier.pedro.suavez.ui.state.initialsOf
import xavier.pedro.suavez.ui.theme.DisplayFontFamily
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

enum class SuaVezButtonVariant {
    Primary,
    Tonal,
    Secondary,
    Neutral,
    Destructive,
    Dashed,
    Ghost,
}

@Composable
fun SuaVezButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SuaVezButtonVariant = SuaVezButtonVariant.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingIcon: SuaVezIconType? = null,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseBg = when (variant) {
        SuaVezButtonVariant.Primary -> colors.teal
        SuaVezButtonVariant.Tonal -> colors.mist
        SuaVezButtonVariant.Secondary -> Color.Transparent
        SuaVezButtonVariant.Neutral -> colors.surface
        SuaVezButtonVariant.Destructive -> Color.Transparent
        SuaVezButtonVariant.Dashed -> colors.surface.copy(alpha = 0.65f)
        SuaVezButtonVariant.Ghost -> Color.Transparent
    }
    val contentColor = when (variant) {
        SuaVezButtonVariant.Primary -> if (colors.isDark) colors.bg else Color.White
        SuaVezButtonVariant.Tonal -> colors.tealDeep
        SuaVezButtonVariant.Secondary -> colors.teal
        SuaVezButtonVariant.Neutral -> colors.ink
        SuaVezButtonVariant.Destructive -> colors.coral
        SuaVezButtonVariant.Dashed -> colors.tealDeep
        SuaVezButtonVariant.Ghost -> colors.muted
    }
    val borderColor = when (variant) {
        SuaVezButtonVariant.Secondary -> colors.teal
        SuaVezButtonVariant.Neutral -> colors.line
        SuaVezButtonVariant.Destructive -> colors.coral
        else -> null
    }

    val effectiveBg = if (isPressed && enabled && !loading) {
        when (variant) {
            SuaVezButtonVariant.Primary -> colors.teal.copy(alpha = 0.88f)
            SuaVezButtonVariant.Tonal -> colors.mist.copy(alpha = 0.8f)
            SuaVezButtonVariant.Neutral -> colors.bg
            else -> colors.mist.copy(alpha = 0.35f)
        }
    } else {
        baseBg
    }

    val height = if (small) SuaVezDimens.ButtonHeightSm else SuaVezDimens.ButtonHeight
    val shape = RoundedCornerShape(SuaVezDimens.RadiusPill)
    val widthMod = if (small) modifier else modifier.fillMaxWidth()

    val dashedColor = colors.dashedBorder
    val dashedModifier = if (variant == SuaVezButtonVariant.Dashed) {
        Modifier.drawBehind {
            val strokePx = 1.5.dp.toPx()
            drawRoundRect(
                color = dashedColor,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                style = Stroke(
                    width = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                ),
            )
        }
    } else {
        Modifier
    }

    val borderStroke = when (variant) {
        SuaVezButtonVariant.Secondary, SuaVezButtonVariant.Destructive -> BorderStroke(1.5.dp, borderColor!!)
        SuaVezButtonVariant.Neutral -> BorderStroke(1.dp, borderColor!!)
        else -> null
    }

    Box(
        modifier = widthMod
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(effectiveBg, shape)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .then(dashedModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (small) 16.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingContent != null) {
                    leadingContent()
                }
                Text(
                    text = text,
                    style = if (small) typo.buttonSm else typo.button,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (trailingIcon != null) {
                    SuaVezIcon(type = trailingIcon, tint = contentColor, size = 18.dp)
                }
            }
        }
    }
}

enum class SuaVezRoundButtonVariant {
    Default,
    Primary,
    Soft,
}

@Composable
fun SuaVezRoundButton(
    icon: SuaVezIconType,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SuaVezRoundButtonVariant = SuaVezRoundButtonVariant.Default,
    size: Dp = SuaVezDimens.RoundButtonSize,
) {
    val colors = SuaVezTheme.colors
    val bg = when (variant) {
        SuaVezRoundButtonVariant.Default -> colors.surface
        SuaVezRoundButtonVariant.Primary -> colors.teal
        SuaVezRoundButtonVariant.Soft -> colors.mist
    }
    val tint = when (variant) {
        SuaVezRoundButtonVariant.Default -> colors.ink
        SuaVezRoundButtonVariant.Primary -> if (colors.isDark) colors.bg else Color.White
        SuaVezRoundButtonVariant.Soft -> colors.tealDeep
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = SuaVezDimens.MinTouchTarget, minHeight = SuaVezDimens.MinTouchTarget)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg, CircleShape)
                .then(
                    if (variant == SuaVezRoundButtonVariant.Default) {
                        Modifier.border(1.dp, colors.line, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            SuaVezIcon(type = icon, tint = tint, size = 18.dp)
        }
    }
}

@Composable
fun SuaVezCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(SuaVezDimens.CardPadding),
    backgroundBrush: Brush? = null,
    leftAccentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SuaVezTheme.colors
    val shape = RoundedCornerShape(SuaVezDimens.RadiusCard)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (colors.isDark) 0.dp else 4.dp,
                shape = shape,
                ambientColor = colors.ink.copy(alpha = 0.08f),
                spotColor = colors.ink.copy(alpha = 0.08f),
            ),
        shape = shape,
        color = if (backgroundBrush == null) colors.surface else Color.Transparent,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier)
                .then(
                    if (leftAccentColor != null) {
                        Modifier.drawBehind {
                            drawRect(
                                color = leftAccentColor,
                                size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height),
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

enum class SuaVezChipVariant {
    Info,
    Admin,
    Vez,
    Ok,
    Eyebrow,
}

@Composable
fun SuaVezChip(
    text: String,
    variant: SuaVezChipVariant,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    val bg = when (variant) {
        SuaVezChipVariant.Info, SuaVezChipVariant.Admin -> colors.mist
        SuaVezChipVariant.Vez, SuaVezChipVariant.Eyebrow -> colors.solSoft
        SuaVezChipVariant.Ok -> colors.leaf.copy(alpha = if (colors.isDark) 0.22f else 0.15f)
    }
    val fg = when (variant) {
        SuaVezChipVariant.Info, SuaVezChipVariant.Admin -> colors.tealDeep
        SuaVezChipVariant.Vez, SuaVezChipVariant.Eyebrow -> colors.solInk
        SuaVezChipVariant.Ok -> colors.leaf
    }
    val border = if (variant == SuaVezChipVariant.Admin) {
        BorderStroke(1.dp, colors.teal.copy(alpha = 0.28f))
    } else {
        null
    }

    val height = if (compact) 22.dp else SuaVezDimens.ChipHeight
    val horizPad = if (compact) 9.dp else 11.dp
    val shape = RoundedCornerShape(SuaVezDimens.RadiusPill)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .padding(horizontal = horizPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (variant == SuaVezChipVariant.Eyebrow) text.uppercase() else text,
            style = typo.chip.copy(
                letterSpacing = if (variant == SuaVezChipVariant.Eyebrow) 0.6.sp else 0.sp,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun SuaVezAvatar(
    name: String,
    idForColor: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isMe: Boolean = false,
    isLeader: Boolean = false,
    whiteBorder: Boolean = false,
) {
    val colors = SuaVezTheme.colors
    val bg = colors.avatarColors[avatarIndexFor(idForColor)]
    val initials = initialsOf(name)
    val fontSize = when {
        size >= 52.dp -> 20.sp
        size <= 34.dp -> 12.sp
        size <= 37.dp -> 14.sp
        else -> 15.sp
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (isMe) {
                        Modifier
                            .border(2.dp, colors.sol, CircleShape)
                            .padding(3.dp)
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(bg, CircleShape)
                .then(
                    if (whiteBorder) {
                        Modifier.border(2.dp, colors.surface, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                color = colors.tealDeep,
            )
        }
        if (isLeader) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.sol, CircleShape)
                    .border(2.dp, colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SuaVezIcon(
                    type = SuaVezIconType.STAR,
                    tint = Color.White,
                    size = 10.dp,
                    filled = true,
                )
            }
        }
    }
}

@Composable
fun SuaVezInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val borderColor = when {
        error != null -> colors.coral
        isFocused -> colors.teal
        else -> colors.line
    }
    val borderWidth = if (isFocused || error != null) 1.5.dp else 1.dp
    val shape = RoundedCornerShape(SuaVezDimens.RadiusControl)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label.isNotEmpty()) {
            Text(text = label, style = typo.label, color = colors.ink)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SuaVezDimens.InputHeight)
                .clip(shape)
                .background(colors.surface, shape)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = typo.body.copy(fontSize = 16.sp),
                            color = colors.faint,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = typo.body.copy(fontSize = 16.sp, color = colors.ink),
                        singleLine = singleLine,
                        cursorBrush = SolidColor(colors.teal),
                        interactionSource = interactionSource,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = if (isPassword && !passwordVisible) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                    )
                }
                if (isPassword) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClick = { passwordVisible = !passwordVisible },
                            )
                            .semantics {
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        SuaVezIcon(
                            type = if (passwordVisible) SuaVezIconType.EYE_OFF else SuaVezIconType.EYE,
                            tint = colors.muted,
                            size = 18.dp,
                        )
                    }
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = typo.meta,
                color = colors.coralInk,
            )
        }
    }
}

@Composable
fun SuaVezProgressBar(
    primaryFraction: Float,
    modifier: Modifier = Modifier,
    secondaryFraction: Float = 0f,
    thick: Boolean = false,
    useAmberGradient: Boolean = false,
) {
    val colors = SuaVezTheme.colors
    val height = if (thick) 12.dp else 10.dp
    val clampedPrimary = primaryFraction.coerceIn(0f, 1f)
    val clampedSecondary = secondaryFraction.coerceIn(0f, 1f - clampedPrimary)

    val animPrimary by animateFloatAsState(targetValue = clampedPrimary, label = "barPrimary")
    val animSecondary by animateFloatAsState(targetValue = clampedSecondary, label = "barSecondary")

    val primaryBrush = if (useAmberGradient) {
        Brush.horizontalGradient(listOf(colors.sol, Color(0xFFFBC66B)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF0A6B61), colors.teal))
    }
    val secondaryColor = colors.sol.copy(alpha = if (colors.isDark) 0.35f else 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(SuaVezDimens.RadiusPill))
            .background(colors.mist),
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            if (animPrimary > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animPrimary)
                        .fillMaxHeight()
                        .background(primaryBrush),
                )
            }
            if (animSecondary > 0.001f) {
                val remainingSpace = (1f - animPrimary).coerceAtLeast(0.001f)
                val relativeSecondary = (animSecondary / remainingSpace).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(relativeSecondary)
                        .fillMaxHeight()
                        .background(secondaryColor),
                )
            }
        }
    }
}

@Composable
fun SuaVezNoteBanner(
    text: AnnotatedString,
    icon: SuaVezIconType = SuaVezIconType.STAR,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val shape = RoundedCornerShape(SuaVezDimens.RadiusControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.mist, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SuaVezIcon(type = icon, tint = colors.tealDeep, size = 18.dp)
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colors.tealDeep,
        )
    }
}

enum class OptionChipTone {
    Default,
    EffortMedia,
    EffortPesada,
}

@Composable
fun SuaVezOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: OptionChipTone = OptionChipTone.Default,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    val selectedBg = when (tone) {
        OptionChipTone.Default -> colors.mist
        OptionChipTone.EffortMedia -> colors.solSoft
        OptionChipTone.EffortPesada -> colors.coral.copy(alpha = 0.12f)
    }
    val selectedBorder = when (tone) {
        OptionChipTone.Default -> colors.teal
        OptionChipTone.EffortMedia -> colors.sol
        OptionChipTone.EffortPesada -> colors.coral
    }
    val selectedText = when (tone) {
        OptionChipTone.Default -> colors.tealDeep
        OptionChipTone.EffortMedia -> colors.solInk
        OptionChipTone.EffortPesada -> colors.coralInk
    }

    val bg by animateColorAsState(if (selected) selectedBg else colors.surface, label = "optBg")
    val border by animateColorAsState(if (selected) selectedBorder else colors.line, label = "optBorder")
    val textColor by animateColorAsState(if (selected) selectedText else colors.ink, label = "optFg")

    val shape = RoundedCornerShape(SuaVezDimens.RadiusPill)

    Box(
        modifier = modifier
            .height(SuaVezDimens.OptionHeight)
            .clip(shape)
            .background(bg, shape)
            .border(1.5.dp, border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typo.buttonSm,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun SuaVezPreferenceSegmented(
    selected: PreferenceLevel,
    onSelect: (PreferenceLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val shape = RoundedCornerShape(SuaVezDimens.RadiusPill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SuaVezDimens.SegmentedHeight)
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(4.dp),
    ) {
        PreferenceLevel.entries.forEach { level ->
            val isSelected = selected == level
            val selBg = when (level) {
                PreferenceLevel.ODEIO -> colors.coral.copy(alpha = 0.14f)
                PreferenceLevel.TANTO_FAZ -> colors.slate.copy(alpha = 0.22f)
                PreferenceLevel.GOSTO -> colors.leaf.copy(alpha = 0.14f)
            }
            val selBorder = when (level) {
                PreferenceLevel.ODEIO -> colors.coral
                PreferenceLevel.TANTO_FAZ -> colors.slate
                PreferenceLevel.GOSTO -> colors.leaf
            }
            val selText = when (level) {
                PreferenceLevel.ODEIO -> colors.coralInk
                PreferenceLevel.TANTO_FAZ -> colors.muted
                PreferenceLevel.GOSTO -> colors.leaf
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (isSelected) selBg else Color.Transparent, shape)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.5.dp, selBorder, shape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.RadioButton) { onSelect(level) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = level.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) selText else colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SuaVezDividerWithText(
    text: String = "ou",
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.line)
        Text(text = text, style = SuaVezTheme.typography.meta, color = colors.muted)
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.line)
    }
}
