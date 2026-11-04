package com.tvart.slideshow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ArtDao {
    // ---- images ----
    @Query("SELECT * FROM images WHERE deletedAt IS NULL")
    suspend fun liveImages(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun deletedImages(): List<ImageEntity>

    @Query("SELECT * FROM images")
    suspend fun allImageRows(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun image(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE id IN (:ids)")
    suspend fun images(ids: List<Long>): List<ImageEntity>

    @Query("SELECT * FROM images WHERE hash = :hash AND deletedAt IS NULL LIMIT 1")
    suspend fun liveImageByHash(hash: String): ImageEntity?

    @Insert
    suspend fun insertImage(img: ImageEntity): Long

    @Update
    suspend fun updateImage(img: ImageEntity)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteImageRows(ids: List<Long>)

    // ---- batches ----
    @Query("SELECT * FROM batches ORDER BY createdAt DESC")
    suspend fun batches(): List<BatchEntity>

    @Insert
    suspend fun insertBatch(b: BatchEntity): Long

    @Update
    suspend fun updateBatch(b: BatchEntity)

    // ---- slideshows ----
    @Query("SELECT * FROM slideshows ORDER BY kind = 0 DESC, sortOrder, name")
    suspend fun slideshows(): List<SlideshowEntity>

    @Query("SELECT * FROM slideshows WHERE id = :id")
    suspend fun slideshow(id: Long): SlideshowEntity?

    @Query("SELECT * FROM slideshows WHERE kind = 0 LIMIT 1")
    suspend fun defaultSlideshow(): SlideshowEntity?

    @Insert
    suspend fun insertSlideshow(s: SlideshowEntity): Long

    @Update
    suspend fun updateSlideshow(s: SlideshowEntity)

    @Delete
    suspend fun deleteSlideshow(s: SlideshowEntity)

    // ---- memberships ----
    @Query("SELECT * FROM memberships")
    suspend fun memberships(): List<MembershipEntity>

    @Query("SELECT imageId FROM memberships WHERE slideshowId = :slideshowId")
    suspend fun memberIds(slideshowId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMemberships(m: List<MembershipEntity>)

    @Query("DELETE FROM memberships WHERE slideshowId = :slideshowId AND imageId IN (:imageIds)")
    suspend fun removeMemberships(slideshowId: Long, imageIds: List<Long>)

    @Query("DELETE FROM memberships WHERE slideshowId = :slideshowId")
    suspend fun clearMemberships(slideshowId: Long)

    // ---- ranges ----
    @Query("SELECT * FROM ranges")
    suspend fun ranges(): List<RangeEntity>

    @Query("SELECT * FROM ranges WHERE slideshowId = :slideshowId")
    suspend fun rangesFor(slideshowId: Long): List<RangeEntity>

    @Insert
    suspend fun insertRange(r: RangeEntity): Long

    @Update
    suspend fun updateRange(r: RangeEntity)

    @Delete
    suspend fun deleteRange(r: RangeEntity)

    @Query("DELETE FROM ranges WHERE slideshowId = :slideshowId")
    suspend fun clearRanges(slideshowId: Long)
}
