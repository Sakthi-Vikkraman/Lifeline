package com.lifeline.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.lifeline.app.data.DaySummary
import com.lifeline.app.data.Repo
import com.lifeline.app.data.Review
import com.lifeline.app.data.Stats
import com.lifeline.app.data.WeekReport
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The two-minute night review: one win, one fail, one fix for tomorrow (shown on tomorrow's plan). */
@Composable
fun NightReview(date: LocalDate, onDone: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf<DaySummary?>(null) }
    var win by remember { mutableStateOf("") }
    var fail by remember { mutableStateOf("") }
    var fix by remember { mutableStateOf("") }
    LaunchedEffect(date) {
        day = Repo.day(context, date)
        AppDatabase.get(context).entries().reviewOn(date.toString())?.let { win = it.win; fail = it.fail; fix = it.fix }
    }

    Column(
        Modifier.fillMaxSize().background(Lx.Bg).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("NIGHT REVIEW · 2 MINUTES", style = Kicker.copy(color = Lx.Volt, fontSize = 12.sp))
        Text("HOW DID TODAY GO?", style = display(44.sp))
        day?.let { d ->
            Text(
                "${d.productive} H PRODUCTIVE · ${d.lost} H LOST" + (d.planScore?.let { " · PLAN KEPT $it%" } ?: ""),
                style = Kicker.copy(color = if (d.won) Lx.Volt else Lx.Blaze, fontSize = 12.sp),
            )
        }
        ReviewField("ONE WIN", "What went right?", win) { win = it }
        ReviewField("ONE FAIL", "Where did the day slip?", fail) { fail = it }
        ReviewField("ONE FIX FOR TOMORROW", "What will you do differently?", fix) { fix = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LxButton("LATER", ghost = true, onClick = onLater)
            LxButton("LOCK IT IN", modifier = Modifier.weight(1f), enabled = win.isNotBlank() || fail.isNotBlank() || fix.isNotBlank()) {
                scope.launch {
                    AppDatabase.get(context).entries().saveReview(Review(date.toString(), win.trim(), fail.trim(), fix.trim(), System.currentTimeMillis()))
                    CoachNotifier.cancel(context, CoachNotifier.REVIEW)
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun ReviewField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Kicker)
        OutlinedTextField(
            value = value, onValueChange = { onChange(it.take(200)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = Lx.Muted) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lx.Volt, unfocusedBorderColor = Lx.Line, cursorColor = Lx.Volt,
                focusedContainerColor = Lx.Surface, unfocusedContainerColor = Lx.Surface,
            ),
        )
    }
}

/** Shows the day's review on the dashboard. */
@Composable
fun ReviewCard(review: Review) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lx.Surface).border(1.dp, Lx.Line, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("NIGHT REVIEW", style = Kicker)
        if (review.win.isNotBlank()) Text("WIN  ·  ${review.win}", color = Lx.Volt, fontSize = 14.sp)
        if (review.fail.isNotBlank()) Text("FAIL  ·  ${review.fail}", color = Lx.Blaze, fontSize = 14.sp)
        if (review.fix.isNotBlank()) Text("FIX  ·  ${review.fix}", color = Lx.Text, fontSize = 14.sp)
    }
}

private fun gradeColor(grade: String) = when (grade) {
    "A", "B" -> Lx.Volt
    "C" -> Color(0xFFE3A60D)
    "—" -> Lx.Muted
    else -> Lx.Blaze
}

fun shareReport(context: Context, report: WeekReport) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.shareText())
    context.startActivity(Intent.createChooser(send, "Send to your accountability partner"))
}

