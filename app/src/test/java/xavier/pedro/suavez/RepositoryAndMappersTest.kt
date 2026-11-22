package xavier.pedro.suavez

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.data.repository.AssignmentRepository
import xavier.pedro.suavez.data.repository.DateCycleUtils
import xavier.pedro.suavez.data.repository.HouseRepository
import xavier.pedro.suavez.data.repository.toDefaultCategoryModel
import xavier.pedro.suavez.data.repository.toFirestoreAssignment
import xavier.pedro.suavez.data.repository.toFirestoreMember
import xavier.pedro.suavez.data.repository.toFirestoreTask
import xavier.pedro.suavez.data.repository.toFirestoreUser
import xavier.pedro.suavez.data.repository.toUiAssignment
import xavier.pedro.suavez.data.repository.toUiRecurringTask
import xavier.pedro.suavez.data.repository.toUiHouse
import xavier.pedro.suavez.data.repository.toUiMember
import xavier.pedro.suavez.data.repository.toUiUser
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency

class RepositoryAndMappersTest {

    @Test
    fun userAndHouseMappersRoundTripCorrectly() {
        val user = User(
            uid = "u_1",
            nome = "Ana Costa",
            email = "ana@suavez.app",
            currentHouseId = "h_1",
        )
        val uiUser = user.toUiUser()
        assertEquals("u_1", uiUser.uid)
        assertEquals("Ana Costa", uiUser.nome)
        assertEquals("ana@suavez.app", uiUser.email)

        val backToFirestore = uiUser.toFirestoreUser(currentHouseId = "h_1")
        assertEquals("h_1", backToFirestore.currentHouseId)

        val house = House(
            id = "h_1",
            nome = "República dos Cravos",
            codigoConvite = "SV-4821",
            semanaAtual = "2026-W46",
        )
        val uiHouse = house.toUiHouse()
        assertEquals("h_1", uiHouse.id)
        assertEquals("República dos Cravos", uiHouse.nome)
        assertEquals("SV-4821", uiHouse.codigoConvite)
        assertEquals(46, uiHouse.semanaNumero)
    }

    @Test
    fun memberAndTaskMappersPreservePointsPreferencesAndFrequency() {
        val member = Member(
            uid = "u_bruno",
            nome = "Bruno",
            papel = "ADMIN",
            pontosPorMes = mapOf("2026-10" to 15, "2026-11" to 28),
            preferences = mapOf("t_1" to "GOSTO", "t_2" to "ODEIO"),
        )
        val uiMember = member.toUiMember(monthKey = "2026-11")
        assertEquals(HouseRole.ADMIN, uiMember.role)
        assertEquals(28, uiMember.monthlyPoints)
        assertEquals(PreferenceLevel.GOSTO, uiMember.preferences["t_1"])
        assertEquals(PreferenceLevel.ODEIO, uiMember.preferences["t_2"])

        val firestoreMember = uiMember.toFirestoreMember(
            monthKey = "2026-11",
            existingPontosPorMes = member.pontosPorMes,
        )
        assertEquals(15, firestoreMember.pontosPorMes["2026-10"])
        assertEquals(28, firestoreMember.pontosPorMes["2026-11"])

        val task = Task(
            id = "t_1",
            titulo = "Lavar banheiro",
            peso = 8,
            pesoNivel = TaskWeightLevel.HEAVY.name,
            frequencia = "QUINZENAL",
            categoriaId = "limpeza",
            categoriaNome = "Limpeza",
        )
        val uiRecurringTask = task.toUiRecurringTask()
        assertEquals("t_1", uiRecurringTask.id)
        assertEquals(TaskCategory.LIMPEZA, uiRecurringTask.categoria)
        assertEquals(8, uiRecurringTask.pontos)
        assertEquals(TaskFrequency.QUINZENAL, uiRecurringTask.frequencia)
        assertEquals(TaskWeightLevel.HEAVY, uiRecurringTask.effortLevel)

        val backToTask = uiRecurringTask.toFirestoreTask(criadaPorUid = "u_bruno")
        assertEquals("QUINZENAL", backToTask.frequencia)
        assertEquals("HEAVY", backToTask.pesoNivel)
        assertEquals("limpeza", backToTask.categoriaId)
        assertEquals("Limpeza", backToTask.categoriaNome)
    }

