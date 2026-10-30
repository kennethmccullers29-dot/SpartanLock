package com.ken1dot.spartanlock

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class QuickStatus(val packageName: String, val count: Int, val icon: ImageBitmap)

object QuickStatusStore {
    var items by mutableStateOf<List<QuickStatus>>(emptyList())
        private set

    fun update(items: List<QuickStatus>) {
        this.items = items
    }
}

class LockNotificationListener : NotificationListenerService() {
    private val packObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    override fun onListenerConnected() {
        LockMediaStore.connect(this)
        runCatching { contentResolver.registerContentObserver(LauncherIconPack.PACK_URI, false, packObserver) }
        refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refresh()
        LockMediaStore.refreshSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refresh()
        LockMediaStore.refreshSessions()
    }

    override fun onListenerDisconnected() {
        runCatching { contentResolver.unregisterContentObserver(packObserver) }
        QuickStatusStore.update(emptyList())
        LockMediaStore.disconnect()
    }

    private fun refresh() {
        val notifications = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
        val visible = notifications.filter { sbn ->
            val flags = sbn.notification.flags
            sbn.packageName != packageName &&
                flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                flags and Notification.FLAG_ONGOING_EVENT == 0 &&
                flags and Notification.FLAG_FOREGROUND_SERVICE == 0
        }
        val items = visible.groupBy { it.packageName }
            .values
            .sortedByDescending { group -> group.maxOf { it.postTime } }
            .take(4)
            .map { group ->
                QuickStatus(
                    packageName = group.first().packageName,
                    count = group.size,
                    icon = loadIcon(group.first())
                )
            }
        QuickStatusStore.update(items)
    }

    private fun loadIcon(sbn: StatusBarNotification): ImageBitmap {
        val side = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = runCatching { LauncherIconPack.iconFor(this, sbn.packageName)?.mutate() }.getOrNull()
            ?: runCatching { packageManager.getApplicationIcon(sbn.packageName).mutate() }
            .getOrNull()
            ?: runCatching { sbn.notification.smallIcon.loadDrawable(this)?.mutate() }.getOrNull()
        val drawn = drawable?.let {
            runCatching {
                it.setBounds(0, 0, side, side)
                it.draw(canvas)
            }.isSuccess
        } ?: false
        if (!drawn) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                strokeWidth = side * 0.07f
                style = Paint.Style.STROKE
            }
            val inset = side * 0.1f
            canvas.drawRoundRect(RectF(inset, inset, side - inset, side - inset),
                side * 0.14f, side * 0.14f, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = side * 0.46f
            val letter = sbn.packageName.substringAfterLast('.').firstOrNull()?.uppercaseChar() ?: '?'
            canvas.drawText(letter.toString(), side / 2f, side * 0.68f, paint)
        }
        return bitmap.asImageBitmap()
    }
}
