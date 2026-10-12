package com.lifeline.app.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lifeline.app.alarm.AlarmScheduler
import com.lifeline.app.data.Prefs
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import com.lifeline.app.widget.Widgets

private data class Check(val label: String, val ok: Boolean, val fix: (Context) -> Unit)

private fun appSettings(action: String) = { c: Context ->
    c.startActivity(Intent(action, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun permissionChecks(context: Context): List<Check> = buildList {
    add(
        Check("Notifications", NotificationManagerCompat.from(context).areNotificationsEnabled()) { c ->
            c.startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, c.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
    if (Build.VERSION.SDK_INT >= 34) {
        val nm = context.getSystemService(NotificationManager::class.java)
        add(Check("Full-screen alarm", nm.canUseFullScreenIntent(), appSettings(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)))
    }
    if (Build.VERSION.SDK_INT in 31..32) {
        val am = context.getSystemService(AlarmManager::class.java)
        add(Check("Exact alarms", am.canScheduleExactAlarms(), appSettings(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)))
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onTestAlarm: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val checks = remember(refresh) { permissionChecks(context) }

    fun update(s: com.lifeline.app.data.Settings) {
        prefs.updateSettings(s)
        AlarmScheduler.scheduleNext(context)
        Widgets.refresh(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Lx.Surface)
                .border(1.dp, Lx.Line, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SETUP", style = display(36.sp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HourStepper("ALARMS FROM", settings.startHour, Modifier.weight(1f)) { update(settings.copy(startHour = it)) }
                HourStepper("UNTIL", settings.endHour, Modifier.weight(1f)) { update(settings.copy(endHour = it)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DAILY GOAL · PRODUCTIVE HOURS", style = Kicker)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lx.Bg).border(1.dp, Lx.Line, RoundedCornerShape(12.dp)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SquareIconButton(Icons.Filled.Remove, "Fewer", size = 34.dp) { update(settings.copy(dailyGoal = (settings.dailyGoal - 1).coerceAtLeast(1))) }
                    Text("${settings.dailyGoal} REPS", style = display(22.sp), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    SquareIconButton(Icons.Filled.Add, "More", size = 34.dp) { update(settings.copy(dailyGoal = (settings.dailyGoal + 1).coerceAtMost(16))) }
                }
            }

            SettingSwitch("Alarm sound", null, settings.sound) { update(settings.copy(sound = it)) }
            SettingSwitch("Reward sounds", "Level-up sound for productive check-ins", settings.rewardSound) { update(settings.copy(rewardSound = it)) }
            SettingSwitch("Photo proof for productivity", "No typing \"studying\" from bed", settings.photoForProductive) {
                update(settings.copy(photoForProductive = it))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PERMISSIONS", style = Kicker)
                checks.forEach { check ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (check.ok) "✓  " else "✕  ") + check.label,
                            color = if (check.ok) Lx.Volt else Lx.Danger,
                            modifier = Modifier.weight(1f),
                        )
                        if (!check.ok) LxButton("FIX", ghost = true) { check.fix(context) }
                    }
                }
            }

            Text(
                "Alarms ring at the top of every hour in your window and keep ringing until you submit a check-in.",
                color = Lx.Muted, fontSize = 12.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LxButton("TEST ALARM", ghost = true, modifier = Modifier.weight(1f)) { onTestAlarm() }
                LxButton("DONE", modifier = Modifier.weight(1f)) { onDismiss() }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Lx.Text)
            hint?.let { Text(it, color = Lx.Muted, fontSize = 12.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Lx.Volt, checkedThumbColor = Lx.Ink),
        )
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Kicker)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lx.Bg).border(1.dp, Lx.Line, RoundedCornerShape(12.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareIconButton(Icons.Filled.Remove, "Earlier", size = 34.dp) { onChange((hour + 23) % 24) }
            Text("%02d:00".format(hour), style = display(22.sp), maxLines = 1, softWrap = false, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            SquareIconButton(Icons.Filled.Add, "Later", size = 34.dp) { onChange((hour + 1) % 24) }
        }
    }
}
