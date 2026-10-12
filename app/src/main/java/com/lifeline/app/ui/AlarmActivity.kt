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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.lifeline.app.alarm.AlarmNotifier
import com.lifeline.app.data.Prefs
import com.lifeline.app.ui.theme.LifelineTheme
import com.lifeline.app.ui.theme.Lx
import java.time.LocalDate

/** Full-screen alarm. Back is blocked; the only way out is submitting a check-in (or Home, which leaves it ringing). */
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
        if (prefs.pending.value == null) {
            finish()
            return
        }
        setContent {
            LifelineTheme {
                val pending by prefs.pending.collectAsState()
                BackHandler { /* no escape without proof */ }
                val p = pending
                if (p == null) {
                    finish()
                } else {
                    Box(Modifier.fillMaxSize().background(Lx.Bg).safeDrawingPadding()) {
                        key(p.date, p.hour) { CheckInForm(
                            mode = CheckInMode.ALARM,
                            date = LocalDate.parse(p.date),
                            initialHour = p.hour,
                            onDone = { tag -> loggedToast(this@AlarmActivity, tag); finish() },
                            onMute = { AlarmNotifier.show(this@AlarmActivity, muted = true) },
                        ) }
                    }
                }
            }
        }
    }
}
