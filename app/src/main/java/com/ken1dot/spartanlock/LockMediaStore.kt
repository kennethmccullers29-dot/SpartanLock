package com.ken1dot.spartanlock

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal data class LockMedia(
    val title: String,
    val artist: String,
    val artwork: ImageBitmap?,
    val artistArtwork: ImageBitmap? = null,
    val isPlaying: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean
)

internal object LockMediaStore {
    var current by mutableStateOf<LockMedia?>(null)
        private set

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controllers: List<MediaController> = emptyList()
    private var selected: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private val hiRes = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val loading = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refreshSessions()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener {
        attach(it.orEmpty())
    }

    fun connect(context: Context) {
        disconnect()
        appContext = context.applicationContext
        val sessionManager = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, LockNotificationListener::class.java)
        manager = sessionManager
        component = listener
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            attach(sessionManager.getActiveSessions(listener))
        } catch (_: SecurityException) {
            current = null
        }
    }

    fun refreshSessions() {
        val sessionManager = manager ?: return
        val listener = component ?: return
        try {
            attach(sessionManager.getActiveSessions(listener))
        } catch (_: SecurityException) {
            attach(emptyList())
        }
    }

    fun disconnect() {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
        selected = null
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        manager = null
        component = null
        current = null
    }

    private fun attach(sessions: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = sessions
        controllers.forEach { it.registerCallback(callback, handler) }
        publish()
    }

    private fun publish() {
        val available = controllers.filter { controller ->
            controller.playbackState?.state in setOf(
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_BUFFERING
            )
        }
        selected = available.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: available.firstOrNull()
        val controller = selected
        val metadata = controller?.metadata
        if (controller == null || metadata == null) {
            current = null
            return
        }
        // Prefer the largest embedded bitmap, then upgrade from the full-res content uri when the
        // session only ships a thumbnail (most players send ~300-500px bitmaps).
        val embedded = listOfNotNull(
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ).maxByOrNull { it.width * it.height }
        val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        val upgraded = artUri?.let { uriBitmap(it) }
        val bitmap = listOfNotNull(embedded, upgraded).maxByOrNull { it.width * it.height }
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artwork = bitmap?.let(::scaledArtwork)?.asImageBitmap()
        // Artist image: the display icon when it differs from the album art (YTM/Spotify often send the
        // artist/channel picture there), otherwise its uri.
        val icon = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)?.takeIf { it != artUri }?.let { uriBitmap(it) }
        val artistArtwork = icon?.takeIf { bitmap != null && !it.sameAs(bitmap) && it.width >= 256 }
            ?.let(::scaledArtwork)?.asImageBitmap()
        val actions = controller.playbackState?.actions ?: 0L
        current = LockMedia(
            title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: "Unknown track",
            artist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: "",
            artwork = artwork,
            artistArtwork = artistArtwork,
            isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
        )
    }

    /** Cached full-res bitmap for a local (content/file/resource) uri; loads off-thread then republishes. */
    private fun uriBitmap(uri: String): Bitmap? {
        hiRes[uri]?.let { return it }
        val context = appContext ?: return null
        val parsed = android.net.Uri.parse(uri)
        if (parsed.scheme !in setOf("content", "file", "android.resource")) return null
        if (!loading.add(uri)) return null
        io.execute {
            try {
                val resolver = context.contentResolver
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
                val decoded = resolver.openInputStream(parsed)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                }
                if (decoded != null) {
                    if (hiRes.size > 6) hiRes.clear()
                    hiRes[uri] = decoded
                    handler.post { publish() }
                }
            } catch (_: Exception) {
            } finally { loading.remove(uri) }
        }
        return null
    }

    private fun scaledArtwork(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= 2400) return bitmap
        val scale = 2400f / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    fun previous() {
        selected?.transportControls?.skipToPrevious()
    }

    fun togglePlayback() {
        selected?.let {
            if (it.playbackState?.state == PlaybackState.STATE_PLAYING) {
                it.transportControls.pause()
            } else {
                it.transportControls.play()
            }
        }
    }

    fun next() {
        selected?.transportControls?.skipToNext()
    }
}
