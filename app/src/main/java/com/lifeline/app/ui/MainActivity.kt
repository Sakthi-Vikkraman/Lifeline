package com.lifeline.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import com.lifeline.app.R
import com.lifeline.app.alarm.AlarmScheduler
import com.lifeline.app.data.AlarmStyle
import com.lifeline.app.data.Prefs
import com.lifeline.app.ui.theme.LifelineTheme
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import com.lifeline.app.Actions
import com.lifeline.app.data.Quotes
import com.lifeline.app.data.WeekReport
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Set from widget buttons and coach notifications; MainScreen opens the matching screen and clears it. */
    private val request = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) request.value = intent?.action?.takeIf { it in SCREEN_ACTIONS }
        setContent { LifelineTheme { MainScreen(request) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.action?.takeIf { it in SCREEN_ACTIONS }?.let { request.value = it }
    }
}

private val SCREEN_ACTIONS = setOf(Actions.LOG, Actions.PLAN, Actions.REVIEW, Actions.REPORT)

private enum class Tab { DASHBOARD, CALENDAR }

private data class ManualLog(val date: LocalDate, val hour: Int)

/** Full-screen flows layered over the tabs. */
private sealed interface Overlay {
    data class MorningPlan(val date: LocalDate) : Overlay
    data class MorningQuoteCard(val quote: String, val planned: Int, val alarms: Int) : Overlay
    data class Night(val date: LocalDate) : Overlay
    data class Report(val start: LocalDate) : Overlay
    data class Celebrate(val reward: Reward) : Overlay
}

/** Alarms still to come today, for the morning quote. */
fun remainingAlarms(settings: com.lifeline.app.data.Settings, now: LocalDateTime = LocalDateTime.now()) =
    settings.slots().count { now.toLocalDate().atStartOfDay().plusHours(settings.alarmHour(it).toLong()) > now }

