package xavier.pedro.suavez.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

data class Member(
    @DocumentId
    val uid: String = "",
    val nome: String = "",
    val fotoUrl: String? = null,
    val papel: String = HouseRole.MEMBER.name,
    val pontosPorMes: Map<String, Int> = emptyMap(),
    val preferences: Map<String, String> = emptyMap(),
    @ServerTimestamp
    val entrouEm: Timestamp? = null
) {
    @Exclude
    fun getRole(): HouseRole = HouseRole.fromString(papel)

    @Exclude
    fun getPreferenceFor(taskId: String): PreferenceLevel {
        val raw = preferences[taskId]
        return PreferenceLevel.fromString(raw)
    }

    @Exclude
    fun getPointsForMonth(monthKey: String): Int {
        return pontosPorMes[monthKey] ?: 0
    }
}
