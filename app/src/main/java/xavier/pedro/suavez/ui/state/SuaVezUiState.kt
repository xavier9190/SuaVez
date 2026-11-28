package xavier.pedro.suavez.ui.state

import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.PreferenceLevel
import kotlin.math.roundToInt

/**
 * Snapshot imutável de estado de UI exposto por [SuaVezStateHolder] via `StateFlow`.
 */
data class SuaVezUiState(
    val destination: AppDestination = AppDestination.Login,
    val isAuthenticating: Boolean = false,
    val activeTab: MainTab = MainTab.TASKS,
    val currentUser: UiUser? = null,
    val currentHouse: UiHouse? = null,
    val currentWeekKey: String? = null,
    val members: List<UiMember> = emptyList(),
    val recurringTasks: List<UiRecurringTask> = emptyList(),
    val assignments: List<UiAssignment> = emptyList(),
    val snackbar: SnackbarFeedback? = null,
) {
    val currentMember: UiMember?
        get() = currentUser?.let { user -> members.firstOrNull { it.uid == user.uid } }

    val isAdmin: Boolean
        get() = currentMember?.role == HouseRole.ADMIN

    val myAssignments: List<UiAssignment>
        get() {
            val uid = currentUser?.uid ?: return emptyList()
            val mine = assignments.filter { it.assigneeUid == uid }
            val pending = mine.filter { !it.isDone }
                .sortedBy { it.deadline?.urgencyOrder ?: 99 }
            val done = mine.filter { it.isDone }
            return pending + done
        }

    val openAssignments: List<UiAssignment>
        get() = assignments.filter { it.assigneeUid == null && !it.isDone }

    val openScreenAssignments: List<UiAssignment>
        get() = assignments.filter { (it.assigneeUid == null || it.justClaimedInSession) && !it.isDone }

    val weeklyQuotaPerMember: Int
        get() = calculateWeeklyQuota(recurringTasks, members.size.coerceAtLeast(1), currentWeekKey)

    val myDonePoints: Int
        get() = myAssignments.filter { it.isDone }.sumOf { it.pontos }

    val myTotalPoints: Int
        get() {
            val assignedSum = myAssignments.sumOf { it.pontos }
            return maxOf(assignedSum, weeklyQuotaPerMember)
        }

    val myPendingPoints: Int
        get() = (myTotalPoints - myDonePoints).coerceAtLeast(0)

    val myPendingCount: Int
        get() = myAssignments.count { !it.isDone }

    val houseDoneCount: Int
        get() = assignments.count { it.isDone }

    val houseTotalCount: Int
        get() = assignments.size

    val houseCompletionPercent: Int
        get() {
            if (houseTotalCount == 0) return 0
            return ((houseDoneCount.toFloat() / houseTotalCount.toFloat()) * 100f).roundToInt()
        }

    fun memberWeeklyPoints(uid: String): Int =
        members.firstOrNull { it.uid == uid }?.monthlyPoints ?: 0

    fun memberPendingCount(uid: String): Int =
        assignments.count { it.assigneeUid == uid && !it.isDone }

    fun memberPendingPoints(uid: String): Int =
        assignments.filter { it.assigneeUid == uid && !it.isDone }.sumOf { it.pontos }

    fun nextInTurnUid(): String? {
        if (members.isEmpty()) return null
        return members.minByOrNull { memberWeeklyPoints(it.uid) + memberPendingPoints(it.uid) }?.uid
    }

    fun rankedMembers(): List<UiMember> =
        members.sortedByDescending { memberWeeklyPoints(it.uid) }

    fun isBalanced(): Boolean =
        isHouseBalanced(members.map { memberWeeklyPoints(it.uid) })

    fun balanceGapPercent(): Int {
        val scores = members.map { memberWeeklyPoints(it.uid) }
        val max = scores.maxOrNull() ?: return 0
        val min = scores.minOrNull() ?: return 0
        if (max <= 0) return 0
        return (((max - min).toDouble() / max.toDouble()) * 100.0).roundToInt()
    }

    fun getMyPreference(recurringTaskId: String): PreferenceLevel {
        return currentMember?.preferences?.get(recurringTaskId) ?: PreferenceLevel.TANTO_FAZ
    }
}
