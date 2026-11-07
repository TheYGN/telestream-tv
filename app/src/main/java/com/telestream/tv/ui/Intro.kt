package com.telestream.tv.ui

import android.graphics.Paint
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.telestream.tv.Prefs
import com.telestream.tv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The opening title, about three seconds, drawn on one Canvas so it stays smooth on TV boxes.
 *
 * Shot list (seconds):
 *  0.00–0.60  darkness, a thin anamorphic light streak opens across the screen; an airy riser builds
 *  0.25–0.94  the three "Prism" play triangles fly in from the right, one after another, with motion trails
 *  0.94       the front triangle lands: light bloom, shock ring, light rays and sparks; a soft sub hit and a warm chord
 *  1.05–1.55  a glint of light runs across the triangles (bell sparkle in the sound)
 *  1.15–1.90  "TeleStream" types itself in under the mark while its letter spacing settles
 *  1.90–2.35  hold, the glow breathes, a slow push in
 *  2.35–2.95  the camera flies through the mark and the home screen opens up behind it
 *
 * The home screen is composed underneath from the first frame, so its data loads while this plays.
 */
/** Lets screens behind the intro wait for it (e.g. to open the on-screen keyboard only once it is over). */
object IntroGate {
    var done by mutableStateOf(true)
}

class IntroState(val enabled: Boolean) {
    /** Seconds since the intro started. Read only in draw/layer blocks, so frames don't recompose. */
    var time by mutableFloatStateOf(0f)
    var done by mutableStateOf(!enabled)
    var skipRequested = false

    /** 0 while the intro covers the screen, then 0..1 while the home screen is revealed. */
    val reveal: Float get() = if (done) 1f else ((time - REVEAL_START) / (END - REVEAL_START)).coerceIn(0f, 1f)

    fun skip() { skipRequested = true }

