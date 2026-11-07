package com.telestream.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Button
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.telestream.tv.Api
import com.telestream.tv.BuildConfig
import com.telestream.tv.Prefs
import kotlinx.coroutines.launch

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, ltr: Boolean = false, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Text(label, color = Muted, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 20.sp, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (ltr) KeyboardType.Uri else KeyboardType.Text),
        modifier = modifier.width(520.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                leaveOrType(it, keyboard, focusManager)
            }
            .background(Panel, RoundedCornerShape(10.dp))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Spacer(Modifier.height(18.dp))
}

/** First run (name this TV) and later the settings tab. */
@Composable
fun SetupScreen(nav: Nav, first: Boolean) {
    var server by remember { mutableStateOf(Prefs.server) }
    var name by remember { mutableStateOf(Prefs.tvName) }
    var lead by remember { mutableStateOf(Prefs.nextLead) }
    var skipMode by remember { mutableStateOf(Prefs.skipMode) }
    var status by remember { mutableStateOf("") }
    var updateStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    fun save() {
        if (name.isBlank()) { status = "צריך לתת שם לטלוויזיה"; return }
        Prefs.server = server.ifBlank { Prefs.DEFAULT_SERVER }.let { if (it.startsWith("http")) it else "http://$it" }
        Prefs.tvName = name
        Prefs.nextLead = lead
        Prefs.skipMode = skipMode
        Cache.values.clear()
        status = "בודק חיבור לשרת…"
        scope.launch {
            status = try {
                Api.hello()
                val home = Api.home()
                if (first) nav.replace(AppScreen.Main)
                "נשמר. השרת עונה: ${home.movies} סרטים, ${home.shows} סדרות."
            } catch (e: Exception) {
                "נשמר, אבל השרת לא עונה בכתובת הזאת (${e.message})."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 24.dp)) {
        if (first) {
            Text("ברוך הבא ל-TeleStream", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Accent)
            Text("עוד שני פרטים, פעם אחת, והטלוויזיה מוכנה.", color = Muted, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
        }
        Field("איך לקרוא לטלוויזיה הזאת? (למשל: סלון, חדר שינה)", name, { name = it }, modifier = Modifier.focusRequester(focus))
        Field("כתובת השרת במחשב", server, { server = it }, ltr = true)
        Text("כמה שניות לפני סוף פרק להציע את הבא", color = Muted, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(30, 60, 90, 120).forEach { s -> FilterChip(selected = lead == s, onClick = { lead = s }) { Text("$s") } }
        }
        Spacer(Modifier.height(10.dp))
        Text("דילוג על הפתיח והתקציר", color = Muted, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("להציג כפתור", "לדלג אוטומטית", "לא לדלג").forEachIndexed { i, label ->
                FilterChip(selected = skipMode == i, onClick = { skipMode = i }) { Text(label) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = ::save) { Text(if (first) "התחל" else "שמור") }
        if (status.isNotBlank()) Text(status, modifier = Modifier.padding(top = 12.dp), fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("גרסה ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.width(16.dp))
            androidx.tv.material3.OutlinedButton(onClick = {
                updateStatus = "בודק…"
                scope.launch {
                    updateStatus = try {
                        val found = com.telestream.tv.Updater.check()
                        UpdateOffer.release = found
                        if (found == null) "זו הגרסה העדכנית ביותר." else ""
                    } catch (e: Exception) {
                        "לא הצלחתי לבדוק (${e.message})."
                    }
                }
            }) { Text("בדוק עדכונים") }
        }
        if (updateStatus.isNotBlank()) Text(updateStatus, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
    LaunchedEffect(Unit) { if (first) runCatching { focus.requestFocus() } }
}
