package app.sonora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize

val Bg = Color(0xFF050505)
val Surface1 = Color(0xFF121214)
val Surface2 = Color(0xFF1C1C20)
val Brand = Color(0xFF35E0A1)
val Muted = Color(0xFF9A9AA3)
val TextMain = Color(0xFFF4F4F6)

@Composable
fun SonoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand,
            onPrimary = Color.Black,
            background = Bg,
            onBackground = TextMain,
            surface = Surface1,
            onSurface = TextMain,
            surfaceVariant = Surface2,
            onSurfaceVariant = Muted
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = TextMain, content = content)
    }
}
