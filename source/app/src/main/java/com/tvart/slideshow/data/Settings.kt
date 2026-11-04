package com.tvart.slideshow.data

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

/** Defaults replicate the Fire TV "Dissolve" screensaver as timed on the user's TV. */
object Defaults {
    const val DISPLAY_MS = 80_000L
    const val CROSSFADE_MS = 1_000L
    const val MOTION_MS = 30_000L
    const val MAX_ZOOM = 1.15f
    const val MIN_ZOOM_TO_ANIMATE = 1.02f
    const val PAN_PERCENT = 50
    const val DECODE_HEADROOM = 1.15f
    const val DOUBLE_PRESS_MS = 500L
    const val TOGGLE_KEY = KeyEvent.KEYCODE_MENU
    const val WEB_PORT = 8080
    const val WEB_IDLE_MS = 30 * 60_000L
    const val TRASH_DAYS = 30
    const val FAVORITE_WEIGHT = 2
}

/** Thin typed wrapper over SharedPreferences. Read live each time so changes apply at the next image. */
class Settings(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tvart", Context.MODE_PRIVATE)

    private fun int(k: String, d: Int) = prefs.getInt(k, d)
    private fun long(k: String, d: Long) = prefs.getLong(k, d)
    private fun bool(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun float(k: String, d: Float) = prefs.getFloat(k, d)
    private fun put(block: SharedPreferences.Editor.() -> Unit) = prefs.edit().apply(block).apply()

    // ---- slideshow engine ----
    var displayMs: Long
        get() = long("displayMs", Defaults.DISPLAY_MS)
        set(v) = put { putLong("displayMs", v) }
    var crossfadeMs: Long
        get() = long("crossfadeMs", Defaults.CROSSFADE_MS)
        set(v) = put { putLong("crossfadeMs", v) }
    var motionMs: Long
        get() = long("motionMs", Defaults.MOTION_MS)
        set(v) = put { putLong("motionMs", v) }
    var zoomEnabled: Boolean
        get() = bool("zoomEnabled", true)
        set(v) = put { putBoolean("zoomEnabled", v) }
    var maxZoom: Float
        get() = float("maxZoom", Defaults.MAX_ZOOM)
        set(v) = put { putFloat("maxZoom", v) }
    var panEnabled: Boolean
        get() = bool("panEnabled", true)
        set(v) = put { putBoolean("panEnabled", v) }
    var panPercent: Int
        get() = int("panPercent", Defaults.PAN_PERCENT)
        set(v) = put { putInt("panPercent", v) }
    var shuffle: Boolean
        get() = bool("shuffle", true)
        set(v) = put { putBoolean("shuffle", v) }
    var favoriteWeight: Int
        get() = int("favoriteWeight", Defaults.FAVORITE_WEIGHT)
        set(v) = put { putInt("favoriteWeight", v) }

    // ---- clock ----
    var clockOn: Boolean
        get() = bool("clockOn", true)
        set(v) = put { putBoolean("clockOn", v) }
    /** CLOCK_LIKE_AMAZON (default), CLOCK_GLOW or CLOCK_STRONG_SHADOW: readability over bright art. */
    var clockStyle: Int
        get() = int("clockStyle", CLOCK_LIKE_AMAZON)
        set(v) = put { putInt("clockStyle", v) }

    // ---- overlay ----
    var toggleKey: Int
        get() = int("toggleKey", Defaults.TOGGLE_KEY)
        set(v) = put { putInt("toggleKey", v) }

    // ---- library ----
    /** true: Default = images not in any other slideshow; false: Default = all images. */
    var defaultUnassignedOnly: Boolean
        get() = bool("defaultUnassignedOnly", true)
        set(v) = put { putBoolean("defaultUnassignedOnly", v) }

    // ---- scheduler ----
    /** true (default): a range stops at midnight at the START of its end date. false: runs through the end date. */
    var endExclusive: Boolean
        get() = bool("endExclusive", true)
        set(v) = put { putBoolean("endExclusive", v) }
    var overrideSlideshowId: Long
        get() = long("overrideId", -1)
        set(v) = put { putLong("overrideId", v) }
    /** Epoch millis when the override ends; Long.MAX_VALUE = until turned off. */
    var overrideUntil: Long
        get() = long("overrideUntil", 0)
        set(v) = put { putLong("overrideUntil", v) }
    var quietOn: Boolean
        get() = bool("quietOn", false)
        set(v) = put { putBoolean("quietOn", v) }
    var quietStartHour: Int
        get() = int("quietStart", 22)
        set(v) = put { putInt("quietStart", v) }
    var quietEndHour: Int
        get() = int("quietEnd", 7)
        set(v) = put { putInt("quietEnd", v) }
    var quietSlideshowId: Long
        get() = long("quietId", -1)
        set(v) = put { putLong("quietId", v) }

    // ---- system hooks ----
    /** What screensaver_components held before we enabled ours. NULL_MARK when it was unset. */
    var savedScreensaver: String?
        get() = prefs.getString("savedScreensaver", null)
        set(v) = put { putString("savedScreensaver", v) }
    var savedScreensaverEnabled: String?
        get() = prefs.getString("savedScreensaverEnabled", null)
        set(v) = put { putString("savedScreensaverEnabled", v) }
    var expectScreensaver: Boolean
        get() = bool("expectScreensaver", false)
        set(v) = put { putBoolean("expectScreensaver", v) }
    var expectOverlay: Boolean
        get() = bool("expectOverlay", false)
        set(v) = put { putBoolean("expectOverlay", v) }

    // ---- web manager ----
    var webEnabled: Boolean
        get() = bool("webEnabled", false)
        set(v) = put { putBoolean("webEnabled", v) }
    var webPin: String
        get() = prefs.getString("webPin", null) ?: newPin().also { webPin = it }
        set(v) = put { putString("webPin", v) }

    fun newPin(): String = (100000 + java.security.SecureRandom().nextInt(900000)).toString()

    /** Everything that belongs in a JSON backup (engine, clock, scheduler options). */
    fun exportMap(): Map<String, Any?> = prefs.all.filterKeys { it !in BACKUP_EXCLUDED }

    fun importMap(map: Map<String, Any?>) = put {
        for ((k, v) in map) {
            if (k in BACKUP_EXCLUDED) continue
            when (v) {
                is Boolean -> putBoolean(k, v)
                is Int -> if (k in LONG_KEYS) putLong(k, v.toLong()) else putInt(k, v)
                is Long -> if (k in LONG_KEYS) putLong(k, v) else putInt(k, v.toInt())
                is Float -> putFloat(k, v)
                is Double -> if (k in LONG_KEYS) putLong(k, v.toLong()) else if (k in FLOAT_KEYS) putFloat(k, v.toFloat()) else putInt(k, v.toInt())
                is String -> putString(k, v)
            }
        }
    }

    companion object {
        const val NULL_MARK = "\u0000null"
        const val CLOCK_LIKE_AMAZON = 0
        const val CLOCK_GLOW = 1
        const val CLOCK_STRONG_SHADOW = 2
        private val BACKUP_EXCLUDED = setOf(
            "savedScreensaver", "savedScreensaverEnabled", "expectScreensaver", "expectOverlay",
            "webEnabled", "webPin", "overrideId", "overrideUntil", "quietId",
        )
        val LONG_KEYS = setOf("displayMs", "crossfadeMs", "motionMs", "overrideId", "overrideUntil", "quietId")
        val FLOAT_KEYS = setOf("maxZoom")
    }
}
