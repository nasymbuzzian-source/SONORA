package app.sonora

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sonora.ui.ACCENTS
import app.sonora.ui.SonoraApp
import app.sonora.ui.SonoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val vm: MainViewModel = viewModel()
            val accent by vm.accent.collectAsStateWithLifecycle()
            SonoraTheme(ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)].second) {
                SonoraApp(vm)
            }
        }
    }
}
