package com.ken1dot.spartanlock

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

internal val LockAccent = Color(0xFF0078D7)

/** Salted SHA-256 PIN for the SpartanLock overlay only. The system keyguard stays the real lock. */
internal object PinStore {
    private fun prefs(c: Context) = c.getSharedPreferences("lock_pin", Context.MODE_PRIVATE)
    private fun hash(salt: String, pin: String) = MessageDigest.getInstance("SHA-256")
        .digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    fun hasPin(c: Context) = prefs(c).contains("hash")
    fun set(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs(c).edit().putString("salt", salt).putString("hash", hash(salt, pin)).apply()
    }
    fun clear(c: Context) = prefs(c).edit().remove("salt").remove("hash").apply()
    fun check(c: Context, pin: String): Boolean {
        val p = prefs(c); val salt = p.getString("salt", null) ?: return true
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), (p.getString("hash", "") ?: "").toByteArray())
    }
    fun helloName(c: Context) = prefs(c).getString("hello_name", "") ?: ""
    fun setHelloName(c: Context, n: String) = prefs(c).edit().putString("hello_name", n.trim().take(40)).apply()
}

internal enum class HelloState { IDLE, SEARCHING, HINT, SUCCESS, FAILED }

/** Bridge between MainActivity (system keyguard dismiss) and the Hello face. */
internal object HelloBus {
    var recognized by mutableStateOf(false)
    var wakeTick by mutableStateOf(0)
    /** Set by MainActivity: show the system unlock (fingerprint/face active there). */
    var requestSystemUnlock: (() -> Unit)? = null
}

/**
 * TrueSight face (W10M iris-style). Idle: smiling, blinks now and then.
 * Searching: flat mouth, eyes sweep side to side. Success: mouth springs into a smile,
 * greeting slides in. Failed: frown-ish flat mouth and a horizontal shake.
 */
