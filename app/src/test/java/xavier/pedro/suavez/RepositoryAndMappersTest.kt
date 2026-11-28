package xavier.pedro.suavez

import com.google.firebase.Timestamp
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
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiRecurringTask
import xavier.pedro.suavez.ui.state.calculateWeeklyQuota
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone

class RepositoryAndMappersTest {

    @Test
    fun userAndHouseMappersRoundTripCorrectly() {
        val user = User(
            uid = "u_1",
            nome = "Ana Costa",
            email = "ana@suavez.app",
            fotoUrl = "https://example.com/ana.jpg",
            currentHouseId = "h_1",
        )
        val uiUser = user.toUiUser()
        assertEquals("u_1", uiUser.uid)
        assertEquals("Ana Costa", uiUser.nome)
        assertEquals("ana@suavez.app", uiUser.email)
        assertEquals("https://example.com/ana.jpg", uiUser.fotoUrl)

        val backToFirestore = uiUser.toFirestoreUser(currentHouseId = "h_1")
        assertEquals("h_1", backToFirestore.currentHouseId)
        assertEquals("https://example.com/ana.jpg", backToFirestore.fotoUrl)

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
        assertEquals("2026-W46_task_t_1_2026-11-15", plan.catalogAssignments[0].id)
        assertEquals("2026-W46_task_t_2_2026-11-15", plan.catalogAssignments[1].id)
    }

    @Test
    fun deterministicAssignmentIdsAndRolloverIdempotencyWorkCorrectly() {
        assertEquals("2026-W46_task_t_1", AssignmentRepository.catalogAssignmentId("2026-W46", "t_1"))
        assertEquals("2026-W46_rollover_a_1", AssignmentRepository.rolloverAssignmentId("2026-W46", "a_1"))

        val previousAssignments = listOf(
            Assignment(
                id = "old_pending",
                semana = "2026-W45",
                taskId = "t_2",
                titulo = "Comprar sabão",
                peso = 5,
                status = AssignmentStatus.PENDENTE.name,
            ),
        )
        val catalog = listOf(
            Task(id = "t_1", titulo = "Lavar banheiro", peso = 8, frequencia = "SEMANAL", ativa = true),
            Task(id = "t_2", titulo = "Comprar sabão", peso = 5, frequencia = "QUINZENAL", ativa = true),
        )
        val existingInNewWeek = listOf(
            Assignment(
                id = AssignmentRepository.catalogAssignmentId("2026-W46", "t_1"),
                semana = "2026-W46",
                taskId = "t_1",
                titulo = "Lavar banheiro",
                peso = 8,
            ),
            Assignment(
                id = AssignmentRepository.rolloverAssignmentId("2026-W46", "old_pending"),
                semana = "2026-W46",
                taskId = "t_2",
                titulo = "Comprar sabão",
                peso = 5,
                ehRollover = true,
            ),
        )

        // Rollover existente de t_2 não deve suprimir a instância de catálogo de t_2 na nova semana
        val partialPlan = AssignmentRepository.buildWeekRolloverPlan(
            previousWeekKey = "2026-W45",
            newWeekKey = "2026-W46",
            previousWeekAssignments = previousAssignments,
            activeCatalogTasks = catalog,
            existingNewWeekAssignments = existingInNewWeek,
            createdByUid = "u_ana",
        )
        assertTrue(partialPlan.rolledOverAssignments.isEmpty())
        assertEquals(listOf("2026-W46_task_t_2_2026-11-15"), partialPlan.catalogAssignments.map { it.id })

        // Quando todas as instâncias determinísticas já existem na nova semana, o plano é totalmente idempotente
        val fullExisting = existingInNewWeek + partialPlan.catalogAssignments
        val idempotentPlan = AssignmentRepository.buildWeekRolloverPlan(
            previousWeekKey = "2026-W45",
            newWeekKey = "2026-W46",
            previousWeekAssignments = previousAssignments,
            activeCatalogTasks = catalog,
            existingNewWeekAssignments = fullExisting,
            createdByUid = "u_ana",
        )
        assertTrue(idempotentPlan.rolledOverAssignments.isEmpty())
        assertTrue(idempotentPlan.catalogAssignments.isEmpty())
    }

