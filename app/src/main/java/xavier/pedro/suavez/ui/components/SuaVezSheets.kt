package xavier.pedro.suavez.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiFixedTask
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuaVezBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = SuaVezTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = SuaVezDimens.RadiusSheetTop,
            topEnd = SuaVezDimens.RadiusSheetTop,
        ),
        containerColor = colors.surface,
        scrimColor = colors.ink.copy(alpha = 0.42f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.line),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            content()
        }
    }
}

enum class ActivityRecurrenceType(val label: String) {
    UNICA("Única (esta semana)"),
    RECORRENTE("Recorrente"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityFormSheet(
    isAdmin: Boolean,
    editingTask: UiFixedTask? = null,
    onDismiss: () -> Unit,
    onCreateWeeklyTask: (
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
    ) -> Unit = { _, _, _, _ -> },
    onSaveRecurringTask: (
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency,
        openInCurrentWeek: Boolean,
        deadline: TaskDeadline?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteRecurringTask: ((UiFixedTask) -> Unit)? = null,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val isEditing = editingTask != null

    var title by remember(editingTask) { mutableStateOf(editingTask?.titulo ?: "") }
    var category by remember(editingTask) { mutableStateOf(editingTask?.categoria) }
    var effortPoints by remember(editingTask) { mutableStateOf(editingTask?.effortLevel?.points) }
    var recurrenceType by remember(editingTask, isAdmin) {
        mutableStateOf(
            when {
                isEditing -> ActivityRecurrenceType.RECORRENTE
                !isAdmin -> ActivityRecurrenceType.UNICA
                else -> null
            },
        )
    }
    var deadline by remember(editingTask) { mutableStateOf<TaskDeadline?>(null) }
    var frequency by remember(editingTask) { mutableStateOf(editingTask?.frequencia) }
    var openInCurrentWeek by remember(editingTask) { mutableStateOf<Boolean?>(null) }

    var titleError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var effortError by remember { mutableStateOf<String?>(null) }
    var recurrenceError by remember { mutableStateOf<String?>(null) }
    var deadlineError by remember { mutableStateOf<String?>(null) }
    var frequencyError by remember { mutableStateOf<String?>(null) }
    var openNowError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val showTypeSelector = !isEditing && isAdmin
    val showFrequencySection = recurrenceType == ActivityRecurrenceType.RECORRENTE
    val showOpenNowSection = !isEditing && recurrenceType == ActivityRecurrenceType.RECORRENTE
    val showDeadlineSection = recurrenceType == ActivityRecurrenceType.UNICA ||
        (showOpenNowSection && openInCurrentWeek == true)

    SuaVezBottomSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isEditing) "Editar tarefa recorrente" else "Criar atividade",
                style = typo.title,
                color = colors.ink,
            )
            if (isEditing) {
                SuaVezChip(text = "Recorrente", variant = SuaVezChipVariant.Info)
            }
        }

