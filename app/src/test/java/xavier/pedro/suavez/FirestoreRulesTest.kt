package xavier.pedro.suavez

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirestoreRulesTest {

    private fun repoFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromAppModule = File("..", relativePath)
        if (fromAppModule.exists()) return fromAppModule
        return direct
    }

    private fun extractMatchBlock(rules: String, matchHeader: String): String {
        val start = rules.indexOf(matchHeader)
        assertTrue("Bloco '$matchHeader' não encontrado em firestore.rules", start >= 0)
        val openBrace = rules.indexOf('{', start + matchHeader.length)
        assertTrue("Chave de abertura não encontrada para '$matchHeader'", openBrace >= 0)
        var depth = 0
        for (i in openBrace until rules.length) {
            when (rules[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return rules.substring(start, i + 1)
                }
            }
        }
        error("Chave de fechamento não encontrada para '$matchHeader'")
    }

    private fun extractDirectAllowClause(block: String, allowPrefix: String): String {
        val start = block.indexOf(allowPrefix)
        assertTrue("Cláusula '$allowPrefix' não encontrada no bloco", start >= 0)
        val end = block.indexOf(';', start)
        assertTrue("Ponto e vírgula final não encontrado para '$allowPrefix'", end >= 0)
        return block.substring(start, end + 1)
    }

    @Test
    fun firebaseJsonPointsToFirestoreRules() {
        val firebaseJson = repoFile("firebase.json")
        assertTrue("firebase.json deve existir na raiz do repositório", firebaseJson.exists())
        val content = firebaseJson.readText()
        assertTrue(content.contains("\"firestore\""))
        assertTrue(content.contains("\"rules\""))
        assertTrue(content.contains("\"firestore.rules\""))
    }

    @Test
    fun firestoreRulesUsesV2BalancedSyntaxAndNeverAllowsUnauthenticatedAccess() {
        val rulesFile = repoFile("firestore.rules")
        assertTrue("firestore.rules deve existir na raiz do repositório", rulesFile.exists())
        val rules = rulesFile.readText()

        assertTrue(rules.trimStart().startsWith("rules_version = '2';"))
        assertTrue(rules.contains("request.auth != null"))
        assertFalse(
            "Nenhuma regra pode liberar acesso irrestrito (if true)",
            Regex("""allow\s+[^:]+:\s*if\s+true\s*;""").containsMatchIn(rules),
        )

        var braceBalance = 0
        var parenBalance = 0
        rules.forEach { ch ->
            when (ch) {
                '{' -> braceBalance++
                '}' -> braceBalance--
                '(' -> parenBalance++
                ')' -> parenBalance--
            }
            assertTrue("Chaves desbalanceadas em firestore.rules", braceBalance >= 0)
            assertTrue("Parênteses desbalanceados em firestore.rules", parenBalance >= 0)
        }
        assertEquals("Chaves abertas sem fechamento em firestore.rules", 0, braceBalance)
        assertEquals("Parênteses abertos sem fechamento em firestore.rules", 0, parenBalance)
    }

    @Test
    fun usersAndHousesRulesEnforceOwnershipAndMemberRolloverRestriction() {
        val rules = repoFile("firestore.rules").readText()

        val usersBlock = extractMatchBlock(rules, "match /users/{uid}")
        assertEquals("allow read: if isOwner(uid);", extractDirectAllowClause(usersBlock, "allow read:"))
        assertEquals("allow create: if isOwner(uid);", extractDirectAllowClause(usersBlock, "allow create:"))
        val usersUpdate = extractDirectAllowClause(usersBlock, "allow update:")
        assertTrue(usersUpdate.contains("isOwner(uid)"))
        assertTrue(usersUpdate.contains("affectedKeys().hasOnly(['currentHouseId'])"))
        assertTrue(usersUpdate.contains("request.resource.data.currentHouseId == null"))
        assertEquals("allow delete: if false;", extractDirectAllowClause(usersBlock, "allow delete:"))

        val housesBlock = extractMatchBlock(rules, "match /houses/{houseId}")
        val housesUpdate = extractDirectAllowClause(housesBlock, "allow update:")
        assertTrue(housesUpdate.contains("isHouseAdmin(houseId)"))
        assertTrue(housesUpdate.contains("isHouseMember(houseId)"))
        assertTrue(housesUpdate.contains("affectedKeys().hasOnly(['semanaAtual'])"))
        assertTrue(housesUpdate.contains("request.resource.data.semanaAtual != resource.data.semanaAtual"))
        assertTrue(housesUpdate.contains("isValidWeekKey(request.resource.data.semanaAtual)"))
        assertEquals("allow delete: if isHouseAdmin(houseId);", extractDirectAllowClause(housesBlock, "allow delete:"))
    }

    @Test
    fun categoriesAndTasksRestrictWriteToAdminAndBlockPhysicalDeleteOnTasks() {
        val rules = repoFile("firestore.rules").readText()

        val categoriesBlock = extractMatchBlock(rules, "match /categories/{categoryId}")
        assertEquals("allow read: if isHouseMember(houseId);", extractDirectAllowClause(categoriesBlock, "allow read:"))
        val catUpdateDelete = extractDirectAllowClause(categoriesBlock, "allow update, delete:")
        assertEquals("allow update, delete: if isHouseAdmin(houseId);", catUpdateDelete)

        val tasksBlock = extractMatchBlock(rules, "match /tasks/{taskId}")
        assertEquals("allow read: if isHouseMember(houseId);", extractDirectAllowClause(tasksBlock, "allow read:"))
        val tasksWrite = extractDirectAllowClause(tasksBlock, "allow create, update:")
        assertTrue(tasksWrite.contains("isHouseAdmin(houseId)"))
        assertTrue(tasksWrite.contains("request.resource.data.peso in [2, 5, 8]"))
        assertFalse(tasksWrite.contains("isHouseMember"))
        assertEquals(
            "allow delete: if isHouseAdmin(houseId) && !existsAfter(housePath(houseId));",
            extractDirectAllowClause(tasksBlock, "allow delete:"),
        )
    }

    @Test
    fun membersRulesPreventSelfPromotionAndProtectPreferencesAndRemoval() {
        val rules = repoFile("firestore.rules").readText()
        val membersBlock = extractMatchBlock(rules, "match /members/{uid}")

        val createClause = extractDirectAllowClause(membersBlock, "allow create:")
        assertTrue(createClause.contains("isOwner(uid)"))
        assertTrue(createClause.contains("request.resource.data.papel == 'MEMBER'"))
        assertTrue(createClause.contains("isHouseCreatorInBatch(houseId)"))

        val updateClause = extractDirectAllowClause(membersBlock, "allow update:")
        assertTrue(updateClause.contains("isHouseAdmin(houseId)"))
        assertTrue(updateClause.contains("request.resource.data.papel == resource.data.papel"))
        assertTrue(updateClause.contains("request.resource.data.pontosPorMes is map"))
        assertTrue(updateClause.contains("request.resource.data.preferences is map"))
        assertTrue(updateClause.contains("hasOnly(['preferences', 'pontosPorMes', 'nome', 'fotoUrl'])"))

        assertEquals(
            "allow delete: if isHouseAdmin(houseId) || isOwner(uid);",
            extractDirectAllowClause(membersBlock, "allow delete:"),
        )
    }

    @Test
    fun assignmentsRulesEnforceAssigneeCompletionClaimRolloverAndAdminDistribution() {
        val rules = repoFile("firestore.rules").readText()
        val assignmentsBlock = extractMatchBlock(rules, "match /assignments/{assignmentId}")

        val createClause = extractDirectAllowClause(assignmentsBlock, "allow create:")
        assertTrue(createClause.contains("request.resource.data.peso in [2, 5, 8]"))
        assertTrue(createClause.contains("isRolloverTransition(houseId)"))
        assertTrue(createClause.contains("request.resource.data.criadaPorUid == request.auth.uid"))

        val updateClause = extractDirectAllowClause(assignmentsBlock, "allow update:")
        assertTrue(updateClause.contains("resource.data.responsavelUid == request.auth.uid"))
        assertTrue(updateClause.contains("affectedKeys().hasOnly(['status', 'concluidaEm'])"))
        assertTrue(updateClause.contains("resource.data.status == 'ATRIBUIDA' && request.resource.data.status == 'CONCLUIDA' && request.resource.data.concluidaEm != null"))
        assertTrue(updateClause.contains("request.resource.data.distribuidaAutomaticamente == false"))
        assertTrue(updateClause.contains("isHouseAdmin(houseId)"))

        val deleteClause = extractDirectAllowClause(assignmentsBlock, "allow delete:")
        assertTrue(deleteClause.contains("isHouseAdmin(houseId)"))
        assertTrue(deleteClause.contains("resource.data.status != 'CONCLUIDA'"))
        assertTrue(deleteClause.contains("isRolloverTransition(houseId)"))
    }

    @Test
    fun firestoreSchemaDocReflectsAdminOnlyDistributionAndSecurityRules() {
        val schemaDoc = repoFile("docs/Esquema_Banco_Firestore.md")
        assertTrue(schemaDoc.exists())
        val content = schemaDoc.readText()

        assertTrue(
            "Tabela de permissões deve marcar 'Distribuir Pendentes' como ADMIN-only",
            Regex("""\|\s*Disparar "Distribuir Pendentes"\s*\|\s*✅\s*\|\s*❌\s*\|""").containsMatchIn(content),
        )
        assertTrue(content.contains("firestore.rules"))
        assertTrue(content.contains("semanaAtual"))
    }
}
