package com.tvart.slideshow.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.StatFs
import com.tvart.slideshow.engine.Decode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single entry point for the library, slideshows and schedule.
 * Used by the TV screens, the web manager and the slideshow engine. All suspend funs run on IO.
 */
class ArtRepo private constructor(context: Context) {
    val app: Context = context.applicationContext
    val dao = ArtDatabase.get(app).dao()
    val settings = Settings(app)

    val folder: File get() = File(Environment.getExternalStorageDirectory(), "Pictures/TVArt")
    val trashDir: File get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "trash")
    val thumbDir: File get() = File(app.filesDir, "thumbs")

    private val scanLock = Mutex()
    private val seedLock = Mutex()

    /** Bumped whenever library/slideshow/schedule data changes, so running slideshows can react. */
    @Volatile var revision = 0L
        private set
    fun touch() { revision++ }

    fun fileOf(img: ImageEntity): File =
        if (img.deletedAt != null) File(trashDir, img.filename) else File(folder, img.filename)

    // ------------------------------------------------------------------ seed

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        seedLock.withLock {
            if (dao.defaultSlideshow() != null) return@withLock
            dao.insertSlideshow(SlideshowEntity(name = "Default", color = 0xFF607D8B.toInt(), icon = "🖼", kind = SlideshowKind.DEFAULT))
            val seasons = listOf(
                Triple("Christmas", 0xFFC62828.toInt(), "🎄") to intArrayOf(11, 8, 1, 8),
                Triple("Valentine's", 0xFFE91E63.toInt(), "💘") to intArrayOf(1, 8, 3, 1),
                Triple("Spring", 0xFF7CB342.toInt(), "🌷") to intArrayOf(3, 1, 6, 1),
                Triple("Anime", 0xFF7E57C2.toInt(), "✨") to intArrayOf(6, 1, 7, 8),
                Triple("Summer", 0xFFFFB300.toInt(), "☀") to intArrayOf(7, 8, 9, 1),
                Triple("Halloween", 0xFFEF6C00.toInt(), "🎃") to intArrayOf(9, 1, 11, 8),
            )
            seasons.forEachIndexed { i, (meta, r) ->
                val id = dao.insertSlideshow(SlideshowEntity(name = meta.first, color = meta.second, icon = meta.third, kind = SlideshowKind.SEASON, sortOrder = i + 1))
                dao.insertRange(RangeEntity(slideshowId = id, startMonth = r[0], startDay = r[1], endMonth = r[2], endDay = r[3]))
            }
            touch()
        }
    }

    // ------------------------------------------------------------------ scanning / import

    private fun isImageName(n: String): Boolean {
        val l = n.lowercase(Locale.US)
        return !l.startsWith(".") && (l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") || l.endsWith(".bmp"))
    }

    /** Image files in the folder and one level of subfolders, keyed by path relative to [folder] ("Christmas/a.jpg"). */
    private fun filesOnDisk(): Map<String, File> {
        val out = HashMap<String, File>()
        for (f in folder.listFiles() ?: emptyArray()) {
            if (f.isFile && isImageName(f.name)) out[f.name] = f
            else if (f.isDirectory && !f.name.startsWith(".")) {
                for (g in f.listFiles() ?: emptyArray()) if (g.isFile && isImageName(g.name)) out["${f.name}/${g.name}"] = g
            }
        }
        return out
    }

    /**
     * Sync DB with the folder: new files get addedAt = now (one "Folder" batch per scan), missing files are dropped.
     * A new file inside a subfolder named like a slideshow (e.g. "Christmas/") is added to that slideshow once.
     */
    suspend fun rescan(): Int = withContext(Dispatchers.IO) {
        scanLock.withLock {
            ensureSeeded()
            // Unmounted storage or a missing permission would look like "all files deleted" and drop memberships.
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return@withLock 0
            folder.mkdirs()
            if (folder.listFiles() == null) return@withLock 0
            val files = filesOnDisk()
            val rows = dao.allImageRows()
            val live = rows.filter { it.deletedAt == null }
            val known = rows.map { it.filename }.toHashSet()

            val missing = live.filter { it.filename !in files }.map { it.id }
            if (missing.isNotEmpty()) dao.deleteImageRows(missing)

            val showsByName = dao.slideshows().filter { it.kind != SlideshowKind.DEFAULT }
                .associateBy { it.name.lowercase(Locale.US).replace("'", "") }
            var batchId: Long? = null
            var added = 0
            for ((rel, f) in files.toSortedMap()) {
                if (rel in known) continue
                val b = Decode.bounds(f) ?: continue
                if (batchId == null) {
                    batchId = dao.insertBatch(BatchEntity(name = "Folder scan " + fmtDate(System.currentTimeMillis()), source = "Folder", createdAt = System.currentTimeMillis()))
                }
                val id = dao.insertImage(ImageEntity(filename = rel, hash = hashOf(f), addedAt = System.currentTimeMillis(), width = b.width, height = b.height, sizeBytes = f.length(), batchId = batchId))
                val sub = rel.substringBefore('/', "")
                showsByName[sub.lowercase(Locale.US).replace("'", "")]?.let { dao.addMemberships(listOf(MembershipEntity(it.id, id))) }
                makeThumb(id, f)
                added++
            }
            purgeOldTrash()
            if (added > 0 || missing.isNotEmpty()) touch()
            added
        }
    }

    suspend fun newBatch(name: String, source: String): Long = withContext(Dispatchers.IO) {
        dao.insertBatch(BatchEntity(name = name.ifBlank { "$source import " + fmtDate(System.currentTimeMillis()) }, source = source, createdAt = System.currentTimeMillis()))
    }

    sealed class ImportResult {
        data class Added(val image: ImageEntity) : ImportResult()
        data class Duplicate(val existing: ImageEntity) : ImportResult()
        data class Failed(val reason: String) : ImportResult()
    }

    /**
     * Copies [input] into the library as [displayName]. Hashes while copying. When [targetSlideshowId] is set the
     * image (or its existing duplicate) is added to that slideshow.
     */
    suspend fun importStream(
        input: InputStream, displayName: String, batchId: Long?, targetSlideshowId: Long?, skipDuplicates: Boolean,
    ): ImportResult = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val safe = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ ()-]"), "_").ifBlank { "image.jpg" }
        if (!isImageName(safe)) return@withContext ImportResult.Failed("Not a supported image type: $safe")
        val tmp = File(folder, ".tmp_${System.nanoTime()}")
        val md = MessageDigest.getInstance("SHA-1")
        try {
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    md.update(buf, 0, n); out.write(buf, 0, n)
                }
            }
        } catch (e: Exception) {
            tmp.delete(); return@withContext ImportResult.Failed("Copy failed: ${e.message}")
        }
        val hash = md.digest().toHex()
        val dup = dao.liveImageByHash(hash)
        if (dup != null && skipDuplicates) {
            tmp.delete()
            if (targetSlideshowId != null) setMembership(targetSlideshowId, listOf(dup.id), true)
            return@withContext ImportResult.Duplicate(dup)
        }
        val b = Decode.bounds(tmp)
        if (b == null) { tmp.delete(); return@withContext ImportResult.Failed("Unreadable image: $safe") }
        val dest = uniqueName(folder, safe)
        scanLock.withLock {
            if (!tmp.renameTo(dest)) { tmp.delete(); return@withContext ImportResult.Failed("Could not save $safe") }
            val img = ImageEntity(filename = dest.name, hash = hash, addedAt = System.currentTimeMillis(), width = b.width, height = b.height, sizeBytes = dest.length(), batchId = batchId)
            val id = dao.insertImage(img)
            makeThumb(id, dest)
            if (targetSlideshowId != null) dao.addMemberships(listOf(MembershipEntity(targetSlideshowId, id)))
            touch()
            ImportResult.Added(img.copy(id = id))
        }
    }

    private fun uniqueName(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists() && !File(trashDir, name).exists()) return f
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "jpg")
        var i = 2
        while (true) {
            f = File(dir, "$base ($i).$ext")
            if (!f.exists() && !File(trashDir, f.name).exists()) return f
            i++
        }
    }

    // ------------------------------------------------------------------ thumbnails

    fun thumbFile(imageId: Long) = File(thumbDir, "$imageId.jpg")

    /** Returns the thumbnail file, generating it if the cache was cleared. */
    suspend fun thumb(img: ImageEntity): File? = withContext(Dispatchers.IO) {
        val t = thumbFile(img.id)
        if (t.exists() || makeThumb(img.id, fileOf(img))) t else null
    }

    private fun makeThumb(id: Long, src: File): Boolean {
        val bmp = Decode.thumbnail(src) ?: return false
        thumbDir.mkdirs()
        return try {
            FileOutputStream(thumbFile(id)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } catch (_: Exception) { false } finally { bmp.recycle() }
    }

    // ------------------------------------------------------------------ delete / restore

    suspend fun moveToTrash(ids: List<Long>) = withContext(Dispatchers.IO) {
        trashDir.mkdirs()
        val now = System.currentTimeMillis()
        for (img in dao.images(ids)) {
            if (img.deletedAt != null) continue
            if (moveFile(File(folder, img.filename), File(trashDir, img.filename))) dao.updateImage(img.copy(deletedAt = now))
        }
        touch()
    }

    suspend fun restore(ids: List<Long>) = withContext(Dispatchers.IO) {
        folder.mkdirs()
        for (img in dao.images(ids)) {
            if (img.deletedAt == null) continue
            var name = img.filename
            if (File(folder, name).exists()) name = uniqueName(folder, name).name
            if (moveFile(File(trashDir, img.filename), File(folder, name))) dao.updateImage(img.copy(filename = name, deletedAt = null))
        }
        touch()
    }

    suspend fun deletePermanently(ids: List<Long>) = withContext(Dispatchers.IO) {
        for (img in dao.images(ids)) {
            fileOf(img).delete()
            thumbFile(img.id).delete()
        }
        dao.deleteImageRows(ids)
        touch()
    }

    suspend fun emptyTrash() = deletePermanently(dao.deletedImages().map { it.id })

    private suspend fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - Defaults.TRASH_DAYS * 86_400_000L
        val old = dao.deletedImages().filter { (it.deletedAt ?: 0) < cutoff }.map { it.id }
        if (old.isNotEmpty()) deletePermanently(old)
    }

    private fun moveFile(from: File, to: File): Boolean {
        if (!from.exists()) return false
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return true
        return try { from.copyTo(to, overwrite = true); from.delete(); true } catch (_: Exception) { false }
    }

    // ------------------------------------------------------------------ slideshows & membership

    suspend fun setMembership(slideshowId: Long, imageIds: List<Long>, include: Boolean) = withContext(Dispatchers.IO) {
        if (include) dao.addMemberships(imageIds.map { MembershipEntity(slideshowId, it) })
        else dao.removeMemberships(slideshowId, imageIds)
        touch()
    }

    suspend fun setFavorite(imageIds: List<Long>, favorite: Boolean) = withContext(Dispatchers.IO) {
        for (img in dao.images(imageIds)) dao.updateImage(img.copy(favorite = favorite))
        touch()
    }

    /** Live images that play in [slideshowId]. Default follows the "unassigned only / all images" setting. */
    suspend fun imagesFor(slideshowId: Long): List<ImageEntity> = withContext(Dispatchers.IO) {
        val s = dao.slideshow(slideshowId) ?: return@withContext emptyList()
        val live = dao.liveImages()
        if (s.kind == SlideshowKind.DEFAULT) {
            if (!settings.defaultUnassignedOnly) return@withContext live
            val defaultId = s.id
            val ms = dao.memberships()
            val inOther = ms.filter { it.slideshowId != defaultId }.map { it.imageId }.toHashSet()
            val inDefault = ms.filter { it.slideshowId == defaultId }.map { it.imageId }.toHashSet()
            live.filter { it.id !in inOther || it.id in inDefault }
        } else {
            val ids = dao.memberIds(slideshowId).toHashSet()
            live.filter { it.id in ids }
        }
    }

    suspend fun imageCount(slideshowId: Long) = imagesFor(slideshowId).size

    suspend fun createSlideshow(name: String, kind: Int, color: Int, icon: String): Long = withContext(Dispatchers.IO) {
        val order = (dao.slideshows().maxOfOrNull { it.sortOrder } ?: 0) + 1
        dao.insertSlideshow(SlideshowEntity(name = name, color = color, icon = icon, kind = kind, sortOrder = order)).also { touch() }
    }

    suspend fun updateSlideshow(s: SlideshowEntity) = withContext(Dispatchers.IO) { dao.updateSlideshow(s); touch() }

    suspend fun deleteSlideshow(s: SlideshowEntity) = withContext(Dispatchers.IO) {
        if (s.kind == SlideshowKind.DEFAULT) return@withContext
        dao.deleteSlideshow(s)
        if (settings.overrideSlideshowId == s.id) clearOverride()
        if (settings.quietSlideshowId == s.id) settings.quietSlideshowId = -1
        touch()
    }

    // ------------------------------------------------------------------ schedule

    suspend fun schedRanges(): List<SchedRange> = withContext(Dispatchers.IO) {
        val shows = dao.slideshows().associateBy { it.id }
        dao.ranges().mapNotNull { r ->
            val s = shows[r.slideshowId] ?: return@mapNotNull null
            if (s.kind == SlideshowKind.DEFAULT) return@mapNotNull null
            SchedRange(r.slideshowId, SlideshowKind.priority(s.kind), MonthDay(r.startMonth, r.startDay), MonthDay(r.endMonth, r.endDay))
        }
    }

    enum class Reason(val label: String) { SCHEDULED("on schedule"), OVERRIDE("you picked it"), QUIET("night slideshow"), DEFAULT("nothing scheduled") }

    data class Active(
        val slideshow: SlideshowEntity,
        val reason: Reason,
        /** Next scheduled change (ignores override / quiet hours). */
        val next: Scheduler.Switch?,
        val nextSlideshow: SlideshowEntity?,
        /** What the schedule alone would play now. */
        val scheduled: SlideshowEntity,
    )

    /** Manual override > quiet hours > schedule > Default. */
    suspend fun resolveActive(now: Long = System.currentTimeMillis()): Active = withContext(Dispatchers.IO) {
        ensureSeeded()
        val default = dao.defaultSlideshow()!!
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val ranges = schedRanges()
        val excl = settings.endExclusive
        val schedId = Scheduler.winner(ranges, MonthDay.of(cal), excl)?.slideshowId
        val scheduled = schedId?.let { dao.slideshow(it) } ?: default
        val next = Scheduler.nextSwitch(ranges, cal, excl)
        val nextShow = next?.let { n -> n.slideshowId?.let { dao.slideshow(it) } ?: default }

        val oId = settings.overrideSlideshowId
        if (oId >= 0) {
            val o = dao.slideshow(oId)
            if (o != null && now < settings.overrideUntil) return@withContext Active(o, Reason.OVERRIDE, next, nextShow, scheduled)
            if (o == null || now >= settings.overrideUntil) clearOverride()
        }
        if (settings.quietOn && Scheduler.inHourWindow(cal.get(Calendar.HOUR_OF_DAY), settings.quietStartHour, settings.quietEndHour)) {
            dao.slideshow(settings.quietSlideshowId)?.let { return@withContext Active(it, Reason.QUIET, next, nextShow, scheduled) }
        }
        Active(scheduled, if (schedId != null) Reason.SCHEDULED else Reason.DEFAULT, next, nextShow, scheduled)
    }

    enum class OverrideMode { UNTIL_NEXT_SWITCH, DAYS, UNTIL_OFF }

    suspend fun setOverride(slideshowId: Long, mode: OverrideMode, days: Int = 1) = withContext(Dispatchers.IO) {
        val until = when (mode) {
            OverrideMode.UNTIL_OFF -> Long.MAX_VALUE
            OverrideMode.DAYS -> Calendar.getInstance().apply { Scheduler.startOfDay(this); add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
            OverrideMode.UNTIL_NEXT_SWITCH -> Scheduler.nextSwitch(schedRanges(), Calendar.getInstance(), settings.endExclusive)
                ?.date?.timeInMillis ?: Long.MAX_VALUE
        }
        settings.overrideSlideshowId = slideshowId
        settings.overrideUntil = until
        touch()
    }

    fun clearOverride() {
        settings.overrideSlideshowId = -1
        settings.overrideUntil = 0
        touch()
    }

    /** "57 days until Christmas" for the nearest slideshow with its countdown turned on, or null. */
    suspend fun countdownText(): String? = withContext(Dispatchers.IO) {
        val ranges = schedRanges()
        val today = Calendar.getInstance()
        dao.slideshows().filter { it.countdownOn }
            .mapNotNull { s -> Scheduler.nextStartOf(s.id, ranges, today, settings.endExclusive)?.let { s to it } }
            .filter { it.second > 0 }
            .minByOrNull { it.second }
            ?.let { (s, d) -> if (d == 1) "Tomorrow is ${s.name}" else "$d days until ${s.name}" }
    }

    // ------------------------------------------------------------------ storage & USB

    data class StorageInfo(val artBytes: Long, val artCount: Int, val trashBytes: Long, val trashCount: Int, val freeBytes: Long)

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val live = dao.liveImages(); val del = dao.deletedImages()
        val free = try { StatFs(Environment.getExternalStorageDirectory().path).availableBytes } catch (_: Exception) { -1L }
        StorageInfo(live.sumOf { it.sizeBytes }, live.size, del.sumOf { it.sizeBytes }, del.size, free)
    }

    /** Removable volumes that contain a TVArt folder. */
    fun usbTvArtFolders(): List<File> {
        val roots = mutableListOf<File>()
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let(roots::addAll)
        File("/mnt/media_rw").listFiles()?.let(roots::addAll)
        File("/mnt/usb").listFiles()?.let(roots::addAll)
        return roots.mapNotNull { r ->
            r.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("TVArt", ignoreCase = true) }
        }.filter { it.canRead() }.distinctBy { it.canonicalPath }
    }

    data class UsbResult(val added: Int, val duplicates: Int, val failed: Int)

    suspend fun importFromFolder(dir: File, batchName: String, targetSlideshowId: Long?, skipDuplicates: Boolean, source: String = "USB"): UsbResult = withContext(Dispatchers.IO) {
        val files = dir.listFiles()?.filter { it.isFile && isImageName(it.name) }?.sortedBy { it.name } ?: emptyList()
        val batch = newBatch(batchName, source)
        var a = 0; var d = 0; var f = 0
        for (file in files) {
            when (file.inputStream().use { importStream(it, file.name, batch, targetSlideshowId, skipDuplicates) }) {
                is ImportResult.Added -> a++
                is ImportResult.Duplicate -> d++
                is ImportResult.Failed -> f++
            }
        }
        val b = dao.batches().firstOrNull { it.id == batch }
        if (b != null) dao.updateBatch(b.copy(name = b.name.let { if (batchName.isBlank()) "$it — $source, $a images" else it }))
        UsbResult(a, d, f)
    }

    companion object {
        @Volatile private var inst: ArtRepo? = null
        fun get(context: Context): ArtRepo = inst ?: synchronized(this) { inst ?: ArtRepo(context).also { inst = it } }

        fun hashOf(f: File): String {
            val md = MessageDigest.getInstance("SHA-1")
            f.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().toHex()
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

        fun fmtDate(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
        fun fmtBytes(b: Long): String = when {
            b < 0 -> "?"
            b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
            b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
            else -> "%.2f GB".format(b / 1073741824.0)
        }
    }
}
