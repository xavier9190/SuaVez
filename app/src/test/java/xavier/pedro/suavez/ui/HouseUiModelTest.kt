package xavier.pedro.suavez.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiHouse
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiRecurringTask
import xavier.pedro.suavez.ui.state.UiUser

class HouseUiModelTest {

    private val adminUser = UiUser(uid = "u1", nome = "Ana Clara", email = "ana@example.com")
    private val memberUser = UiUser(uid = "u2", nome = "Bruno Lima", email = "bruno@example.com")
    private val house = UiHouse(
        id = "h1",
        nome = "Casa da Praia",
        codigoConvite = "SV-4821",
        semanaNumero = 18,
    )

    @Test
    fun returnsNullWhenUserOrHouseIsMissing() {
        assertNull(SuaVezUiState(currentUser = null, currentHouse = house).toHouseUiModel())
        assertNull(SuaVezUiState(currentUser = adminUser, currentHouse = null).toHouseUiModel())
    }

    @Test
    fun mapsRankingLeaderCurrentUserBalanceAndDoneCounts() {
        val state = SuaVezUiState(
            currentUser = memberUser,
            currentHouse = house,
            members = listOf(
                UiMember(uid = "u1", nome = "Ana Clara", role = HouseRole.ADMIN, monthlyPoints = 8),
                UiMember(uid = "u2", nome = "Bruno Lima", role = HouseRole.MEMBER, monthlyPoints = 20),
                UiMember(uid = "u3", nome = "Carla Dias", role = HouseRole.MEMBER, monthlyPoints = 0),
            ),
            assignments = listOf(
                UiAssignment("a1", null, "Lavar louça", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE, assigneeUid = "u1", isDone = true),
                UiAssignment("a2", null, "Mercado", TaskCategory.COMPRAS, 5, TaskDeadline.SABADO, assigneeUid = "u2", isDone = false),
                UiAssignment("a3", null, "Pagar luz", TaskCategory.CONTAS, 2, TaskDeadline.DOMINGO, assigneeUid = null, isDone = false),
            ),
        )

        val model = state.toHouseUiModel()!!

        assertEquals("Casa da Praia", model.houseName)
        assertEquals("SV-4821", model.inviteCode)
        assertEquals(3, model.memberCount)
        assertFalse(model.isAdmin)
        assertEquals(1, model.houseDoneCount)
        assertEquals(3, model.houseTotalCount)
        assertFalse(model.isBalanced)
        assertEquals(100, model.balanceGapPercent)

        assertEquals(listOf("u2", "u1", "u3"), model.rankedMembers.map { it.member.uid })
        assertTrue(model.rankedMembers[0].isLeader)
        assertTrue(model.rankedMembers[0].isMe)
        assertEquals(1f, model.rankedMembers[0].progressFraction, 0.001f)

        assertFalse(model.rankedMembers[1].isLeader)
        assertFalse(model.rankedMembers[1].isMe)
        assertEquals(0.4f, model.rankedMembers[1].progressFraction, 0.001f)

        assertFalse(model.rankedMembers[2].isLeader)
        assertEquals(0.08f, model.rankedMembers[2].progressFraction, 0.001f)
    }

    @Test
    fun doesNotMarkLeaderWhenEveryoneHasZeroPointsAndMarksBalancedHouse() {
        val state = SuaVezUiState(
            currentUser = adminUser,
            currentHouse = house,
            members = listOf(
                UiMember(uid = "u1", nome = "Ana Clara", role = HouseRole.ADMIN, monthlyPoints = 0),
                UiMember(uid = "u2", nome = "Bruno Lima", role = HouseRole.MEMBER, monthlyPoints = 0),
            ),
        )

        val model = state.toHouseUiModel()!!

        assertTrue(model.isBalanced)
        assertEquals(0, model.balanceGapPercent)
        assertTrue(model.rankedMembers.none { it.isLeader })
        assertTrue(model.rankedMembers.all { it.progressFraction == 0.08f })
    }

