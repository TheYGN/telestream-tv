package com.telestream.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.telestream.tv.Api
import com.telestream.tv.MediaFile
import com.telestream.tv.Movie
import com.telestream.tv.Show
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SearchResults(val movies: List<Movie>, val shows: List<Show>, val other: List<MediaFile>)

@Composable
fun SearchTab(nav: Nav) {
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = text.trim(); keyboard?.hide() }),
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp).fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    leaveOrType(it, keyboard, focusManager)
                }
                .background(Panel, RoundedCornerShape(10.dp))
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("שם של סרט או סדרה… (לחץ OK להקלדה)", color = Muted, fontSize = 22.sp)
                    inner()
                }
            },
        )
        if (query.isBlank()) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            return@Column
        }
        val state = load("search:$query") {
            coroutineScope {
                val m = async { Api.movies(q = query, size = 40).items }
                val s = async { Api.shows(q = query, size = 40).items }
                val o = async { runCatching { Api.other(q = query).items.take(40) }.getOrDefault(emptyList()) }
                SearchResults(m.await(), s.await(), o.await())
            }
        }
        Loading(state) { r ->
            if (r.movies.isEmpty() && r.shows.isEmpty() && r.other.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("לא נמצא כלום עבור \"$query\"", color = Muted, fontSize = 20.sp) }
                return@Loading
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                if (r.movies.isNotEmpty()) {
                    item { SectionTitle("סרטים (${r.movies.size})") }
                    item { MixedRow(nav, "search", r.movies) }
                }
                if (r.shows.isNotEmpty()) {
                    item { SectionTitle("סדרות (${r.shows.size})") }
                    item { MixedRow(nav, "search", r.shows) }
                }
                if (r.other.isNotEmpty()) {
                    item { SectionTitle("קבצים שלא זוהו (${r.other.size})") }
                    items(r.other.size) { i -> FileRow(nav, r.other[i]) }
                }
            }
        }
    }
}
