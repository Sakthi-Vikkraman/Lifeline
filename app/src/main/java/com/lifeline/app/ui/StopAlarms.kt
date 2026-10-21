package com.lifeline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifeline.app.alarm.AlarmScheduler
import com.lifeline.app.data.Pause
import com.lifeline.app.data.Prefs
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The word to type before alarms switch off: stopping should take a decision, not a slip of the thumb. */
private const val CONFIRM_WORD = "REST"

/** How long to switch off for. [days] counts whole days; null means until turned back on. */
private enum class StopFor(val label: String, val days: Long?) {
    TODAY("REST OF TODAY", 1), THREE("3 DAYS", 3), WEEK("1 WEEK", 7), OFF("UNTIL I TURN THEM ON", null)
}

/** Alarm on/off section of setup: shows the pause if there is one, otherwise the way into [StopAlarmsDialog]. */
@Composable
fun AlarmSwitchSection() {
    val context = LocalContext.current
    val settings by Prefs.get(context).settings.collectAsState()
    var stopping by remember { mutableStateOf(false) }
    val pause = settings.pauseAt(System.currentTimeMillis())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ALARMS", style = Kicker)
        if (pause == null) {
            LxButton("STOP ALARMS", ghost = true, modifier = Modifier.fillMaxWidth()) { stopping = true }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lx.Blaze.copy(alpha = 0.12f))
                    .border(1.dp, Lx.Blaze, RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text("ALARMS OFF", style = display(24.sp, Lx.Blaze))
                Text(backLabel(context, pause), color = Lx.Text, fontSize = 13.sp)
            }
            LxButton("TURN ALARMS BACK ON", modifier = Modifier.fillMaxWidth()) { AlarmScheduler.restart(context) }
        }
    }
    if (stopping) StopAlarmsDialog { stopping = false }
}

private fun backLabel(context: android.content.Context, pause: Pause): String {
    if (pause.forever) return "Until you turn them back on."
    val next = AlarmScheduler.nextTrigger(context) ?: return "Until you turn them back on."
    return "First alarm back: ${next.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"))}."
}

/** Three steps: pick how long, read what it costs, type the word. */
@Composable
private fun StopAlarmsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by Prefs.get(context).settings.collectAsState()
    var choice by remember { mutableStateOf<StopFor?>(null) }
    var typed by remember { mutableStateOf("") }
    val confirmed = typed.trim().equals(CONFIRM_WORD, ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Lx.Surface)
                .border(1.dp, Lx.Blaze, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("STOP ALARMS", style = display(36.sp, Lx.Blaze))

            Text("1 · HOW LONG?", style = Kicker)
            StopFor.entries.forEach { option ->
                val on = choice == option
                Text(
                    option.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Lx.Blaze else Lx.Bg)
                        .border(1.dp, if (on) Lx.Blaze else Lx.Line, RoundedCornerShape(10.dp))
                        .clickable { choice = option }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    style = display(20.sp, if (on) Lx.Ink else Lx.Text),
                )
            }

            Text("2 · KNOW THE DEAL", style = Kicker)
            Text(
                "No hourly alarms, no plan reminder, no coach. Hours while alarms are off count as neither reps nor " +
                    "lost time, and a rest day won't break your streak. If an alarm is ringing now, it stops too.",
                color = Lx.Muted, fontSize = 13.sp,
            )

            Text("3 · TYPE $CONFIRM_WORD TO CONFIRM", style = Kicker)
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(CONFIRM_WORD, color = Lx.Muted) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lx.Blaze, unfocusedBorderColor = Lx.Line, cursorColor = Lx.Blaze,
                    focusedContainerColor = Lx.Bg, unfocusedContainerColor = Lx.Bg,
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LxButton("KEEP GOING", ghost = true, modifier = Modifier.weight(1f), onClick = onDismiss)
                LxButton("STOP", modifier = Modifier.weight(1f), enabled = choice != null && confirmed) {
                    val days = choice?.days
                    val until = if (days == null) Pause.FOREVER else
                        // Alarms come back in time for that morning's plan reminder.
                        LocalDate.now().plusDays(days).atTime(settings.dayStart, 0).minusMinutes(30)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    AlarmScheduler.stop(context, until)
                    onDismiss()
                }
            }
            if (choice == null || !confirmed) {
                Text(
                    if (choice == null) "Pick how long first." else "Type $CONFIRM_WORD to unlock.",
                    color = Lx.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
