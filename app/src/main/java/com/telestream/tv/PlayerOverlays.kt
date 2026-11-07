package com.telestream.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import java.util.Locale

/** Sizes on the player are given in 1080p pixels, like the Kodi skin, and scaled to the real screen. */
fun View.px(v: Number): Float = v.toFloat() * resources.displayMetrics.heightPixels.coerceAtMost(resources.displayMetrics.widthPixels) / 1080f

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/**
 * "Skip intro" like Netflix and the Kodi add-on: a white button with dark text, and a dark fill that
 * sweeps across it while it is on screen. Where the fill has reached, the text turns white.
 */
class SkipButton(context: Context) : View(context) {
    var label = ""
        set(v) { field = v; invalidate() }
    private var startedAt = 0L
    private var durationMs = 1L

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2141519.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3EA6FF.toInt(); style = Paint.Style.STROKE }
    private val darkText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0B0F.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val lightText = Paint(darkText).apply { color = Color.WHITE }
    private val box = RectF()

    init { isFocusable = true; isClickable = true }

    /** Starts the fill from empty; it is full after [ms]. */
    fun startFill(ms: Long) {
        startedAt = SystemClock.uptimeMillis(); durationMs = ms.coerceAtLeast(1); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = px(8)
        darkText.textSize = px(30); lightText.textSize = px(30); ring.strokeWidth = px(3)
        box.set(0f, 0f, w, h)
        val y = h / 2 - (darkText.descent() + darkText.ascent()) / 2
        canvas.drawRoundRect(box, r, r, white)
        canvas.drawText(label, w / 2, y, darkText)
        val fill = ((SystemClock.uptimeMillis() - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
        if (fill > 0f) {
            canvas.save()
            canvas.clipRect(0f, 0f, w * fill, h)
            canvas.drawRoundRect(box, r, r, dark)
            canvas.drawText(label, w / 2, y, lightText)
            canvas.restore()
        }
        if (fill < 1f && visibility == VISIBLE) postInvalidateOnAnimation()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
    }
}

/** The bar shown while seeking with left/right: where you are, where you will land, and by how much. */
class SeekPreview(context: Context) : View(context) {
    var position = 0L
    var target = 0L
    var total = 1L
    /** A picture of the frame at [target], when one could be fetched in time (like Netflix). */
    var frame: android.graphics.Bitmap? = null
        set(v) { field = v; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3EA6FF.toInt() }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x993EA6FF.toInt() }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB7BFCC.toInt(); textAlign = Paint.Align.LEFT }
    private val box = RectF()
    private val frameBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE }

    fun show(position: Long, target: Long, total: Long) {
        this.position = position; this.target = target; this.total = total.coerceAtLeast(1); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // soft dark band so the bar reads over any picture
        shade.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, 0x00000000, 0xD9000000.toInt(), android.graphics.Shader.TileMode.CLAMP)
        box.set(0f, 0f, w, h); canvas.drawRect(box, shade)
        val side = px(60); val barY = h - px(50); val barH = px(6)
        val left = side; val right = w - side; val span = right - left
        fun x(t: Long) = left + span * (t.toFloat() / total).coerceIn(0f, 1f)
        box.set(left, barY, right, barY + barH); canvas.drawRoundRect(box, barH, barH, track)
        val px = x(position); val tx = x(target)
        box.set(left, barY, minOf(px, tx), barY + barH); canvas.drawRoundRect(box, barH, barH, played)
        if (tx != px) { box.set(minOf(px, tx), barY, maxOf(px, tx), barY + barH); canvas.drawRect(box, ghost) }
        canvas.drawCircle(tx, barY + barH / 2, px(14), knob)

        big.textSize = px(38); small.textSize = px(26)
        val delta = (target - position) / 1000
        val sign = if (delta >= 0) "+" else "−"
        val label = "${clock(target)}   ($sign${clock(kotlin.math.abs(delta) * 1000)})"
        val lx = tx.coerceIn(left + px(170), right - px(170))
        val f = frame
        if (f != null) {
            val fw = px(384); val fh = fw * f.height / f.width.coerceAtLeast(1)
            val fx = tx.coerceIn(left + fw / 2, right - fw / 2)
            box.set(fx - fw / 2, barY - px(90) - fh, fx + fw / 2, barY - px(90))
            canvas.drawBitmap(f, null, box, null)
            frameBorder.strokeWidth = px(3); canvas.drawRect(box, frameBorder)
        }
        canvas.drawText(label, lx, barY - px(30), big)
        small.textAlign = Paint.Align.LEFT; canvas.drawText(clock(total), right - small.measureText(clock(total)), barY + px(42), small)
        canvas.drawText("0:00", left, barY + px(42), small)
    }
}
