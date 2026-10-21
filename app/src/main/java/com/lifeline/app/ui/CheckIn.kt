package com.lifeline.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lifeline.app.alarm.AlarmScheduler
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.Entry
import com.lifeline.app.data.Photos
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Tag
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.color
import com.lifeline.app.ui.theme.display
import com.lifeline.app.ui.theme.label
import com.lifeline.app.widget.Widgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class CheckInMode { ALARM, MANUAL }

/**
 * The check-in form. In [CheckInMode.ALARM] it's locked to the alarm's hour (or session); with no [onCancel]
 * the only way out is submitting (full-screen alarm style), otherwise it offers LATER. In
 * [CheckInMode.MANUAL] the hour or session is selectable and it can be cancelled.
 */
@Composable
fun CheckInForm(
    mode: CheckInMode,
    date: LocalDate,
    initialHour: Int,
    onDone: (Reward) -> Unit,
    line: String? = null,
    onCancel: (() -> Unit)? = null,
    onMute: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = Prefs.get(context).settings.value

    var hour by rememberSaveable { mutableStateOf(initialHour) }
    var text by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<Tag?>(null) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(!settings.sound) }
    var kept by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val plans by remember(date) { AppDatabase.get(context).entries().observePlans(date.toString()) }.collectAsState(initial = emptyList())
    // A session's name is its standing plan unless that day has its own plan.
    val planGoal = plans.firstOrNull { it.hour == hour }?.goal ?: settings.defaultGoal(hour)

    fun importPhoto(uri: Uri) {
        busy = true
        scope.launch {
            val path = Photos.importToStorage(context, uri)
            Photos.clearCaptures(context)
            if (path != null) {
                Photos.delete(photoPath)
                photoPath = path
                error = null
            } else {
                error = "Couldn't read that photo. Try again."
            }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri?.let(Uri::parse)
        if (ok && uri != null) importPhoto(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importPhoto(uri)
    }

    fun submit() {
        val trimmed = text.trim()
        val chosen = tag
        when {
            photoPath == null && trimmed.isEmpty() -> error = "No proof, no submit. Snap a photo or type what you're doing."
            chosen == null -> error = "Tag it: productivity or entertainment."
            chosen == Tag.PRODUCTIVITY && settings.photoForProductive && photoPath == null ->
                error = "Productivity needs photo proof. Snap it or it didn't happen."
            planGoal != null && kept == null -> error = "The plan said $planGoal. Did you stick to it?"
            else -> {
                busy = true
                scope.launch {
                    AppDatabase.get(context).entries().insert(
                        Entry(date = date.toString(), hour = hour, createdAt = System.currentTimeMillis(), text = trimmed, tag = chosen, photoPath = photoPath,
                            keptPlan = if (planGoal != null) kept else null),
                    )
                    Widgets.refresh(context)
                    val pending = Prefs.get(context).pending.value
                    if (mode == CheckInMode.ALARM || (pending != null && pending.date == date.toString() && pending.hour == hour)) {
                        AlarmScheduler.resolve(context)
                    }
                    onDone(buildReward(context, date, hour, chosen))
                }
            }
        }
    }

    val alarm = mode == CheckInMode.ALARM
    val bg = if (alarm) Brush.verticalGradient(listOf(Lx.Blaze.copy(alpha = 0.30f), Lx.Bg, Lx.Bg)) else Brush.verticalGradient(listOf(Lx.Surface, Lx.Surface))

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (alarm) {
            var blink by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { while (true) { delay(600); blink = !blink } }
            Text("● ALARM — CLOCK IN", style = Kicker.copy(color = if (blink) Lx.Blaze else Color.Transparent, fontSize = 12.sp))
            val session = settings.slotOf(hour)?.takeIf { settings.sessionMode }
            Text(session?.let { "%02d–%02d".format(it.start, it.end) } ?: "%02d:00".format(hour), style = display(88.sp, spacing = 0.02.em))
            session?.label?.takeIf { it.isNotBlank() }?.let { Text(it, style = display(30.sp, Lx.Volt)) }
            Text(line ?: "What are you doing right now? Snap it or type it.", style = display(26.sp, Lx.Text))
            Text(if (onCancel == null) "The alarm stops when you submit." else "Log it now, or come back to it later.", color = Lx.Muted)
        } else {
            val day = if (date == LocalDate.now()) "TODAY" else date.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
            Text("CHECK-IN · $day", style = Kicker.copy(color = Lx.Volt, fontSize = 12.sp))
            Text("LOG THE WORK", style = display(46.sp))
            HourPicker(date, hour, settings) { hour = it; kept = null }
        }

        planGoal?.let { PlanCheck(it, kept) { v -> kept = v; error = null } }

        PhotoBox(
            photoPath = photoPath,
            busy = busy,
            onCamera = {
                val uri = Photos.newCaptureUri(context)
                captureUri = uri.toString()
                camera.launch(uri)
            },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onClear = { Photos.delete(photoPath); photoPath = null },
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("WHAT ARE YOU DOING?", style = Kicker)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                placeholder = { Text("Leg day. Shipping the API. Reading 30 pages…", color = Lx.Muted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lx.Volt, unfocusedBorderColor = Lx.Line, cursorColor = Lx.Volt,
                    focusedContainerColor = Lx.Bg, unfocusedContainerColor = Lx.Bg,
                ),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TAG IT", style = Kicker)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tag.entries.forEach { t ->
                    val selected = tag == t
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) t.color() else Color.Transparent)
                            .border(2.dp, if (selected) t.color() else Lx.Line, RoundedCornerShape(12.dp))
                            .clickable { tag = t; error = null }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t.label(), style = display(22.sp, if (selected) Lx.Ink else Lx.Text))
                    }
                }
            }
        }

        error?.let { Text(it, color = Lx.Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                alarm && onCancel == null -> LxButton(if (muted) "MUTED" else "MUTE", ghost = true) { muted = true; onMute() }
                else -> LxButton(if (alarm) "LATER" else "CANCEL", ghost = true) { Photos.delete(photoPath); onCancel?.invoke() }
            }
            LxButton("SUBMIT", modifier = Modifier.weight(1f), enabled = !busy) { submit() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Plan vs reality: what this hour was planned for, and whether you stuck to it. */
@Composable
private fun PlanCheck(goal: String, kept: Boolean?, onPick: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lx.Bg).border(1.dp, Lx.Volt, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THE PLAN", style = Kicker.copy(color = Lx.Volt), modifier = Modifier.weight(1f))
            Text(goal, style = display(24.sp, Lx.Volt))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(true to "✓ KEPT IT", false to "✕ OFF PLAN").forEach { (value, label) ->
                val on = kept == value
                val c = if (value) Lx.Volt else Lx.Blaze
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c else Color.Transparent)
                        .border(2.dp, if (on) c else Lx.Line, RoundedCornerShape(10.dp)).clickable { onPick(value) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = display(19.sp, if (on) Lx.Ink else Lx.Text)) }
            }
        }
    }
}

