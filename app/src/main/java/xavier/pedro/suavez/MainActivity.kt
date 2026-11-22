package xavier.pedro.suavez

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import xavier.pedro.suavez.ui.SuaVezApp
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.theme.SuaVezTheme

class MainActivity : ComponentActivity() {

    private val sharedViewModel: SuaVezSharedViewModel by viewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuaVezTheme {
                SuaVezApp(state = sharedViewModel.stateHolder)
            }
        }
    }
}
