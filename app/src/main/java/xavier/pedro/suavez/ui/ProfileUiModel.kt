package xavier.pedro.suavez.ui

import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiUser

data class ProfileUiModel(
    val user: UiUser,
    val houseName: String?,
    val isAdmin: Boolean,
    val otherMembers: List<UiMember>,
) {
    val hasHouse: Boolean get() = houseName != null
    val showAdminBadge: Boolean get() = hasHouse && isAdmin
}

fun SuaVezUiState.toProfileUiModel(): ProfileUiModel? {
    val user = currentUser ?: return null
    return ProfileUiModel(
        user = user,
        houseName = currentHouse?.nome,
        isAdmin = isAdmin,
        otherMembers = members.filterNot { it.uid == user.uid },
    )
}

fun isValidHouseRename(input: String): Boolean = input.trim().length >= 2

fun isDestroyHouseConfirmed(typedInput: String, houseName: String): Boolean {
    val cleanTarget = houseName.trim()
    return cleanTarget.isNotEmpty() && typedInput.trim() == cleanTarget
}
