package com.ken1dot.spartanlock

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockScreenAccessibilityService : AccessibilityService(),
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        var suppressUntilScreenOff = false
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: ComposeView? = null
    private var receiverRegistered = false
    private var unlockAttempt by mutableStateOf(false)
    private var wallpaperImage by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    private var nextEvent by mutableStateOf<UpcomingCalendarEvent?>(null)
    private var notificationAccessGranted by mutableStateOf(false)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> when {
                    // Samsung dozes the display under Glance; a power press / tap wakes it from doze.
                    glanceView != null && !glanceWaking &&
                        android.os.SystemClock.uptimeMillis() - glanceShownAt > 1200L -> wakeFromGlance(viaPower = false)
                    glanceView == null && !glanceWaking -> handler.postDelayed(::showIfLocked, 250L)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    suppressUntilScreenOff = false
                    hideOverlay()
                    handler.removeCallbacks(showGlanceRunnable)
                    if (glanceView != null) {
                        // W10M: power while glancing wakes to the lock screen (never off, never unlock).
                        wakeFromGlance(viaPower = true)
                    } else if (glanceTimedOut) {
                        glanceTimedOut = false // let the screen really sleep after the Glance timeout
                    } else if (GlanceActivity.enabled(context) && !context.isCallActive() &&
                        getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                        handler.postDelayed(showGlanceRunnable, 300L)
                    }
                }
                Intent.ACTION_USER_PRESENT -> { handler.removeCallbacks(showGlanceRunnable); hideGlance(); hideOverlay() }
            }
        }
    }

    private val modeListener = android.media.AudioManager.OnModeChangedListener {
        handler.post {
            if (isCallActive()) { hideGlance(); hideOverlay() } else handler.postDelayed(::showIfLocked, 600L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.media.AudioManager::class.java)
                ?.addOnModeChangedListener(mainExecutor, modeListener)
        }
        scope.launch {
            while (true) {
                if (overlay != null && checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    nextEvent = withContext(Dispatchers.IO) {
                        CalendarEvents.next(this@LockScreenAccessibilityService)
                    }
                }
                delay(60_000L)
            }
        }
        showIfLocked()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName == "com.android.systemui"
        ) {
            handler.postDelayed(::showIfLocked, 150L)
        }
    }

    private fun showIfLocked() {
        if (glanceView != null || overlay != null || MainActivity.isPresented || MainActivity.unlockInProgress ||
            suppressUntilScreenOff || isCallActive()) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        if (!keyguard.isKeyguardLocked || !power.isInteractive) return

        unlockAttempt = false
        notificationAccessGranted = if (Build.VERSION.SDK_INT >= 27) {
            getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(
                android.content.ComponentName(this, LockNotificationListener::class.java)
            )
        } else {
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(packageName)
        }
        scope.launch {
            wallpaperImage = withContext(Dispatchers.IO) {
                loadLauncherWallpaper(this@LockScreenAccessibilityService)
            }
            if (checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                nextEvent = withContext(Dispatchers.IO) {
                    CalendarEvents.next(this@LockScreenAccessibilityService)
                }
            }
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LockScreenAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LockScreenAccessibilityService)
            setViewTreeViewModelStoreOwner(this@LockScreenAccessibilityService)
            setContent {
                LockScreen(
                    notificationAccessGranted = notificationAccessGranted,
                    lockScreenServiceEnabled = true,
                    wallpaperImage = wallpaperImage,
                    opaqueBackground = true,
                    nextEvent = nextEvent,
                    onEnableNotifications = {
                        openExternal(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onEnableLockScreen = {},
                    unlockAttempt = unlockAttempt,
                    onUnlockGesture = { unlockAttempt = true },
                    onUnlockAnimationFinished = ::openCredentialActivity,
                    onCamera = { openExternal(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
                    onHome = { unlockAttempt = true },
                    onSearch = { openExternal(Intent(Intent.ACTION_WEB_SEARCH)) }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = 0
            }
        }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
            handler.post {
                if (overlay == view && !MainActivity.isPresented) {
                    try {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                    } catch (_: RuntimeException) {
                        // The overlay remains visible if this device rejects the activity launch.
                    }
                    handler.postDelayed({
                        if (MainActivity.isPresented && overlay == view) hideOverlay()
                    }, 400L)
                }
            }
        } catch (_: RuntimeException) {
            view.disposeComposition()
        }
    }

    private fun openCredentialActivity() {
        MainActivity.unlockInProgress = true
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DISMISS_KEYGUARD, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            hideOverlay()
        } catch (_: RuntimeException) {
            MainActivity.unlockInProgress = false
            unlockAttempt = false
        }
    }

    private fun openExternal(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            hideOverlay()
        } catch (_: RuntimeException) {
            // Keep the lock screen visible if no activity handles the shortcut.
        }
    }

    private var glanceView: ComposeView? = null
    private val glanceTimeout = Runnable { glanceTimedOut = true; hideGlance() }
    private val showGlanceRunnable = Runnable { showGlance() }
    private var glanceTimedOut = false
    private var glanceWaking = false
    private var glanceShownAt = 0L
    private var glanceParams: WindowManager.LayoutParams? = null

    /** Keep the lock screen activity under Glance: it occludes the keyguard (so Samsung face/fingerprint
     *  don't silently unlock in the dark) and makes waking a simple crossfade. */
    private fun ensureLockActivityUnderGlance() {
        if (MainActivity.isPresented || MainActivity.unlockInProgress) return
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
        }
    }

    /** Glance -> lock screen: brightness fades up while Glance fades out, then it's removed. */
    private fun wakeFromGlance(viaPower: Boolean) {
        val v = glanceView ?: return
        if (glanceWaking) return
        glanceWaking = true
        handler.removeCallbacks(glanceTimeout)
        if (viaPower) {
            // The power key just turned the display off: bring it straight back on.
            @Suppress("DEPRECATION")
            runCatching {
                getSystemService(PowerManager::class.java).newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE, "SpartanLock:wake").acquire(500L)
            }
        }
        MainActivity.helloWake()
        val wm = getSystemService(WindowManager::class.java)
        val p = glanceParams
        // Brightness straight back to the user's level; lock screen is already composed underneath.
        if (p != null) {
            p.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { wm.updateViewLayout(v, p) }
        }
        ensureLockActivityUnderGlance()
        v.animate().alpha(0f).setDuration(120L).withEndAction {
            if (glanceView === v) hideGlance()
            glanceWaking = false
            if (!MainActivity.isPresented) showIfLocked()
        }.start()
    }

    /** W10M Glance: black, dim overlay window drawn by this service (no activity, so nothing can cover it). */
    private fun showGlance() {
        if (glanceView != null || isCallActive()) return
        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        glanceWaking = false
        glanceShownAt = android.os.SystemClock.uptimeMillis()
        val power = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        runCatching {
            power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "SpartanLock:glance")
                .acquire(1500L)
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LockScreenAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@LockScreenAccessibilityService)
            setViewTreeViewModelStoreOwner(this@LockScreenAccessibilityService)
            setContent { GlanceScreen(onWake = { if (glanceView != null && !glanceWaking) wakeFromGlance(viaPower = false) }) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            screenBrightness = 0.01f
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            glanceView = view
            glanceParams = params
            ensureLockActivityUnderGlance()
            handler.removeCallbacks(glanceTimeout)
            handler.postDelayed(glanceTimeout, 30 * 60_000L)
        } catch (_: RuntimeException) { view.disposeComposition() }
    }

    private fun hideGlance() {
        handler.removeCallbacks(glanceTimeout)
        glanceParams = null
        glanceView?.let { v -> glanceView = null; runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
    }

    private fun hideOverlay() {
        overlay?.let { view ->
            overlay = null
            getSystemService(WindowManager::class.java).removeView(view)
        }
        unlockAttempt = false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.media.AudioManager::class.java)
                ?.removeOnModeChangedListener(modeListener)
        }
        hideOverlay()
        scope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
