package org.notifledger.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF00504B),
    onSecondaryContainer = Color(0xFFA7F0E9),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF3B1046),
    tertiaryContainer = Color(0xFF54285F),
    onTertiaryContainer = Color(0xFFF3D4FA),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF006494),
    surfaceTint = Color(0xFF90CAF9),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006494),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF006B64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F0E9),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF7C4F88),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3D4FA),
    onTertiaryContainer = Color(0xFF330A3F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFF90CAF9),
    surfaceTint = Color(0xFF006494),
)

@Composable
fun NotifLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AmoledDarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
