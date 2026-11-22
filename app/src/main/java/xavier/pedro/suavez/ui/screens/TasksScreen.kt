package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.ui.components.ActivityFormSheet
import xavier.pedro.suavez.ui.components.EffortIconCircle
import xavier.pedro.suavez.ui.components.EffortPill
import xavier.pedro.suavez.ui.components.RitmoRing
import xavier.pedro.suavez.ui.components.SuaVezAvatar
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezCard
import xavier.pedro.suavez.ui.components.SuaVezChip
import xavier.pedro.suavez.ui.components.SuaVezChipVariant
import xavier.pedro.suavez.ui.components.SuaVezIcon
import xavier.pedro.suavez.ui.components.SuaVezIconType
import xavier.pedro.suavez.ui.components.SuaVezMark
import xavier.pedro.suavez.ui.components.SuaVezProgressBar
import xavier.pedro.suavez.ui.components.SuaVezRoundButton
import xavier.pedro.suavez.ui.components.SuaVezRoundButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezTaskCard
import xavier.pedro.suavez.ui.components.toSuaVezIcon
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.firstNameOf
import xavier.pedro.suavez.ui.theme.BodyFontFamily
import xavier.pedro.suavez.ui.theme.DisplayFontFamily
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun TasksScreen(
    state: SuaVezStateHolder,
    onOpenTasksClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    val uiState by state.uiState.collectAsState()
    val user = uiState.currentUser ?: return
    val house = uiState.currentHouse ?: return
    val myTasks = uiState.myAssignments
    val openTasks = uiState.openAssignments
    val donePts = uiState.myDonePoints
    val totalPts = uiState.myTotalPoints
    val pendingPts = uiState.myPendingPoints
    val pendingCount = uiState.myPendingCount

    val doneFraction = if (totalPts > 0) donePts.toFloat() / totalPts.toFloat() else 0f
    val pendingFraction = if (totalPts > 0) pendingPts.toFloat() / totalPts.toFloat() else 0f
    val firstPendingId = myTasks.firstOrNull { !it.isDone }?.id

    val houseDone = uiState.houseDoneCount
    val houseTotal = uiState.houseTotalCount
    val housePct = uiState.houseCompletionPercent
    val houseRemaining = (houseTotal - houseDone).coerceAtLeast(0)
    var showCreateActivitySheet by remember { mutableStateOf(false) }

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
        // 1. Saudação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Bom dia, ${firstNameOf(user.nome)}",
                    style = typo.pageTitle,
                    color = colors.ink,
                )
                Text(
                    text = "Semana ${house.semanaNumero} · ${uiState.members.size} moradores",
                    style = typo.meta,
                    color = colors.muted,
                )
            }
            SuaVezRoundButton(
                icon = SuaVezIconType.PLUS,
                contentDescription = "Criar atividade",
                onClick = { showCreateActivitySheet = true },
                variant = SuaVezRoundButtonVariant.Primary,
            )
        }

        // 2. Card da cota semanal
        SuaVezCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SUA COTA DA SEMANA",
                        style = typo.overline,
                        color = colors.muted,
                    )
                    if (pendingPts > 0) {
                        SuaVezChip(
                            text = "$pendingPts pts pendentes",
                            variant = SuaVezChipVariant.Vez,
                        )
                    } else {
                        SuaVezChip(
                            text = "Cota concluída",
                            variant = SuaVezChipVariant.Ok,
                        )
                    }
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 40.sp,
                                color = colors.teal,
                            ),
                        ) {
                            append("$donePts")
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = colors.muted,
                            ),
                        ) {
                            append("  de $totalPts pts feitos")
                        }
                    },
                )

                SuaVezProgressBar(
                    primaryFraction = doneFraction,
                    secondaryFraction = pendingFraction,
                    thick = true,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(color = colors.teal, label = "Feitos")
                    LegendDot(
                        color = colors.sol.copy(alpha = 0.65f),
                        label = "Pendentes",
                    )
                }
            }
        }

        // 3. Suas tarefas da semana (lista única, sem filtros)
        Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Suas tarefas da semana",
                    style = typo.title,
                    color = colors.ink,
                )
                SuaVezChip(
                    text = if (pendingCount == 1) "1 pendente" else "$pendingCount pendentes",
                    variant = SuaVezChipVariant.Info,
                )
            }

            if (myTasks.isEmpty()) {
                SuaVezCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SuaVezMark(size = 44.dp)
                        Text(
                            text = "Tudo em dia por aqui",
                            style = typo.itemTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = "Você ainda não pegou nenhuma tarefa nesta semana.",
                            style = typo.meta,
                            color = colors.muted,
                        )
                        SuaVezButton(
                            text = "Ver tarefas abertas",
                            onClick = onOpenTasksClick,
                            variant = SuaVezButtonVariant.Secondary,
                            small = true,
                        )
                    }
                }
            } else {
                myTasks.forEach { task ->
                    SuaVezTaskCard(
                        task = task,
                        isSuaVez = task.id == firstPendingId,
                        onToggleDone = { state.toggleTaskDone(task.id) },
                    )
                }
            }

            SuaVezButton(
                text = "Criar atividade",
                onClick = { showCreateActivitySheet = true },
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

        // 4. Card Ritmo da casa + prévia de Tarefas abertas
        SuaVezCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ritmo da casa",
                            style = typo.title,
                            color = colors.ink,
                        )
                        Text(
                            text = "Semana coletiva · ${house.nome}",
                            style = typo.meta,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "$housePct%",
                        style = typo.pct,
                        color = colors.teal,
                    )
                }

                // Faixa com anel de progresso
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SuaVezDimens.RadiusFaixa))
                        .background(colors.bg)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RitmoRing(
                        progress = if (houseTotal > 0) houseDone.toFloat() / houseTotal.toFloat() else 0f,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "$houseDone de $houseTotal tarefas feitas",
                            style = typo.itemTitle.copy(fontSize = 15.sp),
                            color = colors.ink,
                        )
                        Text(
                            text = if (houseRemaining > 0) {
                                "Faltam $houseRemaining para fechar 100% até domingo."
                            } else {
                                "Casa 100% em dia nesta semana!"
                            },
                            style = typo.meta,
                            color = colors.muted,
                        )
                    }
                }

                // Avatares sobrepostos + status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        uiState.members.take(5).forEachIndexed { index, member ->
                            SuaVezAvatar(
                                name = member.nome,
                                idForColor = member.uid,
                                size = 32.dp,
                                whiteBorder = true,
                                modifier = Modifier.offset(x = (-8 * index).dp),
                            )
                        }
                    }
                    Text(
                        text = "Todo mundo no ritmo 👏",
                        style = typo.meta,
                        color = colors.muted,
                    )
                }

                // Divisor + Prévia Tarefas abertas
                Column {
                    HorizontalDivider(color = colors.line)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Tarefas abertas",
                                style = typo.itemTitle,
                                color = colors.ink,
                            )
                            Text(
                                text = "Ninguém pegou ainda",
                                style = typo.meta,
                                color = colors.muted,
                            )
                        }
                        SuaVezChip(
                            text = "${openTasks.size}",
                            variant = SuaVezChipVariant.Vez,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (openTasks.isEmpty()) {
                        Text(
                            text = "Todas as tarefas desta semana já foram escolhidas.",
                            style = typo.meta,
                            color = colors.muted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        val preview = openTasks.take(2)
                        preview.forEachIndexed { index, openTask ->
                            if (index > 0) {
                                HorizontalDivider(color = colors.line)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                EffortIconCircle(
                                    icon = openTask.iconHint.toSuaVezIcon(),
                                    level = openTask.effortLevel,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                )
                                Text(
                                    text = openTask.titulo,
                                    style = typo.itemTitle.copy(fontSize = 15.sp),
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                EffortPill(
                                    points = openTask.pontos,
                                    level = openTask.effortLevel,
                                )
                            }
                        }
                        val extra = openTasks.size - preview.size
                        if (extra > 0) {
                            Text(
                                text = if (extra == 1) "+1 tarefa" else "+$extra tarefas",
                                style = typo.meta,
                                color = colors.muted,
                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    SuaVezButton(
                        text = "Ver tarefas abertas",
                        onClick = onOpenTasksClick,
                        variant = SuaVezButtonVariant.Primary,
                        trailingIcon = SuaVezIconType.ARROW_RIGHT,
                    )
                }
            }
        }
    }

    if (showCreateActivitySheet) {
        ActivityFormSheet(
            isAdmin = uiState.isAdmin,
            onDismiss = { showCreateActivitySheet = false },
            onCreateWeeklyTask = { title, category, points, deadline ->
                showCreateActivitySheet = false
                state.addWeeklyTask(title, category, points, deadline)
            },
            onSaveRecurringTask = { title, category, points, frequency, openInCurrentWeek, deadline ->
                showCreateActivitySheet = false
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
}

@Composable
private fun LegendDot(
    color: androidx.compose.ui.graphics.Color,
    label: String,
) {
    val colors = SuaVezTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
        )
    }
}
