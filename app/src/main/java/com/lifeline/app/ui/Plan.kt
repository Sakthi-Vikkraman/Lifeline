package com.lifeline.app.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeline.app.alarm.CoachNotifier
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.Plan
import com.lifeline.app.data.Prefs
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import com.lifeline.app.widget.Widgets
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val PRESETS = listOf("DEEP WORK", "GYM", "STUDY", "WORK", "CLASS", "READ", "SIDE PROJECT", "MEALS", "COMMUTE", "FAMILY", "REST")

/**
 * The morning plan. Pick a goal (the "brush"), then tap hours to paint it on; tap a painted hour again
 * with the same brush to clear it. [onLocked] receives the number of planned hours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(date: LocalDate, onLocked: (Int) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).entries() }
    val settings = Prefs.get(context).settings.value
    val plan = remember { mutableStateMapOf<Int, String>() }
    var brush by remember { mutableStateOf(PRESETS.first()) }
    var custom by remember { mutableStateOf("") }
    var yesterdayFix by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(date) {
        dao.plansOn(date.toString()).forEach { plan[it.hour] = it.goal }
        yesterdayFix = dao.reviewOn(date.minusDays(1).toString())?.fix?.takeIf { it.isNotBlank() }
    }

    val now = LocalDateTime.now()
    val hours = settings.activeHours().filter { date > now.toLocalDate() || date.atTime(it, 0).plusHours(1) > now }

    Column(Modifier.fillMaxSize().background(Lx.Bg).safeDrawingPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val day = if (date == now.toLocalDate()) "TODAY" else date.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
            Text("MORNING PLAN · $day", style = Kicker.copy(color = Lx.Volt, fontSize = 12.sp))
            Text("GIVE EVERY HOUR A JOB", style = display(40.sp))
            yesterdayFix?.let {
                Text(
                    "YESTERDAY'S FIX: $it",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Lx.Surface).border(1.dp, Lx.Blaze, RoundedCornerShape(10.dp)).padding(10.dp),
                    color = Lx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Text("Pick a goal, then tap the hours it owns.", color = Lx.Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (PRESETS + listOfNotNull(custom.trim().uppercase().takeIf { it.isNotEmpty() && it !in PRESETS })).forEach { g ->
                    val on = brush == g
                    Text(
                        g,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) Lx.Volt else Lx.Surface)
                            .border(1.dp, if (on) Lx.Volt else Lx.Line, RoundedCornerShape(9.dp))
                            .clickable { brush = g }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = display(17.sp, if (on) Lx.Ink else Lx.Text),
                    )
                }
            }
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(24); if (it.isNotBlank()) brush = it.trim().uppercase() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("+ Custom goal", color = Lx.Muted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lx.Volt, unfocusedBorderColor = Lx.Line, cursorColor = Lx.Volt,
                    focusedContainerColor = Lx.Surface, unfocusedContainerColor = Lx.Surface,
                ),
            )
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(hours, key = { it }) { hour ->
                val goal = plan[hour]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (goal != null) Lx.Volt.copy(alpha = 0.12f) else Lx.Surface)
                        .border(1.dp, if (goal != null) Lx.Volt else Lx.Line, RoundedCornerShape(12.dp))
                        .clickable { if (plan[hour] == brush) plan.remove(hour) else plan[hour] = brush }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02d:00".format(hour), style = display(22.sp), modifier = Modifier.width(70.dp))
                    Text(goal ?: "tap to assign", style = if (goal != null) display(22.sp, Lx.Volt) else androidx.compose.ui.text.TextStyle(color = Lx.Muted, fontSize = 13.sp))
                }
            }
        }

        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LxButton("SKIP", ghost = true, onClick = onSkip)
            LxButton(
                if (plan.isEmpty()) "ASSIGN AN HOUR" else "LOCK IN PLAN · ${plan.size}H",
                modifier = Modifier.weight(1f),
                enabled = plan.isNotEmpty(),
            ) {
                scope.launch {
                    dao.replacePlans(date.toString(), plan.map { (h, g) -> Plan(date.toString(), h, g) })
                    CoachNotifier.cancel(context, CoachNotifier.PLAN)
                    Widgets.refresh(context)
                    onLocked(plan.size)
                }
            }
        }
    }
}

@Composable
internal fun Pill(text: String, color: Color, ink: Color = Lx.Ink) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(color).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, color = ink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
}
