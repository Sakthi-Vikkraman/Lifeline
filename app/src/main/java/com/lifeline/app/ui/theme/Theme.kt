package com.lifeline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifeline.app.R
import com.lifeline.app.data.Tag

object Lx {
    val Bg = Color(0xFF0B0B0C)
    val Surface = Color(0xFF151517)
    val Surface2 = Color(0xFF1D1D20)
    val Line = Color(0xFF2A2A2F)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFF8B8B93)
    val Volt = Color(0xFFC8FF00)   // productivity
    val Blaze = Color(0xFFFF4D1A)  // entertainment
    val Danger = Color(0xFFFF3B3B)
    val Ink = Color(0xFF0B0B0C)
}

val Bebas = FontFamily(Font(R.font.bebas_neue))

fun display(size: TextUnit, color: Color = Lx.Text, spacing: TextUnit = 0.04.em) =
    TextStyle(fontFamily = Bebas, fontSize = size, lineHeight = size * 1.05f, color = color, letterSpacing = spacing)

val Kicker = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.16.em, color = Lx.Muted)

fun Tag.color() = if (this == Tag.PRODUCTIVITY) Lx.Volt else Lx.Blaze
fun Tag.label() = if (this == Tag.PRODUCTIVITY) "PRODUCTIVITY" else "ENTERTAINMENT"

/**
 * Calendar day color: hue slides blaze → volt with the productive share of tracked hours (missed hours
 * count as lost); strength grows with how many hours were logged.
 */
fun dayColor(share: Float, loggedHours: Int, fullDay: Int): Color {
    val mix = lerp(Lx.Blaze, Lx.Volt, share)
    val alpha = 0.22f + 0.78f * minOf(loggedHours.toFloat() / maxOf(fullDay, 1), 1f)
    return mix.copy(alpha = alpha)
}

@Composable
fun LifelineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lx.Volt, onPrimary = Lx.Ink,
            secondary = Lx.Blaze, onSecondary = Lx.Ink,
            background = Lx.Bg, onBackground = Lx.Text,
            surface = Lx.Surface, onSurface = Lx.Text,
            surfaceVariant = Lx.Surface2, onSurfaceVariant = Lx.Muted,
            outline = Lx.Line, error = Lx.Danger,
        ),
        content = content,
    )
}