@Composable
internal fun HelloHeader(modifier: Modifier = Modifier, state: HelloState = HelloState.IDLE) {
    val ctx = LocalContext.current
    val name = remember { PinStore.helloName(ctx) }
    val t = rememberInfiniteTransition(label = "hello")
    val scan by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scan")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Restart), label = "pulse")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 3600; 1f at 0; 1f at 3260; 0.1f at 3360 using FastOutSlowInEasing; 1f at 3500 using LinearOutSlowInEasing
    }), label = "blink")
    val smile by animateFloatAsState(
        when (state) { HelloState.SEARCHING, HelloState.FAILED, HelloState.HINT -> 0f; else -> 1f },
        spring(dampingRatio = 0.6f, stiffness = 180f), label = "smile")
    val scanAmount by animateFloatAsState(if (state == HelloState.SEARCHING) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "scanAmt")
    val glow = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            HelloState.SUCCESS -> coroutineScope {
                launch { pop.snapTo(0.88f); pop.animateTo(1.08f, tween(180, easing = FastOutSlowInEasing)); pop.animateTo(1f, spring(0.45f, 220f)) }
                launch { glow.snapTo(0f); glow.animateTo(1f, tween(650, easing = LinearOutSlowInEasing)) }
            }
            HelloState.FAILED -> { for (x in listOf(12f, -10f, 7f, -4f, 2f)) shake.animateTo(x, tween(70, easing = FastOutSlowInEasing)); shake.animateTo(0f, spring(0.5f, 300f)) }
            else -> Unit
        }
    }
    Column(modifier.graphicsLayer { translationX = shake.value * density },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(48.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            val w = size.width; val s = 2.2.dp.toPx()
            // TrueSight ring: soft pulse while scanning, bright expanding ring on success.
            if (scanAmount > 0.01f) drawCircle(Color.White.copy(alpha = 0.35f * (1f - pulse) * scanAmount),
                radius = w * (0.5f + 0.25f * pulse), style = Stroke(1.5.dp.toPx()))
            if (glow.value in 0.001f..0.999f) drawCircle(Color.White.copy(alpha = 0.6f * (1f - glow.value)),
                radius = w * (0.5f + 0.4f * glow.value), style = Stroke(2.dp.toPx()))
            val eyeX = scan * scanAmount * w * 0.07f
            val eyeH = s * 1.3f * (if (state == HelloState.SEARCHING) 1f else blink)
            for (cx in listOf(0.32f, 0.68f)) {
                drawOval(Color.White, topLeft = Offset(w * cx + eyeX - s * 1.3f, w * 0.34f - eyeH),
                    size = Size(s * 2.6f, eyeH * 2f))
            }
            // Mouth: a flat line (smile=0) morphing to a 140deg arc (smile=1).
            val sweep = 2f + 138f * smile
            val h = w * (0.02f + 0.54f * smile)
            drawArc(Color.White, startAngle = 90f - sweep / 2f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(w * 0.18f, w * 0.72f - h), size = Size(w * 0.64f, h),
                style = Stroke(s, cap = StrokeCap.Round))
        }
        val text = when (state) {
            HelloState.SEARCHING -> "TrueSight is looking\u2026"
            HelloState.FAILED -> "TrueSight couldn\u2019t recognize you"
            HelloState.HINT -> "Swipe up, or tap the face for PIN"
            else -> if (name.isBlank()) "Hello" else "Hello $name"
        }
        AnimatedContent(text, transitionSpec = {
            (fadeIn(tween(320, 120)) + slideInVertically(tween(420, 120, easing = LinearOutSlowInEasing)) { it / 2 }) togetherWith
                (fadeOut(tween(160)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 3 })
        }, label = "greeting") { value ->
            Text(value, color = Color.White, fontFamily = SelawikFamily,
                fontWeight = FontWeight.Light, fontSize = 22.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

/** True when the phone has fingerprint/face enrolled for the system lock. */
internal fun systemBiometricReady(ctx: Context): Boolean = android.os.Build.VERSION.SDK_INT >= 30 &&
    ctx.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
        ?.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS

/**
 * TrueSight without any prompt: Samsung's own fingerprint/face unlock runs as usual and
 * SpartanLock just reacts inline. While locked the smiley scans; when the keyguard unlocks
 * (ACTION_USER_PRESENT or isKeyguardLocked flips) it smiles, greets, then calls onUnlocked.
 */
@Composable
internal fun rememberHelloState(onUnlocked: () -> Unit): HelloState {
    val ctx = LocalContext.current
    val unlocked by rememberUpdatedState(onUnlocked)
    val biometric = remember { systemBiometricReady(ctx) }
    var state by remember { mutableStateOf(if (biometric) HelloState.SEARCHING else HelloState.IDLE) }
    fun recognized() {
        if (state == HelloState.SUCCESS) return
        state = HelloState.SUCCESS
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ unlocked() }, 900L)
    }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: android.content.Intent) { recognized() }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT)
        if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else ctx.registerReceiver(receiver, filter)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(HelloBus.recognized) {
        if (HelloBus.recognized) { state = HelloState.SUCCESS }
        else if (state == HelloState.SUCCESS) state = if (biometric) HelloState.SEARCHING else HelloState.IDLE
    }
    LaunchedEffect(HelloBus.wakeTick) {
        if (HelloBus.wakeTick > 0 && biometric && state != HelloState.SUCCESS) state = HelloState.SEARCHING
        // Never sit on the scanning text forever.
        delay(3000)
        if (state == HelloState.SEARCHING) state = HelloState.HINT
    }
    LaunchedEffect(Unit) {
        val km = ctx.getSystemService(android.app.KeyguardManager::class.java)
        var wasLocked = km.isKeyguardLocked
        while (true) {
            delay(250)
            val locked = km.isKeyguardLocked
            if (wasLocked && !locked) recognized()
            wasLocked = locked
        }
    }
    return state
}
/** Original W10M PIN entry. */
@Composable
internal fun PinPad(onSuccess: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(280, easing = LinearOutSlowInEasing)) }
    fun submit() {
        if (pin.isEmpty()) return
        if (PinStore.check(ctx, pin)) onSuccess() else {
            error = true; pin = ""
            scope.launch { for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45)) }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 140f }
            .clickable(enabled = false) {}
            .padding(horizontal = 24.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        AnimatedContent(if (error) "That PIN is incorrect. Try again." else "Enter PIN", label = "pinPrompt",
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }) {
            Text(it, color = Color.White, fontFamily = SelawikFamily, fontSize = 18.sp)
        }
        Box(Modifier.padding(top = 14.dp).graphicsLayer { translationX = shake.value * density }
            .fillMaxWidth(0.72f).height(44.dp).border(2.dp, Color.White).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart) {
            Text("\u25CF".repeat(pin.length), color = Color.White, fontSize = 16.sp, letterSpacing = 6.sp)
        }
        Spacer(Modifier.height(28.dp))
        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "<" to "", "0" to "+", "ok" to "")
        keys.chunked(3).forEachIndexed { r, row ->
            val rowIn = remember { Animatable(0f) }
            LaunchedEffect(Unit) { delay(40L * r); rowIn.animateTo(1f, tween(260, easing = LinearOutSlowInEasing)) }
            Row(Modifier.fillMaxWidth().graphicsLayer { alpha = rowIn.value; translationY = (1f - rowIn.value) * 60f },
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (d, l) ->
                    Column(
                        Modifier.weight(1f).height(72.dp).padding(bottom = 6.dp)
                            .background(if (d == "ok") LockAccent else Color(0xFF1F1F1F))
                            .clickable {
                                error = false
                                when (d) { "<" -> pin = pin.dropLast(1); "ok" -> submit(); else -> if (pin.length < 16) pin += d }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        when (d) {
                            "<" -> androidx.compose.foundation.Image(Mdl2.Backspace, "backspace", Modifier.size(22.dp))
                            "ok" -> Text("OK", color = Color.White, fontFamily = SelawikFamily, fontSize = 20.sp)
                            else -> {
                                Text(d, color = Color.White, fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 32.sp)
                                if (l.isNotEmpty()) Text(l, color = Color(0xFFBBBBBB), fontFamily = SelawikFamily, fontSize = 11.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("cancel", color = Color(0xFFBBBBBB), fontFamily = SelawikFamily, fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 18.dp).clickable(onClick = onCancel))
    }
}