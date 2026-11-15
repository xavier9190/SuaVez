package xavier.pedro.suavez.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.ui.components.ActivityFormSheet
import xavier.pedro.suavez.ui.components.EffortDot
import xavier.pedro.suavez.ui.components.EffortIconCircle
import xavier.pedro.suavez.ui.components.EffortPill
import xavier.pedro.suavez.ui.components.MemberActionsSheet
import xavier.pedro.suavez.ui.components.SuaVezAvatar
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezCard
import xavier.pedro.suavez.ui.components.SuaVezChip
import xavier.pedro.suavez.ui.components.SuaVezChipVariant
import xavier.pedro.suavez.ui.components.SuaVezIcon
import xavier.pedro.suavez.ui.components.SuaVezIconType
import xavier.pedro.suavez.ui.components.SuaVezNoteBanner
import xavier.pedro.suavez.ui.components.SuaVezRankRow
import xavier.pedro.suavez.ui.components.SuaVezRoundButton
import xavier.pedro.suavez.ui.components.SuaVezRoundButtonVariant
import xavier.pedro.suavez.ui.components.effortLabel
import xavier.pedro.suavez.ui.components.toSuaVezIcon
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.UiFixedTask
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun HouseScreen(
    state: SuaVezStateHolder,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val context = LocalContext.current

    val house = state.currentHouse ?: return
    val currentUser = state.currentUser ?: return
    val isAdmin = state.isAdmin
    val members = state.members
    val ranked = state.rankedMembers()
    val maxScore = ranked.maxOfOrNull { state.memberWeeklyPoints(it.uid) } ?: 0
    val nextInTurnUid = state.nextInTurnUid()
    val fixedTasks = state.fixedTasks
    val weeklyQuota = state.weeklyQuotaPerMember

    var copied by remember { mutableStateOf(false) }
    var managingMember by remember { mutableStateOf<UiMember?>(null) }
    var showNewFixedSheet by remember { mutableStateOf(false) }
    var editingFixedTask by remember { mutableStateOf<UiFixedTask?>(null) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
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
        // 1. Cabeçalho
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SuaVezChip(
                    text = "Configurações da casa",
                    variant = SuaVezChipVariant.Eyebrow,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = house.nome,
                    style = typo.pageTitle,
                    color = colors.ink,
                )
                Text(
                    text = "#${house.codigoConvite} · ${members.size} ${if (members.size == 1) "morador" else "moradores"}",
                    style = typo.meta,
                    color = colors.muted,
                )
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(colors.mist, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SuaVezIcon(type = SuaVezIconType.HOME, tint = colors.teal, size = 24.dp)
            }
        }

        // 2. Card de convite (só admin)
        if (isAdmin) {
            SuaVezCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "CÓDIGO DE CONVITE",
                                style = typo.overline,
                                color = colors.muted,
                            )
                            Text(
                                text = house.codigoConvite,
                                style = typo.code,
                                color = colors.ink,
                            )
                        }
                        SuaVezChip(text = "Ativo", variant = SuaVezChipVariant.Ok)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SuaVezButton(
                            text = if (copied) "Copiado ✓" else "Copiar código",
                            onClick = {
                                copyToClipboard(context, house.codigoConvite)
                                copied = true
                            },
                            variant = SuaVezButtonVariant.Tonal,
                            modifier = Modifier.weight(1f),
                            leadingContent = {
                                SuaVezIcon(type = SuaVezIconType.COPY, tint = colors.tealDeep, size = 18.dp)
                            },
                        )
                        SuaVezButton(
                            text = "Compartilhar",
                            onClick = { shareHouseInvite(context, house.nome, house.codigoConvite) },
                            variant = SuaVezButtonVariant.Primary,
                            modifier = Modifier.weight(1f),
                            leadingContent = {
                                SuaVezIcon(
                                    type = SuaVezIconType.SHARE,
                                    tint = if (colors.isDark) colors.bg else androidx.compose.ui.graphics.Color.White,
                                    size = 18.dp,
                                )
                            },
                        )
                    }
                }
            }
        }

        // 3. Pontuação da semana
        SuaVezCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Pontuação da semana", style = typo.title, color = colors.ink)
                SuaVezChip(text = "Ciclo atual", variant = SuaVezChipVariant.Info)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ranked.forEachIndexed { index, member ->
                    val pts = state.memberWeeklyPoints(member.uid)
                    SuaVezRankRow(
                        name = member.nome,
                        idForColor = member.uid,
                        points = pts,
                        maxPoints = maxScore.coerceAtLeast(1),
                        isMe = member.uid == currentUser.uid,
                        isLeader = index == 0 && pts > 0,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.line)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isBalanced()) {
                    SuaVezChip(text = "Casa equilibrada", variant = SuaVezChipVariant.Ok)
                } else {
                    SuaVezChip(
                        text = "Diferença de ${state.balanceGapPercent()}%",
                        variant = SuaVezChipVariant.Vez,
                    )
                }
                Text(
                    text = "${state.houseDoneCount} de ${state.houseTotalCount} tarefas feitas",
                    style = typo.meta,
                    color = colors.muted,
                )
            }
        }

        // 4. Moradores
        Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Moradores", style = typo.title, color = colors.ink)
                SuaVezChip(
                    text = if (members.size == 1) "1 integrante" else "${members.size} integrantes",
                    variant = SuaVezChipVariant.Info,
                )
            }

            members.forEach { member ->
                val openCount = state.memberPendingCount(member.uid)
                val openPts = state.memberPendingPoints(member.uid)
                val openMeta = if (openCount == 0) {
                    "0 abertas"
                } else {
                    val unit = if (openCount == 1) "aberta" else "abertas"
                    "$openCount $unit · $openPts pts"
                }

                SuaVezCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SuaVezAvatar(
                            name = member.nome,
                            idForColor = member.uid,
                            size = 40.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = member.nome,
                                    style = typo.itemTitle,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (member.role == HouseRole.ADMIN) {
                                    SuaVezChip(text = "Admin", variant = SuaVezChipVariant.Admin)
                                } else if (member.uid == nextInTurnUid) {
                                    SuaVezChip(text = "Próximo da vez", variant = SuaVezChipVariant.Vez)
                                }
                            }
                            Text(
                                text = openMeta,
                                style = typo.meta,
                                color = colors.muted,
                            )
                        }

                        if (isAdmin && member.role != HouseRole.ADMIN) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(
                                        minWidth = SuaVezDimens.MinTouchTarget,
                                        minHeight = SuaVezDimens.MinTouchTarget,
                                    )
                                    .clip(CircleShape)
                                    .semantics { contentDescription = "Gerenciar ${member.nome}" }
                                    .clickable(role = Role.Button) { managingMember = member },
                                contentAlignment = Alignment.Center,
                            ) {
                                SuaVezIcon(
                                    type = SuaVezIconType.MORE_VERT,
                                    tint = colors.muted,
                                    size = 20.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Tarefas fixas da casa
        Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tarefas fixas da casa",
                        style = typo.title,
                        color = colors.ink,
                    )
                    Text(
                        text = "Repetem toda semana · definem a cota de cada um",
                        style = typo.meta,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (isAdmin) {
                    SuaVezRoundButton(
                        icon = SuaVezIconType.PLUS,
                        contentDescription = "Criar atividade",
                        onClick = { showNewFixedSheet = true },
                        variant = SuaVezRoundButtonVariant.Primary,
                    )
                }
            }

            SuaVezNoteBanner(
                text = buildAnnotatedString {
                    append("Cota semanal por morador: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("$weeklyQuota pts")
                    }
                },
                icon = SuaVezIconType.STAR,
            )

            fixedTasks.forEach { task ->
                val level = task.effortLevel
                SuaVezCard(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 14.dp,
                        end = if (isAdmin) 8.dp else 16.dp,
                        bottom = 14.dp,
                    ),
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                EffortDot(level = level)
                                Text(
                                    text = "${effortLabel(level)} · ${task.frequencia.label}",
                                    style = typo.meta,
                                    color = colors.muted,
                                    maxLines = 1,
                                )
                            }
                        }

                        EffortPill(points = task.pontos, level = level)

                        if (isAdmin) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(
                                        minWidth = SuaVezDimens.MinTouchTarget,
                                        minHeight = SuaVezDimens.MinTouchTarget,
                                    )
                                    .clip(CircleShape)
                                    .semantics { contentDescription = "Editar ${task.titulo}" }
                                    .clickable(role = Role.Button) { editingFixedTask = task },
                                contentAlignment = Alignment.Center,
                            ) {
                                SuaVezIcon(
                                    type = SuaVezIconType.PENCIL,
                                    tint = colors.muted,
                                    size = 18.dp,
                                )
                            }
                        }
                    }
                }
            }

            if (isAdmin) {
                SuaVezButton(
                    text = "Criar atividade",
                    onClick = { showNewFixedSheet = true },
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
        }
    }

    val selectedMember = managingMember
    if (selectedMember != null) {
        MemberActionsSheet(
            member = selectedMember,
            onDismiss = { managingMember = null },
            onMakeAdmin = {
                managingMember = null
                state.promoteToAdmin(selectedMember.uid)
            },
            onRemove = {
                managingMember = null
                state.removeMember(selectedMember.uid)
            },
        )
    }

    if (showNewFixedSheet) {
        ActivityFormSheet(
            isAdmin = isAdmin,
            onDismiss = { showNewFixedSheet = false },
            onCreateWeeklyTask = { title, cat, pts, deadline ->
                showNewFixedSheet = false
                state.addWeeklyTask(title, cat, pts, deadline)
            },
            onSaveRecurringTask = { title, cat, pts, freq, openInCurrentWeek, deadline ->
                showNewFixedSheet = false
                state.saveFixedTask(null, title, cat, pts, freq, openInCurrentWeek, deadline)
            },
        )
    }

    val taskToEdit = editingFixedTask
    if (taskToEdit != null) {
        ActivityFormSheet(
            isAdmin = isAdmin,
            editingTask = taskToEdit,
            onDismiss = { editingFixedTask = null },
            onSaveRecurringTask = { title, cat, pts, freq, _, _ ->
                editingFixedTask = null
                state.saveFixedTask(taskToEdit.id, title, cat, pts, freq)
            },
            onDeleteRecurringTask = { toDelete ->
                editingFixedTask = null
                state.deleteFixedTask(toDelete.id)
            },
        )
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Código de convite Sua Vez", code))
}

private fun shareHouseInvite(context: Context, houseName: String, code: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Entre na casa \"$houseName\" no Sua Vez usando o código: $code",
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar convite"))
}
