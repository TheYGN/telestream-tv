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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private lateinit var skipButton: Button
    private lateinit var nextCard: LinearLayout
    private lateinit var nextTitle: TextView
    private lateinit var nextCountdown: TextView
    private lateinit var nextNow: Button

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
        skips.clear(); shownSkip = null; outro = null; upcoming = null
        nextDecision = null; nextOffered = false
        hideSkip(); nextCard.visibility = View.GONE
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
        skipButton.text = active.label
        skipButton.visibility = View.VISIBLE
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
            nextCard.visibility = View.GONE
            nextOffered = false; nextDecision = null
            playerView.requestFocus()
        }
        if (nextDecision != null || total < lead * 3 || left > lead) return
        if (!nextOffered) {
            nextOffered = true
            nextTitle.text = "${next.show}\nעונה ${next.season} פרק ${next.episode}" + (if (next.name.isNotBlank()) " · ${next.name}" else "")
            nextCard.visibility = View.VISIBLE
            playerView.hideController()
            nextNow.requestFocus()
        }
        val s = left.toInt().coerceAtLeast(0)
        nextCountdown.text = String.format(Locale.US, "מתחיל אוטומטית בעוד %d:%02d", s / 60, s % 60)
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
                nextDecision = "cancel"; nextCard.visibility = View.GONE; playerView.requestFocus(); return true
            }
            if (skipButton.visibility == View.VISIBLE) {
                shownSkip?.done = true; hideSkip(); return true
            }
            if (playerView.isControllerFullyVisible) {
                playerView.hideController(); return true
            }
        }
        // Any key while the controls are hidden: show them (OK pauses too), like a TV remote expects
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible &&
            !skipButton.hasFocus() && !nextCard.hasFocus()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    player.playWhenReady = !player.playWhenReady; playerView.showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> { player.seekBack(); playerView.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player.seekForward(); playerView.showController(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        reportNow()
        player.pause()
    }

    override fun onDestroy() {
        watcher?.cancel()
        player.release()
        super.onDestroy()
    }

    // ---------- views ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun pill(color: Int) = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(color) }

    private fun tvButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(dp(20), dp(8), dp(20), dp(8))
        isFocusable = true
        val normal = pill(0xCC1E2230.toInt())
        val focused = pill(Color.WHITE)
        background = normal
        setTextColor(Color.WHITE)
        setOnFocusChangeListener { _, has ->
            background = if (has) focused else normal
            setTextColor(if (has) Color.BLACK else Color.WHITE)
        }
        setOnClickListener { onClick() }
    }

    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        playerView = PlayerView(this).apply {
            keepScreenOn = true
            setShowSubtitleButton(true)
            controllerShowTimeoutMs = 4000
            isFocusable = true
            layoutDirection = View.LAYOUT_DIRECTION_LTR // the time bar reads left to right
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        skipButton = tvButton("") {
            shownSkip?.let { it.done = true; player.seekTo((it.end * 1000).toLong()) }
            hideSkip()
        }.apply { visibility = View.GONE }
        root.addView(skipButton, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(48), dp(48), dp(48), dp(110))
        })

        nextCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(0xEE11131A.toInt())
            setPadding(dp(24), dp(18), dp(24), dp(18))
            visibility = View.GONE
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        nextCard.addView(TextView(this).apply {
            text = "הפרק הבא"; setTextColor(0xFF3EA6FF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        nextTitle = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTypeface(typeface, Typeface.BOLD)
        }
        nextCard.addView(nextTitle)
        nextCountdown = TextView(this).apply { setTextColor(0xFFB0B4C0.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        nextCard.addView(nextCountdown)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        nextNow = tvButton("▶ נגן עכשיו") { nextDecision = "now"; nextCard.visibility = View.GONE; playNext() }
        row.addView(nextNow)
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        row.addView(tvButton("ביטול") { nextDecision = "cancel"; nextCard.visibility = View.GONE; playerView.requestFocus() })
        nextCard.addView(row)
        root.addView(nextCard, FrameLayout.LayoutParams(dp(420), -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(48), dp(48), dp(48), dp(110))
        })
        setContentView(root)
    }
}
