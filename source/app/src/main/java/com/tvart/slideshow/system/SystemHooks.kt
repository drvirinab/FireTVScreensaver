package com.tvart.slideshow.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings.Secure
import com.tvart.slideshow.data.Settings

/**
 * Turns the screensaver and overlay hooks on/off by writing secure settings. Requires
 * WRITE_SECURE_SETTINGS, granted once over ADB. Always preserves what was there before.
 */
class SystemHooks(private val context: Context) {
    private val cr = context.contentResolver
    private val settings = Settings(context)

    val dream = ComponentName(context, ArtDreamService::class.java)
    val overlay = ComponentName(context, OverlayService::class.java)

    val grantCommand get() = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun canWrite() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun matches(entry: String, c: ComponentName) =
        ComponentName.unflattenFromString(entry.trim()) == c

    // ---------------- screensaver ----------------

    fun screensaverActive(): Boolean {
        val comps = Secure.getString(cr, KEY_COMPONENTS) ?: return false
        val enabled = Secure.getInt(cr, KEY_ENABLED, 1) == 1
        return enabled && comps.split(',').any { matches(it, dream) }
    }

    fun enableScreensaver(): Boolean {
        if (!canWrite()) return false
        if (!screensaverActive()) {
            val cur = Secure.getString(cr, KEY_COMPONENTS)
            // Don't back up our own component (e.g. after a partial reset).
            if (cur == null || cur.split(',').none { matches(it, dream) }) {
                settings.savedScreensaver = cur ?: Settings.NULL_MARK
                settings.savedScreensaverEnabled = Secure.getString(cr, KEY_ENABLED) ?: Settings.NULL_MARK
            }
        }
        return try {
            Secure.putString(cr, KEY_COMPONENTS, dream.flattenToString())
            Secure.putInt(cr, KEY_ENABLED, 1)
            settings.expectScreensaver = true
            true
        } catch (_: SecurityException) { false }
    }

    fun disableScreensaver(): Boolean {
        if (!canWrite()) return false
        return try {
            val saved = settings.savedScreensaver
            Secure.putString(cr, KEY_COMPONENTS, if (saved == null || saved == Settings.NULL_MARK) null else saved)
            val savedEnabled = settings.savedScreensaverEnabled
            if (savedEnabled != null && savedEnabled != Settings.NULL_MARK) Secure.putString(cr, KEY_ENABLED, savedEnabled)
            settings.expectScreensaver = false
            true
        } catch (_: SecurityException) { false }
    }

    fun currentScreensaverValue(): String? = Secure.getString(cr, KEY_COMPONENTS)

    // ---------------- overlay (accessibility) ----------------

    private fun a11yEntries(): List<String> =
        (Secure.getString(cr, KEY_A11Y) ?: "").split(':').map { it.trim() }.filter { it.isNotEmpty() }

    fun overlayEnabledInSettings() = a11yEntries().any { matches(it, overlay) }

    /** Enabled in settings AND bound by the system. */
    fun overlayRunning() = OverlayService.instance != null

    fun enableOverlay(): Boolean {
        if (!canWrite()) return false
        return try {
            val entries = a11yEntries().filterNot { matches(it, overlay) } + overlay.flattenToString()
            Secure.putString(cr, KEY_A11Y, entries.joinToString(":"))
            Secure.putInt(cr, KEY_A11Y_ON, 1)
            settings.expectOverlay = true
            true
        } catch (_: SecurityException) { false }
    }

    fun disableOverlay(): Boolean {
        if (!canWrite()) return false
        return try {
            OverlayService.instance?.hide()
            val entries = a11yEntries().filterNot { matches(it, overlay) }
            Secure.putString(cr, KEY_A11Y, entries.joinToString(":"))
            if (entries.isEmpty()) Secure.putInt(cr, KEY_A11Y_ON, 0)
            settings.expectOverlay = false
            true
        } catch (_: SecurityException) { false }
    }

    companion object {
        const val KEY_COMPONENTS = "screensaver_components"
        const val KEY_ENABLED = "screensaver_enabled"
        const val KEY_A11Y = "enabled_accessibility_services"
        const val KEY_A11Y_ON = "accessibility_enabled"
    }
}
