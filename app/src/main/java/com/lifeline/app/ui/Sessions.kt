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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifeline.app.data.Session
import com.lifeline.app.ui.theme.Kicker
import com.lifeline.app.ui.theme.Lx
import com.lifeline.app.ui.theme.display

/** Why [sessions] can't be saved, or null when they're valid. */
private fun problem(sessions: List<Session>): String? {
    if (sessions.isEmpty()) return "Add at least one session."
    val sorted = sessions.sortedBy { it.start }
    sorted.zipWithNext().firstOrNull { (a, b) -> b.start < a.end }?.let { (a, b) ->
        return "%02d–%02d overlaps %02d–%02d.".format(a.start, a.end, b.start, b.end)
    }
    return null
}

/**
 * Edits the day's check-in sessions, e.g. 07–09 GYM, 10–13 WORK. Each session rings once, when it ends,
 * and one check-in covers all its hours. [onSave] gets them sorted by start.
 */
@Composable
fun SessionsDialog(initial: List<Session>, onSave: (List<Session>) -> Unit, onDismiss: () -> Unit) {
    val draft = remember { mutableStateListOf<Session>().apply { addAll(initial.ifEmpty { Session.EXAMPLE }) } }
    var tried by remember { mutableStateOf(false) }
    val error = problem(draft)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Lx.Surface)
                .border(1.dp, Lx.Line, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SESSIONS", style = display(36.sp))
            Text(
                "Split your day into blocks. Each one rings once when it ends, and one check-in covers the whole block. " +
                    "Its name is the plan unless you plan that day differently. Gaps between blocks aren't tracked.",
                color = Lx.Muted, fontSize = 12.sp,
            )

            draft.forEachIndexed { i, s ->
                SessionCard(s, onChange = { draft[i] = it }, onRemove = { draft.removeAt(i) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LxButton("+ ADD", ghost = true, modifier = Modifier.weight(1f)) {
                    // Start the new block where the last one ended.
                    val start = (draft.maxOfOrNull { it.end } ?: 9).coerceAtMost(23)
                    draft.add(Session(start, start + 1, ""))
                }
                LxButton("EXAMPLE", ghost = true, modifier = Modifier.weight(1f)) {
                    draft.clear()
                    draft.addAll(Session.EXAMPLE)
                }
            }

            if (tried && error != null) Text(error, color = Lx.Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LxButton("CANCEL", ghost = true, modifier = Modifier.weight(1f), onClick = onDismiss)
                LxButton("SAVE", modifier = Modifier.weight(1f)) {
                    tried = true
                    if (error == null) onSave(draft.map { it.copy(label = it.label.trim().uppercase()) }.sortedBy { it.start })
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: Session, onChange: (Session) -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lx.Bg).border(1.dp, Lx.Line, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Start keeps the length where it can; end stays after start and within the day.
            MiniStepper(s.start, Modifier.weight(1f)) { v ->
                val start = v.coerceIn(0, 23)
                onChange(s.copy(start = start, end = (start + (s.end - s.start)).coerceIn(start + 1, 24)))
            }
            Text("→", color = Lx.Muted)
            MiniStepper(s.end, Modifier.weight(1f)) { v -> onChange(s.copy(end = v.coerceIn(s.start + 1, 24))) }
            Text(
                "✕",
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRemove).padding(horizontal = 10.dp, vertical = 8.dp),
                color = Lx.Muted, fontWeight = FontWeight.ExtraBold,
            )
        }
        OutlinedTextField(
            value = s.label,
            onValueChange = { onChange(s.copy(label = it.replace("|", "").take(20))) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Name it: GYM, WORK, STUDY…", color = Lx.Muted) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lx.Volt, unfocusedBorderColor = Lx.Line, cursorColor = Lx.Volt,
                focusedContainerColor = Lx.Surface, unfocusedContainerColor = Lx.Surface,
            ),
        )
    }
}

@Composable
private fun MiniStepper(hour: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SquareIconButton(Icons.Filled.Remove, "Earlier", size = 30.dp) { onChange(hour - 1) }
        Text("%02d".format(hour), style = display(22.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        SquareIconButton(Icons.Filled.Add, "Later", size = 30.dp) { onChange(hour + 1) }
    }
}

/** A row of mutually exclusive choices. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lx.Bg).border(1.dp, Lx.Line, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Lx.Volt else Lx.Bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                style = display(17.sp, if (on) Lx.Ink else Lx.Text),
                textAlign = TextAlign.Center,
            )
        }
    }
}
