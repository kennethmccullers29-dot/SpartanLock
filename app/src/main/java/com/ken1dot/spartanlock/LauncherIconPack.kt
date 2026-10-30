package com.ken1dot.spartanlock

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Follows the icon pack picked in SpartanLauncher (read from its accent provider) and
 * resolves notification app icons through that pack's appfilter.xml, the same way the
 * launcher does. Messaging keeps its own icon, matching the launcher's exclusion.
 */
internal object LauncherIconPack {
    val PACK_URI: Uri = Uri.parse("content://com.ken1dot.spartanlauncher.accent/icon-pack")
    @Volatile private var filterFor = ""
    @Volatile private var filter: Map<String, String> = emptyMap()

    fun current(context: Context): String = runCatching {
        context.contentResolver.query(PACK_URI, null, null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0).orEmpty() else ""
        }
    }.getOrNull().orEmpty()

    private fun excluded(context: Context, pkg: String): Boolean {
        val sms = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        return pkg == sms || pkg.contains("messaging") || pkg.endsWith(".mms")
    }

    /** The pack's drawable for this app's launcher activity, or null to keep the app icon. */
    fun iconFor(context: Context, pkg: String): Drawable? {
        val pack = current(context)
        if (pack.isEmpty() || excluded(context, pkg)) return null
        val pm = context.packageManager
        val res = runCatching { pm.getResourcesForApplication(pack) }.getOrNull() ?: return null
        val map = filterOf(pack, res)
        val cls = runCatching { pm.getLaunchIntentForPackage(pkg)?.component?.className }.getOrNull()
        val name = cls?.let { map["$pkg/$it"] } ?: map.entries.firstOrNull { it.key.startsWith("$pkg/") }?.value
            ?: return null
        val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    @Synchronized
    private fun filterOf(pack: String, res: android.content.res.Resources): Map<String, String> {
        if (filterFor == pack) return filter
        val map = HashMap<String, String>()
        val parser: XmlPullParser? = runCatching {
            val p = XmlPullParserFactory.newInstance().newPullParser()
            p.setInput(res.assets.open("appfilter.xml"), "UTF-8"); p
        }.getOrNull() ?: res.getIdentifier("appfilter", "xml", pack).takeIf { it != 0 }?.let { res.getXml(it) }
        runCatching {
            parser?.let { p ->
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType != XmlPullParser.START_TAG || p.name != "item") continue
                    val comp = p.getAttributeValue(null, "component") ?: continue
                    val drawable = p.getAttributeValue(null, "drawable") ?: continue
                    val inner = comp.substringAfter("ComponentInfo{", "").substringBefore("}")
                    if (inner.isEmpty()) continue
                    val pkg = inner.substringBefore("/")
                    var cls = inner.substringAfter("/", "")
                    if (cls.startsWith(".")) cls = pkg + cls
                    map.putIfAbsent("$pkg/$cls", drawable)
                }
            }
        }
        filterFor = pack; filter = map
        return map
    }
}