/** Compact weekly report card for the top of the calendar. */
@Composable
fun ReportCard(report: WeekReport, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lx.Surface).border(1.dp, Lx.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(report.grade, style = display(64.sp, gradeColor(report.grade)), modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("THIS WEEK'S REPORT CARD", style = Kicker)
            Text("${report.productive} H PRODUCTIVE · ${report.lost} H LOST", style = display(20.sp))
            Text(Stats.lostPhrase(report.lost), color = Lx.Blaze, fontSize = 12.sp)
        }
        Text("OPEN →", color = Lx.Volt, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

/** The full weekly report: grade, hours, best/worst day, top time-waster, vs last week, and share. */
@Composable
fun ReportScreen(start: LocalDate, onClose: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<WeekReport?>(null) }
    LaunchedEffect(start) { report = Repo.week(context, start); CoachNotifier.cancel(context, CoachNotifier.REPORT) }
    val r = report ?: return
    val day = DateTimeFormatter.ofPattern("EEEE")
    val fmt = DateTimeFormatter.ofPattern("d MMM")

    Column(
        Modifier.fillMaxSize().background(Lx.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("WEEKLY REPORT CARD · ${r.start.format(fmt).uppercase()} – ${r.end.format(fmt).uppercase()}", style = Kicker.copy(color = Lx.Volt, fontSize = 12.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.grade, style = display(140.sp, gradeColor(r.grade)))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(r.share?.let { "${(it * 100).toInt()}% PRODUCTIVE" } ?: "NOTHING TRACKED YET", style = display(26.sp))
                Text("${r.productive} h productive · ${r.lost} h lost", color = Lx.Muted, fontSize = 14.sp)
                r.planScore?.let { Text("Plan kept $it% of the time", color = Lx.Muted, fontSize = 14.sp) }
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lx.Blaze.copy(alpha = 0.12f)).border(1.dp, Lx.Blaze, RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            Text("HOURS LOST", style = Kicker.copy(color = Lx.Blaze))
            Text("${r.lost} " + if (r.lost == 1) "HR" else "HRS", style = display(48.sp, Lx.Blaze))
            Text("${r.entertainment} h entertainment + ${r.missed} h missed check-ins. ${Stats.lostPhrase(r.lost)}", color = Lx.Text, fontSize = 13.sp)
        }

        WeekBars(r)

        ReportLine("BEST DAY", r.best?.let { "${it.date.format(day)} · ${it.productive} h productive" } ?: "—", Lx.Volt)
        ReportLine("WORST DAY", r.worst?.let { "${it.date.format(day)} · ${it.lost} h lost" } ?: "—", Lx.Blaze)
        ReportLine("TOP TIME-WASTER", r.waster?.let { "${it.first} · ${it.second}×" } ?: "None. Respect.", Lx.Blaze)
        if (r.prevTracked > 0) {
            val d = r.productive - r.prevProductive
            val l = r.lost - r.prevLost
            ReportLine(
                "VS LAST WEEK",
                "${if (d >= 0) "+" else ""}$d h productive · ${if (l >= 0) "+" else ""}$l h lost",
                if (d >= 0) Lx.Volt else Lx.Blaze,
            )
        } else {
            ReportLine("VS LAST WEEK", "No data from last week yet", Lx.Muted)
        }

        Spacer(Modifier.height(4.dp))
        LxButton("SHARE WITH YOUR PARTNER", modifier = Modifier.fillMaxWidth()) { shareReport(context, r) }
        LxButton("CLOSE", ghost = true, modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

@Composable
private fun ReportLine(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lx.Surface).border(1.dp, Lx.Line, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Kicker, modifier = Modifier.width(132.dp))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

/** Seven stacked bars: productive (volt) over lost (blaze), one per day. */
@Composable
private fun WeekBars(r: WeekReport) {
    val max = maxOf(r.days.maxOfOrNull { it.tracked } ?: 0, 1)
    Row(
        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(Lx.Surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        r.days.forEach { d ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val unit = maxHeight / max
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        if (d.lost > 0) Box(Modifier.fillMaxWidth().height(unit * d.lost).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Lx.Blaze))
                        if (d.productive > 0) Box(Modifier.fillMaxWidth().height(unit * d.productive).background(Lx.Volt))
                        if (d.tracked == 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Lx.Line))
                    }
                }
                Text(d.date.format(DateTimeFormatter.ofPattern("EEEEE")), style = Kicker, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
