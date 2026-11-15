package xavier.pedro.suavez

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xavier.pedro.suavez.ui.SuaVezApp
import xavier.pedro.suavez.ui.theme.SuaVezTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuaVezTheme {
                SuaVezApp()
            }
        }
    }
}
