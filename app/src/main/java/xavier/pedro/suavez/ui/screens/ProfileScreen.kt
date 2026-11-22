package xavier.pedro.suavez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.ui.components.ConfirmActionDialog
import xavier.pedro.suavez.ui.components.DestroyHouseDialog
import xavier.pedro.suavez.ui.components.RenameHouseDialog
import xavier.pedro.suavez.ui.components.SuaVezAvatar
import xavier.pedro.suavez.ui.components.SuaVezButton
import xavier.pedro.suavez.ui.components.SuaVezButtonVariant
import xavier.pedro.suavez.ui.components.SuaVezCard
import xavier.pedro.suavez.ui.components.SuaVezChip
import xavier.pedro.suavez.ui.components.SuaVezChipVariant
import xavier.pedro.suavez.ui.components.TransferAdminAndLeaveDialog
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun ProfileScreen(
    state: SuaVezStateHolder,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    val uiState by state.uiState.collectAsState()
    val user = uiState.currentUser ?: return
    val house = uiState.currentHouse
    val isAdmin = uiState.isAdmin
    val otherMembers = uiState.members.filterNot { it.uid == user.uid }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveAdminDialog by remember { mutableStateOf(false) }
    var showLeaveMemberDialog by remember { mutableStateOf(false) }
    var showDestroyDialog by remember { mutableStateOf(false) }

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
        Text(
            text = "Perfil",
            style = typo.pageTitle,
            color = colors.ink,
        )

        SuaVezCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SuaVezAvatar(
                    name = user.nome,
                    idForColor = user.uid,
                    size = 56.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = "NOME", style = typo.overline, color = colors.muted)
                    Text(
                        text = user.nome,
                        style = typo.itemTitle.copy(fontSize = 18.sp),
                        color = colors.ink,
                    )
                }
                if (house != null && isAdmin) {
                    SuaVezChip(text = "Admin", variant = SuaVezChipVariant.Admin)
                }
            }
        }

        if (house != null) {
            SuaVezCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "NOME DA CASA", style = typo.overline, color = colors.muted)
                    Text(
                        text = house.nome,
                        style = typo.itemTitle,
                        color = colors.ink,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f).heightIn(min = 12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(SuaVezDimens.CardGap)) {
            if (house != null) {
                if (isAdmin) {
                    SuaVezButton(
                        text = "Renomear casa",
                        onClick = { showRenameDialog = true },
                        variant = SuaVezButtonVariant.Secondary,
                    )
                    SuaVezButton(
                        text = "Sair da casa",
                        onClick = { showLeaveAdminDialog = true },
                        variant = SuaVezButtonVariant.Neutral,
                    )
                    SuaVezButton(
                        text = "Destruir casa",
                        onClick = { showDestroyDialog = true },
                        variant = SuaVezButtonVariant.Destructive,
                    )
                } else {
                    SuaVezButton(
                        text = "Sair da casa",
                        onClick = { showLeaveMemberDialog = true },
                        variant = SuaVezButtonVariant.Neutral,
                    )
                }
                SuaVezButton(
                    text = "Sair da conta",
                    onClick = { state.signOut() },
                    variant = SuaVezButtonVariant.Ghost,
                )
            } else {
                SuaVezButton(
                    text = "Sair da conta",
                    onClick = { state.signOut() },
                    variant = SuaVezButtonVariant.Neutral,
                )
            }
        }
    }

    if (showRenameDialog && house != null) {
        RenameHouseDialog(
            currentName = house.nome,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                showRenameDialog = false
                state.renameHouse(newName)
            },
        )
    }

    if (showLeaveAdminDialog) {
        TransferAdminAndLeaveDialog(
            otherMembers = otherMembers,
            onDismiss = { showLeaveAdminDialog = false },
            onTransferAndLeave = { newAdminUid ->
                showLeaveAdminDialog = false
                state.transferAdminAndLeave(newAdminUid)
            },
        )
    }

    if (showLeaveMemberDialog && house != null) {
        ConfirmActionDialog(
            title = "Sair da casa?",
            message = "Você deixará de fazer parte da casa \"${house.nome}\".",
            confirmText = "Sair da casa",
            destructive = false,
            onConfirm = {
                showLeaveMemberDialog = false
                state.leaveHouseAsMember()
            },
            onDismiss = { showLeaveMemberDialog = false },
        )
    }

    if (showDestroyDialog && house != null) {
        DestroyHouseDialog(
            houseName = house.nome,
            onDismiss = { showDestroyDialog = false },
            onConfirmDestroy = {
                showDestroyDialog = false
                state.destroyHouse()
            },
        )
    }
}