@Composable
private fun MainScreen(request: MutableState<String?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs.get(context) }
    val pending by prefs.pending.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var dashDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var calMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var manual by remember { mutableStateOf<ManualLog?>(null) }
    var photo by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    // An unanswered alarm always takes over the screen when the app is opened (full-screen alarm style only).
    LifecycleResumeEffect(pending) {
        if (pending != null && prefs.settings.value.alarmStyle == AlarmStyle.ALARM) context.startActivity(Intent(context, AlarmActivity::class.java))
        onPauseOrDispose { }
    }

    fun openManual(date: LocalDate, hour: Int?) {
        val now = LocalDateTime.now()
        if (date > now.toLocalDate()) return
        val settings = prefs.settings.value
        manual = ManualLog(date, hour ?: settings.logHour(if (date == now.toLocalDate()) now.hour else settings.dayStart))
    }

    LaunchedEffect(request.value) {
        val today = LocalDate.now()
        when (request.value) {
            Actions.LOG -> { tab = Tab.DASHBOARD; dashDate = today.toString(); openManual(today, null) }
            Actions.PLAN -> overlay = Overlay.MorningPlan(today)
            Actions.REVIEW -> overlay = Overlay.Night(today)
            Actions.REPORT -> overlay = Overlay.Report(WeekReport.weekStart(today))
        }
        request.value = null
    }

    Scaffold(
        containerColor = Lx.Bg,
        topBar = { TopBar { showSettings = true } },
        bottomBar = { TabBar(tab) { tab = it } },
        floatingActionButton = {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Lx.Volt)
                    .clickable { openManual(if (tab == Tab.DASHBOARD) LocalDate.parse(dashDate) else LocalDate.now(), null) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, "Log work", tint = Lx.Ink, modifier = Modifier.size(30.dp)) }
        },
    ) { padding ->
        when (tab) {
            Tab.DASHBOARD -> DashboardScreen(
                date = LocalDate.parse(dashDate),
                onDateChange = { dashDate = it.toString() },
                onLog = { d, h -> openManual(d, h) },
                onPhoto = { photo = it },
                onPlan = { overlay = Overlay.MorningPlan(it) },
                onReview = { overlay = Overlay.Night(it) },
                onReport = { overlay = Overlay.Report(it) },
                contentPadding = padding,
            )
            Tab.CALENDAR -> CalendarScreen(
                month = YearMonth.parse(calMonth),
                onMonthChange = { calMonth = it.toString() },
                onOpenDay = { dashDate = it.toString(); tab = Tab.DASHBOARD },
                onReport = { overlay = Overlay.Report(WeekReport.weekStart(LocalDate.now())) },
                contentPadding = padding,
            )
        }
    }

    manual?.let { m ->
        Dialog(onDismissRequest = { manual = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(Lx.Bg.copy(alpha = 0.6f)).statusBarsPadding().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Lx.Line, RoundedCornerShape(20.dp))) {
                    CheckInForm(
                        mode = CheckInMode.MANUAL, date = m.date, initialHour = m.hour,
                        onDone = { manual = null; overlay = Overlay.Celebrate(it) },
                        onCancel = { manual = null },
                    )
                }
            }
        }
    }

    overlay?.let { o ->
        Dialog(onDismissRequest = { overlay = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize()) {
                when (o) {
                    is Overlay.MorningPlan -> PlanScreen(
                        date = o.date,
                        onLocked = { n, edited, day ->
                            // First plan of the day gets the morning quote; edits and plans ahead just close.
                            if (edited || day != LocalDate.now()) {
                                overlay = null
                            } else {
                                overlay = Overlay.MorningQuoteCard(Quotes.pick(prefs, "morning", Quotes.morning), n, remainingAlarms(prefs.settings.value))
                            }
                        },
                        onSkip = { overlay = null },
                    )
                    is Overlay.MorningQuoteCard -> MorningQuote(o.quote, o.planned, o.alarms) { overlay = null }
                    is Overlay.Night -> NightReview(
                        date = o.date,
                        onDone = { overlay = null; Toast.makeText(context, "LOCKED IN. TOMORROW WE GO AGAIN.", Toast.LENGTH_SHORT).show() },
                        onLater = { overlay = null },
                    )
                    is Overlay.Report -> ReportScreen(o.start) { overlay = null }
                    is Overlay.Celebrate -> RewardOverlay(o.reward) { overlay = null }
                }
            }
        }
    }

    photo?.let { path ->
        Dialog(onDismissRequest = { photo = null }) {
            AsyncImage(
                model = File(path), contentDescription = "Check-in photo", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { photo = null },
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onTestAlarm = {
                showSettings = false
                scope.launch {
                    val now = LocalDateTime.now()
                    val hour = prefs.settings.value.logHour(now.hour)
                    AlarmScheduler.raise(context, now.toLocalDate().toString(), hour, AlarmScheduler.coachLine(context, now.toLocalDate(), hour))
                    context.startActivity(Intent(context, AlarmActivity::class.java))
                }
            },
        )
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    val settings by Prefs.get(context).settings.collectAsState()
    val next = remember(now, settings) { AlarmScheduler.nextTrigger(context, now) }
    Row(
        Modifier.fillMaxWidth().background(Lx.Bg).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_notification), null, tint = Lx.Volt, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("LIFELINE", style = display(30.sp, spacing = 0.06.em))
        Spacer(Modifier.weight(1f))
        val paused = settings.pauseAt(System.currentTimeMillis()) != null
        if (next != null || paused) {
            Column(horizontalAlignment = Alignment.End) {
                Text(if (paused) "ALARMS OFF" else "NEXT CHECK-IN", color = if (paused) Lx.Blaze else Lx.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(
                    next?.let { if (paused) it.format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm")).uppercase() else "%02d:00".format(it.hour) } ?: "TAP SETUP",
                    color = Lx.Text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        SquareIconButton(Icons.Outlined.Tune, "Setup", onClick = onSettings)
    }
}

@Composable
private fun TabBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Lx.Bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Lx.Line))
        Row(Modifier.navigationBarsPadding().height(64.dp)) {
            TabItem("DASHBOARD", Icons.Outlined.ViewTimeline, tab == Tab.DASHBOARD, Modifier.weight(1f)) { onSelect(Tab.DASHBOARD) }
            TabItem("CALENDAR", Icons.Outlined.CalendarMonth, tab == Tab.CALENDAR, Modifier.weight(1f)) { onSelect(Tab.CALENDAR) }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) Lx.Volt else Lx.Muted
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color)
        Text(label, style = display(16.sp, color, 0.12.em))
    }
}