    companion object {
        const val IMPACT = 0.94f
        const val EXIT = 2.35f
        const val REVEAL_START = 2.40f
        const val END = 2.95f
    }
}

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
private fun easeInCubic(x: Float) = x * x * x
private fun easeInOut(x: Float) = if (x < .5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOutBack(x: Float, s: Float = 1.25f): Float { val y = x - 1; return 1 + (s + 1) * y * y * y + s * y * y }

private class Spark(val angle: Float, val speed: Float, val size: Float, val life: Float, val delay: Float, val hue: Int)

private val BgCenter = Color(0xFF13234A)
private val BgMid = Color(0xFF090D1A)
private val BgEdge = Color(0xFF040508)
private val Cyan = Color(0xFF3EA6FF)
private val Ice = Color(0xFFBFE8FF)

@Composable
fun IntroOverlay(state: IntroState, onFinished: () -> Unit) {
    val context = LocalContext.current
    val player = remember { if (Prefs.introMode == 0) runCatching { MediaPlayer.create(context, R.raw.telestream_intro) }.getOrNull() else null }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }

    // the logo's play triangle, in the 1024×1024 space of icons/logo.svg, with its rounded (stroke 70, round join) outline baked in
    val triangle = remember { roundedTriangle() }
    val brushes = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFF6D5BFF), Color(0xFF2A2FB8)), Offset(300f, 290f), Offset(690f, 734f)),
            Brush.linearGradient(listOf(Color(0xFF2FB6FF), Color(0xFF1A6FD6)), Offset(300f, 290f), Offset(690f, 734f)),
            Brush.linearGradient(0f to Color.White, .45f to Ice, 1f to Color(0xFF5CC3FF), start = Offset(320f, 300f), end = Offset(690f, 734f)),
        )
    }
    val sparks = remember {
        val r = Random(1024)
        List(70) { Spark(r.nextFloat() * 2 * PI.toFloat(), .35f + r.nextFloat() * .9f, 1.2f + r.nextFloat() * 2.8f, .7f + r.nextFloat() * .9f, r.nextFloat() * .12f, r.nextInt(3)) }
    }
    val measurer = rememberTextMeasurer()
    val letters = remember {
        "TeleStream".map { ch -> measurer.measure(ch.toString(), TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)) }
    }

    // follows the system animation speed (Developer options), which also makes a slow-motion check possible
    val speed = remember { runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f).coerceIn(.1f, 20f) }

    LaunchedEffect(Unit) {
        withFrameNanos { } // let the first frame (and the home screen behind) compose before the clock starts
        runCatching { player?.start() }
        val start = withFrameNanos { it }
        var offset = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            var t = (now - start) / 1e9f / speed + offset
            if (state.skipRequested && t < IntroState.EXIT) {
                offset += IntroState.EXIT - t; t = IntroState.EXIT
                state.skipRequested = false
                player?.let { p -> launch { fadeOut(p) } }
            }
            state.time = t
            if (t >= IntroState.END) break
        }
        state.done = true
        IntroGate.done = true
        onFinished()
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = state.time
        val w = size.width; val h = size.height
        val center = Offset(w / 2, h * .43f)
        val markSize = h * .34f
        val u = markSize / 600f // px per logo unit

        // camera: slow push during the hold, then fly through the mark
        val push = 1f + .035f * easeInOut(seg(t, 1.2f, IntroState.EXIT))
        val fly = 1f + 5f * easeInCubic(seg(t, IntroState.EXIT, IntroState.END))
        val cam = push * fly
        val bgAlpha = seg(t, .15f, 1.0f).let { easeOutCubic(it) } * (1f - easeInOut(seg(t, IntroState.REVEAL_START, 2.78f)))
        val logoAlpha = 1f - easeOutCubic(seg(t, 2.45f, 2.82f)) // the mark dissolves as the camera passes through it

        // 1. background
        if (t < IntroState.REVEAL_START) drawRect(Color.Black)
        drawRect(Color.Black, alpha = 1f - seg(t, IntroState.REVEAL_START, 2.78f))
        drawRect(Brush.radialGradient(0f to BgCenter, .55f to BgMid, 1f to BgEdge, center = center, radius = w * .75f), alpha = bgAlpha)

        withTransform({ scale(cam, cam, center) }) {
            // 2. opening light streak
            val streak = seg(t, 0f, .55f)
            val streakA = (sin(PI.toFloat() * seg(t, 0f, .8f))).coerceAtLeast(0f) * (1f - seg(t, .7f, 1.0f))
            if (streakA > 0f) {
                val half = w * .48f * easeOutCubic(streak)
                val y = center.y + 10f * u
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Cyan, Color.White, Cyan, Color.Transparent), startX = center.x - half, endX = center.x + half),
                    topLeft = Offset(center.x - half, y - 1.5f), size = androidx.compose.ui.geometry.Size(half * 2, 3f), alpha = streakA,
                )
                drawOval(
                    Brush.radialGradient(listOf(Cyan.copy(alpha = .35f), Color.Transparent), center = Offset(center.x, y), radius = half),
                    topLeft = Offset(center.x - half, y - half * .12f), size = androidx.compose.ui.geometry.Size(half * 2, half * .24f), alpha = streakA,
                )
            }

            // 3. glow behind the mark, born at the impact and breathing during the hold
            val impact = seg(t, IntroState.IMPACT - .04f, IntroState.IMPACT + .04f)
            if (impact > 0f) {
                val breathe = 1f + .06f * sin((t - IntroState.IMPACT) * 3.2f)
                drawCircle(
                    Brush.radialGradient(listOf(Cyan.copy(alpha = .45f), Color.Transparent), center = center, radius = 380f * u * breathe),
                    radius = 380f * u * breathe, center = center, alpha = impact * logoAlpha,
                )
            }

            // 4. the three triangles fly in: back (indigo), middle (blue), front (white-cyan)
            val restX = floatArrayOf(90f, 15f, -60f)
            val alphas = floatArrayOf(.55f, .75f, 1f)
            val landBump = 1f + .07f * exp(-12f * (t - IntroState.IMPACT).coerceAtLeast(0f)) * seg(t, IntroState.IMPACT - .02f, IntroState.IMPACT)
            for (i in 0..2) {
                val s0 = .25f + .12f * i
                val dur = .45f + .01f * i
                fun pos(tt: Float): Float = easeOutBack(seg(tt, s0, s0 + dur), 1.1f)
                val appear = seg(t, s0, s0 + .12f)
                if (appear <= 0f) continue
                // motion trail: earlier positions, fainter
                for (k in 4 downTo 0) {
                    val p = pos(t - k * .022f)
                    val a = if (k == 0) 1f else .16f * (1f - k / 5f) * (1f - p)
                    if (a <= .002f) continue
                    val dx = (1f - p) * w * .62f
                    val sc = (1.22f - .22f * p) * landBump
                    withTransform({
                        translate(center.x + dx, center.y)
                        scale(u * sc, u * sc, Offset.Zero)
                        translate(-512f + restX[i], -512f)
                    }) {
                        drawPath(triangle, brushes[i], alpha = alphas[i] * appear * a * logoAlpha)
                        // 5. glint: a band of light sweeps right to left across the landed mark
                        if (k == 0 && t in 1.0f..1.65f) {
                            val g = easeInOut(seg(t, 1.05f + .05f * (2 - i), 1.55f + .05f * (2 - i)))
                            val gx = 900f - 800f * g
                            clipPath(triangle) {
                                rotate(-24f, Offset(gx, 512f)) {
                                    drawRect(
                                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .75f), Color.Transparent), startX = gx - 60f, endX = gx + 60f),
                                        topLeft = Offset(gx - 60f, -200f), size = androidx.compose.ui.geometry.Size(120f, 1400f), alpha = logoAlpha,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. the impact: flash, shock ring, rays, sparks
            val dt = t - IntroState.IMPACT
            if (dt > 0f) {
                val flash = exp(-dt * 5.5f) * seg(t, IntroState.IMPACT, IntroState.IMPACT + .03f)
                drawCircle(
                    Brush.radialGradient(0f to Color.White.copy(alpha = .9f), .25f to Cyan.copy(alpha = .5f), 1f to Color.Transparent, center = center, radius = markSize * 1.6f),
                    radius = markSize * 1.6f, center = center, alpha = flash * .8f,
                )
                if (dt < .75f) {
                    val rp = easeOutCubic(dt / .75f)
                    drawCircle(Ice, radius = markSize * (.45f + 1.9f * rp), center = center, alpha = (1f - rp) * .55f, style = Stroke(width = 3f + 10f * (1f - rp)))
                }
                drawRays(center, markSize, dt, logoAlpha)
                drawSparks(sparks, center, markSize, dt, logoAlpha)
            }

            // 7. wordmark
            drawWordmark(letters, t, center, markSize, logoAlpha * (1f - seg(t, IntroState.EXIT, 2.6f)))
        }

        // 8. a touch of vignette and grain-free letterbox feel while the intro covers the screen
        if (bgAlpha > 0f) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)), center = center, radius = w * .8f), alpha = bgAlpha)
    }
}

