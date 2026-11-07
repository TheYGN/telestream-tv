package com.telestream.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.telestream.tv.Api
import com.telestream.tv.Upcoming

private val months = listOf("בינואר", "בפברואר", "במרץ", "באפריל", "במאי", "ביוני", "ביולי", "באוגוסט", "בספטמבר", "באוקטובר", "בנובמבר", "בדצמבר")

/** "2026-11-20" → "20 בנובמבר 2026" */
private fun longDate(iso: String?): String? {
    val p = iso?.take(10)?.split("-")?.takeIf { it.size == 3 } ?: return null
    val m = p[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return iso
    return "${p[2].trimStart('0')} ${months[m - 1]} ${p[0]}"
}

/** A title that hasn't arrived yet: when it is expected, the trailer and the cast. */
@Composable
fun UpcomingScreen(nav: Nav, key: String) {
    val state = load("upcoming") { Api.upcoming() }
    Loading(state) { list ->
        val u = list.firstOrNull { it.key == key } ?: return@Loading
        UpcomingDetails(u)
    }
}

@Composable
private fun UpcomingDetails(u: Upcoming) {
    Box(Modifier.fillMaxSize()) {
        Backdrop(u.fanart ?: u.poster)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (u.poster != null) {
                        AsyncImage(u.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.size(200.dp, 300.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(36.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("בקרוב ברשת · " + u.whenText, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(u.title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        if (u.originalTitle.isNotBlank() && u.originalTitle != u.title) Text(u.originalTitle, color = Muted, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        MetaLine(u.year, u.rating, u.genres)
                        Spacer(Modifier.height(14.dp))
                        Text(u.overview, fontSize = 17.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("דיגיטלי" to u.digital, "בלו-ריי" to u.bluray, "בקולנוע" to u.cinema).forEach { (label, date) ->
                                Column(Modifier.background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Text(label, color = Muted, fontSize = 13.sp)
                                    Text(longDate(date) ?: "עוד לא ידוע", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        TrailerButton(u.kind, u.tmdbId)
                    }
                }
            }
            item { CastRow(u.kind, u.tmdbId) }
        }
    }
}
