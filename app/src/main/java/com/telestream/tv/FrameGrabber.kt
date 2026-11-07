package com.telestream.tv

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pictures for the seek bar, taken straight from the stream (nothing new needed on the server).
 * Only the latest wanted time is fetched; it is best effort and may be slow on big files.
 */
class FrameGrabber(private val onFrame: (Bitmap) -> Unit) {
    private var retriever: MediaMetadataRetriever? = null
    private var job: Job? = null
    private val wanted = Channel<Long>(Channel.CONFLATED)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun open(url: String, scope: CoroutineScope) {
        close()
        job = scope.launch(Dispatchers.IO) {
            val r = runCatching { MediaMetadataRetriever().apply { setDataSource(url, HashMap()) } }.getOrNull() ?: return@launch
            retriever = r
            for (ms in wanted) {
                val bmp = runCatching {
                    if (Build.VERSION.SDK_INT >= 27)
                        r.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 216)
                    else r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull() ?: continue
                withContext(Dispatchers.Main) { onFrame(bmp) }
            }
        }
    }

    fun want(ms: Long) { wanted.trySend(ms) }

    fun close() {
        job?.cancel(); job = null
        val r = retriever; retriever = null
        if (r != null) Thread { runCatching { r.release() } }.start()
    }
}
