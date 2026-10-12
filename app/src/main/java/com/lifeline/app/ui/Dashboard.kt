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
import com.lifeline.app.data.DayCount
import com.lifeline.app.data.Entry
import com.lifeline.app.data.Photos
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Tag
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.color
import com.lifeline.app.ui.theme.display
import com.lifeline.app.ui.theme.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HEADER_ITEMS = 3 // day header, stats, split bar

private enum class HourState { PAST, MISSED, NOW, FUTURE }

/** Ticks once a minute so NOW / MISSED markers roll over without a restart. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    return now
}

/** Consecutive days, ending today (or yesterday if today has no logs yet), where productivity beat entertainment. */
fun winStreak(counts: List<DayCount>, today: LocalDate): Int {
    val byDay = counts.associateBy { it.date }
    var d = if (byDay.containsKey(today.toString())) today else today.minusDays(1)
    var streak = 0
    while (true) {
        val c = byDay[d.toString()] ?: break
        if (c.productive <= c.entertainment) break
        streak++
        d = d.minusDays(1)
    }
    return streak
}

@Composable
fun DashboardScreen(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onLog: (LocalDate, Int) -> Unit,
    onPhoto: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).entries() }
    val settings by Prefs.get(context).settings.collectAsState()
    val now = rememberNow()
    val today = now.toLocalDate()
    val entries by remember(date) { dao.observeDay(date.toString()) }.collectAsState(initial = emptyList())
    val history by remember(today) { dao.observeDayCounts(today.minusDays(366).toString(), today.toString()) }.collectAsState(initial = emptyList())

    val byHour = entries.groupBy { it.hour }
    val hours = (settings.activeHours() + byHour.keys).distinct().sortedBy { settings.dayOrder(it) }
    val productive = entries.count { it.tag == Tag.PRODUCTIVITY }
    val entertainment = entries.size - productive

    // Open on the current hour instead of the top of the day.
    val listState = rememberLazyListState()
    val nowIndex = if (date == today) hours.indexOf(now.hour) else -1
    LaunchedEffect(date, nowIndex >= 0) {
        if (nowIndex >= 0) listState.scrollToItem(maxOf(nowIndex + HEADER_ITEMS - 2, 0))
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DayHeader(date, today, onDateChange)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Logs", "${entries.size}", Modifier.weight(1f))
                StatTile("Productive", "$productive", Modifier.weight(1f), Lx.Volt)
                StatTile("Entertain", "$entertainment", Modifier.weight(1f), Lx.Blaze)
                StatTile("Streak", "${winStreak(history, today)}", Modifier.weight(1f))
            }
        }
        item {
            SplitBar(productive, entertainment)
        }
        items(hours, key = { it }) { hour ->
            val list = byHour[hour].orEmpty()
            val state = when {
                date < today || (date == today && hour < now.hour) -> if (list.isEmpty() && settings.inWindow(hour)) HourState.MISSED else HourState.PAST
                date == today && hour == now.hour -> HourState.NOW
                else -> HourState.FUTURE
            }
            HourRow(hour, state, list, onLog = { onLog(date, hour) }, onPhoto = onPhoto)
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

@Composable
private fun SplitBar(productive: Int, entertainment: Int) {
    val total = productive + entertainment
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Lx.Surface2),
    ) {
        if (total > 0) {
            if (productive > 0) Box(Modifier.weight(productive.toFloat()).fillMaxHeight().background(Lx.Volt))
            if (entertainment > 0) Box(Modifier.weight(entertainment.toFloat()).fillMaxHeight().background(Lx.Blaze))
        }
    }
}

@Composable
private fun HourRow(hour: Int, state: HourState, entries: List<Entry>, onLog: () -> Unit, onPhoto: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val border = when (state) {
        HourState.NOW -> Lx.Volt
        HourState.MISSED -> Lx.Danger.copy(alpha = 0.5f)
        else -> Lx.Line
    }
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (state == HourState.FUTURE && entries.isEmpty()) 0.4f else 1f)
            .clip(shape)
            .background(if (state == HourState.FUTURE) Color.Transparent else Lx.Surface)
            .border(if (state == HourState.NOW) 2.dp else 1.dp, border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.width(62.dp)) {
            Text("%02d:00".format(hour), style = display(24.sp, if (state == HourState.MISSED) Lx.Danger else Lx.Text))
            if (state == HourState.NOW) {
                Text(
                    "NOW",
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Lx.Volt).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = Lx.Ink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { EntryCard(it, onPhoto) }
            val link = when (state) {
                HourState.NOW -> if (entries.isEmpty()) "LOG THIS HOUR →" else "+ ADD MORE"
                HourState.MISSED -> "MISSED — LOG IT →"
                HourState.PAST -> "+ ADD"
                HourState.FUTURE -> null
            }
            if (link != null) {
                Text(
                    link,
                    modifier = Modifier.clickable(onClick = onLog).padding(vertical = 8.dp),
                    color = if (state == HourState.MISSED) Lx.Danger else Lx.Volt,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.2.sp,
                )
            } else if (entries.isEmpty()) {
                Text("—", color = Lx.Muted, modifier = Modifier.padding(vertical = 8.dp))
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
                Text(
                    entry.tag.label(),
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(entry.tag.color()).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = Lx.Ink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                )
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
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = if (armed) Lx.Danger else Lx.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}