    @Test
    fun assignmentMapperMapsDeadlineRolloverAndStatus() {
        val assignment = Assignment(
            id = "a_1",
            semana = "2026-W46",
            taskId = "t_1",
            titulo = "Tirar o lixo",
            peso = 2,
            deadline = "SABADO",
            categoriaId = "limpeza",
            categoriaNome = "Limpeza",
            status = AssignmentStatus.ATRIBUIDA.name,
            responsavelUid = "u_ana",
            ehRollover = true,
        )
        val uiAssignment = assignment.toUiAssignment()
        assertEquals("a_1", uiAssignment.id)
        assertEquals("t_1", uiAssignment.recurringTaskId)
        assertEquals(TaskCategory.LIMPEZA, uiAssignment.categoria)
        assertEquals(TaskDeadline.SABADO, uiAssignment.deadline)
        assertEquals("acumulada", uiAssignment.subtitleOverride)
        assertEquals("u_ana", uiAssignment.assigneeUid)
        assertFalse(uiAssignment.isDone)

        val roundTrip = uiAssignment.toFirestoreAssignment(semana = "2026-W46", criadaPorUid = "u_ana")
        assertEquals(AssignmentStatus.ATRIBUIDA.name, roundTrip.status)
        assertEquals("SABADO", roundTrip.deadline)
        assertFalse(roundTrip.ehAvulsa)
    }

    @Test
    fun greedyDistributionRespectsPreferencesAndBalancesLoad() {
        val recurringTasks = listOf(
            Task(id = "t_1", titulo = "Lavar banheiro", peso = 8),
            Task(id = "t_2", titulo = "Limpar geladeira", peso = 5),
            Task(id = "t_3", titulo = "Tirar lixo", peso = 2),
        )
        val members = listOf(
            Member(
                uid = "u_ana",
                nome = "Ana",
                pontosPorMes = mapOf("2026-11" to 20),
                preferences = mapOf("t_1" to PreferenceLevel.ODEIO.name),
            ),
            Member(
                uid = "u_bruno",
                nome = "Bruno",
                pontosPorMes = mapOf("2026-11" to 10),
                preferences = mapOf("t_1" to PreferenceLevel.GOSTO.name),
            ),
        )
        val openAssignments = listOf(
            Assignment(id = "a_1", taskId = "t_1", titulo = "Lavar banheiro", peso = 8, status = "PENDENTE"),
            Assignment(id = "a_2", taskId = "t_2", titulo = "Limpar geladeira", peso = 5, status = "PENDENTE"),
            Assignment(id = "a_3", taskId = "t_3", titulo = "Tirar lixo", peso = 2, status = "PENDENTE"),
        )

        val decisions = AssignmentRepository.computeGreedyDistribution(
            weekAssignments = openAssignments,
            recurringTasks = recurringTasks,
            members = members,
            monthKey = "2026-11",
        )

        assertEquals(3, decisions.size)
        val byAssignment = decisions.associateBy { it.assignmentId }
        // Ana odeia t_1 e Bruno gosta de t_1 -> t_1 vai para Bruno
        assertEquals("u_bruno", byAssignment["a_1"]?.memberUid)
        // Com Bruno já carregando 8 pts (acima da cota de 8), t_2 (5 pts) e t_3 (2 pts) vão para Ana (total 7 pts)
        assertEquals("u_ana", byAssignment["a_2"]?.memberUid)
        assertEquals("u_ana", byAssignment["a_3"]?.memberUid)
    }

