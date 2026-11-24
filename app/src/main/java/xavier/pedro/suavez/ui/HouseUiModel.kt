package xavier.pedro.suavez.ui

import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiRecurringTask

enum class HouseMemberRoleBadge {
    ADMIN,
    NEXT_IN_TURN,
    NONE,
}

data class HouseRankItemUiModel(
    val member: UiMember,
    val points: Int,
    val progressFraction: Float,
    val isMe: Boolean,
    val isLeader: Boolean,
)

data class HouseMemberItemUiModel(
    val member: UiMember,
    val roleBadge: HouseMemberRoleBadge,
    val openCount: Int,
    val openPoints: Int,
    val canManage: Boolean,
)

data class HouseUiModel(
    val houseName: String,
    val inviteCode: String,
    val memberCount: Int,
    val isAdmin: Boolean,
    val rankedMembers: List<HouseRankItemUiModel>,
    val isBalanced: Boolean,
    val balanceGapPercent: Int,
    val houseDoneCount: Int,
    val houseTotalCount: Int,
    val members: List<HouseMemberItemUiModel>,
    val weeklyQuotaPerMember: Int,
    val recurringTasks: List<UiRecurringTask>,
)

fun SuaVezUiState.toHouseUiModel(): HouseUiModel? {
    val user = currentUser ?: return null
    val house = currentHouse ?: return null

    val ranked = rankedMembers()
    val maxPoints = (ranked.maxOfOrNull { memberWeeklyPoints(it.uid) } ?: 0).coerceAtLeast(1)
    val rankItems = ranked.mapIndexed { index, member ->
        val points = memberWeeklyPoints(member.uid)
        HouseRankItemUiModel(
            member = member,
            points = points,
            progressFraction = (points.toFloat() / maxPoints.toFloat()).coerceIn(0.08f, 1f),
            isMe = member.uid == user.uid,
            isLeader = index == 0 && points > 0,
        )
    }

    val nextUid = nextInTurnUid()
    val memberItems = members.map { member ->
        val badge = when {
            member.role == HouseRole.ADMIN -> HouseMemberRoleBadge.ADMIN
            member.uid == nextUid -> HouseMemberRoleBadge.NEXT_IN_TURN
            else -> HouseMemberRoleBadge.NONE
        }
        HouseMemberItemUiModel(
            member = member,
            roleBadge = badge,
            openCount = memberPendingCount(member.uid),
            openPoints = memberPendingPoints(member.uid),
            canManage = isAdmin && member.role != HouseRole.ADMIN,
        )
    }

    return HouseUiModel(
        houseName = house.nome,
        inviteCode = house.codigoConvite,
        memberCount = members.size,
        isAdmin = isAdmin,
        rankedMembers = rankItems,
        isBalanced = isBalanced(),
        balanceGapPercent = balanceGapPercent(),
        houseDoneCount = houseDoneCount,
        houseTotalCount = houseTotalCount,
        members = memberItems,
        weeklyQuotaPerMember = weeklyQuotaPerMember,
        recurringTasks = recurringTasks,
    )
}
