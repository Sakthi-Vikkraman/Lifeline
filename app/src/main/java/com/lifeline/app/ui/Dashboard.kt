package com.lifeline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.DaySummary
import com.lifeline.app.data.Entry
import com.lifeline.app.data.HourKind
import com.lifeline.app.data.HourStat
import com.lifeline.app.data.Photos
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Stats
import com.lifeline.app.data.WeekReport
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.color
import com.lifeline.app.ui.theme.display
import com.lifeline.app.ui.theme.label
import com.lifeline.app.widget.Widgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ticks every 30 s so NOW / MISSED markers roll over without a restart. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    return now
}

private enum class Banner { PLAN, REVIEW, REPORT }

@Composable
fun DashboardScreen(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onLog: (LocalDate, Int) -> Unit,
    onPhoto: (String) -> Unit,
    onPlan: (LocalDate) -> Unit,
    onReview: (LocalDate) -> Unit,
    onReport: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).entries() }
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val now = rememberNow()
    val today = now.toLocalDate()
    val weekStart = WeekReport.weekStart(date)
    val from = minOf(weekStart, today.minusDays(120)).toString()
    val to = maxOf(weekStart.plusDays(6), today).toString()
    val entries by remember(from, to) { dao.observeBetween(from, to) }.collectAsState(initial = emptyList())
    val plans by remember(from, to) { dao.observePlansBetween(from, to) }.collectAsState(initial = emptyList())
    val review by remember(date) { dao.observeReview(date.toString()) }.collectAsState(initial = null)

    val day = remember(entries, plans, settings, now, date) { Stats.summarize(date, entries, plans, settings, now, prefs.trackingSince) }
    val weekLost = remember(entries, plans, settings, now, weekStart) {
        Stats.summarizeRange(weekStart, minOf(weekStart.plusDays(6), today), entries, plans, settings, now, prefs.trackingSince).sumOf { it.lost }
    }
    val streak = remember(entries, plans, settings, now) {
        Stats.winStreak(Stats.summarizeRange(today.minusDays(120), today, entries, plans, settings, now, prefs.trackingSince), today)
    }

    val banners = buildList {
        if (date == today) {
            val last = settings.activeHours().lastOrNull()
            if (plans.none { it.date == date.toString() } && last != null && settings.dayOrder(now.hour) <= settings.dayOrder(last)) add(Banner.PLAN)
            val reviewTime = if (settings.startHour <= settings.endHour) now.hour >= settings.endHour else now.hour == last
            if (reviewTime && review == null) add(Banner.REVIEW)
            if (today.dayOfWeek == DayOfWeek.SUNDAY || today.dayOfWeek == DayOfWeek.MONDAY) add(Banner.REPORT)
        }
    }
    val headerCount = 4 + banners.size + 1 + (if (review != null) 1 else 0)

    // Open on the current hour instead of the top of the day.
    val listState = rememberLazyListState()
    val nowIndex = if (date == today) day.hours.indexOfFirst { it.hour == now.hour } else -1
    LaunchedEffect(date, nowIndex >= 0) {
        if (nowIndex >= 0) listState.scrollToItem(maxOf(nowIndex + headerCount - 2, 0))
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { DayHeader(date, today, onDateChange) }
        item { GoalCard(day.productive, settings.dailyGoal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Productive", "${day.productive}H", Modifier.weight(1f), Lx.Volt)
                StatTile("Lost", "${day.lost}H", Modifier.weight(1f), Lx.Blaze)
                StatTile("Plan kept", day.planScore?.let { "$it%" } ?: "—", Modifier.weight(1f))
                StatTile("Streak", "$streak", Modifier.weight(1f))
            }
        }
        item { SplitBar(day) }
        banners.forEach { b ->
            item {
                when (b) {
                    Banner.PLAN -> ActionBanner("NO PLAN YET", "PLAN TODAY →", Lx.Volt) { onPlan(today) }
                    Banner.REVIEW -> ActionBanner("DAY'S DONE", "NIGHT REVIEW →", Lx.Volt) { onReview(today) }
                    // Sunday: this week's card. Monday: the week that just ended.
                    Banner.REPORT -> ActionBanner("THE WEEK IS GRADED", "REPORT CARD →", Lx.Text) {
                        onReport(WeekReport.weekStart(if (today.dayOfWeek == DayOfWeek.MONDAY) today.minusDays(1) else today))
                    }
                }
            }
        }
        item { LostCard(weekLost, if (weekStart == WeekReport.weekStart(today)) "THIS WEEK" else "WEEK OF ${weekStart.format(DateTimeFormatter.ofPattern("d MMM")).uppercase()}") }
        review?.let { r -> item { ReviewCard(r) } }
        items(day.hours, key = { it.hour }) { h ->
            HourRow(h, onLog = { onLog(date, h.hour) }, onPhoto = onPhoto)
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val rel = when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE")).uppercase()
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SquareIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") { onDateChange(date.minusDays(1)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rel, style = Kicker)
            Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")).uppercase(), style = display(40.sp), textAlign = TextAlign.Center)
        }
        SquareIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", enabled = date < today) { onDateChange(date.plusDays(1)) }
    }
}