        SuaVezInput(
            value = title,
            onValueChange = {
                title = it
                if (titleError != null) titleError = null
            },
            label = "Nome",
            placeholder = if (showFrequencySection) "Ex.: Lavar banheiro" else "Ex.: Limpar a geladeira",
            error = titleError,
            required = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        FormFieldSection(
            label = "Categoria",
            required = true,
            error = categoryError,
        ) {
            TaskCategory.entries.forEach { cat ->
                SuaVezOptionChip(
                    text = cat.label,
                    selected = category == cat,
                    isError = categoryError != null,
                    onClick = {
                        category = cat
                        categoryError = null
                    },
                )
            }
        }

        FormFieldSection(
            label = "Esforço",
            required = true,
            error = effortError,
        ) {
            TaskWeightLevel.entries.forEach { level ->
                SuaVezOptionChip(
                    text = level.label,
                    selected = effortPoints == level.points,
                    isError = effortError != null,
                    onClick = {
                        effortPoints = level.points
                        effortError = null
                    },
                    tone = when (level) {
                        TaskWeightLevel.LIGHT -> OptionChipTone.Default
                        TaskWeightLevel.MEDIUM -> OptionChipTone.EffortMedia
                        TaskWeightLevel.HEAVY -> OptionChipTone.EffortPesada
                    },
                )
            }
        }

        if (showTypeSelector) {
            FormFieldSection(
                label = "Recorrência",
                required = true,
                error = recurrenceError,
            ) {
                ActivityRecurrenceType.entries.forEach { type ->
                    SuaVezOptionChip(
                        text = type.label,
                        selected = recurrenceType == type,
                        isError = recurrenceError != null,
                        onClick = {
                            recurrenceType = type
                            recurrenceError = null
                            deadlineError = null
                            frequencyError = null
                            openNowError = null
                        },
                    )
                }
            }
        }

        if (showFrequencySection) {
            FormFieldSection(
                label = "Frequência",
                required = true,
                error = frequencyError,
            ) {
                TaskFrequency.entries.forEach { freq ->
                    SuaVezOptionChip(
                        text = freq.label,
                        selected = frequency == freq,
                        isError = frequencyError != null,
                        onClick = {
                            frequency = freq
                            frequencyError = null
                        },
                    )
                }
            }
        }

        if (showOpenNowSection) {
            FormFieldSection(
                label = "Abrir atividade na semana atual?",
                required = true,
                error = openNowError,
            ) {
                SuaVezOptionChip(
                    text = "Já abrir agora",
                    selected = openInCurrentWeek == true,
                    isError = openNowError != null,
                    onClick = {
                        openInCurrentWeek = true
                        openNowError = null
                        deadlineError = null
                    },
                )
                SuaVezOptionChip(
                    text = "Deixar para a próxima semana",
                    selected = openInCurrentWeek == false,
                    isError = openNowError != null,
                    onClick = {
                        openInCurrentWeek = false
                        openNowError = null
                        deadline = null
                        deadlineError = null
                    },
                )
            }
        }

        if (showDeadlineSection) {
            FormFieldSection(
                label = "Prazo",
                required = true,
                error = deadlineError,
            ) {
                TaskDeadline.entries.forEach { d ->
                    SuaVezOptionChip(
                        text = d.label,
                        selected = deadline == d,
                        isError = deadlineError != null,
                        onClick = {
                            deadline = d
                            deadlineError = null
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SuaVezButton(
                text = if (isEditing) "Salvar alterações" else "Criar atividade",
                onClick = {
                    val cleanTitle = title.trim()
                    var valid = true

                    if (cleanTitle.isEmpty()) {
                        titleError = "Digite o nome da atividade"
                        valid = false
                    }
                    if (category == null) {
                        categoryError = "Selecione uma categoria"
                        valid = false
                    }
                    if (effortPoints == null) {
                        effortError = "Selecione o nível de esforço"
                        valid = false
                    }
                    if (showTypeSelector && recurrenceType == null) {
                        recurrenceError = "Selecione o tipo da atividade"
                        valid = false
                    }
                    if (showFrequencySection && frequency == null) {
                        frequencyError = "Selecione a frequência"
                        valid = false
                    }
                    if (showOpenNowSection && openInCurrentWeek == null) {
                        openNowError = "Escolha se deseja abrir na semana atual"
                        valid = false
                    }
                    if (showDeadlineSection && deadline == null) {
                        deadlineError = "Selecione um prazo"
                        valid = false
                    }

                    if (!valid) return@SuaVezButton

                    val chosenCategory = category!!
                    val chosenPoints = effortPoints!!
                    when (recurrenceType!!) {
                        ActivityRecurrenceType.UNICA -> {
                            onCreateWeeklyTask(cleanTitle, chosenCategory, chosenPoints, deadline!!)
                        }
                        ActivityRecurrenceType.RECORRENTE -> {
                            onSaveRecurringTask(
                                cleanTitle,
                                chosenCategory,
                                chosenPoints,
                                frequency!!,
                                openInCurrentWeek ?: false,
                                deadline,
                            )
                        }
                    }
                },
                variant = SuaVezButtonVariant.Primary,
            )
            if (isEditing && onDeleteRecurringTask != null) {
                SuaVezButton(
                    text = "Excluir tarefa recorrente",
                    onClick = { confirmDelete = true },
                    variant = SuaVezButtonVariant.Destructive,
                )
            } else {
                SuaVezButton(
                    text = "Cancelar",
                    onClick = onDismiss,
                    variant = SuaVezButtonVariant.Ghost,
                )
            }
        }
    }

    if (confirmDelete && editingTask != null && onDeleteRecurringTask != null) {
        ConfirmActionDialog(
            title = "Excluir tarefa recorrente?",
            message = "A tarefa \"${editingTask.titulo}\" deixará de entrar nos próximos ciclos e no cálculo da cota.",
            confirmText = "Excluir",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteRecurringTask(editingTask)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormFieldSection(
    label: String,
    required: Boolean,
    error: String?,
    content: @Composable () -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = buildAnnotatedString {
                append(label)
                if (required) {
                    withStyle(SpanStyle(color = colors.coral)) {
                        append(" *")
                    }
                }
            },
            style = typo.label,
            color = if (error != null) colors.coralInk else colors.ink,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
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
fun CreateAccountSheet(
    onDismiss: () -> Unit,
    onCreateAccount: (name: String, email: String) -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    SuaVezBottomSheet(onDismiss = onDismiss) {
        Text(text = "Criar conta", style = typo.title, color = colors.ink)

        SuaVezInput(
            value = name,
            onValueChange = {
                name = it
                nameError = null
            },
            label = "Nome",
            placeholder = "Como os moradores te chamam",
            error = nameError,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )

        SuaVezInput(
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            label = "E-mail",
            placeholder = "voce@email.com",
            error = emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )

        SuaVezInput(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = "Senha",
            placeholder = "Mínimo 6 caracteres",
            isPassword = true,
            error = passwordError,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SuaVezButton(
                text = "Criar conta",
                onClick = {
                    val cleanName = name.trim()
                    val cleanEmail = email.trim()
                    var valid = true
                    if (cleanName.length < 2) {
                        nameError = "Informe seu nome"
                        valid = false
                    }
                    if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                        emailError = "Informe um e-mail válido"
                        valid = false
                    }
                    if (password.length < 6) {
                        passwordError = "A senha precisa de pelo menos 6 caracteres"
                        valid = false
                    }
                    if (valid) onCreateAccount(cleanName, cleanEmail)
                },
                variant = SuaVezButtonVariant.Primary,
            )
            SuaVezButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
            )
        }
    }
}

@Composable
fun CreateHouseSheet(
    onDismiss: () -> Unit,
    onCreateHouse: (houseName: String) -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    var houseName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SuaVezBottomSheet(onDismiss = onDismiss) {
        Text(text = "Criar casa", style = typo.title, color = colors.ink)
        SuaVezInput(
            value = houseName,
            onValueChange = {
                houseName = it
                error = null
            },
            label = "Nome da casa",
            placeholder = "Ex.: Casa dos amigos",
            error = error,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SuaVezButton(
                text = "Criar casa",
                onClick = {
                    if (houseName.trim().length < 2) {
                        error = "Digite um nome para a casa"
                    } else {
                        onCreateHouse(houseName)
                    }
                },
                variant = SuaVezButtonVariant.Primary,
            )
            SuaVezButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
            )
        }
    }
}

@Composable
fun MemberActionsSheet(
    member: UiMember,
    onDismiss: () -> Unit,
    onMakeAdmin: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    var pendingConfirm by remember { mutableStateOf<String?>(null) }

    SuaVezBottomSheet(onDismiss = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SuaVezAvatar(name = member.nome, idForColor = member.uid, size = 40.dp)
            Column {
                Text(text = member.nome, style = typo.title, color = colors.ink)
                Text(text = "Gerenciar morador", style = typo.meta, color = colors.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SuaVezButton(
                text = "Tornar admin",
                onClick = { pendingConfirm = "admin" },
                variant = SuaVezButtonVariant.Secondary,
            )
            SuaVezButton(
                text = "Remover da casa",
                onClick = { pendingConfirm = "remove" },
                variant = SuaVezButtonVariant.Destructive,
            )
            SuaVezButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
            )
        }
    }

    when (pendingConfirm) {
        "admin" -> ConfirmActionDialog(
            title = "Transferir administração?",
            message = "${member.nome} passará a ser Admin da casa e você passará a ser morador comum.",
            confirmText = "Tornar admin",
            destructive = false,
            onConfirm = {
                pendingConfirm = null
                onMakeAdmin()
            },
            onDismiss = { pendingConfirm = null },
        )
        "remove" -> ConfirmActionDialog(
            title = "Remover ${member.nome}?",
            message = "As tarefas pendentes desse morador voltarão para as tarefas abertas.",
            confirmText = "Remover",
            destructive = true,
            onConfirm = {
                pendingConfirm = null
                onRemove()
            },
            onDismiss = { pendingConfirm = null },
        )
    }
}

@Composable
fun RenameHouseDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    var value by remember(currentName) { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(SuaVezDimens.RadiusCard),
        title = {
            Text(text = "Renomear casa", style = typo.title, color = colors.ink)
        },
        text = {
            SuaVezInput(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                label = "Novo nome",
                placeholder = "Ex.: Casa dos amigos",
                error = error,
            )
        },
        confirmButton = {
            SuaVezButton(
                text = "Salvar",
                onClick = {
                    if (value.trim().length < 2) {
                        error = "Digite um nome válido"
                    } else {
                        onRename(value)
                    }
                },
                variant = SuaVezButtonVariant.Primary,
                small = true,
            )
        },
        dismissButton = {
            SuaVezButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
                small = true,
            )
        },
    )
}

@Composable
fun TransferAdminAndLeaveDialog(
    otherMembers: List<UiMember>,
    onDismiss: () -> Unit,
    onTransferAndLeave: (String) -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    var selectedUid by remember(otherMembers) { mutableStateOf(otherMembers.firstOrNull()?.uid) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(SuaVezDimens.RadiusCard),
        title = {
            Text(text = "Transferir administração", style = typo.title, color = colors.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (otherMembers.isEmpty()) {
                        "Você é o único morador da casa. Para encerrá-la, use \"Destruir casa\"."
                    } else {
                        "Escolha quem será o novo admin antes de sair da casa:"
                    },
                    style = typo.body,
                    color = colors.muted,
                )
                otherMembers.forEach { member ->
                    val selected = selectedUid == member.uid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.mist else colors.bg)
                            .clickable { selectedUid = member.uid }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SuaVezAvatar(name = member.nome, idForColor = member.uid, size = 32.dp)
                        Text(
                            text = member.nome,
                            style = typo.itemTitle,
                            color = if (selected) colors.tealDeep else colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            SuaVezIcon(type = SuaVezIconType.CHECK, tint = colors.teal, size = 18.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (otherMembers.isNotEmpty()) {
                SuaVezButton(
                    text = "Transferir e sair",
                    onClick = { selectedUid?.let(onTransferAndLeave) },
                    variant = SuaVezButtonVariant.Primary,
                    small = true,
                )
            }
        },
        dismissButton = {
            SuaVezButton(
                text = "Fechar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
                small = true,
            )
        },
    )
}

@Composable
fun DestroyHouseDialog(
    houseName: String,
    onDismiss: () -> Unit,
    onConfirmDestroy: () -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(houseName.trim(), ignoreCase = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(SuaVezDimens.RadiusCard),
        title = {
            Text(text = "Destruir casa?", style = typo.title, color = colors.coralInk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Essa ação apaga todas as tarefas, preferências e histórico da casa. Digite \"$houseName\" para confirmar.",
                    style = typo.body,
                    color = colors.muted,
                )
                SuaVezInput(
                    value = typed,
                    onValueChange = { typed = it },
                    label = "Nome da casa",
                    placeholder = houseName,
                )
            }
        },
        confirmButton = {
            SuaVezButton(
                text = "Destruir",
                onClick = onConfirmDestroy,
                enabled = matches,
                variant = SuaVezButtonVariant.Destructive,
                small = true,
            )
        },
        dismissButton = {
            SuaVezButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
                small = true,
            )
        },
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(SuaVezDimens.RadiusCard),
        title = {
            Text(
                text = title,
                style = typo.title,
                color = if (destructive) colors.coralInk else colors.ink,
            )
        },
        text = {
            Text(text = message, style = typo.body, color = colors.muted)
        },
        confirmButton = {
            SuaVezButton(
                text = confirmText,
                onClick = onConfirm,
                variant = if (destructive) SuaVezButtonVariant.Destructive else SuaVezButtonVariant.Primary,
                small = true,
            )
        },
        dismissButton = {
            SuaVezButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = SuaVezButtonVariant.Ghost,
                small = true,
            )
        },
    )
}