    @Test
    fun balanceThresholdMarksTenPercentBalancedAndAboveTenPercentUnbalanced() {
        val balancedState = SuaVezUiState(
            currentUser = adminUser,
            currentHouse = house,
            members = listOf(
                UiMember(uid = "u1", nome = "Ana Clara", role = HouseRole.ADMIN, monthlyPoints = 10),
                UiMember(uid = "u2", nome = "Bruno Lima", role = HouseRole.MEMBER, monthlyPoints = 9),
            ),
        )
        val balancedModel = balancedState.toHouseUiModel()!!
        assertEquals(10, balancedModel.balanceGapPercent)
        assertTrue(balancedModel.isBalanced)

        val unbalancedState = balancedState.copy(
            members = listOf(
                UiMember(uid = "u1", nome = "Ana Clara", role = HouseRole.ADMIN, monthlyPoints = 10),
                UiMember(uid = "u2", nome = "Bruno Lima", role = HouseRole.MEMBER, monthlyPoints = 8),
            ),
        )
        val unbalancedModel = unbalancedState.toHouseUiModel()!!
        assertEquals(20, unbalancedModel.balanceGapPercent)
        assertFalse(unbalancedModel.isBalanced)
    }

    @Test
    fun mapsMemberBadgesPendingTotalsCanManageAndWeeklyQuota() {
        val state = SuaVezUiState(
            currentUser = adminUser,
            currentHouse = house,
            members = listOf(
                UiMember(uid = "u1", nome = "Ana Clara", role = HouseRole.ADMIN, monthlyPoints = 10),
                UiMember(uid = "u2", nome = "Bruno Lima", role = HouseRole.MEMBER, monthlyPoints = 12),
                UiMember(uid = "u3", nome = "Carla Dias", role = HouseRole.MEMBER, monthlyPoints = 4),
            ),
            recurringTasks = listOf(
                UiRecurringTask("r1", "Lavar banheiro", TaskCategory.LIMPEZA, 8, TaskFrequency.SEMANAL),
                UiRecurringTask("r2", "Tirar o lixo", TaskCategory.LIMPEZA, 2, TaskFrequency.DIARIA),
                UiRecurringTask("r3", "Compras", TaskCategory.COMPRAS, 5, TaskFrequency.SEMANAL),
            ),
            assignments = listOf(
                UiAssignment("a1", null, "T1", TaskCategory.LIMPEZA, 5, TaskDeadline.HOJE, assigneeUid = "u2", isDone = false),
                UiAssignment("a2", null, "T2", TaskCategory.COMPRAS, 2, TaskDeadline.HOJE, assigneeUid = "u2", isDone = false),
                UiAssignment("a3", null, "T3", TaskCategory.CONTAS, 2, TaskDeadline.SABADO, assigneeUid = "u3", isDone = true),
            ),
        )

        val model = state.toHouseUiModel()!!

        assertTrue(model.isAdmin)
        assertEquals(5, model.weeklyQuotaPerMember)
        assertEquals(3, model.recurringTasks.size)

        val ana = model.members[0]
        assertEquals(HouseMemberRoleBadge.ADMIN, ana.roleBadge)
        assertEquals(0, ana.openCount)
        assertEquals(0, ana.openPoints)
        assertFalse(ana.canManage)

        val bruno = model.members[1]
        assertEquals(HouseMemberRoleBadge.NONE, bruno.roleBadge)
        assertEquals(2, bruno.openCount)
        assertEquals(7, bruno.openPoints)
        assertTrue(bruno.canManage)

        val carla = model.members[2]
        assertEquals(HouseMemberRoleBadge.NEXT_IN_TURN, carla.roleBadge)
        assertEquals(0, carla.openCount)
        assertEquals(0, carla.openPoints)
        assertTrue(carla.canManage)
    }
}