    @Test
    fun weekRolloverMarksUnfinishedAsUrgentAndInstantiatesActiveCatalogTasks() {
        val previousAssignments = listOf(
            Assignment(
                id = "old_done",
                semana = "2026-W45",
                taskId = "t_1",
                titulo = "Lavar banheiro",
                peso = 8,
                status = AssignmentStatus.CONCLUIDA.name,
                responsavelUid = "u_ana",
            ),
            Assignment(
                id = "old_pending",
                semana = "2026-W45",
                taskId = "t_2",
                titulo = "Comprar sabão",
                peso = 5,
                status = AssignmentStatus.ATRIBUIDA.name,
                responsavelUid = "u_bruno",
            ),
        )
        val catalog = listOf(
            Task(id = "t_1", titulo = "Lavar banheiro", peso = 8, frequencia = "SEMANAL", ativa = true),
            Task(id = "t_2", titulo = "Comprar sabão", peso = 5, frequencia = "QUINZENAL", ativa = true),
            Task(id = "t_3", titulo = "Tarefa inativa", peso = 2, frequencia = "SEMANAL", ativa = false),
        )

        val plan = AssignmentRepository.buildWeekRolloverPlan(
            previousWeekKey = "2026-W45",
            newWeekKey = "2026-W46",
            previousWeekAssignments = previousAssignments,
            activeCatalogTasks = catalog,
            createdByUid = "u_ana",
        )

        assertEquals("2026-W46", plan.newWeekKey)
        assertEquals(1, plan.rolledOverAssignments.size)
        val rolled = plan.rolledOverAssignments.first()
        assertEquals("2026-W46_rollover_old_pending", rolled.id)
        assertEquals("Comprar sabão", rolled.titulo)
        assertTrue(rolled.ehRollover)
        assertTrue(rolled.urgente)
        assertEquals(TaskDeadline.HOJE.name, rolled.deadline)
        assertEquals(AssignmentStatus.PENDENTE.name, rolled.status)
        assertNull(rolled.responsavelUid)
        assertEquals("2026-W45", rolled.semanaOrigem)

        // Apenas t_1 e t_2 estão ativas no catálogo
        assertEquals(2, plan.catalogAssignments.size)
        assertEquals("2026-W46_task_t_1", plan.catalogAssignments[0].id)
        assertEquals("2026-W46_task_t_2", plan.catalogAssignments[1].id)
    }

    @Test
    fun parseWeekNumberHandlesValidAndMalformedKeys() {
        assertEquals(46, DateCycleUtils.parseWeekNumber("2026-W46"))
        assertEquals(1, DateCycleUtils.parseWeekNumber("2026", fallbackWeek = 1))
        assertEquals(1, DateCycleUtils.parseWeekNumber(null, fallbackWeek = 1))
    }

    @Test
    fun inviteCodeAndDefaultCategoriesHelpersFormatCorrectly() {
        assertEquals("SV-0042", HouseRepository.formatInviteCode(42))
        assertEquals("SV-7824", HouseRepository.normalizeInviteCode("sv7824"))
        assertEquals("SV-7824", HouseRepository.normalizeInviteCode(" sv-7824 "))

        val defaultCategories = TaskCategory.entries.map { it.toDefaultCategoryModel() }
        assertEquals(4, defaultCategories.size)
        assertTrue(defaultCategories.all { it.padrao })
    }

    @Test
    fun inviteCodeValidationAcceptsNormalizedAndRejectsMalformedCodes() {
        assertTrue(HouseRepository.isValidInviteCode("SV-7824"))
        assertTrue(HouseRepository.isValidInviteCode("sv7824"))
        assertTrue(HouseRepository.isValidInviteCode(" sv-1000 "))
        assertTrue(HouseRepository.isValidInviteCode("SV4X89"))

        assertFalse(HouseRepository.isValidInviteCode(""))
        assertFalse(HouseRepository.isValidInviteCode("   "))
        assertFalse(HouseRepository.isValidInviteCode("123"))
        assertFalse(HouseRepository.isValidInviteCode("7824"))
        assertFalse(HouseRepository.isValidInviteCode("SV-12"))
        assertFalse(HouseRepository.isValidInviteCode("SV-12345"))
        assertFalse(HouseRepository.isValidInviteCode("ABCD"))
    }
}
