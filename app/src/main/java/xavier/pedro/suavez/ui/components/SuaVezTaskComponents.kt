package xavier.pedro.suavez.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun effortLabel(level: TaskWeightLevel): String = when (level) {
    TaskWeightLevel.LIGHT -> "Leve"
    TaskWeightLevel.MEDIUM -> "Média"
    TaskWeightLevel.HEAVY -> "Pesada"
}

@Composable
fun SuaVezCheckbox(
    checked: Boolean,
    taskTitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val bg by animateColorAsState(
        targetValue = if (checked) colors.teal else colors.surface,
        label = "cbBg",
    )
    val border by animateColorAsState(
        targetValue = if (checked) colors.teal else colors.checkboxBorder,
        label = "cbBorder",
    )
    val shape = RoundedCornerShape(SuaVezDimens.RadiusCheckbox)

    Box(
        modifier = modifier
            .size(SuaVezDimens.MinTouchTarget)
            .semantics {
                contentDescription = "Concluir $taskTitle"
                stateDescription = if (checked) "Concluída" else "Pendente"
            }
            .clickable(role = Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SuaVezDimens.CheckboxSize)
                .clip(shape)
                .background(bg, shape)
                .border(2.dp, border, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                SuaVezIcon(
                    type = SuaVezIconType.CHECK,
                    tint = if (colors.isDark) colors.bg else Color.White,
                    size = 16.dp,
                    strokeWidthDp = 3.dp,
                )
            }
        }
    }
}

@Composable
fun EffortIconCircle(
    icon: SuaVezIconType,
    level: TaskWeightLevel,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val colors = SuaVezTheme.colors
    val bg = when (level) {
        TaskWeightLevel.LIGHT -> colors.mist
        TaskWeightLevel.MEDIUM -> colors.solSoft
        TaskWeightLevel.HEAVY -> colors.coral.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
    }
    val tint = when (level) {
        TaskWeightLevel.LIGHT -> colors.teal
        TaskWeightLevel.MEDIUM -> colors.solStroke
        TaskWeightLevel.HEAVY -> colors.coral
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SuaVezIcon(type = icon, tint = tint, size = iconSize)
    }
}

@Composable
fun EffortPill(
    points: Int,
    level: TaskWeightLevel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    val bg = when (level) {
        TaskWeightLevel.LIGHT -> colors.mist
        TaskWeightLevel.MEDIUM -> colors.solSoft
        TaskWeightLevel.HEAVY -> colors.coral.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
    }
    val fg = when (level) {
        TaskWeightLevel.LIGHT -> colors.tealDeep
        TaskWeightLevel.MEDIUM -> colors.solInk
        TaskWeightLevel.HEAVY -> colors.coralInk
    }

    val height = if (compact) SuaVezDimens.EffortPillSmHeight else SuaVezDimens.EffortPillHeight
    val shape = RoundedCornerShape(SuaVezDimens.RadiusPill)
    val unit = if (points == 1) "pt" else "pts"

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg, shape)
            .padding(horizontal = if (compact) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$points $unit",
            style = if (compact) typo.effortPillSm else typo.effortPill,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun EffortDot(
    level: TaskWeightLevel,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val dotColor = when (level) {
        TaskWeightLevel.LIGHT -> colors.teal
        TaskWeightLevel.MEDIUM -> colors.sol
        TaskWeightLevel.HEAVY -> colors.coral
    }
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(dotColor, CircleShape),
    )
}

@Composable
fun SuaVezTaskCard(
    task: UiAssignment,
    isSuaVez: Boolean,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val level = task.effortLevel
    val showSuaVez = isSuaVez && !task.isDone

    val suaVezBrush = if (showSuaVez) {
        Brush.horizontalGradient(
            0.0f to colors.sol.copy(alpha = if (colors.isDark) 0.16f else 0.11f),
            0.48f to colors.surface,
            1.0f to colors.surface,
        )
    } else {
        null
    }

    SuaVezCard(
        modifier = modifier.clickable(role = Role.Checkbox, onClick = onToggleDone),
        contentPadding = PaddingValues(start = 8.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        backgroundBrush = suaVezBrush,
        leftAccentColor = if (showSuaVez) colors.sol else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuaVezCheckbox(
                checked = task.isDone,
                taskTitle = task.titulo,
                onToggle = onToggleDone,
            )

            EffortIconCircle(
                icon = task.iconHint.toSuaVezIcon(),
                level = level,
                modifier = Modifier.alpha(if (task.isDone) 0.55f else 1f),
            )

            Spacer(modifier = Modifier.width(2.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showSuaVez) {
                    SuaVezChip(
                        text = "Sua vez",
                        variant = SuaVezChipVariant.Vez,
                        compact = true,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    text = task.titulo,
                    style = typo.itemTitle.copy(
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (task.isDone) colors.faint else colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.metaLabel(),
                    style = typo.meta,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            EffortPill(
                points = task.pontos,
                level = level,
                modifier = Modifier.alpha(if (task.isDone) 0.55f else 1f),
            )
        }
    }
}

@Composable
fun SuaVezOpenTaskCard(
    task: UiAssignment,
    onClaim: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val level = task.effortLevel

    SuaVezCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EffortIconCircle(
                icon = task.iconHint.toSuaVezIcon(),
                level = level,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = task.titulo,
                    style = typo.itemTitle,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.metaLabel(),
                    style = typo.meta,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                EffortPill(
                    points = task.pontos,
                    level = level,
                    compact = true,
                )
            }

            if (onEdit != null) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = SuaVezDimens.MinTouchTarget,
                            minHeight = SuaVezDimens.MinTouchTarget,
                        )
                        .clip(CircleShape)
                        .semantics { contentDescription = "Editar ${task.titulo}" }
                        .clickable(role = Role.Button, onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    SuaVezIcon(
                        type = SuaVezIconType.PENCIL,
                        tint = colors.muted,
                        size = 18.dp,
                    )
                }
            }

            SuaVezButton(
                text = if (task.justClaimedInSession) "Sua ✓" else "Pegar",
                onClick = onClaim,
                variant = SuaVezButtonVariant.Secondary,
                small = true,
                enabled = !task.justClaimedInSession,
                modifier = Modifier.width(84.dp),
            )
        }
    }
}

@Composable
fun SuaVezRankRow(
    name: String,
    idForColor: String,
    points: Int,
    maxPoints: Int,
    isMe: Boolean,
    isLeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val fraction = if (maxPoints > 0) (points.toFloat() / maxPoints.toFloat()).coerceIn(0.08f, 1f) else 0f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SuaVezAvatar(
            name = name,
            idForColor = idForColor,
            size = 40.dp,
            isMe = isMe,
            isLeader = isLeader,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = colors.ink,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                            ),
                        ) {
                            append(name)
                        }
                        if (isMe) {
                            withStyle(SpanStyle(color = colors.muted, fontWeight = FontWeight.Normal)) {
                                append(" · você")
                            }
                        }
                    },
                    style = typo.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$points pts",
                    style = typo.rankPoints,
                    color = if (isMe) colors.solInk else colors.teal,
                )
            }
            SuaVezProgressBar(
                primaryFraction = fraction,
                thick = true,
                useAmberGradient = isMe,
            )
        }
    }
}

@Composable
fun RitmoRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "ritmoRing",
    )

    Box(
        modifier = modifier.size(60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(60.dp)) {
            val strokePx = 7.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = colors.ringTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            drawArc(
                color = colors.teal,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        SuaVezIcon(
            type = SuaVezIconType.HOME,
            tint = colors.teal,
            size = 22.dp,
        )
    }
}
