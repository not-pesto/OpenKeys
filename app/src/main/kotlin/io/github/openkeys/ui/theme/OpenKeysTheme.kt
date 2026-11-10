package io.github.openkeys.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OkColors {
    val Background = Color(0xFF11151C)
    val Card = Color(0xFF202630)
    val Elevated = Color(0xFF29313C)
    val Border = Color(0xFF343C47)
    val Primary = Color(0xFF168FF0)
    val Cyan = Color(0xFF16B9F5)
    val TextPrimary = Color(0xFFF4F5F7)
    val TextSecondary = Color(0xFFA3ACB8)
    val TextMuted = Color(0xFF77818E)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF5B942)
    val Error = Color(0xFFF05252)
}

private val colorScheme = darkColorScheme(
    primary = OkColors.Primary,
    onPrimary = Color.White,
    primaryContainer = OkColors.Primary,
    onPrimaryContainer = Color.White,
    secondary = OkColors.Cyan,
    onSecondary = OkColors.Background,
    secondaryContainer = OkColors.Border,
    onSecondaryContainer = OkColors.TextPrimary,
    tertiary = OkColors.Cyan,
    background = OkColors.Background,
    onBackground = OkColors.TextPrimary,
    surface = OkColors.Background,
    onSurface = OkColors.TextPrimary,
    surfaceVariant = OkColors.Card,
    onSurfaceVariant = OkColors.TextSecondary,
    surfaceContainerLowest = OkColors.Background,
    surfaceContainerLow = OkColors.Card,
    surfaceContainer = OkColors.Elevated,
    surfaceContainerHigh = OkColors.Elevated,
    surfaceContainerHighest = OkColors.Border,
    outline = OkColors.Border,
    outlineVariant = OkColors.Border,
    error = OkColors.Error,
    onError = Color.White,
)

private val base = Typography()
private val typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun OpenKeysTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
}

@Composable
fun okSwitchColors(): SwitchColors = SwitchDefaults.colors(
    uncheckedThumbColor = OkColors.TextSecondary,
    uncheckedTrackColor = OkColors.Elevated,
    uncheckedBorderColor = OkColors.TextMuted,
)
