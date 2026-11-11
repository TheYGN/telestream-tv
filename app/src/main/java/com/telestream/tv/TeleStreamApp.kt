package com.telestream.tv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TeleStreamApp : Application(), coil.ImageLoaderFactory {
    /** Pictures fade in instead of popping. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).crossfade(200).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listener: Job? = null
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        // Listen whenever any of our screens is on the TV (also while a video plays), like Kodi does while it is open.
        // That keeps this TV "דלוקה" in the library page's list, and a play sent from the phone replaces what is playing.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0 && listener?.isActive != true) listener = scope.launch { listenForPlays() }
            }
            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) { listener?.cancel(); listener = null }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Tell the server this TV exists, then keep asking (long-poll) for something to play. */
    private suspend fun listenForPlays() {
        var announced: Pair<String, String>? = null
        while (scope.isActive) {
            if (!Prefs.isSetUp) { delay(3000); continue }
            try {
                val me = Prefs.server to Prefs.tvName
                if (announced != me) { Api.hello(); announced = me }
                Api.waitForPlay(20)?.let { PlayerActivity.start(this, it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message?.contains("404") == true) announced = null // the server forgot this TV
                delay(10_000)
            }
        }
    }
}
