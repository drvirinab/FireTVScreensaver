package com.tvart.slideshow.engine

import android.graphics.Typeface
import java.io.File

/**
 * Amazon Ember from the device's /system/fonts (never bundled). Falls back to sans-serif,
 * which on Fire OS often already maps to Ember.
 */
object Fonts {
    val regular: Typeface by lazy { find(listOf("_rg", "regular", "-rg")) ?: Typeface.create("sans-serif", Typeface.NORMAL) }
    val light: Typeface by lazy { find(listOf("_lt", "light", "-lt")) ?: Typeface.create("sans-serif-light", Typeface.NORMAL) }

    /** For the settings screen: which file was used, or "sans-serif". */
    val description: String by lazy { emberFiles().joinToString { it.name }.ifEmpty { "sans-serif (no Ember file in /system/fonts)" } }

    private fun emberFiles(): List<File> =
        File("/system/fonts").listFiles()?.filter { it.name.contains("ember", ignoreCase = true) }?.sortedBy { it.name } ?: emptyList()

    private fun find(hints: List<String>): Typeface? {
        val files = emberFiles().filter { !it.name.contains("italic", true) && !it.name.contains("display", true) && !it.name.contains("condensed", true) }
        val f = files.firstOrNull { file -> hints.any { file.name.contains(it, ignoreCase = true) } } ?: return null
        return try { Typeface.createFromFile(f) } catch (_: Exception) { null }
    }
}
