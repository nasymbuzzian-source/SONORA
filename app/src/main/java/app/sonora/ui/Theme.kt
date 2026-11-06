package app.sonora.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Bg = Color(0xFF050505)
val Surface1 = Color(0xFF121214)
val Surface2 = Color(0xFF1C1C20)
val Muted = Color(0xFF9A9AA3)
val TextMain = Color(0xFFF4F4F6)

val ACCENTS: List<Pair<String, Color>> = listOf(
    "Verde" to Color(0xFF35E0A1),
    "Azul" to Color(0xFF4DA3FF),
    "Violeta" to Color(0xFFB08CFF),
    "Naranja" to Color(0xFFFF9F43),
    "Rosa" to Color(0xFFFF6FA5)
)

val LocalBrand = compositionLocalOf { ACCENTS[0].second }

/** Color de identidad actual (cambia con el ajuste de tema). */
val Brand: Color
    @Composable @ReadOnlyComposable get() = LocalBrand.current

/** Versión muy oscura del color de identidad, para degradados de fondo. */
val BrandDeep: Color
    @Composable @ReadOnlyComposable get() = lerp(Bg, LocalBrand.current, 0.16f)

@Composable
fun SonoraTheme(accent: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrand provides accent) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = accent,
                onPrimary = Color.Black,
                background = Bg,
                onBackground = TextMain,
                surface = Surface1,
                onSurface = TextMain,
                surfaceVariant = Surface2,
                onSurfaceVariant = Muted,
                surfaceContainer = Surface2,
                surfaceContainerHigh = Surface2
            )
        ) {
            Surface(Modifier.fillMaxSize(), color = Bg, contentColor = TextMain, content = content)
        }
    }
}
