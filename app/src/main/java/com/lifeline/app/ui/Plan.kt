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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val PRESETS = listOf("DEEP WORK", "GYM", "STUDY", "WORK", "CLASS", "READ", "SIDE PROJECT", "MEALS", "COMMUTE", "FAMILY", "REST")

/** Copies a day's plan to later days of the same month. */
private enum class Repeat { ONCE, DAILY, WEEKDAYS, WEEKLY }

private fun Repeat.label(day: LocalDate) = when (this) {
    Repeat.ONCE -> "JUST THIS DAY"
    Repeat.DAILY -> "EVERY DAY"
    Repeat.WEEKDAYS -> "WEEKDAYS"
    Repeat.WEEKLY -> "EVERY " + day.format(DateTimeFormatter.ofPattern("EEE")).uppercase()
}

/** The later days of [day]'s month that a [repeat] copies the plan to. */
private fun copyTargets(day: LocalDate, repeat: Repeat): List<LocalDate> =
    generateSequence(day.plusDays(1)) { it.plusDays(1) }.takeWhile { it.month == day.month }.filter {
        when (repeat) {
            Repeat.ONCE -> false
            Repeat.DAILY -> true
            Repeat.WEEKDAYS -> it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY
            Repeat.WEEKLY -> it.dayOfWeek == day.dayOfWeek
        }
    }.toList()

/** How far ahead a plan can be made. */
private const val PLAN_AHEAD_DAYS = 62L

/**
 * The day plan, for today or any day up to two months ahead. Pick a goal (the "brush"), then tap hours (or
 * sessions) to paint it on; tap a painted one again with the same brush to clear it. Hours already over
 * can't be re-planned. The plan can be copied to the rest of the month: every day, weekdays, or the same
 * weekday. [onLocked] receives the number of planned slots ahead, whether this was an edit, and the day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(date: LocalDate, onLocked: (planned: Int, edited: Boolean, day: LocalDate) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).entries() }
    val settings = Prefs.get(context).settings.value
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    var day by remember { mutableStateOf(date) }
    val plan = remember { mutableStateMapOf<Int, String>() }
    var existed by remember { mutableStateOf(false) }
    var brush by remember { mutableStateOf(PRESETS.first()) }
    var custom by remember { mutableStateOf("") }
    var fix by remember { mutableStateOf<String?>(null) }
    var repeat by remember { mutableStateOf(Repeat.ONCE) }

    LaunchedEffect(day) {
        val saved = dao.plansOn(day.toString())
        plan.clear()
        saved.forEach { plan[it.hour] = it.goal }
        existed = saved.isNotEmpty()
        fix = dao.reviewOn(day.minusDays(1).toString())?.fix?.takeIf { it.isNotBlank() }
    }

    // One row per hour, or per session; a plan is filed under the slot's start hour.
    val slots = settings.slots().filter { day > today || day.atStartOfDay().plusHours(it.end.toLong()) > now }
    val open = plan.keys.count { h -> slots.any { it.start == h } }
    val targets = remember(day, repeat) { copyTargets(day, repeat) }

    Column(Modifier.fillMaxSize().background(Lx.Bg).safeDrawingPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", enabled = day > today, size = 34.dp) { day = day.minusDays(1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (existed) "EDIT PLAN" else if (day == today) "MORNING PLAN" else "PLAN AHEAD", style = Kicker.copy(color = Lx.Volt, fontSize = 12.sp))
                    Text(
                        when (day) {
                            today -> "TODAY"
                            today.plusDays(1) -> "TOMORROW"
                            else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
                        },
                        style = display(22.sp),
                    )
                }
                SquareIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", enabled = day < today.plusDays(PLAN_AHEAD_DAYS), size = 34.dp) { day = day.plusDays(1) }
            }
            Text(
                if (day == today) "GIVE EVERY HOUR A JOB" else "WIN ${day.format(DateTimeFormatter.ofPattern("EEEE")).uppercase()} IN ADVANCE",
                style = display(36.sp),
            )
            fix?.let {
                Text(
                    (if (day == today) "YESTERDAY'S FIX: " else "LAST REVIEW'S FIX: ") + it,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Lx.Surface).border(1.dp, Lx.Blaze, RoundedCornerShape(10.dp)).padding(10.dp),
                    color = Lx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (settings.sessionMode) "Pick a goal, then tap the sessions it owns." else "Pick a goal, then tap the hours it owns.",
                    color = Lx.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                if (open > 0) {
                    Text(
                        "CLEAR",
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { slots.forEach { plan.remove(it.start) } }.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = Kicker.copy(color = Lx.Blaze),
                    )
                }
            }
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
            items(slots, key = { it.start }) { slot ->
                val hour = slot.start
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
                    Text(
                        if (settings.sessionMode) "%02d–%02d".format(slot.start, slot.end) else "%02d:00".format(hour),
                        style = display(22.sp), modifier = Modifier.width(76.dp),
                    )
                    when {
                        goal != null -> Text(goal, style = display(22.sp, Lx.Volt))
                        // An unplanned session falls back to its name.
                        slot.label.isNotBlank() -> Text("${slot.label} · tap to change", color = Lx.Muted, fontSize = 13.sp)
                        else -> Text("tap to assign", color = Lx.Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("REPEAT UNTIL END OF ${day.format(DateTimeFormatter.ofPattern("MMMM")).uppercase()}", style = Kicker)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Repeat.entries.toList()) { r ->
                    val on = repeat == r
                    Text(
                        r.label(day),
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) Lx.Text else Lx.Surface)
                            .border(1.dp, if (on) Lx.Text else Lx.Line, RoundedCornerShape(9.dp))
                            .clickable { repeat = r }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = display(16.sp, if (on) Lx.Ink else Lx.Text),
                    )
                }
            }
            if (repeat != Repeat.ONCE) {
                Text(
                    if (targets.isEmpty()) "No days left this month to copy to."
                    else "Copies to ${targets.size} more day${if (targets.size == 1) "" else "s"} and replaces any plan already on them.",
                    color = Lx.Muted, fontSize = 12.sp,
                )
            }
        }

        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LxButton(if (existed) "CANCEL" else "SKIP", ghost = true, onClick = onSkip)
            val unit = if (settings.sessionMode) "" else "H"
            LxButton(
                when {
                    open > 0 && existed -> "SAVE PLAN · $open$unit"
                    open > 0 -> "LOCK IN PLAN · $open$unit"
                    existed -> "CLEAR PLAN"
                    else -> if (settings.sessionMode) "ASSIGN A SESSION" else "ASSIGN AN HOUR"
                },
                modifier = Modifier.weight(1f),
                enabled = open > 0 || existed,
            ) {
                scope.launch {
                    // Hours already over keep their plan: it's what they were judged against.
                    dao.replacePlans(day.toString(), plan.map { (h, g) -> Plan(day.toString(), h, g) })
                    targets.forEach { d -> dao.replacePlans(d.toString(), plan.map { (h, g) -> Plan(d.toString(), h, g) }) }
                    if (day == today) CoachNotifier.cancel(context, CoachNotifier.PLAN)
                    Widgets.refresh(context)
                    val message = when {
                        targets.isNotEmpty() -> "PLAN SET FOR ${targets.size + 1} DAYS."
                        existed -> "PLAN UPDATED."
                        day != today -> "${day.format(DateTimeFormatter.ofPattern("EEEE")).uppercase()} IS LOCKED IN."
                        else -> null
                    }
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    onLocked(open, existed, day)
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
