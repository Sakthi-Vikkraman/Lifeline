package com.lifeline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.DaySummary
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Stats
import com.lifeline.app.data.WeekReport
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.dayColor
import com.lifeline.app.ui.theme.display
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onReport: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).entries() }
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val now = rememberNow()
    val today = now.toLocalDate()
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val weekStart = WeekReport.weekStart(today)
    // Load the month plus this week and last week, for the report card.
    val from = minOf(first, weekStart.minusDays(7)).toString()
    val to = maxOf(last, today).toString()
    val entries by remember(from, to) { dao.observeBetween(from, to) }.collectAsState(initial = emptyList())
    val plans by remember(from, to) { dao.observePlansBetween(from, to) }.collectAsState(initial = emptyList())

    val days = remember(entries, plans, settings, now, month) {
        Stats.summarizeRange(first, minOf(last, today), entries, plans, settings, now, prefs.trackingSince).associateBy { it.date }
    }
    val report = remember(entries, plans, settings, now) { WeekReport.build(weekStart, entries, plans, settings, now, prefs.trackingSince) }
    val productive = days.values.sumOf { it.productive }
    val lost = days.values.sumOf { it.lost }
    val fullDay = settings.activeHours().size
    val isCurrent = month == YearMonth.from(today)

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
    ) {
        Box(Modifier.padding(top = 10.dp)) { ReportCard(report, onReport) }

        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") { onMonthChange(month.minusMonths(1)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (productive + lost > 0) "$productive H PRODUCTIVE · $lost H LOST" else "NOTHING TRACKED YET", style = Kicker)
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(), style = display(40.sp))
            }
            SquareIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", enabled = !isCurrent) { onMonthChange(month.plusMonths(1)) }
        }

        Row(Modifier.padding(bottom = 6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = Kicker, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        val offset = first.dayOfWeek.value - 1 // Monday-first
        val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f)) {
                        if (day != null) DayCell(day, days[day], fullDay, today) { onOpenDay(day) }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }

        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(Lx.Blaze, Color(0xFFE3A60D), Lx.Volt))),
            )
            Row {
                Text("LOST", color = Lx.Blaze, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Box(Modifier.weight(1f))
                Text("PRODUCTIVE", color = Lx.Volt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Text(
                "Number = works logged that day. Color = productive share of tracked hours, where missed check-ins count as lost. Brighter = more hours logged. Tap a day to open it.",
                color = Lx.Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, summary: DaySummary?, fullDay: Int, today: LocalDate, onClick: () -> Unit) {
    val share = summary?.share
    val color = share?.let { dayColor(it, summary.productive + summary.entertainment, fullDay) }
    val logs = summary?.logs ?: 0
    val future = day > today
    val ink = color != null && color.alpha > 0.55f
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .aspectRatio(1f)
            .alpha(if (future) 0.35f else 1f)
            .then(if (day == today) Modifier.border(2.dp, Lx.Text, shape) else Modifier)
            .padding(if (day == today) 2.dp else 0.dp)
            .clip(shape)
            .background(color ?: Lx.Surface)
            .then(if (color == null) Modifier.border(1.dp, Lx.Line, shape) else Modifier)
            .clickable(enabled = !future, onClick = onClick)
            .semantics {
                contentDescription = summary?.let { "$day: ${it.productive} productive hours, ${it.lost} lost, $logs logs" } ?: "$day"
            }
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${day.dayOfMonth}", style = display(16.sp, if (ink) Lx.Ink else Lx.Text))
        if (logs > 0) Text("$logs", style = display(20.sp, if (ink) Lx.Ink else Lx.Text), modifier = Modifier.align(Alignment.End))
    }
}