    @Test
    fun parseWeekNumberHandlesValidAndMalformedKeys() {
        assertEquals(46, DateCycleUtils.parseWeekNumber("2026-W46"))
        assertEquals(1, DateCycleUtils.parseWeekNumber("2026", fallbackWeek = 1))
        assertEquals(1, DateCycleUtils.parseWeekNumber(null, fallbackWeek = 1))
    }

    @Test
    fun monthKeyFromExtractsMonthAcrossBoundariesAndFallsBackWhenNull() {
        val zoneId = ZoneId.of("America/Recife")
        val timeZone = TimeZone.getTimeZone(zoneId)

        val endOfOctober = Date.from(ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, zoneId).toInstant())
        val startOfNovember = Date.from(ZonedDateTime.of(2026, 11, 1, 0, 0, 1, 0, zoneId).toInstant())
        val endOfYear = Date.from(ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, zoneId).toInstant())
        val startOfNewYear = Date.from(ZonedDateTime.of(2027, 1, 1, 0, 0, 1, 0, zoneId).toInstant())

        assertEquals("2026-10", DateCycleUtils.monthKeyFrom(endOfOctober, timeZone))
        assertEquals("2026-11", DateCycleUtils.monthKeyFrom(startOfNovember, timeZone))
        assertEquals("2026-12", DateCycleUtils.monthKeyFrom(Timestamp(endOfYear), fallbackMonthKey = "2027-01", timeZone = timeZone))
        assertEquals("2027-01", DateCycleUtils.monthKeyFrom(Timestamp(startOfNewYear), fallbackMonthKey = "2026-12", timeZone = timeZone))
        assertEquals("2026-11", DateCycleUtils.monthKeyFrom(null as Timestamp?, fallbackMonthKey = "2026-11", timeZone = timeZone))
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

    @Test
    fun computeReminderTriggerAtMillisCalculatesFridayAndSaturdayMorning() {
        val zoneId = ZoneId.of("America/Recife")
        val timeZone = TimeZone.getTimeZone(zoneId)
        val mondayMorning = ZonedDateTime.of(2026, 4, 13, 8, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()

        val expectedFriday = ZonedDateTime.of(2026, 4, 17, 9, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
        val expectedSaturday = ZonedDateTime.of(2026, 4, 18, 9, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            expectedFriday,
            DateCycleUtils.computeReminderTriggerAtMillis(
                semanaKey = "2026-W16",
                deadline = TaskDeadline.SABADO,
                nowMillis = mondayMorning,
                timeZone = timeZone,
            ),
        )
        assertEquals(
            expectedSaturday,
            DateCycleUtils.computeReminderTriggerAtMillis(
                semanaKey = "2026-W16",
                deadline = TaskDeadline.DOMINGO,
                nowMillis = mondayMorning,
                timeZone = timeZone,
            ),
        )
    }

    @Test
    fun computeReminderTriggerAtMillisHandlesYearBoundaryAndIneligibleCases() {
        val zoneId = ZoneId.of("America/Recife")
        val timeZone = TimeZone.getTimeZone(zoneId)
        val beforeYearStart = ZonedDateTime.of(2025, 12, 29, 10, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
        val expectedJan2Friday = ZonedDateTime.of(2026, 1, 2, 9, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            expectedJan2Friday,
            DateCycleUtils.computeReminderTriggerAtMillis(
                semanaKey = "2026-W01",
                deadline = TaskDeadline.SABADO,
                nowMillis = beforeYearStart,
                timeZone = timeZone,
            ),
        )

        val mondayW16 = ZonedDateTime.of(2026, 4, 13, 8, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val exactFriday9am = ZonedDateTime.of(2026, 4, 17, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val afterFriday9am = exactFriday9am + 1_000L

        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("2026-W16", TaskDeadline.HOJE, mondayW16, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("2026-W16", TaskDeadline.SEM_PRAZO, mondayW16, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("2026-W16", null, mondayW16, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("2026-W16", TaskDeadline.SABADO, exactFriday9am, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("2026-W16", TaskDeadline.SABADO, afterFriday9am, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("", TaskDeadline.SABADO, mondayW16, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("invalid", TaskDeadline.SABADO, mondayW16, timeZone),
        )
        assertNull(
            DateCycleUtils.computeReminderTriggerAtMillis("2025-W53", TaskDeadline.SABADO, beforeYearStart, timeZone),
        )
    }

    @Test
    fun memberRemovalReopensOnlyUnfinishedAssignmentsFromCurrentWeekAndResetsAutoDistribution() {
        val currentWeek = "2026-W46"
        val removedUid = "u_bruno"

        val autoAssignedCurrent = Assignment(
            id = "a_1",
            semana = currentWeek,
            titulo = "Lavar banheiro",
            status = AssignmentStatus.ATRIBUIDA.name,
            responsavelUid = removedUid,
            responsavelNome = "Bruno",
            distribuidaAutomaticamente = true,
        )
        val completedCurrent = Assignment(
            id = "a_2",
            semana = currentWeek,
            titulo = "Tirar lixo",
            status = AssignmentStatus.CONCLUIDA.name,
            responsavelUid = removedUid,
            responsavelNome = "Bruno",
            distribuidaAutomaticamente = true,
        )
        val previousWeekPending = Assignment(
            id = "a_3",
            semana = "2026-W45",
            titulo = "Passar pano",
            status = AssignmentStatus.ATRIBUIDA.name,
            responsavelUid = removedUid,
            responsavelNome = "Bruno",
            distribuidaAutomaticamente = true,
        )
        val otherMemberCurrent = Assignment(
            id = "a_4",
            semana = currentWeek,
            titulo = "Lavar louça",
            status = AssignmentStatus.ATRIBUIDA.name,
            responsavelUid = "u_ana",
            responsavelNome = "Ana",
            distribuidaAutomaticamente = true,
        )

        assertTrue(HouseRepository.shouldReopenAssignmentOnMemberRemoval(autoAssignedCurrent, removedUid, currentWeek))
        assertFalse(HouseRepository.shouldReopenAssignmentOnMemberRemoval(completedCurrent, removedUid, currentWeek))
        assertFalse(HouseRepository.shouldReopenAssignmentOnMemberRemoval(previousWeekPending, removedUid, currentWeek))
        assertFalse(HouseRepository.shouldReopenAssignmentOnMemberRemoval(otherMemberCurrent, removedUid, currentWeek))

        val reopened = HouseRepository.reopenAssignmentOnMemberRemoval(autoAssignedCurrent)
        assertNull(reopened.responsavelUid)
        assertNull(reopened.responsavelNome)
        assertEquals(AssignmentStatus.PENDENTE.name, reopened.status)
        assertFalse(reopened.distribuidaAutomaticamente)

        assertEquals(
            mapOf(
                "responsavelUid" to null,
                "responsavelNome" to null,
                "status" to AssignmentStatus.PENDENTE.name,
                "distribuidaAutomaticamente" to false,
            ),
            HouseRepository.reopenedAssignmentUpdateMap(),
        )
    }

    @Test
    fun taskAndUiRecurringTaskRoundTripPreservesScheduleFieldsAndLegacyDefaults() {
        val task = Task(
            id = "t_sched",
            titulo = "Regar plantas",
            peso = 2,
            pesoNivel = TaskWeightLevel.LIGHT.name,
            frequencia = "SEMANAL",
            diasSemana = listOf(2, 4, 6),
            horario = "19:00",
            intervaloValor = 2,
            intervaloUnidade = Task.INTERVAL_UNIT_WEEKS,
            diaDoMes = null,
            semanaReferencia = "2026-W46",
            categoriaId = "outras",
            categoriaNome = "Outras",
        )
        val ui = task.toUiRecurringTask()
        assertEquals(listOf(2, 4, 6), ui.diasSemana)
        assertEquals("19:00", ui.horario)
        assertEquals(2, ui.intervaloValor)
        assertEquals(Task.INTERVAL_UNIT_WEEKS, ui.intervaloUnidade)
        assertNull(ui.diaDoMes)
        assertEquals("2026-W46", ui.semanaReferencia)

        val roundTrip = ui.toFirestoreTask(criadaPorUid = "u_1")
        assertEquals(listOf(2, 4, 6), roundTrip.diasSemana)
        assertEquals("19:00", roundTrip.horario)
        assertEquals(2, roundTrip.intervaloValor)
        assertEquals(Task.INTERVAL_UNIT_WEEKS, roundTrip.intervaloUnidade)
        assertEquals("2026-W46", roundTrip.semanaReferencia)
    }

    @Test
    fun assignmentMetaLabelFormatsWeekdayDateHorarioAndFallback() {
        val recurringInstance = Assignment(
            id = "2026-W46_task_t1_2026-11-11",
            semana = "2026-W46",
            taskId = "t1",
            titulo = "Lavar louça",
            peso = 2,
            dataAgendada = "2026-11-11",
            horario = "19:00",
            categoriaId = "limpeza",
            categoriaNome = "Limpeza",
        ).toUiAssignment()
        assertEquals("Limpeza · Qua, 19:00", recurringInstance.metaLabel())

        val oneOffCalendar = Assignment(
            id = "a_cal",
            semana = "2026-W50",
            taskId = null,
            titulo = "Pagar luz",
            peso = 2,
            dataAgendada = "2026-12-10",
            horario = "14:00",
            categoriaId = "contas",
            categoriaNome = "Contas",
            ehAvulsa = true,
        ).toUiAssignment()
        assertEquals("Contas · 10/12 · 14:00", oneOffCalendar.metaLabel())

        val noDeadline = UiAssignment(
            id = "a_none",
            recurringTaskId = null,
            titulo = "Organizar",
            categoria = TaskCategory.LIMPEZA,
            pontos = 2,
            deadline = null,
            assigneeUid = null,
        )
        assertEquals("Limpeza · sem prazo", noDeadline.metaLabel())
    }

    @Test
    fun isoWeekKeyForDateHandlesFutureDatesAndYearBoundary() {
        assertEquals("2026-W49", DateCycleUtils.isoWeekKeyForDate("2026-12-02"))
        assertEquals("2026-W01", DateCycleUtils.isoWeekKeyForDate("2026-01-01"))
        assertEquals("2026-W01", DateCycleUtils.isoWeekKeyForDate("2025-12-29"))
        assertNull(DateCycleUtils.isoWeekKeyForDate("2026-13-45"))
        assertNull(DateCycleUtils.isoWeekKeyForDate("invalid"))
    }

    @Test
    fun calculateOccurrencesInWeekHandlesWeeklyBiweeklyAndMonthlySchedules() {
        val weeklyMulti = Task(
            id = "t_w",
            titulo = "Lixo",
            diasSemana = listOf(2, 4, 6), // Seg, Qua, Sex
            intervaloValor = 1,
            intervaloUnidade = Task.INTERVAL_UNIT_WEEKS,
            ativa = true,
        )
        assertEquals(
            listOf("2026-11-09", "2026-11-11", "2026-11-13"),
            DateCycleUtils.calculateOccurrencesInWeek(weeklyMulti, "2026-W46"),
        )

        val biweekly = Task(
            id = "t_bw",
            titulo = "Banheiro",
            diasSemana = listOf(7), // Sábado
            intervaloValor = 2,
            intervaloUnidade = Task.INTERVAL_UNIT_WEEKS,
            semanaReferencia = "2026-W46",
            ativa = true,
        )
        assertEquals(listOf("2026-11-14"), DateCycleUtils.calculateOccurrencesInWeek(biweekly, "2026-W46"))
        assertEquals(emptyList<String>(), DateCycleUtils.calculateOccurrencesInWeek(biweekly, "2026-W47"))
        assertEquals(listOf("2026-11-28"), DateCycleUtils.calculateOccurrencesInWeek(biweekly, "2026-W48"))

        val monthlyDay10 = Task(
            id = "t_m",
            titulo = "Contas",
            intervaloValor = 1,
            intervaloUnidade = Task.INTERVAL_UNIT_MONTHS,
            diaDoMes = 10,
            semanaReferencia = "2026-W46",
            ativa = true,
        )
        assertEquals(listOf("2026-11-10"), DateCycleUtils.calculateOccurrencesInWeek(monthlyDay10, "2026-W46"))
        assertEquals(emptyList<String>(), DateCycleUtils.calculateOccurrencesInWeek(monthlyDay10, "2026-W47"))

        val monthlyDay31 = Task(
            id = "t_m31",
            titulo = "Fechar mês",
            intervaloValor = 2,
            intervaloUnidade = Task.INTERVAL_UNIT_MONTHS,
            diaDoMes = 31,
            semanaReferencia = "2025-12-31",
            ativa = true,
        )
        // Fevereiro 2026 (W09: 2026-02-23..2026-03-01) tem 28 dias e intervalo=2 a partir de dez/2025 -> 2026-02-28
        assertEquals(listOf("2026-02-28"), DateCycleUtils.calculateOccurrencesInWeek(monthlyDay31, "2026-W09"))
        // Janeiro 2026 (W05: 2026-01-26..2026-02-01) tem diffMonths=1 -> não ocorre
        assertEquals(emptyList<String>(), DateCycleUtils.calculateOccurrencesInWeek(monthlyDay31, "2026-W05"))
        // Virada de ano ISO em isoWeeksBetween
        assertEquals(2, DateCycleUtils.isoWeeksBetween("2025-W52", "2026-W02"))
    }

    @Test
    fun weekRolloverGeneratesOneAssignmentPerOccurrenceWithDeterministicIds() {
        val catalog = listOf(
            Task(
                id = "t_multi",
                titulo = "Tirar lixo",
                peso = 2,
                diasSemana = listOf(2, 4, 6),
                horario = "19:00",
                intervaloValor = 1,
                intervaloUnidade = Task.INTERVAL_UNIT_WEEKS,
                ativa = true,
            ),
        )
        val existing = listOf(
            Assignment(
                id = "2026-W46_task_t_multi_2026-11-09",
                semana = "2026-W46",
                taskId = "t_multi",
                dataAgendada = "2026-11-09",
            ),
        )

        val plan = AssignmentRepository.buildWeekRolloverPlan(
            previousWeekKey = "2026-W45",
            newWeekKey = "2026-W46",
            previousWeekAssignments = emptyList(),
            activeCatalogTasks = catalog,
            existingNewWeekAssignments = existing,
            createdByUid = "u_admin",
        )

        assertEquals(2, plan.catalogAssignments.size)
        assertEquals(
            listOf(
                "2026-W46_task_t_multi_2026-11-11",
                "2026-W46_task_t_multi_2026-11-13",
            ),
            plan.catalogAssignments.map { it.id },
        )
        assertEquals(listOf("2026-11-11", "2026-11-13"), plan.catalogAssignments.map { it.dataAgendada })
        assertTrue(plan.catalogAssignments.all { it.horario == "19:00" })
    }

    @Test
    fun weeklyQuotaAndGreedyDistributionAccountForMultipleOccurrences() {
        val recurring = UiRecurringTask(
            id = "t_multi",
            titulo = "Tirar lixo",
            categoria = TaskCategory.LIMPEZA,
            pontos = 5,
            diasSemana = listOf(2, 4, 6),
            intervaloValor = 1,
            intervaloUnidade = Task.INTERVAL_UNIT_WEEKS,
            semanaReferencia = "2026-W46",
        )
        // 5 pts * 3 ocorrências = 15 pts / 3 moradores = 5 pts
        assertEquals(5, calculateWeeklyQuota(listOf(recurring), memberCount = 3, targetWeekKey = "2026-W46"))

        val members = listOf(
            Member(uid = "u_1", nome = "Ana"),
            Member(uid = "u_2", nome = "Bruno"),
            Member(uid = "u_3", nome = "Carla"),
        )
        val openAssignments = listOf(
            Assignment(id = "a1", semana = "2026-W46", taskId = "t_multi", titulo = "Tirar lixo", peso = 5, status = "PENDENTE"),
            Assignment(id = "a2", semana = "2026-W46", taskId = "t_multi", titulo = "Tirar lixo", peso = 5, status = "PENDENTE"),
            Assignment(id = "a3", semana = "2026-W46", taskId = "t_multi", titulo = "Tirar lixo", peso = 5, status = "PENDENTE"),
        )

        val decisions = AssignmentRepository.computeGreedyDistribution(
            weekAssignments = openAssignments,
            recurringTasks = listOf(recurring.toFirestoreTask()),
            members = members,
            monthKey = "2026-11",
            targetWeekKey = "2026-W46",
        )
        assertEquals(3, decisions.size)
        assertEquals(setOf("u_1", "u_2", "u_3"), decisions.map { it.memberUid }.toSet())
    }
}
