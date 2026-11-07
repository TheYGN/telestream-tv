package com.telestream.tv

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity

/**
 * A title's trailer. The TV's YouTube app plays it best (remote control, quality), so that goes first;
 * without one, a full-screen YouTube player in a web view.
 */
class TrailerActivity : ComponentActivity() {
    companion object {
        fun open(context: Context, key: String) {
            val tries = listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")),
            )
            for (intent in tries) {
                try {
                    context.startActivity(intent)
                    return
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) {
                }
            }
            context.startActivity(Intent(context, TrailerActivity::class.java).putExtra("key", key))
        }
    }

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = Uri.encode(intent.getStringExtra("key") ?: "")
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
        }
        setContentView(web)
        val html = """<html><body style="margin:0;background:#000">
            <iframe style="position:fixed;inset:0;width:100%;height:100%;border:0"
              src="https://www.youtube-nocookie.com/embed/$key?autoplay=1&rel=0&hl=he"
              allow="autoplay; encrypted-media; fullscreen" allowfullscreen></iframe></body></html>"""
        web.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "utf-8", null)
    }

    override fun onDestroy() {
        web.destroy() // stops the sound
        super.onDestroy()
    }
}
