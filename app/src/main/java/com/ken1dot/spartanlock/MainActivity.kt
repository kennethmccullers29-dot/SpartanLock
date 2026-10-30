package com.ken1dot.spartanlock

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val White = Color.White

class MainActivity : ComponentActivity() {
    companion object {
        var isPresented = false
        /** Screen woke from Glance: restart the Hello "looking" sequence. */
        fun helloWake() { HelloBus.recognized = false; HelloBus.wakeTick++ }
        var unlockInProgress = false
        const val EXTRA_DISMISS_KEYGUARD = "dismiss_keyguard"
    }

    private var lockScreenServiceEnabled by mutableStateOf(false)
    private var wallpaperImage by mutableStateOf<ImageBitmap?>(null)
    private var notificationAccessGranted by mutableStateOf(false)
    private var nextEvent by mutableStateOf<UpcomingCalendarEvent?>(null)
    private var unlockAttempt by mutableStateOf(false)
    private var credentialOnly = false
    private var credentialRequested = false
    private var helloPending = false
    private fun helloUnlock() {
        helloPending = true
        requestUnlock()
    }

    private fun openHome() {
        LockScreenAccessibilityService.suppressUntilScreenOff = true
        unlockInProgress = false
        finish()
    }

    private fun requestUnlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            openHome()
            return
        }
        if (Build.VERSION.SDK_INT < 26) {
            finish()
            return
        }
        unlockInProgress = true
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                if (helloPending) {
                    // Fingerprint/face/PIN accepted on the system bouncer: smile, greet, then slide away.
                    helloPending = false
                    HelloBus.recognized = true
                    window.decorView.postDelayed({ unlockAttempt = true }, 900L)
                } else openHome()
            }
            override fun onDismissCancelled() {
                helloPending = false
                unlockInProgress = false
                if (credentialOnly) finish() else unlockAttempt = false
            }
            override fun onDismissError() {
                helloPending = false
                unlockInProgress = false
                if (credentialOnly) finish() else unlockAttempt = false
            }
        })
    }

    private val callModeListener = android.media.AudioManager.OnModeChangedListener { stepAsideForCall() }

    /** Let the dialer's call screen show: never sit above a ringing / active call. */
    private fun stepAsideForCall() {
        if (!credentialOnly && !isFinishing && isCallActive() &&
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ) finish()
    }

    override fun onStart() {
        super.onStart()
        isPresented = true
        HelloBus.recognized = false
        HelloBus.requestSystemUnlock = ::helloUnlock
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.media.AudioManager::class.java)
                ?.addOnModeChangedListener(mainExecutor, callModeListener)
        }
        stepAsideForCall()
    }

    override fun onStop() {
        isPresented = false
        if (HelloBus.requestSystemUnlock != null) HelloBus.requestSystemUnlock = null
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.media.AudioManager::class.java)
                ?.removeOnModeChangedListener(callModeListener)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val service = ComponentName(this, LockScreenAccessibilityService::class.java)
        lockScreenServiceEnabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.split(':')?.contains(service.flattenToString()) == true
        lifecycleScope.launch {
            wallpaperImage = withContext(Dispatchers.IO) {
                loadLauncherWallpaper(this@MainActivity)
            }
        }
        val component = ComponentName(this, LockNotificationListener::class.java)
        notificationAccessGranted = if (Build.VERSION.SDK_INT >= 27) {
            getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component)
        } else {
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        }
    }

    private fun openNotificationAccessSettings() {
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (Build.VERSION.SDK_INT >= 30) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(this@MainActivity, LockNotificationListener::class.java)
                        .flattenToString()
                )
            }
            try {
                startActivity(detail)
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Some devices only provide the general notification access screen.
            }
        }
        startActivity(fallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialOnly = intent.getBooleanExtra(EXTRA_DISMISS_KEYGUARD, false)
        val calendarPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                lifecycleScope.launch {
                    nextEvent = withContext(Dispatchers.IO) {
                        CalendarEvents.next(this@MainActivity)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    nextEvent = withContext(Dispatchers.IO) {
                        CalendarEvents.next(this@MainActivity)
                    }
                    delay(60_000L)
                }
            }
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        if (Build.VERSION.SDK_INT >= 29) { window.isNavigationBarContrastEnforced = false; window.isStatusBarContrastEnforced = false }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        if (!credentialOnly) setContent {
            LockScreen(
                notificationAccessGranted = notificationAccessGranted,
                lockScreenServiceEnabled = lockScreenServiceEnabled,
                wallpaperImage = wallpaperImage,
                nextEvent = nextEvent,
                onEnableNotifications = ::openNotificationAccessSettings,
                onEnableLockScreen = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onCamera = { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
                unlockAttempt = unlockAttempt,
                onUnlockGesture = { unlockAttempt = true },
                onUnlockAnimationFinished = ::requestUnlock,
                onHome = { unlockAttempt = true },
                onSearch = { startActivity(Intent(Intent.ACTION_WEB_SEARCH)) }
            )
        }
        if (!credentialOnly && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            val preferences = getSharedPreferences("lock_permissions", MODE_PRIVATE)
            if (!preferences.getBoolean("calendar_permission_asked", false)) {
                preferences.edit().putBoolean("calendar_permission_asked", true).apply()
                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (credentialOnly && !credentialRequested) {
            credentialRequested = true
            requestUnlock()
        }
    }

}

@Composable
internal fun LockScreen(
    notificationAccessGranted: Boolean,
    lockScreenServiceEnabled: Boolean,
    wallpaperImage: ImageBitmap?,
    opaqueBackground: Boolean = false,
    nextEvent: UpcomingCalendarEvent?,
    onEnableNotifications: () -> Unit,
    onEnableLockScreen: () -> Unit,
    unlockAttempt: Boolean,
    onUnlockGesture: () -> Unit,
    onUnlockAnimationFinished: () -> Unit,
    onCamera: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    val context = LocalContext.current
    val themePreferences = remember(context) {
        context.getSharedPreferences("lock_theme", Context.MODE_PRIVATE)
    }
    fun readTheme() = LockTheme.fromStored(
        themePreferences.getString("theme", null),
        themePreferences.getBoolean("diagonal", false)
    )
    var theme by remember(themePreferences) { mutableStateOf(readTheme()) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var helloRetry by remember { mutableIntStateOf(0) }
    val biometricReady = remember { systemBiometricReady(context) }
    val helloState = rememberHelloState(onUnlocked = { showPin = false; onUnlockGesture() })
    val haptics = LocalHapticFeedback.current
    val openThemePicker = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        showThemePicker = true
    }
    DisposableEffect(themePreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme" || key == "diagonal") theme = readTheme()
        }
        themePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { themePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val themeReveal = remember { Animatable(1f) }
    LaunchedEffect(theme) {
        themeReveal.snapTo(0f)
        themeReveal.animateTo(1f, tween(600))
    }
    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timeFormat = remember(use24Hour) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L)
        }
    }

    val media = LockMediaStore.current
    val zuneTextEnabled = rememberZuneTextEnabled()
    val zuneMode = media?.isPlaying == true && zuneTextEnabled
    val lockDetailsAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (zuneMode) 0f else 1f,
        animationSpec = tween(400),
        label = "Lock screen details fade"
    )
    val swipeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            if (media?.artwork != null || wallpaperImage != null || opaqueBackground) Color.Black
            else Color.Transparent
        )
    ) {
        val screenHeight = maxHeight.value
        LaunchedEffect(unlockAttempt, screenHeight) {
            if (unlockAttempt) {
                swipeOffset.animateTo(-screenHeight, animationSpec = tween(320))
                onUnlockAnimationFinished()
            } else {
                swipeOffset.animateTo(0f, animationSpec = tween(260))
            }
        }
        Box(
            Modifier.fillMaxWidth().height(maxHeight)
                .offset { IntOffset(0, with(density) { swipeOffset.value.dp.roundToPx() }) }
                .semantics {
                    if (!unlockAttempt && !showThemePicker) {
                        onLongClick("Choose lock screen theme") {
                            openThemePicker()
                            true
                        }
                    }
                    customActions = if (unlockAttempt || showThemePicker) emptyList() else listOf(
                        CustomAccessibilityAction("Choose lock screen theme") {
                            openThemePicker()
                            true
                        }
                    )
                }
                .pointerInput(unlockAttempt, showThemePicker) {
                    if (!unlockAttempt && !showThemePicker) {
                        detectTapGestures(onLongPress = { openThemePicker() }, onTap = {
                            scope.launch {
                                swipeOffset.animateTo(-14f, tween(110))
                                swipeOffset.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        })
                    }
                }
                .pointerInput(unlockAttempt, screenHeight, showThemePicker) {
                    if (!unlockAttempt && !showThemePicker) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    swipeOffset.snapTo((swipeOffset.value + dragAmount / density.density).coerceIn(-screenHeight, 0f))
                                }
                            },
                            onDragEnd = {
                                if (swipeOffset.value < -96f) {
                                    if (biometricReady && HelloBus.requestSystemUnlock != null) { HelloBus.requestSystemUnlock?.invoke(); scope.launch { swipeOffset.animateTo(0f, tween(220)) } }
                                    else if (PinStore.hasPin(context) && !biometricReady) { showPin = true; scope.launch { swipeOffset.animateTo(0f, tween(220)) } }
                                    else onUnlockGesture()
                                }
                                else scope.launch { swipeOffset.animateTo(0f, tween(220)) }
                            },
                            onDragCancel = {
                                scope.launch { swipeOffset.animateTo(0f, tween(220)) }
                            }
                        )
                    }
                }
        ) {
        LockMediaArtwork(media, wallpaperImage)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = if (theme == LockTheme.CLASSIC) 0.45f else 0.08f),
                    0.38f to Color.Black.copy(alpha = if (theme == LockTheme.CLASSIC) 0.2f else 0.03f),
                    0.7f to Color.Black.copy(alpha = if (theme == LockTheme.CLASSIC) 0.35f else 0.04f),
                    1f to Color.Black.copy(alpha = if (zuneMode) 0.35f else if (theme == LockTheme.CLASSIC) 0.78f else 0.28f)
                )
            )
        )

        if (theme != LockTheme.CLASSIC && lockDetailsAlpha > 0f) {
            LiveLockClock(
                theme = theme,
                now = now,
                use24Hour = use24Hour,
                progress = {
                    if (theme.hasSpecialUnlock)
                        maxOf(liveLockSwipeProgress(swipeOffset.value, screenHeight),
                            (1f - themeReveal.value) * 0.55f) else 0f
                },
                wallpaper = media?.artwork ?: wallpaperImage,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = lockDetailsAlpha * if (theme.hasSpecialUnlock) 1f
                        else (1f + swipeOffset.value / 180f).coerceIn(0f, 1f)
                },
                bottomClearance = (if (nextEvent != null) 64f else 0f) +
                    (if (!lockScreenServiceEnabled) 24f else 0f),
                animate = !showThemePicker
            )
        }

        if (media?.artwork != null && zuneTextEnabled && lockDetailsAlpha < 1f) {
            AlbumArtGradient(media.artwork, 1f - lockDetailsAlpha)
        }
        if (media != null && zuneTextEnabled && lockDetailsAlpha < 1f) {
            ZuneTypography(
                media,
                Modifier.fillMaxWidth()
                    .fillMaxSize()
                    .padding(top = 190.dp)
                    .graphicsLayer {
                        alpha = (1f - lockDetailsAlpha) * (1f + swipeOffset.value / 180f).coerceIn(0f, 1f)
                    }
            )
        }
        if (media != null) {
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 96.dp, start = 26.dp, end = 26.dp)
                    .graphicsLayer {
                        alpha = (1f + swipeOffset.value / 180f).coerceIn(0f, 1f)
                    }
            ) {
                LockMediaControls(media)
            }
        }

        if (lockDetailsAlpha > 0f) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(bottom = 35.dp, start = 26.dp, end = 26.dp)
                .graphicsLayer {
                    alpha = lockDetailsAlpha * (1f + swipeOffset.value / 180f).coerceIn(0f, 1f)
                }
        ) {
            if (theme == LockTheme.CLASSIC) {
            Text(
                text = timeFormat.format(now),
                color = White,
                fontSize = 96.sp,
                fontFamily = SelawikFamily,
                fontWeight = FontWeight.Thin,
                lineHeight = 100.sp,
                letterSpacing = (-1).sp,
                modifier = Modifier.offset(x = (-4).dp)
            )
            Text(
                text = dateFormat.format(now),
                color = White,
                fontSize = 30.sp,
                fontFamily = SelawikFamily,
                fontWeight = FontWeight.Light,
                lineHeight = 38.sp
            )
            Spacer(Modifier.height(36.dp))
            }
            Box(Modifier.heightIn(min = 44.dp)) {
                if (nextEvent != null) {
                    Column {
                        Text(
                            nextEvent.title,
                            color = White,
                            fontFamily = SelawikFamily,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nextEvent.location.isNotBlank()) Text(
                            nextEvent.location,
                            color = White,
                            fontFamily = SelawikFamily,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            nextEvent.timeLabel(use24Hour),
                            color = White,
                            fontFamily = SelawikFamily,
                            fontSize = 17.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.height(30.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!notificationAccessGranted) {
                    Text(
                        text = "Enable notification access",
                        color = White,
                        fontFamily = SelawikFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(
                            onClickLabel = "Open notification access settings",
                            onClick = onEnableNotifications
                        )
                    )
                } else {
                    QuickStatusStore.items.forEach { status ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val glyph = Mdl2.forPackage(status.packageName)
                            if (glyph != null) {
                                androidx.compose.foundation.Image(
                                    imageVector = glyph,
                                    contentDescription = status.packageName,
                                    modifier = Modifier.size(19.dp)
                                )
                            } else {
                                Image(
                                    bitmap = status.icon,
                                    contentDescription = status.packageName,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                status.count.toString(),
                                color = White,
                                fontFamily = SelawikFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!lockScreenServiceEnabled) {
                Text(
                    text = "Enable lock screen overlay",
                    color = White,
                    fontFamily = SelawikFamily,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(
                        onClickLabel = "Open accessibility settings for SpartanLock",
                        onClick = onEnableLockScreen
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        }

        }

        if (showPin) PinPad(onSuccess = { showPin = false; onUnlockGesture() }, onCancel = { showPin = false })
        if (lockDetailsAlpha > 0f && !showPin && media == null) HelloHeader(state = helloState, modifier = Modifier.align(Alignment.TopCenter).padding(top = 84.dp).clickable(enabled = PinStore.hasPin(context), onClickLabel = "Use PIN") { showPin = true }.graphicsLayer { alpha = lockDetailsAlpha * (1f + swipeOffset.value / 180f).coerceIn(0f, 1f) })
        AnimatedVisibility(
            visible = showThemePicker,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240)),
            exit = fadeOut(tween(140))
        ) {
            LockThemePicker(
                selected = theme,
                wallpaper = wallpaperImage,
                now = now,
                use24Hour = use24Hour,
                onApply = { selected ->
                    themePreferences.edit().putString("theme", selected.id).apply()
                    theme = selected
                    showThemePicker = false
                },
                onCancel = { showThemePicker = false }
            )
        }
    }
}

@Composable
private fun Shortcut(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(width = 96.dp, height = 52.dp).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun CameraGlyph() {
    Canvas(Modifier.size(21.dp)) {
        val stroke = 1.3.dp.toPx()
        drawRect(White, topLeft = Offset(size.width * 0.12f, size.height * 0.27f),
            size = Size(size.width * 0.76f, size.height * 0.58f), style = Stroke(stroke))
        drawRect(White, topLeft = Offset(size.width * 0.3f, size.height * 0.18f),
            size = Size(size.width * 0.28f, size.height * 0.09f))
        drawCircle(White, radius = size.width * 0.18f,
            center = Offset(size.width * 0.5f, size.height * 0.56f), style = Stroke(stroke))
    }
}

@Composable
private fun WindowsGlyph() {
    Canvas(Modifier.size(19.dp)) {
        val gap = 1.4.dp.toPx()
        val half = (size.width - gap) / 2f
        drawRect(White, size = Size(half, half))
        drawRect(White, topLeft = Offset(half + gap, 0f), size = Size(half, half))
        drawRect(White, topLeft = Offset(0f, half + gap), size = Size(half, half))
        drawRect(White, topLeft = Offset(half + gap, half + gap), size = Size(half, half))
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.3.dp.toPx()
        drawCircle(White, radius = size.width * 0.29f,
            center = Offset(size.width * 0.58f, size.height * 0.4f), style = Stroke(stroke))
        drawLine(White, Offset(size.width * 0.37f, size.height * 0.63f),
            Offset(size.width * 0.08f, size.height * 0.92f), strokeWidth = stroke)
    }
}
