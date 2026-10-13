package pl.rytm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Coral = Color(0xFFFF5B3B)
private val CoralDark = Color(0xFFFF8A70)
private val Navy = Color(0xFF1A1C2B)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE2462A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0700),
    secondary = Color(0xFF3F4A7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF111A47),
    tertiary = Color(0xFF00696B),
    tertiaryContainer = Color(0xFF9CF1F2),
    background = Color(0xFFFFFBF8),
    onBackground = Navy,
    surface = Color(0xFFFFFBF8),
    onSurface = Navy,
    surfaceVariant = Color(0xFFF4E4E0),
    onSurfaceVariant = Color(0xFF534340),
    surfaceContainer = Color(0xFFF8EEEB),
    surfaceContainerHigh = Color(0xFFF2E6E3),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = CoralDark,
    onPrimary = Color(0xFF5F1100),
    primaryContainer = Color(0xFF85230C),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFB8C3FF),
    onSecondary = Color(0xFF26315F),
    secondaryContainer = Color(0xFF3D4877),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFF80D4D5),
    tertiaryContainer = Color(0xFF004F51),
    background = Color(0xFF12131C),
    onBackground = Color(0xFFE6E1F0),
    surface = Color(0xFF12131C),
    onSurface = Color(0xFFE6E1F0),
    surfaceVariant = Color(0xFF2B2C3A),
    onSurfaceVariant = Color(0xFFC9C4D6),
    surfaceContainer = Color(0xFF1C1D29),
    surfaceContainerHigh = Color(0xFF242533),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val base = Typography()

private val RytmTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Duże cyfry odliczania – czytelne z odległości. */
val CountdownStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 88.sp,
    lineHeight = 92.sp,
    letterSpacing = (-2).sp,
)

val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium)

@Composable
fun RytmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = RytmTypography,
        content = content,
    )
}

val BrandCoral = Coral
