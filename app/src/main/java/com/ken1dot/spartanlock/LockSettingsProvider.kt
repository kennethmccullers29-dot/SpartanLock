package com.ken1dot.spartanlock

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle

/**
 * Settings bridge for Spartan Launcher (signature-protected). The PIN hash never leaves SpartanLock:
 * the launcher sends the plain new PIN, which is hashed here. Changing/removing a PIN needs the current PIN.
 */
class LockSettingsProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val c = context!!
        val theme = c.getSharedPreferences("lock_theme", Context.MODE_PRIVATE)
        val out = Bundle()
        when (method) {
            "get" -> {}
            "setTheme" -> if (LockTheme.entries.any { it.id == arg }) theme.edit().putString("theme", arg).apply()
            "setGlance" -> GlanceActivity.setEnabled(c, arg == "true")
            "setName" -> PinStore.setHelloName(c, arg.orEmpty())
            "setPin" -> {
                val pin = extras?.getString("pin").orEmpty()
                val old = extras?.getString("old").orEmpty()
                val ok = pin.length in 4..16 && pin.all(Char::isDigit) && (!PinStore.hasPin(c) || PinStore.check(c, old))
                if (ok) PinStore.set(c, pin)
                out.putBoolean("ok", ok)
            }
            "clearPin" -> {
                val ok = !PinStore.hasPin(c) || PinStore.check(c, extras?.getString("old").orEmpty())
                if (ok) PinStore.clear(c)
                out.putBoolean("ok", ok)
            }
        }
        out.putString("theme", LockTheme.fromStored(theme.getString("theme", null), theme.getBoolean("diagonal", false)).id)
        out.putStringArray("themeIds", LockTheme.entries.map { it.id }.toTypedArray())
        out.putStringArray("themeTitles", LockTheme.entries.map { it.title }.toTypedArray())
        out.putBoolean("glance", GlanceActivity.enabled(c))
        out.putString("name", PinStore.helloName(c))
        out.putBoolean("hasPin", PinStore.hasPin(c))
        return out
    }
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}