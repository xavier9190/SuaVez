package xavier.pedro.suavez

import android.app.Application
import xavier.pedro.suavez.data.repository.FirestoreSuaVezDataSource
import xavier.pedro.suavez.ui.state.SuaVezAppContainer
import xavier.pedro.suavez.ui.state.SuaVezContainerProvider

class SuaVezApplication : Application(), SuaVezContainerProvider {
    override val container: SuaVezAppContainer by lazy {
        SuaVezAppContainer(dataSource = FirestoreSuaVezDataSource())
    }
}
