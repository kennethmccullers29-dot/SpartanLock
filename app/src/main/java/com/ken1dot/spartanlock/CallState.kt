package com.ken1dot.spartanlock

import android.content.Context
import android.media.AudioManager

/** True while a call is ringing or active — the lock screen must step aside for the call UI. */
internal fun Context.isCallActive(): Boolean {
    val mode = getSystemService(AudioManager::class.java)?.mode ?: return false
    return mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL ||
        mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_CALL_SCREENING
}

internal const val PHONE_PACKAGE = "com.example.spartanphone"
