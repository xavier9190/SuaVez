package xavier.pedro.suavez.ui

import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.firstNameOf

data class TaskItemUiModel(
    val assignment: UiAssignment,
    val isSuaVez: Boolean,
)

data class TasksUiModel(
    val firstName: String,
    val weekNumber: Int,
    val memberCount: Int,
    val donePoints: Int,
    val totalPoints: Int,
    val pendingPoints: Int,
    val doneFraction: Float,
    val pendingFraction: Float,
    val isQuotaCompleted: Boolean,
    val pendingCount: Int,
    val myTasks: List<TaskItemUiModel>,
    val houseName: String,
    val houseDoneCount: Int,
    val houseTotalCount: Int,
    val houseCompletionPercent: Int,
    val houseProgressFraction: Float,
    val houseRemainingCount: Int,
    val visibleAvatars: List<UiMember>,
    val openTasksCount: Int,
    val openPreviewTasks: List<UiAssignment>,
    val extraOpenTasksCount: Int,
)

fun SuaVezUiState.toTasksUiModel(): TasksUiModel? {
    val user = currentUser ?: return null
    val house = currentHouse ?: return null

    val orderedMyTasks = myAssignments
    val firstPendingId = orderedMyTasks.firstOrNull { !it.isDone }?.id
    val taskItems = orderedMyTasks.map { assignment ->
        TaskItemUiModel(
            assignment = assignment,
            isSuaVez = !assignment.isDone && assignment.id == firstPendingId,
        )
    }

    val donePts = myDonePoints
    val totalPts = myTotalPoints
    val pendingPts = myPendingPoints
    val doneFraction = if (totalPts > 0) {
        (donePts.toFloat() / totalPts.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val pendingFraction = if (totalPts > 0) {
        (pendingPts.toFloat() / totalPts.toFloat()).coerceIn(0f, 1f - doneFraction)
    } else {
        0f
    }

    val houseDone = houseDoneCount
    val houseTotal = houseTotalCount
    val houseProgressFraction = if (houseTotal > 0) {
        (houseDone.toFloat() / houseTotal.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val openList = openAssignments
    val openPreview = openList.take(2)

    return TasksUiModel(
        firstName = firstNameOf(user.nome),
        weekNumber = house.semanaNumero,
        memberCount = members.size,
        donePoints = donePts,
        totalPoints = totalPts,
        pendingPoints = pendingPts,
        doneFraction = doneFraction,
        pendingFraction = pendingFraction,
        isQuotaCompleted = pendingPts <= 0,
        pendingCount = myPendingCount,
        myTasks = taskItems,
        houseName = house.nome,
        houseDoneCount = houseDone,
        houseTotalCount = houseTotal,
        houseCompletionPercent = houseCompletionPercent,
        houseProgressFraction = houseProgressFraction,
        houseRemainingCount = (houseTotal - houseDone).coerceAtLeast(0),
        visibleAvatars = members.take(5),
        openTasksCount = openList.size,
        openPreviewTasks = openPreview,
        extraOpenTasksCount = (openList.size - openPreview.size).coerceAtLeast(0),
    )
}
