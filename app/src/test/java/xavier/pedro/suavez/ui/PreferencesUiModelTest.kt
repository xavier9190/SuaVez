package xavier.pedro.suavez.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiHouse
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiRecurringTask
import xavier.pedro.suavez.ui.state.UiUser

class PreferencesUiModelTest {

    private val user = UiUser(uid = "u1", nome = "Ana Clara", email = "ana@example.com")
    private val house = UiHouse(id = "h1", nome = "República Sol", codigoConvite = "SV-1234", semanaNumero = 14)

    @Test
    fun returnsNullWhenUserOrHouseMissing() {
        assertNull(SuaVezUiState(currentUser = null, currentHouse = house).toPreferencesUiModel())
        assertNull(SuaVezUiState(currentUser = user, currentHouse = null).toPreferencesUiModel())
    }

    @Test
    fun returnsEmptyWhenNoRecurringTasksRegistered() {
        val state = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            members = listOf(UiMember("u1", "Ana Clara", HouseRole.ADMIN, 0)),
            recurringTasks = emptyList(),
        )

        val model = state.toPreferencesUiModel()!!
        assertTrue(model.isEmpty)
        assertTrue(model.items.isEmpty())
    }

    @Test
    fun mapsRecurringTasksWithSavedPreferencesAndDefaultTantoFaz() {
        val tasks = listOf(
            UiRecurringTask("t1", "Comprar sabão", TaskCategory.COMPRAS, 2, TaskFrequency.SEMANAL),
            UiRecurringTask("t2", "Lavar banheiro", TaskCategory.LIMPEZA, 8, TaskFrequency.SEMANAL),
            UiRecurringTask("t3", "Tirar o lixo", TaskCategory.LIMPEZA, 2, TaskFrequency.DIARIA),
        )
        val state = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            members = listOf(
                UiMember(
                    uid = "u1",
                    nome = "Ana Clara",
                    role = HouseRole.ADMIN,
                    monthlyPoints = 0,
                    preferences = mapOf(
                        "t2" to PreferenceLevel.ODEIO,
                        "t3" to PreferenceLevel.GOSTO,
                    ),
                ),
            ),
            recurringTasks = tasks,
        )

        val model = state.toPreferencesUiModel()!!
        assertFalse(model.isEmpty)
        assertEquals(listOf("t1", "t2", "t3"), model.items.map { it.task.id })
        assertEquals(
            listOf(
                PreferenceLevel.TANTO_FAZ,
                PreferenceLevel.ODEIO,
                PreferenceLevel.GOSTO,
            ),
            model.items.map { it.selectedPreference },
        )
    }
}
