package xavier.pedro.suavez.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiHouse
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiUser

class ProfileUiModelTest {

    private val adminUser = UiUser(uid = "u1", nome = "Ana Clara", email = "ana@example.com")
    private val memberUser = UiUser(uid = "u2", nome = "Bruno Silva", email = "bruno@example.com")
    private val house = UiHouse(
        id = "h1",
        nome = "República Sol",
        codigoConvite = "SV-1234",
        semanaNumero = 14,
    )
    private val members = listOf(
        UiMember(uid = "u1", nome = "Ana Clara", role = HouseRole.ADMIN, monthlyPoints = 10),
        UiMember(uid = "u2", nome = "Bruno Silva", role = HouseRole.MEMBER, monthlyPoints = 8),
        UiMember(uid = "u3", nome = "Carla Dias", role = HouseRole.MEMBER, monthlyPoints = 5),
    )

    @Test
    fun returnsNullWhenCurrentUserMissing() {
        assertNull(SuaVezUiState(currentUser = null, currentHouse = house).toProfileUiModel())
    }

    @Test
    fun mapsNoHouseUserWithoutHouseCardOrAdminBadge() {
        val state = SuaVezUiState(
            currentUser = adminUser,
            currentHouse = null,
            members = emptyList(),
        )

        val model = state.toProfileUiModel()!!
        assertEquals(adminUser, model.user)
        assertFalse(model.hasHouse)
        assertNull(model.houseName)
        assertFalse(model.showAdminBadge)
        assertFalse(model.isAdmin)
        assertTrue(model.otherMembers.isEmpty())
    }

    @Test
    fun mapsAdminInHouseWithAdminBadgeAndOtherMembersOnly() {
        val state = SuaVezUiState(
            currentUser = adminUser,
            currentHouse = house,
            members = members,
        )

        val model = state.toProfileUiModel()!!
        assertTrue(model.hasHouse)
        assertEquals("República Sol", model.houseName)
        assertTrue(model.showAdminBadge)
        assertTrue(model.isAdmin)
        assertEquals(listOf("u2", "u3"), model.otherMembers.map { it.uid })
    }

    @Test
    fun mapsCommonMemberInHouseWithoutAdminBadge() {
        val state = SuaVezUiState(
            currentUser = memberUser,
            currentHouse = house,
            members = members,
        )

        val model = state.toProfileUiModel()!!
        assertTrue(model.hasHouse)
        assertEquals("República Sol", model.houseName)
        assertFalse(model.showAdminBadge)
        assertFalse(model.isAdmin)
        assertEquals(listOf("u1", "u3"), model.otherMembers.map { it.uid })
    }

    @Test
    fun validatesHouseRenameRequiresAtLeastTwoNonBlankCharacters() {
        assertFalse(isValidHouseRename(""))
        assertFalse(isValidHouseRename("   "))
        assertFalse(isValidHouseRename(" A "))
        assertTrue(isValidHouseRename("Ab"))
        assertTrue(isValidHouseRename("  Casa Nova  "))
    }

    @Test
    fun validatesDestroyConfirmationRequiresExactTrimmedHouseNameMatch() {
        assertFalse(isDestroyHouseConfirmed("", "Casa Sol"))
        assertFalse(isDestroyHouseConfirmed("Casa", "Casa Sol"))
        assertFalse(isDestroyHouseConfirmed("casa sol", "Casa Sol"))
        assertFalse(isDestroyHouseConfirmed("Casa Sol", ""))
        assertTrue(isDestroyHouseConfirmed("Casa Sol", "Casa Sol"))
        assertTrue(isDestroyHouseConfirmed("  Casa Sol  ", "Casa Sol"))
    }
}