/** Daily goal as a rep counter: one pip per productive hour. */
@Composable
private fun GoalCard(productive: Int, goal: Int) {
    val done = productive >= goal
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lx.Surface)
            .border(if (done) 2.dp else 1.dp, if (done) Lx.Volt else Lx.Line, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (done) "DAILY GOAL · CRUSHED" else "DAILY GOAL", style = Kicker.copy(color = if (done) Lx.Volt else Lx.Muted))
                Text(if (done) "ALL REPS DONE" else "${goal - productive} REPS TO GO", style = display(22.sp))
            }
            Text("$productive", style = display(46.sp, Lx.Volt))
            Text(" / $goal", style = display(26.sp, Lx.Muted), modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(goal) { i ->
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(if (i < productive) Lx.Volt else Lx.Surface2))
            }
        }
        if (productive > goal) Text("+${productive - goal} BONUS", style = Kicker.copy(color = Lx.Volt))
    }
}

@Composable
private fun LostCard(lost: Int, label: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lx.Blaze.copy(alpha = 0.10f))
            .border(1.dp, Lx.Blaze.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$label · HOURS LOST", style = Kicker.copy(color = Lx.Blaze))
            Text(Stats.lostPhrase(lost), color = Lx.Text, fontSize = 13.sp)
        }
        Text("$lost", style = display(44.sp, Lx.Blaze))
        Text(if (lost == 1) " HR" else " HRS", style = display(20.sp, Lx.Blaze), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ActionBanner(kicker: String, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kicker, style = Kicker, modifier = Modifier.weight(1f))
        Text(label, style = display(22.sp, color))
    }
}

/** Productive (volt), entertainment (blaze) and missed (red) hours. */
@Composable
private fun SplitBar(day: DaySummary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Lx.Surface2),
    ) {
        if (day.productive > 0) Box(Modifier.weight(day.productive.toFloat()).fillMaxHeight().background(Lx.Volt))
        if (day.entertainment > 0) Box(Modifier.weight(day.entertainment.toFloat()).fillMaxHeight().background(Lx.Blaze))
        if (day.missed > 0) Box(Modifier.weight(day.missed.toFloat()).fillMaxHeight().background(Lx.Danger.copy(alpha = 0.55f)))
    }
}

@Composable
private fun HourRow(h: HourStat, onLog: () -> Unit, onPhoto: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val future = h.kind == HourKind.FUTURE
    val border = when {
        h.current -> Lx.Volt
        h.kind == HourKind.MISSED -> Lx.Danger.copy(alpha = 0.5f)
        else -> Lx.Line
    }
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (future && h.plan == null) 0.4f else if (future) 0.7f else 1f)
            .clip(shape)
            .background(if (future) Color.Transparent else Lx.Surface)
            .border(if (h.current) 2.dp else 1.dp, border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.width(62.dp)) {
            Text("%02d:00".format(h.hour), style = display(24.sp, if (h.kind == HourKind.MISSED) Lx.Danger else Lx.Text))
            if (h.current) Pill("NOW", Lx.Volt)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            h.plan?.let { plan ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PLAN · ", style = Kicker)
                    Text(plan.goal, style = display(18.sp, Lx.Volt), modifier = Modifier.weight(1f))
                    when (h.keptPlan) {
                        true -> Pill("✓ KEPT", Lx.Volt)
                        false -> Pill("✕ OFF PLAN", Lx.Blaze)
                        null -> {}
                    }
                }
            }
            h.entries.forEach { EntryCard(it, onPhoto) }
            val link = when {
                h.current -> if (h.entries.isEmpty()) "LOG THIS HOUR →" else "+ ADD MORE"
                h.kind == HourKind.MISSED -> "MISSED · COUNTS AS LOST — LOG IT →"
                h.kind == HourKind.FUTURE -> null
                else -> "+ ADD"
            }
            if (link != null) {
                Text(
                    link,
                    modifier = Modifier.clickable(onClick = onLog).padding(vertical = 6.dp),
                    color = if (h.kind == HourKind.MISSED) Lx.Danger else Lx.Volt,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.2.sp,
                )
            } else if (h.entries.isEmpty() && h.plan == null) {
                Text("—", color = Lx.Muted, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry, onPhoto: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(2500); armed = false } }
    val time = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).toLocalTime()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Lx.Surface2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(72.dp).background(entry.tag.color()))
        Spacer(Modifier.width(8.dp))
        entry.photoPath?.let { path ->
            AsyncImage(
                model = File(path), contentDescription = entry.text.ifEmpty { "Check-in photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable { onPhoto(path) },
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(entry.tag.label(), entry.tag.color())
                Spacer(Modifier.width(6.dp))
                Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), color = Lx.Muted, fontSize = 11.sp)
            }
            if (entry.text.isNotEmpty()) Text(entry.text, color = Lx.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            if (armed) "DELETE?" else "✕",
            modifier = Modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (armed) Modifier.border(1.dp, Lx.Danger, RoundedCornerShape(8.dp)) else Modifier)
                .clickable {
                    if (!armed) armed = true
                    else scope.launch {
                        AppDatabase.get(context).entries().delete(entry)
                        Photos.delete(entry.photoPath)
                        Widgets.refresh(context)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = if (armed) Lx.Danger else Lx.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}
