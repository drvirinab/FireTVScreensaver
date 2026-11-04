package com.tvart.slideshow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One image file in /sdcard/Pictures/TVArt. deletedAt != null means it sits in Recently Deleted. */
@Entity(
    tableName = "images",
    indices = [Index("filename", unique = true), Index("hash"), Index("batchId")]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val hash: String,
    /** When the app first saw the file (not file mtime). */
    val addedAt: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val batchId: Long? = null,
    val favorite: Boolean = false,
    val deletedAt: Long? = null,
) {
    val isLowRes get() = width < 1920 || height < 1080
    /** True if a 16:9 center-crop would cut more than ~2% off. */
    val isNot16x9 get() = height > 0 && kotlin.math.abs(width.toFloat() / height - 16f / 9f) > 0.04f
}

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "USB", "Web", "Folder" */
    val source: String,
    val createdAt: Long,
)

object SlideshowKind {
    const val DEFAULT = 0
    const val SEASON = 1
    const val SPECIAL = 2
    /** Not scheduled by default; only plays via override/quiet hours or if the user adds a range. */
    const val MANUAL = 3

    fun label(kind: Int) = when (kind) {
        DEFAULT -> "Default"
        SEASON -> "Season"
        SPECIAL -> "Special day"
        else -> "Manual"
    }

    /** Scheduler priority: special > season > default. Manual slideshows with ranges act as seasons. */
    fun priority(kind: Int) = when (kind) {
        SPECIAL -> 2
        SEASON, MANUAL -> 1
        else -> 0
    }
}

@Entity(tableName = "slideshows")
data class SlideshowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color used in lists and the timeline. */
    val color: Int,
    /** Short icon text, usually one emoji. */
    val icon: String = "",
    val kind: Int = SlideshowKind.SEASON,
    val sortOrder: Int = 0,
    // Optional per-slideshow overrides (null = use global setting)
    val displaySec: Int? = null,
    val panPercent: Int? = null,
    val clockOn: Boolean? = null,
    /** ARGB clock tint, null = white. */
    val clockTint: Int? = null,
    /** Phase 4: countdown line "N days until <name>" pointing at this slideshow's next start. */
    val countdownOn: Boolean = false,
)

@Entity(
    tableName = "memberships",
    primaryKeys = ["slideshowId", "imageId"],
    indices = [Index("imageId")],
    foreignKeys = [
        ForeignKey(SlideshowEntity::class, ["id"], ["slideshowId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ImageEntity::class, ["id"], ["imageId"], onDelete = ForeignKey.CASCADE),
    ]
)
data class MembershipEntity(val slideshowId: Long, val imageId: Long)

/** A yearly range month/day -> month/day. May wrap the new year. End is exclusive by default (setting). */
@Entity(
    tableName = "ranges",
    indices = [Index("slideshowId")],
    foreignKeys = [
        ForeignKey(SlideshowEntity::class, ["id"], ["slideshowId"], onDelete = ForeignKey.CASCADE),
    ]
)
data class RangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slideshowId: Long,
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
)
