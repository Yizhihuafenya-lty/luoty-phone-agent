package com.luoty.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 与 DSH 终端风格接近的深色配色 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C93FF),
    onPrimary = Color(0xFF08101F),
    primaryContainer = Color(0xFF1B2A52),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9AA7C7),
    onSecondary = Color(0xFF10151F),
    secondaryContainer = Color(0xFF1A2130),
    onSecondaryContainer = Color(0xFFD6DEF0),
    tertiary = Color(0xFF5FD3C1),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6EAF3),
    surface = Color(0xFF0E121A),
    onSurface = Color(0xFFE6EAF3),
    surfaceVariant = Color(0xFF151B27),
    onSurfaceVariant = Color(0xFF98A4BA),
    surfaceContainer = Color(0xFF121824),
    surfaceContainerHigh = Color(0xFF161D2B),
    outline = Color(0xFF2A3346),
    outlineVariant = Color(0xFF1C2432),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF20090B),
    errorContainer = Color(0xFF3A1418),
    onErrorContainer = Color(0xFFFFD9D9),
    inverseSurface = Color(0xFFE6EAF3),
    inverseOnSurface = Color(0xFF141A24),
    scrim = Color(0xFF000000),
)

object AppColors {
    val UserBubble = Color(0xFF1B2A52)
    val UserBubbleText = Color(0xFFE9EEFF)
    val CodeBg = Color(0xFF080B11)
    val CodeHeader = Color(0xFF10161F)
    val ToolCard = Color(0xFF101622)
    val ToolCardBorder = Color(0xFF232C3D)
    val Reasoning = Color(0xFF94A3B8)
    val Success = Color(0xFF4EC9A8)
    val Danger = Color(0xFFFF7B72)
    val Code = Color(0xFFE3C79B)
    val Link = Color(0xFF8AB4FF)
}

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
)

val Mono = FontFamily.Monospace

@Composable
fun LuoAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
