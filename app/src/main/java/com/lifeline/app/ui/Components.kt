package com.lifeline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display

@Composable
fun LxButton(
    label: String,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (ghost) Color.Transparent else if (enabled) Lx.Volt else Lx.Volt.copy(alpha = 0.5f))
            .then(if (ghost) Modifier.border(1.dp, Lx.Line, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Disabled state is drawn with colors, not Modifier.alpha: the alpha layer failed to redraw
        // after returning from the camera, leaving SUBMIT invisible (but tappable).
        Text(label, style = display(22.sp, if (ghost) Lx.Text else Lx.Ink, 0.08.em))
    }
}

@Composable
fun SquareIconButton(icon: ImageVector, description: String, enabled: Boolean = true, size: Dp = 42.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(Lx.Surface)
            .border(1.dp, Lx.Line, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (enabled) Lx.Text else Lx.Text.copy(alpha = 0.3f)) }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = Lx.Text) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Lx.Surface)
            .border(1.dp, Lx.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = Kicker.copy(fontSize = 9.sp, letterSpacing = 0.06.em), maxLines = 1, softWrap = false)
        Text(value, style = display(34.sp, color))
    }
}

