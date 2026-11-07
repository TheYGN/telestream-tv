package com.telestream.tv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.text.TextUtils
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import coil.load
import androidx.media3.ui.DefaultTimeBar
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Plays one file from the server, like the Kodi add-on's service:
 * reports where the viewer is, offers to skip the opening/recap, and near the end offers the next episode.
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val REPORT_EVERY_MS = 10_000L
        private const val SKIP_SHOWN_MS = 30_000L

        fun start(context: Context, request: PlayRequest) {
            context.startActivity(Intent(context, PlayerActivity::class.java)
                .putExtra("path", request.path).putExtra("title", request.title).putExtra("start", request.start)
                .addFlags(if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var root: FrameLayout
    private lateinit var skipButton: SkipButton
    private lateinit var nextCard: FrameLayout
    private lateinit var nextStill: ImageView
    private lateinit var nextShow: TextView
    private lateinit var nextEpisode: TextView
    private lateinit var nextCountdown: TextView
    private lateinit var nextNow: TextView
    private lateinit var seekPreview: SeekPreview
    private lateinit var titleBox: LinearLayout
    private lateinit var titleMain: TextView
    private lateinit var titleSub: TextView

    // left/right seeking: presses add up, and the jump happens once you stop pressing
    private var seekTarget: Long? = null
    private var seekFrom = 0L
    private val applySeek = Runnable {
        seekTarget?.let { player.seekTo(it) }
        seekTarget = null
        root.postDelayed(hideSeek, 1200)
    }
    private val hideSeek = Runnable { seekPreview.visibility = View.GONE; seekPreview.frame = null }
    private val frames = FrameGrabber { bmp -> if (seekTarget != null) seekPreview.frame = bmp }

    private var path = ""
    private var title = ""
    private var watcher: Job? = null

    private var skips = mutableListOf<SkipPart>()
    private var shownSkip: SkipPart? = null
    private var skipShownAt = 0L
    private var outro: Double? = null
    private var upcoming: NextEpisode? = null
    private var nextDecision: String? = null // "now", "cancel" or null
    private var nextOffered = false
    private var lastSent = 0L

    data class SkipPart(val start: Double, val end: Double, val label: String, var done: Boolean = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildViews()
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true))
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(30_000)
            .build()
        // Hebrew subtitles and audio first when a file has them
        player.trackSelectionParameters = TrackSelectionParameters.Builder(this)
            .setPreferredTextLanguages("he", "iw").setPreferredAudioLanguages("he", "iw", "en").build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "לא הצלחתי לנגן: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        open(intent.getStringExtra("path") ?: "", intent.getStringExtra("title") ?: "", intent.getDoubleExtra("start", 0.0))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reportNow()
        open(intent.getStringExtra("path") ?: return, intent.getStringExtra("title") ?: "", intent.getDoubleExtra("start", 0.0))
    }

    private fun open(newPath: String, newTitle: String, start: Double) {
        path = newPath
        title = newTitle
        // "Show · עונה 1 פרק 2 · Name": the show big, the rest under it
        val parts = newTitle.split(" · ")
        titleMain.text = parts.first()
        titleSub.text = parts.drop(1).joinToString(" · ")
        titleSub.visibility = if (parts.size > 1) View.VISIBLE else View.GONE
        skips.clear(); shownSkip = null; outro = null; upcoming = null
        nextDecision = null; nextOffered = false
        hideSkip(); hideNext()
        frames.open(Api.streamUrl(path), lifecycleScope)
        val item = MediaItem.Builder().setUri(Uri.parse(Api.streamUrl(path)))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()
        player.setMediaItem(item, (start * 1000).toLong().coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
        playerView.requestFocus()
        lifecycleScope.launch {
            runCatching { Api.skip(path) }.getOrNull()?.let { found ->
                found.recap?.let { if (it.second - it.first >= 5) skips += SkipPart(it.first, it.second, "דלג על התקציר") }
                found.intro?.let { if (it.second - it.first >= 5) skips += SkipPart(it.first, it.second, "דלג על הפתיח") }
                outro = found.outro
            }
            upcoming = runCatching { Api.next(path) }.getOrNull()
        }
        watcher?.cancel()
        watcher = lifecycleScope.launch {
            delay(3000)
            while (isActive) {
                tick()
                delay(1000)
            }
        }
    }

    private fun seconds(ms: Long) = ms / 1000.0

    private fun tick() {
        val total = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let(::seconds) ?: return
        val now = seconds(player.currentPosition)
        if (player.isPlaying && System.currentTimeMillis() - lastSent >= REPORT_EVERY_MS) {
            lastSent = System.currentTimeMillis()
            report(now, total)
        }
        offerSkip(now)
        offerNext(now, total)
    }

    private fun report(position: Double, total: Double) {
        if (path.isBlank() || total <= 0) return
        val p = path
        lifecycleScope.launch { runCatching { Api.progress(p, position, total) } }
    }

    private fun reportNow() {
        val total = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        report(seconds(player.currentPosition), seconds(total))
    }

    // ---------- skip opening / recap ----------

    private fun offerSkip(now: Double) {
        skips.forEach { if (now < it.start - 5) it.done = false } // went back before it: offer again
        val active = skips.firstOrNull { !it.done && it.start - 1 <= now && now < it.end - 3 }
        shownSkip?.let { shown ->
            if (shown !== active || System.currentTimeMillis() - skipShownAt > SKIP_SHOWN_MS) {
                shown.done = true
                hideSkip()
            }
            return
        }
        if (active == null || Prefs.skipMode == 2) return
        if (Prefs.skipMode == 1) {
            active.done = true
            player.seekTo((active.end * 1000).toLong())
            Toast.makeText(this, active.label.replace("דלג", "דילגנו"), Toast.LENGTH_SHORT).show()
            return
        }
        shownSkip = active
        skipShownAt = System.currentTimeMillis()
        skipButton.label = active.label
        skipButton.startFill(SKIP_SHOWN_MS)
        appear(skipButton, 40, 250)
        if (!playerView.isControllerFullyVisible) skipButton.requestFocus()
    }

    private fun hideSkip() {
        shownSkip = null
        if (skipButton.hasFocus()) playerView.requestFocus()
        skipButton.visibility = View.GONE
    }

    // ---------- next episode ----------

    private fun offerNext(now: Double, total: Double) {
        val next = upcoming ?: return
        var lead = Prefs.nextLead.toDouble()
        outro?.let { if (total * 0.5 < it && total - it in 20.0..600.0) lead = total - it } // card comes up with the end credits
        val left = total - now
        if (left > lead + 20 && (nextOffered || nextDecision != null)) { // went back: offer again at the end
            hideNext()
            nextOffered = false; nextDecision = null
        }
        if (nextDecision != null || total < lead * 3 || left > lead) return
        if (!nextOffered) {
            nextOffered = true
            nextShow.text = next.show
            nextEpisode.text = "עונה ${next.season} · פרק ${next.episode}" + (if (next.name.isNotBlank()) " · ${next.name}" else "")
            nextStill.setImageDrawable(null)
            (next.still ?: next.poster)?.let { nextStill.load(it) { crossfade(true) } }
            playerView.hideController()
            appear(nextCard, 60, 300)
            nextNow.requestFocus()
        }
        val s = left.toInt().coerceAtLeast(0)
        nextCountdown.text = String.format(Locale.US, "מתחיל בעוד %d:%02d", s / 60, s % 60)
    }

    private fun playNext() {
        val next = upcoming ?: return
        reportNow()
        val label = "${next.show} · עונה ${next.season} פרק ${next.episode}" + (if (next.name.isNotBlank()) " · ${next.name}" else "")
        open(next.path, label, 0.0)
    }

    private fun onEnded() {
        val total = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let(::seconds)
        if (total != null) report(total, total)
        if (upcoming != null && nextDecision == null) playNext() else finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (nextCard.visibility == View.VISIBLE) {
                nextDecision = "cancel"; hideNext(); return true
            }
            if (skipButton.visibility == View.VISIBLE) {
                shownSkip?.done = true; hideSkip(); return true
            }
            if (playerView.isControllerFullyVisible) {
                playerView.hideController(); return true
            }
        }
        val onCard = nextCard.visibility == View.VISIBLE && nextCard.hasFocus()
        if (!playerView.isControllerFullyVisible && !onCard) {
            when (event.keyCode) {
                // left/right while the controls are hidden: small steps that add up, faster when held (like Kodi)
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val back = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                        nudge(if (back) -1 else 1, event.repeatCount)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (seekTarget != null) { root.removeCallbacks(applySeek); applySeek.run() }
                        else if (skipButton.visibility == View.VISIBLE) skipButton.performClick()
                        else { player.playWhenReady = !player.playWhenReady; playerView.showController() }
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun nudge(direction: Int, repeat: Int) {
        val total = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (repeat > 0 && repeat % 3 != 0) return // held down: about 6 steps a second
        val step = when {
            repeat < 30 -> 10_000L   // a tap, or the first ~1.5 s of holding: 10 seconds
            repeat < 90 -> 30_000L   // held longer: 30 seconds
            else -> 60_000L          // held a long time: a minute
        }
        if (seekTarget == null) seekFrom = player.currentPosition
        val target = ((seekTarget ?: seekFrom) + direction * step).coerceIn(0, (total - 2000).coerceAtLeast(0))
        seekTarget = target
        root.removeCallbacks(hideSeek)
        root.removeCallbacks(applySeek)
        seekPreview.show(seekFrom, target, total)
        seekPreview.visibility = View.VISIBLE
        root.postDelayed(applySeek, 2000)
        frames.want(target)
    }

    private fun appear(view: View, rise: Int, ms: Long) {
        view.animate().cancel()
        view.alpha = 0f; view.translationY = view.px(rise)
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hideNext() {
        val hadFocus = nextCard.hasFocus()
        nextCard.animate().cancel()
        nextCard.visibility = View.GONE
        if (hadFocus) playerView.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        reportNow()
        player.pause()
    }

    override fun onDestroy() {
        watcher?.cancel()
        frames.close()
        player.release()
        super.onDestroy()
    }

    // ---------- views ----------


    /** A 1080p-pixel size (as in the Kodi skin) on this screen. */
    private fun s(v: Int) = playerView.px(v).toInt()

    private fun box(color: Int, radius: Int = 6) = GradientDrawable().apply { cornerRadius = s(radius).toFloat(); setColor(color) }

    /** A flat button like the Kodi card's: [idle] colour, white with dark text when focused. */
    private fun cardButton(text: String, idle: Int, idleText: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, s(24).toFloat())
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        setPadding(0, 0, 0, 0)
        isFocusable = true; isClickable = true
        background = box(idle); setTextColor(idleText)
        setOnFocusChangeListener { _, has ->
            background = if (has) box(Color.WHITE) else box(idle)
            setTextColor(if (has) 0xFF0A0B0F.toInt() else idleText)
            animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(120).start()
        }
        setOnClickListener { onClick() }
    }

    private fun label(size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, s(size).toFloat())
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        textDirection = View.TEXT_DIRECTION_RTL
    }

    /** Places [child] inside a FrameLayout at Kodi-skin coordinates (from the top-left). */
    private fun FrameLayout.at(child: View, left: Int, top: Int, width: Int, height: Int) =
        addView(child, FrameLayout.LayoutParams(s(width), s(height), Gravity.TOP or Gravity.LEFT).apply { leftMargin = s(left); topMargin = s(top) })

    private fun buildViews() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); layoutDirection = View.LAYOUT_DIRECTION_LTR }
        playerView = PlayerView(this).apply {
            keepScreenOn = true
            setShowSubtitleButton(true)
            controllerShowTimeoutMs = 4000
            isFocusable = true
            layoutDirection = View.LAYOUT_DIRECTION_LTR // the time bar reads left to right
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        // when the controls go away, give the remote back to the skip button / next card
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            // what is playing, top right, while the controls are up
            titleBox.animate().cancel()
            if (v == View.VISIBLE) { titleBox.visibility = View.VISIBLE; titleBox.animate().alpha(1f).setDuration(200).start() }
            else titleBox.animate().alpha(0f).setDuration(200).withEndAction { titleBox.visibility = View.GONE }.start()
            if (v != View.VISIBLE) when {
                nextCard.visibility == View.VISIBLE -> nextNow.requestFocus()
                skipButton.visibility == View.VISIBLE -> skipButton.requestFocus()
            }
        })
        // when the controls are up and the time bar is focused, left/right move 10 seconds (not 1/20 of the film)
        playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setKeyTimeIncrement(10_000)

        // Title: top right, over a soft shade, shown with the controls
        titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.RIGHT
            setPadding(s(60), s(44), s(60), s(80))
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB3000000.toInt(), 0x00000000))
            visibility = View.GONE; alpha = 0f
        }
        titleMain = label(44, Color.WHITE, true).apply { setShadowLayer(s(8).toFloat(), 0f, s(2).toFloat(), 0xCC000000.toInt()) }
        titleSub = label(28, 0xFFD5D9E2.toInt()).apply { setShadowLayer(s(6).toFloat(), 0f, s(2).toFloat(), 0xCC000000.toInt()) }
        titleBox.addView(titleMain, LinearLayout.LayoutParams(-1, -2))
        titleBox.addView(titleSub, LinearLayout.LayoutParams(-1, -2))
        root.addView(titleBox, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Seek preview: bottom of the screen, while stepping with left/right
        seekPreview = SeekPreview(this).apply { visibility = View.GONE }
        root.addView(seekPreview, FrameLayout.LayoutParams(-1, s(420), Gravity.BOTTOM))

        // Skip intro: bottom-right, 300x70 at 1560,930 like the Kodi skin
        skipButton = SkipButton(this).apply {
            visibility = View.GONE
            setOnClickListener {
                shownSkip?.let { it.done = true; player.seekTo((it.end * 1000).toLong()) }
                hideSkip()
            }
        }
        root.addView(skipButton, FrameLayout.LayoutParams(s(300), s(70), Gravity.BOTTOM or Gravity.RIGHT).apply {
            rightMargin = s(60); bottomMargin = s(80)
        })

        // Next episode: 640x230 card at 1220,790 — picture on the right, texts and buttons on its left
        nextCard = FrameLayout(this).apply {
            visibility = View.GONE
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = box(0xF2101318.toInt(), 10)
            clipToOutline = true
        }
        nextCard.at(View(this).apply { setBackgroundColor(Color.WHITE) }, 0, 0, 640, 4)
        nextStill = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = box(0xFF1A1D27.toInt())
            clipToOutline = true
        }
        nextCard.at(nextStill, 340, 24, 280, 158)
        nextCard.at(label(22, Color.WHITE, true).apply { text = "הפרק הבא" }, 20, 22, 300, 30)
        nextShow = label(30, Color.WHITE, true); nextCard.at(nextShow, 20, 50, 300, 38)
        nextEpisode = label(24, 0xFFB7BFCC.toInt()); nextCard.at(nextEpisode, 20, 88, 300, 32)
        nextCountdown = label(22, 0xFF7D8696.toInt()); nextCard.at(nextCountdown, 20, 122, 300, 30)
        nextNow = cardButton("▶ לפרק הבא", 0xE6FFFFFF.toInt(), 0xFF0A0B0F.toInt()) { nextDecision = "now"; hideNext(); playNext() }
        val cancel = cardButton("ביטול", 0x33FFFFFF, 0xFFB7BFCC.toInt()) { nextDecision = "cancel"; hideNext() }
        nextCard.at(nextNow, 146, 168, 174, 46)
        nextCard.at(cancel, 20, 168, 114, 46)
        nextNow.id = View.generateViewId(); cancel.id = View.generateViewId()
        nextNow.nextFocusLeftId = cancel.id; nextNow.nextFocusRightId = cancel.id
        cancel.nextFocusLeftId = nextNow.id; cancel.nextFocusRightId = nextNow.id
        nextNow.nextFocusUpId = nextNow.id; nextNow.nextFocusDownId = nextNow.id
        cancel.nextFocusUpId = cancel.id; cancel.nextFocusDownId = cancel.id
        root.addView(nextCard, FrameLayout.LayoutParams(s(640), s(230), Gravity.BOTTOM or Gravity.RIGHT).apply {
            rightMargin = s(60); bottomMargin = s(60)
        })
        setContentView(root)
    }
}
