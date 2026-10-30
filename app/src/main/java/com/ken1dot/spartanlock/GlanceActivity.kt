package com.ken1dot.spartanlock

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * W10M Glance screen. Android won't let apps replace the system AOD, so this is a
 * pure-black, minimum-brightness activity shown over the keyguard when the screen
 * goes off (if enabled). OLED pixels that are black draw no power; text is dim grey
 * and moves every minute to avoid burn-in. Tap to wake to the lock screen.
 */
class GlanceActivity : ComponentActivity() {
    companion object {
        fun enabled(c: Context) = c.getSharedPreferences("glance", Context.MODE_PRIVATE).getBoolean("enabled", false)
        fun setEnabled(c: Context, on: Boolean) = c.getSharedPreferences("glance", Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { GlanceScreen(onWake = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        if (isCallActive()) finish()
    }
}

@Composable
internal fun GlanceScreen(onWake: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = Date(); shift = (shift + 1) % 6
            if (context.isCallActive()) onWake()
        }
    }
    val use24 = android.text.format.DateFormat.is24HourFormat(context)
    val dim = Color(0xFF8A8A8A)
    // small drift pattern, max ~24dp
    val dx = intArrayOf(0, 12, 24, 16, 4, 20)[shift].dp
    val dy = intArrayOf(0, 18, 8, 30, 22, 12)[shift].dp
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onWake() }
    }) {
        Column(Modifier.align(Alignment.TopStart).padding(start = 24.dp + dx, top = 150.dp + dy)) {
            Text(SimpleDateFormat(if (use24) "HH:mm" else "h:mm", Locale.getDefault()).format(now),
                color = dim, fontFamily = SelawikFamily, fontWeight = FontWeight.Thin,
                fontSize = 72.sp, lineHeight = 78.sp)
            Text(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now),
                color = dim, fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = 20.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                QuickStatusStore.items.forEach { s ->
                    val glyph = Mdl2.forPackage(s.packageName)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (glyph != null) Image(glyph, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(dim))
                        else Image(s.icon, null, Modifier.size(16.dp), alpha = 0.5f)
                        Text(s.count.toString(), color = dim, fontFamily = SelawikFamily, fontSize = 15.sp,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}