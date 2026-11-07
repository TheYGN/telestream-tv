package com.telestream.tv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.telestream.tv.BuildConfig
import com.telestream.tv.Updater
import kotlinx.coroutines.launch

/** The update on offer right now (null: none), shared by the start-up check and the settings button. */
object UpdateOffer {
    var release by mutableStateOf<Updater.Release?>(null)
    private var checkedThisLaunch = false

    /** Once per launch, quietly: a failed check (no internet) just means no offer. */
    suspend fun checkOnStart() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        release = runCatching { Updater.check() }.getOrNull()
    }
}

@Composable
fun UpdateDialog() {
    val release = UpdateOffer.release ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(release) { mutableStateOf("") }
    var busy by remember(release) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun start(context: Context) {
        if (!Updater.canInstall(context)) {
            status = "צריך לאשר פעם אחת ל-TeleStream להתקין עדכונים. סמן את האפשרות במסך שנפתח, חזור ולחץ שוב 'עדכן'."
            Updater.openInstallPermission(context)
            return
        }
        busy = true
        scope.launch {
            try {
                val apk = Updater.download(context, release) { status = "מוריד… ${(it * 100).toInt()}%" }
                status = "פותח את ההתקנה…"
                Updater.install(context, apk)
                UpdateOffer.release = null
            } catch (e: Exception) {
                status = "ההורדה נכשלה (${e.message}). נסה שוב."
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = { if (!busy) UpdateOffer.release = null }) {
      androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides androidx.compose.ui.graphics.Color.White) {
        Column(Modifier.background(Panel, RoundedCornerShape(14.dp)).padding(28.dp).width(560.dp)) {
            Text("יש גרסה חדשה ל-TeleStream", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("גרסה ${release.version} (מותקנת עכשיו ${BuildConfig.VERSION_NAME})", color = Muted, fontSize = 15.sp)
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(release.notes, fontSize = 16.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (!busy) start(context) }, modifier = Modifier.focusRequester(focus)) { Text("עדכן עכשיו") }
                OutlinedButton(onClick = { if (!busy) UpdateOffer.release = null }) { Text("אחר כך") }
            }
            if (status.isNotBlank()) Text(status, modifier = Modifier.padding(top = 12.dp), fontSize = 15.sp)
        }
      }
    }
    LaunchedEffect(release) { runCatching { focus.requestFocus() } }
}