private fun DrawScope.drawRays(center: Offset, markSize: Float, dt: Float, logoAlpha: Float) {
    val a = seg(dt, 0f, .08f) * (1f - seg(dt, .25f, 1.15f)) * logoAlpha
    if (a <= 0f) return
    val n = 14
    val len = markSize * (1.2f + 1.4f * easeOutCubic(seg(dt, 0f, .9f)))
    rotate(dt * 9f, center) {
        for (i in 0 until n) {
            val ang = (i * 2 * PI / n).toFloat() + (if (i % 2 == 0) 0f else .11f)
            val spread = if (i % 3 == 0) .045f else .025f
            val p = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(ang - spread) * len, center.y + sin(ang - spread) * len)
                lineTo(center.x + cos(ang + spread) * len, center.y + sin(ang + spread) * len)
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Ice.copy(alpha = .35f), Color.Transparent), center = center, radius = len), alpha = a * (if (i % 3 == 0) 1f else .6f))
        }
    }
}

private fun DrawScope.drawSparks(sparks: List<Spark>, center: Offset, markSize: Float, dt: Float, logoAlpha: Float) {
    for (s in sparks) {
        val lt = dt - s.delay
        if (lt <= 0f || lt > s.life) continue
        val k = lt / s.life
        val d = markSize * (.35f + s.speed * 1.6f * easeOutCubic(k))
        val pos = Offset(center.x + cos(s.angle) * d, center.y + sin(s.angle) * d * .8f + 30f * k * k)
        val color = when (s.hue) { 0 -> Color.White; 1 -> Ice; else -> Cyan }
        val a = (1f - k).pow(1.6f) * logoAlpha
        drawCircle(color.copy(alpha = .35f), radius = s.size * 3f, center = pos, alpha = a)
        drawCircle(color, radius = s.size, center = pos, alpha = a)
    }
}

private fun DrawScope.drawWordmark(letters: List<TextLayoutResult>, t: Float, center: Offset, markSize: Float, alpha: Float) {
    if (t < 1.1f || alpha <= 0f) return
    val scale = markSize * .30f / letters[0].size.height // keep it proportional to the mark on any screen density
    val settle = easeOutCubic(seg(t, 1.15f, 1.95f))
    val gap = (28f - 24f * settle) * scale // letter spacing tightens as it settles
    val widths = letters.map { it.size.width * scale }
    val total = widths.sum() + gap * (letters.size - 1)
    var x = center.x - total / 2
    val y = center.y + markSize * .62f
    val shimmer = seg(t, 1.75f, 2.25f) * (letters.size + 4) - 2
    letters.forEachIndexed { i, lay ->
        val a = easeOutCubic(seg(t, 1.15f + i * .04f, 1.5f + i * .04f))
        if (a > 0f) {
            val lift = (1f - a) * 22f * scale
            val near = (1f - kotlin.math.abs(i - shimmer) / 2f).coerceIn(0f, 1f)
            val base = if (i < 4) Color.White else Color(0xFF5CC3FF)
            val color = androidx.compose.ui.graphics.lerp(base, Color.White, near * .85f)
            withTransform({ translate(x, y + lift); scale(scale, scale, Offset.Zero) }) {
                drawText(lay, color = color, alpha = a * alpha)
            }
        }
        x += widths[i] + gap
    }
}

private suspend fun fadeOut(p: MediaPlayer) {
    var v = 1f
    while (v > 0f) { v -= .1f; runCatching { p.setVolume(v.coerceAtLeast(0f), v.coerceAtLeast(0f)) }; delay(30) }
    runCatching { p.pause() }
}

/** The logo's play triangle (pointing left, like every play icon in TeleStream) with its round outline as one filled path. */
private fun roundedTriangle(): Path {
    val src = android.graphics.Path().apply { moveTo(300f, 512f); lineTo(690f, 290f); lineTo(690f, 734f); close() }
    val paint = Paint().apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 70f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
    val out = android.graphics.Path()
    paint.getFillPath(src, out)
    return out.asComposePath()
}
