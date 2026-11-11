package com.telestream.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.telestream.tv.Api
import com.telestream.tv.MediaFile
import com.telestream.tv.PlayRequest

/** Files the server couldn't match to a movie or show, by name. */
@Composable
fun OtherScreen(nav: Nav) {
    val state = load("other") { Api.other(size = 100) }
    Loading(state) { page ->
        val files = page.items
        val first = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
        // the remote starts on the first file (unless Back is returning to the one that was played)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(120)
            runCatching { first.requestFocus() }
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text("קבצים נוספים · ${page.total}", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 4.dp))
                Text("קבצים שהשרת לא זיהה כסרט או כסדרה", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
            }
            itemsIndexed(files, key = { _, f -> f.path }) { i, f ->
                FileRow(nav, f, if (i == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
    }
}

@Composable
fun FileRow(nav: Nav, file: MediaFile, modifier: Modifier = Modifier) {
    Surface(
        onClick = {
            nav.play(PlayRequest(file.path, file.name))
        },
        modifier = modifier.padding(horizontal = 48.dp).fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Panel, contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(file.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${"%.1f".format(file.size / 1024.0 / 1024 / 1024)} GB · ${file.group}", fontSize = 13.sp, color = Muted)
        }
    }
}
