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
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Tag
import com.lifeline.app.ui.theme.LifelineTheme
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { LifelineTheme { MainScreen() } }
    }
}

private enum class Tab { DASHBOARD, CALENDAR }

private data class ManualLog(val date: LocalDate, val hour: Int)

fun loggedToast(context: android.content.Context, tag: Tag) {
    Toast.makeText(context, if (tag == Tag.PRODUCTIVITY) "LOGGED. KEEP PUSHING." else "LOGGED. EARN THE NEXT ONE.", Toast.LENGTH_SHORT).show()
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val pending by Prefs.get(context).pending.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var dashDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var calMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var manual by remember { mutableStateOf<ManualLog?>(null) }
    var photo by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // An unanswered alarm always takes over the screen when the app is opened.
    LifecycleResumeEffect(pending) {
        if (pending != null) context.startActivity(Intent(context, AlarmActivity::class.java))
        onPauseOrDispose { }
    }

    fun openManual(date: LocalDate, hour: Int?) {
        val now = LocalDateTime.now()
        if (date > now.toLocalDate()) return
        manual = ManualLog(date, hour ?: if (date == now.toLocalDate()) now.hour else Prefs.get(context).settings.value.startHour)
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
                contentPadding = padding,
            )
            Tab.CALENDAR -> CalendarScreen(
                month = YearMonth.parse(calMonth),
                onMonthChange = { calMonth = it.toString() },
                onOpenDay = { dashDate = it.toString(); tab = Tab.DASHBOARD },
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
                        onDone = { loggedToast(context, it); manual = null },
                        onCancel = { manual = null },
                    )
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
                val now = LocalDateTime.now()
                AlarmScheduler.raise(context, now.toLocalDate().toString(), now.hour)
                context.startActivity(Intent(context, AlarmActivity::class.java))
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
        if (next != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("NEXT CHECK-IN", color = Lx.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                Text("%02d:00".format(next.hour), color = Lx.Text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
