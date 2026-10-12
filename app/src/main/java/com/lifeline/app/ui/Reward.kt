package com.lifeline.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Quotes
import com.lifeline.app.data.Repo
import com.lifeline.app.data.Tag
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class RewardKind { PRODUCTIVE, GOAL, ENTERTAINMENT }

data class Reward(val kind: RewardKind, val headline: String, val sub: String, val quote: String)

/**
 * Builds the reward for a just-saved check-in, from the day's totals including it. A second productive
 * entry in an hour that was already productive adds a "set", not a rep, because reps count hours.
 */
suspend fun buildReward(context: Context, date: LocalDate, hour: Int, tag: Tag): Reward {
    val prefs = Prefs.get(context)
    val goal = prefs.settings.value.dailyGoal
    val day = Repo.day(context, date)
    val newRep = (day.hours.firstOrNull { it.hour == hour }?.entries?.count { it.tag == Tag.PRODUCTIVITY } ?: 0) <= 1
    return when {
        tag == Tag.ENTERTAINMENT ->
            Reward(RewardKind.ENTERTAINMENT, "LOGGED.", "${day.lost} H LOST TODAY", Quotes.pick(prefs, "ent", Quotes.entertainment))
        !newRep ->
            Reward(RewardKind.PRODUCTIVE, "+1 SET", "SAME HOUR · REP ${day.productive} / $goal", Quotes.pick(prefs, "prod", Quotes.productive))
        day.productive == goal ->
            Reward(RewardKind.GOAL, "GOAL CRUSHED", "$goal / $goal REPS", Quotes.pick(prefs, "goal", Quotes.goal))
        day.productive > goal ->
            Reward(RewardKind.PRODUCTIVE, "+1 BONUS REP", "${day.productive} / $goal REPS", Quotes.pick(prefs, "prod", Quotes.productive))
        else ->
            Reward(RewardKind.PRODUCTIVE, "+1 REP", "REP ${day.productive} / $goal", Quotes.pick(prefs, "prod", Quotes.productive))
    }
}

/** A short synthesized "level up" arpeggio, so no audio files ship with the app. */
object RewardSound {
    private const val RATE = 44_100

    fun play(big: Boolean) = thread(name = "reward-sound") {
        val notes = if (big) doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5) else doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val step = (RATE * 0.085).toInt()
        val tail = (RATE * 0.45).toInt()
        val pcm = ShortArray(step * (notes.size - 1) + tail)
        notes.forEachIndexed { i, f ->
            val len = if (i == notes.lastIndex) tail else step * 2
            val start = i * step
            for (n in 0 until len) {
                val idx = start + n
                if (idx >= pcm.size) break
                val t = n.toDouble() / RATE
                val env = minOf(1.0, t / 0.004) * exp(-t * if (i == notes.lastIndex) 6.0 else 14.0)
                val wave = sin(2 * PI * f * t) + 0.35 * sin(4 * PI * f * t) + 0.12 * sin(6 * PI * f * t)
                pcm[idx] = (pcm[idx] + wave * env * 7000).coerceIn(-32000.0, 32000.0).toInt().toShort()
            }
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        runCatching {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(pcm.size * 1000L / RATE + 100)
        }
        track.release()
    }
}

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color)

/**
 * Full-screen celebration after a check-in. Productive check-ins get the sound, a haptic thump, a volt
 * burst and a pop-in "+1 REP"; entertainment gets a quieter, honest card. Tap anywhere or wait to continue.
 */
@Composable
fun RewardOverlay(reward: Reward, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val big = reward.kind != RewardKind.ENTERTAINMENT
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(0.3f) }
    val fade = remember { Animatable(0f) }
    val particles = remember {
        val colors = listOf(Lx.Volt, Lx.Volt, Color.White, Lx.Volt.copy(alpha = 0.7f))
        List(56) { Particle(Random.nextFloat() * 2 * PI.toFloat(), 0.45f + Random.nextFloat() * 0.55f, 4f + Random.nextFloat() * 10f, colors.random()) }
    }

    LaunchedEffect(Unit) {
        if (big) {
            if (Prefs.get(context).settings.value.rewardSound) RewardSound.play(reward.kind == RewardKind.GOAL)
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        }
        launch { fade.animateTo(1f, tween(220)) }
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        if (big) launch { burst.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
        delay(if (big) 4500 else 2800)
        onDone()
    }

    val accent = if (big) Lx.Volt else Lx.Blaze
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Brush.radialGradient(listOf(accent.copy(alpha = if (big) 0.45f else 0.22f), Lx.Bg.copy(alpha = 0.97f)), radius = 1400f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        if (big) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height * 0.42f)
                val reach = size.minDimension * 0.75f
                val p = burst.value
                drawCircle(Lx.Volt.copy(alpha = (1 - p) * 0.8f), radius = reach * p, center = c, style = Stroke(width = 10f * (1 - p) + 1f))
                drawCircle(Color.White.copy(alpha = (1 - p) * 0.5f), radius = reach * 0.6f * p, center = c, style = Stroke(width = 4f))
                particles.forEach { pt ->
                    val d = reach * pt.speed * p
                    drawCircle(
                        pt.color.copy(alpha = (pt.color.alpha * (1 - p)).coerceIn(0f, 1f)),
                        radius = pt.size * (1 - p * 0.5f),
                        center = Offset(c.x + cos(pt.angle) * d, c.y + sin(pt.angle) * d + 120f * p * p),
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(reward.sub, style = Kicker.copy(color = accent, fontSize = 13.sp))
            Text(
                reward.headline,
                style = display(if (reward.kind == RewardKind.ENTERTAINMENT) 72.sp else 92.sp, accent, 0.02.em),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
            Spacer(Modifier.height(12.dp))
            Text("“${reward.quote}”", style = display(30.sp, Lx.Text, 0.02.em), textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Text("TAP TO CONTINUE", style = Kicker)
        }
    }
}

/** The striking quote shown after the morning plan is locked in. */
@Composable
fun MorningQuote(quote: String, plannedHours: Int, alarms: Int, onGo: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (Prefs.get(context).settings.value.rewardSound) RewardSound.play(true)
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Lx.Bg)
            .background(Brush.verticalGradient(listOf(Lx.Volt.copy(alpha = 0.28f), Lx.Bg, Lx.Bg)))
            .safeDrawingPadding()
            .padding(28.dp),
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 120f },
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("PLAN LOCKED · $plannedHours HOURS WITH A JOB", style = Kicker.copy(color = Lx.Volt, fontSize = 12.sp))
            Text(quote.uppercase(), style = display(58.sp, Lx.Text, 0.01.em))
            Text("$alarms alarms today. Answer every one like you mean it.", color = Lx.Muted, fontSize = 15.sp)
        }
        LxButton("LET'S GO", modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), onClick = onGo)
    }
}
