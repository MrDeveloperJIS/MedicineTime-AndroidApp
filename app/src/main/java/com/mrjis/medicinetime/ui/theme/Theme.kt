package com.mrjis.medicinetime.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mrjis.medicinetime.data.prefs.SettingsStore
import com.mrjis.medicinetime.data.prefs.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = White,
    primaryContainer = Navy,
    onPrimaryContainer = White,
    secondary = LightMuted,
    onSecondary = White,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightMuted,
    outlineVariant = LightOutline,
    error = LightError,
    onError = White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Navy,
    primaryContainer = Navy,
    onPrimaryContainer = DarkText,
    secondary = DarkMuted,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkMuted,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = DarkBackground
)

/** Colors Material 3 has no slot for (amber warning, low-stock badges). */
@Immutable
data class ExtraColors(
    val warning: Color,
    val onWarning: Color
)

private val LightExtraColors = ExtraColors(warning = LightWarning, onWarning = White)
private val DarkExtraColors = ExtraColors(warning = DarkWarning, onWarning = DarkBackground)

private val LocalExtraColors = staticCompositionLocalOf { LightExtraColors }

val MaterialTheme.extraColors: ExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtraColors.current

@Composable
fun MedicineTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** Resolves System / Light / Dark into a plain dark-or-not answer. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Keeps the status and navigation bar icons readable when the app theme differs from the system theme. */
@Composable
fun SystemBarsEffect(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF),
                AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
            ) { darkTheme }
        )
        onDispose { }
    }
}

/** The app theme following the Light / Dark / System choice from Settings. Used by both activities. */
@Composable
fun SettingsTheme(
    settings: SettingsStore,
    content: @Composable () -> Unit
) {
    val mode by settings.themeMode.collectAsState()
    val dark = mode.isDark()
    SystemBarsEffect(dark)
    MedicineTimeTheme(darkTheme = dark, content = content)
}