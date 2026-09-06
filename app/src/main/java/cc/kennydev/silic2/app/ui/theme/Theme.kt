package cc.kennydev.silic2.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SilicDarkColorScheme = darkColorScheme(
    primary = SilicGreen,
    onPrimary = DarkForestBg,
    primaryContainer = SilicGreenContainer,
    onPrimaryContainer = SilicGreen,
    secondary = TealAccent,
    onSecondary = DarkForestBg,
    background = DarkForestBg,
    surface = ForestSurface,
    surfaceVariant = ForestSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun Silic2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SilicDarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