@Composable
private fun HourPicker(date: LocalDate, selected: Int, settings: com.lifeline.app.data.Settings, onPick: (Int) -> Unit) {
    val maxHour = if (date == LocalDate.now()) LocalTime.now().hour else 23
    // Session mode files a check-in under its session's start hour; only sessions that have begun can be logged.
    val hours = remember(date, maxHour, settings) {
        if (settings.sessionMode) settings.sessions.filter { it.start <= maxHour }.map { it.start } else (0..maxHour).toList()
    }
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem(maxOf(hours.indexOf(selected) - 2, 0)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (settings.sessionMode) "SESSION" else "HOUR", style = Kicker)
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hours) { h ->
                val on = h == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Lx.Volt else Lx.Bg)
                        .border(1.dp, if (on) Lx.Volt else Lx.Line, RoundedCornerShape(10.dp))
                        .clickable { onPick(h) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text(settings.slotLabel(h), style = display(20.sp, if (on) Lx.Ink else Lx.Text)) }
            }
        }
    }
}

@Composable
private fun PhotoBox(photoPath: String?, busy: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onClear: () -> Unit) {
    if (photoPath != null) {
        Box {
            AsyncImage(
                model = java.io.File(photoPath),
                contentDescription = "Proof photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Remove photo", tint = Lx.Text) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashedTile(if (busy) "LOADING…" else "SNAP PROOF", Icons.Outlined.PhotoCamera, Modifier.weight(1.6f), onCamera)
            DashedTile("GALLERY", Icons.Outlined.PhotoLibrary, Modifier.weight(1f), onGallery)
        }
    }
}

@Composable
private fun DashedTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Lx.Bg)
            .border(BorderStroke(2.dp, Lx.Line), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Lx.Text)
        Spacer(Modifier.width(10.dp))
        Text(label, style = display(22.sp))
    }
}
