package com.lifeline.app.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.lifeline.app.alarm.AlarmNotifier
import com.lifeline.app.data.AlarmStyle
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.PendingAlarm
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Quotes
import com.lifeline.app.ui.theme.LifelineTheme
import com.lifeline.app.ui.theme.Lx
import java.time.LocalDate

private sealed interface Stage {
    data object CheckIn : Stage
    data class Celebrate(val reward: Reward, val next: Stage?) : Stage
    data class Plan(val date: LocalDate) : Stage
    data class Morning(val quote: String, val planned: Int, val alarms: Int) : Stage
    data class Night(val date: LocalDate) : Stage
}

/**
 * Full-screen alarm. Back is blocked until the check-in is submitted (Home leaves it ringing). After the
 * check-in it celebrates, then — if needed — asks for the morning plan (early hours) or the night review
 * (last alarm of the window), then closes.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge()

        val prefs = Prefs.get(this)
        val initial = prefs.pending.value
        if (initial == null) {
            finish()
            return
        }
        setContent {
            LifelineTheme {
                val pending by prefs.pending.collectAsState()
                var alarm by remember { mutableStateOf<PendingAlarm>(initial) }
                var stage by remember { mutableStateOf<Stage>(Stage.CheckIn) }
                // A newer hour can fire while this one is still unanswered: switch to it.
                LaunchedEffect(pending) { pending?.let { if (stage == Stage.CheckIn) alarm = it } }
                val strict = prefs.settings.collectAsState().value.alarmStyle == AlarmStyle.ALARM
                BackHandler(enabled = strict && stage == Stage.CheckIn) { /* no escape without proof */ }

                Box(Modifier.fillMaxSize().background(Lx.Bg)) {
                    when (val s = stage) {
                        Stage.CheckIn -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            key(alarm.date, alarm.hour) {
                                CheckInForm(
                                    mode = CheckInMode.ALARM,
                                    date = LocalDate.parse(alarm.date),
                                    initialHour = alarm.hour,
                                    line = alarm.line,
                                    onDone = { reward -> lifecycleScope.launch { stage = Stage.Celebrate(reward, nextStage(alarm)) } },
                                    onMute = { AlarmNotifier.show(this@AlarmActivity, muted = true) },
                                    // Notification style: the check-in can wait; the hour stays open until it's logged.
                                    onCancel = if (strict) null else ({ finish() }),
                                )
                            }
                        }
                        is Stage.Celebrate -> RewardOverlay(s.reward) { if (s.next == null) finish() else stage = s.next }
                        is Stage.Plan -> PlanScreen(
                            date = s.date,
                            onLocked = { n, _, _ -> stage = Stage.Morning(Quotes.pick(prefs, "morning", Quotes.morning), n, remainingAlarms(prefs.settings.value)) },
                            onSkip = { finish() },
                        )
                        is Stage.Morning -> MorningQuote(s.quote, s.planned, s.alarms) { finish() }
                        is Stage.Night -> NightReview(s.date, onDone = { finish() }, onLater = { finish() })
                    }
                }
            }
        }
    }

    /** After the reward: plan the day if it's early and there's no plan yet, or review it at the last alarm. */
    private suspend fun nextStage(alarm: PendingAlarm): Stage? {
        val starts = Prefs.get(this).settings.value.slots().map { it.start }
        val date = LocalDate.parse(alarm.date)
        val dao = AppDatabase.get(this).entries()
        return when {
            starts.indexOf(alarm.hour) in 0..2 && dao.plansOn(alarm.date).isEmpty() -> Stage.Plan(date)
            alarm.hour == starts.lastOrNull() && dao.reviewOn(alarm.date) == null -> Stage.Night(date)
            else -> null
        }
    }
}
