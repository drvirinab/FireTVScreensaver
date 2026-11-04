package com.tvart.slideshow.engine

import com.tvart.slideshow.data.ImageEntity

/**
 * Picks the next image without repeats until every image in the slideshow has shown once.
 * Favorites may show [favoriteWeight] times per cycle. State is per slideshow and shared by the
 * overlay and the screensaver (same process), so toggling doesn't restart the cycle.
 */
object ShuffleBag {
    private val shown = HashMap<Long, HashMap<Long, Int>>()
    private val lastShown = HashMap<Long, Long>()

    @Synchronized
    fun next(slideshowId: Long, images: List<ImageEntity>, shuffle: Boolean, favoriteWeight: Int): ImageEntity? {
        if (images.isEmpty()) return null
        val counts = shown.getOrPut(slideshowId) { HashMap() }
        fun quota(i: ImageEntity) = if (i.favorite) favoriteWeight.coerceAtLeast(1) else 1
        var eligible = images.filter { (counts[it.id] ?: 0) < quota(it) }
        if (eligible.isEmpty()) {
            counts.clear()
            eligible = images
        }
        val last = lastShown[slideshowId]
        if (eligible.size > 1) eligible = eligible.filter { it.id != last }
        val pick = if (shuffle) eligible.random()
        else {
            // Sequential by name, continuing after the last one shown.
            val sorted = images.sortedBy { it.filename.lowercase() }
            val idx = sorted.indexOfFirst { it.id == last }
            (1..sorted.size).map { sorted[(idx + it).mod(sorted.size)] }.firstOrNull { it in eligible } ?: eligible.first()
        }
        counts[pick.id] = (counts[pick.id] ?: 0) + 1
        lastShown[slideshowId] = pick.id
        return pick
    }
}
