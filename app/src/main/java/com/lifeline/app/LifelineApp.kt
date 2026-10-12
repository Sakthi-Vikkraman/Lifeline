package com.lifeline.app

import android.app.Application
import com.lifeline.app.alarm.AlarmNotifier
import com.lifeline.app.alarm.AlarmScheduler

class LifelineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.createChannels(this)
        AlarmScheduler.scheduleNext(this)
    }
}
