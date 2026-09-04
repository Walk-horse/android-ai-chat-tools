package com.ht.apigent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 豆包风格：明亮干净的蓝青渐变主色 + 更大圆角
private val LightColors = lightColorScheme(
    primary = Color(0xFF4E6EF2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6ECFF),
    onPrimaryContainer = Color(0xFF1A2B66),
    secondary = Color(0xFF21C0B3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F5F1),
    onSecondaryContainer = Color(0xFF05403B),
    tertiary = Color(0xFF7C5CFF),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF5A6473),
    outlineVariant = Color(0xFFE2E6EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0E1B47),
    primaryContainer = Color(0xFF2A3A78),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF5FE0D2),
    onSecondary = Color(0xFF00332E),
    tertiary = Color(0xFFB9A6FF),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161A20),
    surfaceVariant = Color(0xFF232A33),
    onSurfaceVariant = Color(0xFFA7B0BF),
    outlineVariant = Color(0xFF2C333D),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ApiAgentTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
