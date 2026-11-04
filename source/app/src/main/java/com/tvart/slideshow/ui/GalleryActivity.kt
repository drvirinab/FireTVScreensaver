package com.tvart.slideshow.ui

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.ArtRepo.Companion.fmtDate
import com.tvart.slideshow.data.BatchEntity
import com.tvart.slideshow.data.Defaults
import com.tvart.slideshow.data.ImageEntity
import com.tvart.slideshow.data.SlideshowEntity
import com.tvart.slideshow.data.SlideshowKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.Random

/**
 * On-TV gallery: thumbnail grid with views, filters, sort and bulk actions, all driven by the remote.
 * OK toggles selection, long-press OK or Play/Pause previews, Menu opens Actions (a hint line says so).
 */
class GalleryActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TRASH = "trash"
        private const val RECENT_DAYS = 30
    }

    private enum class VK { SHOW, ALL, UNASSIGNED, RECENT, TRASH }
    private data class ViewSel(val kind: VK, val showId: Long = -1)
    private enum class Sort(val label: String) {
        NEWEST("Date added, newest first"), OLDEST("Date added, oldest first"), NAME("Name A-Z"), RANDOM("Random")
    }

    private data class Filters(
        val fromMs: Long? = null, val toMs: Long? = null, val dateLabel: String? = null,
        val batchId: Long? = null,
        /** 0 off, 1 in slideshow, 2 not in slideshow, 3 unassigned */
        val showMode: Int = 0, val showId: Long = -1,
        val lowRes: Boolean = false, val not169: Boolean = false, val dupes: Boolean = false,
    )

    private class Loaded(
        val view: ViewSel, val items: List<ImageEntity>, val baseCount: Int, val trashCount: Int,
        val shows: List<SlideshowEntity>, val batches: List<BatchEntity>, val batchCounts: Map<Long, Int>,
        val byShow: Map<Long, List<Long>>, val dupHashes: Set<String>,
    )

    private lateinit var repo: ArtRepo
    private lateinit var title: TextView
    private lateinit var selText: TextView
    private lateinit var summary: TextView
    private lateinit var rv: RecyclerView
    private lateinit var empty: TextView
    private lateinit var infoMain: TextView
    private lateinit var infoWarn: TextView
    private lateinit var btnView: View
    private val adapter = Adapter()

    private var view = ViewSel(VK.ALL)
    private var sort = Sort.NEWEST
    private var sortSeed = System.nanoTime()
    private var filters = Filters()

    private var items: List<ImageEntity> = emptyList()
    private var byId: Map<Long, ImageEntity> = emptyMap()
    private var shows: List<SlideshowEntity> = emptyList()
    private var batches: List<BatchEntity> = emptyList()
    private var batchCounts: Map<Long, Int> = emptyMap()
    private var byShow: Map<Long, List<Long>> = emptyMap()
    private var dupHashes: Set<String> = emptySet()
    private var baseCount = 0
    private var trashCount = 0
    private val selected = LinkedHashSet<Long>()

    private var loadSeq = 0
    private var firstLoad = true
    private var focusFirst = false

    private val gate = Semaphore(3)
    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    // ------------------------------------------------------------------ UI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ArtRepo.get(this)
        if (intent.getBooleanExtra(EXTRA_TRASH, false)) view = ViewSel(VK.TRASH)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Tv.BG)
            // 27dp = 5% of the 540dp-high TV screen (safe area); sides stay at 48dp.
            setPadding(Tv.dp(context, 48f), Tv.dp(context, 27f), Tv.dp(context, 48f), Tv.dp(context, 27f))
        }

        title = Tv.text(this, "Gallery", 28f, Tv.TEXT, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        selText = Tv.text(this, "0 selected", 18f, Tv.ACCENT, true)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(selText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = Tv.dp(this@GalleryActivity, 16f) })
        btnView = button("Show") { openViewMenu() }
        header.setPadding(0, 0, 0, Tv.dp(this, 8f))
        header.addView(btnView, buttonLp())
        header.addView(button("Filter") { openFilterMenu() }, buttonLp())
        header.addView(button("Sort") { openSortMenu() }, buttonLp())
        header.addView(button("Actions") { openActions() }, buttonLp())
        root.addView(header)

        // Only visible while a filter or a non-default sort is on (see updateBars).
        summary = Tv.text(this, "", 15f, Tv.SUBTLE).apply { setPadding(0, Tv.dp(context, 6f), 0, 0); setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; visibility = View.GONE }
        root.addView(summary)

        val dm = resources.displayMetrics
        val span = (dm.widthPixels / dm.density / 190f).toInt().coerceIn(4, 6)
        rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, span)
            adapter = this@GalleryActivity.adapter
            itemAnimator = null
            clipChildren = false
            clipToPadding = false
            val p = Tv.dp(context, 6f); setPadding(p, p, p, p)
            setHasFixedSize(true)
            isFocusable = false
        }
        empty = Tv.text(this, "", 22f, Tv.SUBTLE).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        val frame = FrameLayout(this)
        frame.addView(rv, FrameLayout.LayoutParams(-1, -1))
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        infoMain = Tv.text(this, " ", 17f).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, Tv.dp(context, 6f), 0, 0) }
        infoWarn = Tv.text(this, " ", 15f, Tv.WARN).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        val hintLine = Tv.text(this, "OK select  ·  ▶❚❚ full screen  ·  ≡ actions", 14f, Tv.SUBTLE).apply {
            setSingleLine(); alpha = 0.7f; setPadding(0, Tv.dp(context, 4f), 0, 0)
        }
        root.addView(infoMain)
        root.addView(infoWarn)
        root.addView(hintLine)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selected.isNotEmpty()) { selected.clear(); selectionChanged(); rebindVisible() }
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refresh(rescan = true)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.evictAll()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) when (keyCode) {
            KeyEvent.KEYCODE_MENU -> { openActions(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                (rv.focusedChild as? GalleryThumb)?.let { openPreview(it.imageId); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun button(label: String, onClick: () -> Unit) = Tv.text(this, label, 18f, Tv.TEXT, true).apply {
        val h = Tv.dp(context, 18f); val v = Tv.dp(context, 9f)
        setPadding(h, v, h, v)
        isFocusable = true; isClickable = true
        background = Tv.focusBg(context, Tv.CARD)
        setOnClickListener { onClick() }
    }

    private fun buttonLp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = Tv.dp(this@GalleryActivity, 10f) }

    private fun openPreview(id: Long) {
        startActivity(Intent(this, PreviewActivity::class.java).putExtra(PreviewActivity.EXTRA_IMAGE_ID, id))
    }

    // ------------------------------------------------------------------ loading

    private fun refresh(rescan: Boolean = false) {
        lifecycleScope.launch {
            if (rescan) try { repo.rescan() } catch (_: Exception) {}
            load()
        }
    }

    private suspend fun load() {
        val my = ++loadSeq
        val v = view; val srt = sort; val seed = sortSeed; val f = filters
        val d = withContext(Dispatchers.IO) { compute(v, srt, seed, f) }
        if (my != loadSeq) return

        val focused = rv.focusedChild as? GalleryThumb
        val hadFocus = focused != null || (rv.focusedChild != null)
        val oldFocusId = focused?.imageId
        val oldPos = rv.focusedChild?.let { rv.getChildAdapterPosition(it) } ?: -1

        view = d.view; items = d.items; baseCount = d.baseCount; trashCount = d.trashCount
        shows = d.shows; batches = d.batches; batchCounts = d.batchCounts; byShow = d.byShow; dupHashes = d.dupHashes
        byId = items.associateBy { it.id }
        selected.retainAll(byId.keys)
        adapter.notifyDataSetChanged()
        updateBars()

        val target = when {
            items.isEmpty() -> -1
            focusFirst -> 0
            oldFocusId != null -> items.indexOfFirst { it.id == oldFocusId }.takeIf { it >= 0 } ?: oldPos.coerceIn(0, items.size - 1)
            else -> 0
        }
        if (target >= 0 && (hadFocus || firstLoad || focusFirst)) focusPos(target, 4)
        else if (target < 0 && (hadFocus || firstLoad)) btnView.requestFocus()
        if (target < 0) { infoMain.text = " "; infoWarn.text = " " }
        firstLoad = false; focusFirst = false
    }

    private suspend fun compute(v0: ViewSel, srt: Sort, seed: Long, f: Filters): Loaded {
        val dao = repo.dao
        val live = dao.liveImages(); val del = dao.deletedImages()
        val sh = dao.slideshows(); val ba = dao.batches()
        val ms = dao.memberships()
        val byShow = ms.groupBy({ it.imageId }, { it.slideshowId })
        val v = if (v0.kind == VK.SHOW && sh.none { it.id == v0.showId }) ViewSel(VK.ALL) else v0

        val base: List<ImageEntity> = when (v.kind) {
            VK.SHOW -> repo.imagesFor(v.showId)
            VK.ALL -> live
            VK.UNASSIGNED -> live.filter { byShow[it.id].isNullOrEmpty() }
            VK.RECENT -> {
                val cutoff = System.currentTimeMillis() - RECENT_DAYS * 86_400_000L
                live.filter { it.addedAt >= cutoff }
            }
            VK.TRASH -> del
        }
        val dupHashes = live.groupingBy { it.hash }.eachCount().filter { it.value > 1 }.keys
        val showSet: Set<Long>? = if (f.showMode == 1 || f.showMode == 2) {
            val s = sh.firstOrNull { it.id == f.showId }
            when {
                s == null -> emptySet()
                s.kind == SlideshowKind.DEFAULT -> repo.imagesFor(s.id).map { it.id }.toHashSet()
                else -> ms.filter { it.slideshowId == s.id }.map { it.imageId }.toHashSet()
            }
        } else null

        var out = base.filter { i ->
            (f.fromMs == null || i.addedAt >= f.fromMs) &&
                (f.toMs == null || i.addedAt < f.toMs) &&
                (f.batchId == null || i.batchId == f.batchId) &&
                (when (f.showMode) {
                    1 -> i.id in showSet!!
                    2 -> i.id !in showSet!!
                    3 -> byShow[i.id].isNullOrEmpty()
                    else -> true
                }) &&
                (!f.lowRes || i.isLowRes) && (!f.not169 || i.isNot16x9) && (!f.dupes || i.hash in dupHashes)
        }
        val trash = v.kind == VK.TRASH
        out = when (srt) {
            Sort.NEWEST -> out.sortedByDescending { if (trash) it.deletedAt ?: it.addedAt else it.addedAt }
            Sort.OLDEST -> out.sortedBy { if (trash) it.deletedAt ?: it.addedAt else it.addedAt }
            Sort.NAME -> out.sortedBy { it.filename.lowercase(Locale.US) }
            Sort.RANDOM -> out.shuffled(java.util.Random(seed))
        }
        val counts = live.filter { it.batchId != null }.groupingBy { it.batchId!! }.eachCount()
        return Loaded(v, out, base.size, del.size, sh, ba, counts, byShow, dupHashes)
    }

    // ------------------------------------------------------------------ bars & info

    private fun pics(n: Int) = if (n == 1) "1 picture" else "$n pictures"

    /** "Oct 29" this year, "Oct 29, 2025" otherwise. */
    private fun shortDate(ms: Long): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val y = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
        return if (y == year) java.text.SimpleDateFormat("MMM d", Locale.US).format(java.util.Date(ms)) else fmtDate(ms)
    }

    private fun showLabel(s: SlideshowEntity) = (if (s.icon.isNotBlank()) s.icon + " " else "") + s.name

    private fun viewName(v: ViewSel = view): String = when (v.kind) {
        VK.SHOW -> shows.firstOrNull { it.id == v.showId }?.let { showLabel(it) } ?: "Slideshow"
        VK.ALL -> "All pictures"
        VK.UNASSIGNED -> "In no slideshow"
        VK.RECENT -> "Recently added (last $RECENT_DAYS days)"
        VK.TRASH -> "Recently deleted"
    }

    private fun filterParts(): List<String> {
        val f = filters; val p = ArrayList<String>()
        f.dateLabel?.let { p += "added $it" }
        f.batchId?.let { id -> batches.firstOrNull { it.id == id }?.let { p += "upload ${shortDate(it.createdAt)} ${it.source}" } }
        val sn = shows.firstOrNull { it.id == f.showId }?.name ?: "?"
        when (f.showMode) { 1 -> p += "in $sn"; 2 -> p += "not in $sn"; 3 -> p += "in no slideshow" }
        if (f.lowRes) p += "blurry"
        if (f.not169) p += "gets cropped"
        if (f.dupes) p += "duplicates"
        return p
    }

    private fun hasFilters() = filters != Filters()

    private fun updateBars() {
        title.text = viewName()
        val parts = ArrayList<String>()
        if (hasFilters()) { parts += "${items.size} of $baseCount"; parts += filterParts() }
        if (sort != Sort.NEWEST) parts += "sorted: ${sort.label.replaceFirstChar { it.lowercase() }}"
        if (hasFilters()) parts += "Filter › Clear"
        summary.text = parts.joinToString("  ·  ")
        summary.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        selText.text = "${selected.size} selected"
        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
            empty.text = when {
                view.kind == VK.TRASH && baseCount == 0 -> "Recently deleted is empty."
                baseCount == 0 && view.kind == VK.ALL -> "No art yet. On the main screen, choose Add art from phone or computer."
                baseCount == 0 -> "No pictures in ${viewName()} yet."
                else -> "No pictures match these filters."
            }
        } else empty.visibility = View.GONE
    }

    private fun selectionChanged() { selText.text = "${selected.size} selected" }

    private fun showInfo(id: Long) {
        val img = byId[id] ?: return
        val p = ArrayList<String>()
        p += "Added ${shortDate(img.addedAt)}"
        val names = (byShow[id] ?: emptyList()).mapNotNull { sid -> shows.firstOrNull { it.id == sid }?.let { showLabel(it) } }
        p += if (names.isEmpty()) "not in a slideshow" else "in " + names.joinToString(", ")
        if (img.favorite) p += "★ Favorite"
        p += img.filename
        infoMain.text = p.joinToString("  ·  ")
        val w = ArrayList<String>()
        if (img.deletedAt != null) {
            val left = (Defaults.TRASH_DAYS - (System.currentTimeMillis() - img.deletedAt) / 86_400_000L).coerceAtLeast(0).toInt()
            w += "Deleted ${shortDate(img.deletedAt)}, erased in ${if (left == 1) "1 day" else "$left days"}"
        }
        if (img.isLowRes) w += "May look blurry"
        if (img.isNot16x9) w += "Edges get cropped"
        if (img.hash in dupHashes) w += "Duplicate"
        infoWarn.text = if (w.isEmpty()) " " else w.joinToString("  ·  ")
    }

    private fun focusPos(pos: Int, tries: Int) {
        rv.post {
            if (pos >= adapter.itemCount) return@post
            rv.scrollToPosition(pos)
            rv.post {
                val h = rv.findViewHolderForAdapterPosition(pos)
                if (h != null) h.itemView.requestFocus() else if (tries > 0) focusPos(pos, tries - 1)
            }
        }
    }

    private fun rebindVisible() {
        for (i in 0 until rv.childCount) {
            val t = rv.getChildAt(i) as? GalleryThumb ?: continue
            t.checked = t.imageId in selected; t.invalidate()
        }
    }

    // ------------------------------------------------------------------ thumbnails

    private fun loadThumb(v: GalleryThumb, img: ImageEntity) {
        v.job?.cancel()
        val c = cache.get(img.id)
        if (c != null) { v.bmp = c; v.invalidate(); return }
        v.bmp = null
        v.invalidate()
        v.job = lifecycleScope.launch {
            val b = gate.withPermit {
                withContext(Dispatchers.IO) {
                    val f = repo.thumb(img) ?: return@withContext null
                    val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    BitmapFactory.decodeFile(f.path, o)
                }
            }
            if (b != null) {
                cache.put(img.id, b)
                if (v.imageId == img.id) { v.bmp = b; v.invalidate() }
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        init { setHasStableIds(true) }
        override fun getItemCount() = items.size
        override fun getItemId(position: Int) = items[position].id

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = GalleryThumb(this@GalleryActivity)
            val m = Tv.dp(this@GalleryActivity, 5f)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(m, m, m, m) }
            v.setOnClickListener {
                val id = v.imageId
                if (!selected.remove(id)) selected.add(id)
                v.checked = id in selected
                v.invalidate()
                selectionChanged()
            }
            v.setOnLongClickListener { openPreview(v.imageId); true }
            v.setOnFocusChangeListener { _, has -> if (has) showInfo(v.imageId) }
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView as GalleryThumb
            val img = items[position]
            v.imageId = img.id
            v.fav = img.favorite
            v.checked = img.id in selected
            loadThumb(v, img)
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            val v = holder.itemView as GalleryThumb
            v.job?.cancel(); v.job = null; v.bmp = null
        }
    }

    // ------------------------------------------------------------------ view / sort menus

    private fun openViewMenu() {
        val opts = ArrayList<Pair<String, ViewSel>>()
        for (s in shows) opts += showLabel(s) to ViewSel(VK.SHOW, s.id)
        opts += "All pictures" to ViewSel(VK.ALL)
        opts += "In no slideshow" to ViewSel(VK.UNASSIGNED)
        opts += "Recently added (last $RECENT_DAYS days)" to ViewSel(VK.RECENT)
        opts += "Recently deleted ($trashCount)" to ViewSel(VK.TRASH)
        val cur = opts.indexOfFirst { it.second == view }
        Tv.choose(this, "Show", opts.map { it.first }, cur) { i ->
            view = opts[i].second
            if (view.kind == VK.RECENT) sort = Sort.NEWEST
            selected.clear()
            focusFirst = true
            refresh()
        }
    }

    private fun openSortMenu() {
        Tv.choose(this, "Sort", Sort.values().map { it.label }, sort.ordinal) { i ->
            sort = Sort.values()[i]
            if (sort == Sort.RANDOM) sortSeed = System.nanoTime()
            focusFirst = true
            refresh()
        }
    }

    // ------------------------------------------------------------------ filters

    private fun openFilterMenu() {
        val f = filters
        val batchText = f.batchId?.let { id -> batches.firstOrNull { it.id == id }?.let { batchLabel(it) } } ?: "any"
        val showText = when (f.showMode) {
            1 -> "in ${shows.firstOrNull { it.id == f.showId }?.name ?: "?"}"
            2 -> "not in ${shows.firstOrNull { it.id == f.showId }?.name ?: "?"}"
            3 -> "in none"
            else -> "any"
        }
        val q = listOfNotNull("Blurry".takeIf { f.lowRes }, "Gets cropped".takeIf { f.not169 }).joinToString(" + ").ifEmpty { "any" }
        val items = listOf(
            "Date added: ${f.dateLabel ?: "any"}",
            "Upload: $batchText",
            "In a slideshow: $showText",
            "Picture quality: $q",
            "Duplicates: ${if (f.dupes) "on" else "off"}",
            "Clear filters",
        )
        Tv.choose(this, "Filter", items) { i ->
            when (i) {
                0 -> openDateFilter()
                1 -> openBatchFilter()
                2 -> openShowFilter()
                3 -> openQualityFilter()
                4 -> applyFilters(filters.copy(dupes = !filters.dupes))
                5 -> applyFilters(Filters())
            }
        }
    }

    private fun applyFilters(f: Filters, reopen: Boolean = true) {
        filters = f
        focusFirst = true
        refresh()
        if (reopen) openFilterMenu()
    }

    private fun batchLabel(b: BatchEntity): String {
        val n = batchCounts[b.id] ?: 0
        val extra = if (b.name.isNotBlank() && !b.name.contains(b.source, ignoreCase = true)) " (${b.name})" else ""
        return "${shortDate(b.createdAt)} — ${b.source}$extra, ${pics(n)}"
    }

    private fun openBatchFilter() {
        val bs = batches.filter { (batchCounts[it.id] ?: 0) > 0 || it.id == filters.batchId }
        val labels = listOf("Any upload") + bs.map { batchLabel(it) }
        val cur = filters.batchId?.let { id -> bs.indexOfFirst { it.id == id } + 1 } ?: 0
        Tv.choose(this, "Upload", labels, cur) { i ->
            applyFilters(filters.copy(batchId = if (i == 0) null else bs[i - 1].id))
        }
    }

    private fun openShowFilter() {
        val modes = listOf("Any", "In a slideshow…", "Not in a slideshow…", "In no slideshow")
        Tv.choose(this, "In a slideshow", modes, filters.showMode) { m ->
            if (m == 1 || m == 2) {
                pickShow("Slideshow") { s -> applyFilters(filters.copy(showMode = m, showId = s.id)) }
            } else applyFilters(filters.copy(showMode = m, showId = -1))
        }
    }

    private fun openQualityFilter() {
        val opts = listOf("Any", "Blurry (below 1920x1080)", "Gets cropped (not 16:9)", "Blurry and gets cropped")
        val cur = (if (filters.lowRes) 1 else 0) + (if (filters.not169) 2 else 0)
        Tv.choose(this, "Picture quality", opts, cur) { i ->
            applyFilters(filters.copy(lowRes = i == 1 || i == 3, not169 = i == 2 || i == 3))
        }
    }

    private fun openDateFilter() {
        val opts = listOf("Any date", "Added today", "Added this week", "Added this month", "On a date...", "Before a date...", "After a date...", "Between two dates...")
        Tv.choose(this, "Date added", opts) { i ->
            val now = System.currentTimeMillis()
            fun day(ms: Long) = fmtDate(ms)
            when (i) {
                0 -> applyFilters(filters.copy(fromMs = null, toMs = null, dateLabel = null))
                1 -> applyFilters(filters.copy(fromMs = dayStart(now), toMs = null, dateLabel = "today"))
                2 -> applyFilters(filters.copy(fromMs = weekStart(now), toMs = null, dateLabel = "this week"))
                3 -> applyFilters(filters.copy(fromMs = monthStart(now), toMs = null, dateLabel = "this month"))
                4 -> pickDate("On date") { d -> applyFilters(filters.copy(fromMs = d, toMs = addDays(d, 1), dateLabel = "on ${day(d)}")) }
                5 -> pickDate("Before date") { d -> applyFilters(filters.copy(fromMs = null, toMs = d, dateLabel = "before ${day(d)}")) }
                6 -> pickDate("After date") { d -> applyFilters(filters.copy(fromMs = addDays(d, 1), toMs = null, dateLabel = "after ${day(d)}")) }
                7 -> pickDate("From date") { a ->
                    pickDate("To date") { b ->
                        val lo = minOf(a, b); val hi = maxOf(a, b)
                        applyFilters(filters.copy(fromMs = lo, toMs = addDays(hi, 1), dateLabel = "${day(lo)} to ${day(hi)}"))
                    }
                }
            }
        }
    }

    private fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(ms: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_MONTH, n) }.timeInMillis

    private fun weekStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart(ms)
        while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis

    private fun monthStart(ms: Long): Long = Calendar.getInstance().apply { timeInMillis = dayStart(ms); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    /** Year, month, day lists: works with just a D-pad. */
    private fun pickDate(title: String, onDate: (Long) -> Unit) {
        val y0 = Calendar.getInstance().get(Calendar.YEAR)
        val years = (y0 downTo y0 - 9).toList()
        Tv.choose(this, "$title: year", years.map { it.toString() }) { yi ->
            val months = DateFormatSymbols(Locale.US).months.take(12)
            Tv.choose(this, "$title: month", months) { mi ->
                val cal = Calendar.getInstance().apply { clear(); set(years[yi], mi, 1) }
                val n = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                Tv.choose(this, "$title: day (${months[mi]} ${years[yi]})", (1..n).map { it.toString() }) { di ->
                    cal.set(Calendar.DAY_OF_MONTH, di + 1)
                    onDate(dayStart(cal.timeInMillis))
                }
            }
        }
    }

    private fun pickShow(title: String, onPick: (SlideshowEntity) -> Unit) {
        Tv.choose(this, title, shows.map { showLabel(it) }) { onPick(shows[it]) }
    }

    // ------------------------------------------------------------------ actions

    private fun focusedImageId(): Long? = (rv.focusedChild as? GalleryThumb)?.imageId

    private fun targets(): List<Long> =
        if (selected.isNotEmpty()) selected.toList() else focusedImageId()?.let { listOf(it) } ?: emptyList()

    private fun targetLabel() = when {
        selected.isNotEmpty() -> "${pics(selected.size)} selected"
        focusedImageId() != null -> "This picture"
        else -> "Nothing selected"
    }

    private fun openActions() {
        val trash = view.kind == VK.TRASH
        val acts = ArrayList<Pair<String, () -> Unit>>()
        fun add(label: String, f: () -> Unit) { acts += label to f }

        add("Select all (${items.size})") { items.forEach { selected.add(it.id) }; selectionChanged(); rebindVisible() }
        add("Clear selection") { selected.clear(); selectionChanged(); rebindVisible() }
        add("Invert selection") {
            val inv = items.map { it.id }.filter { it !in selected }
            selected.clear(); selected.addAll(inv); selectionChanged(); rebindVisible()
        }
        val ids = targets()
        val n = ids.size
        val what = if (selected.isEmpty()) "this picture" else pics(n)
        if (trash) {
            add("Restore") { needTargets(ids) { act("Restored ${pics(n)}") { repo.restore(ids) } } }
            add("Delete permanently…") {
                needTargets(ids) {
                    Tv.confirm(this, "Delete permanently", "Erase $what? This cannot be undone.", "Delete") { act("Deleted ${pics(n)} permanently") { repo.deletePermanently(ids) } }
                }
            }
            add("Empty Recently deleted ($trashCount)…") {
                if (trashCount == 0) toast("Recently deleted is already empty")
                else Tv.confirm(this, "Empty Recently deleted", "Erase all ${pics(trashCount)}? This cannot be undone.", "Empty") {
                    act("Recently deleted emptied") { repo.emptyTrash() }
                }
            }
        } else {
            add("Add to slideshow…") {
                needTargets(ids) { pickShow("Add $what to") { s -> act("Added ${pics(n)} to ${s.name}") { repo.setMembership(s.id, ids, true) } } }
            }
            add("Remove from slideshow…") {
                needTargets(ids) { pickShow("Remove $what from") { s -> act("Removed ${pics(n)} from ${s.name}") { repo.setMembership(s.id, ids, false) } } }
            }
            val allFav = ids.isNotEmpty() && ids.all { byId[it]?.favorite == true }
            add(if (allFav) "Unmark favorite" else "Mark favorite") {
                needTargets(ids) { act(if (allFav) "Unmarked ${pics(n)}" else "Marked ${pics(n)} as favorite") { repo.setFavorite(ids, !allFav) } }
            }
            add("Delete (goes to Recently deleted)") {
                needTargets(ids) { act("Moved ${pics(n)} to Recently deleted (kept ${Defaults.TRASH_DAYS} days)") { repo.moveToTrash(ids) } }
            }
            add("Delete permanently…") {
                needTargets(ids) {
                    Tv.confirm(this, "Delete permanently", "Erase $what? This cannot be undone.", "Delete") { act("Deleted ${pics(n)} permanently") { repo.deletePermanently(ids) } }
                }
            }
        }
        Tv.choose(this, targetLabel(), acts.map { it.first }) { acts[it].second() }
    }

    private fun needTargets(ids: List<Long>, f: () -> Unit) {
        if (ids.isEmpty()) toast("Select pictures with OK first") else f()
    }

    private fun act(done: String, block: suspend () -> Unit) {
        lifecycleScope.launch {
            try { block(); toast(done) } catch (e: Exception) { toast("Failed: ${e.message}") }
            selected.clear()
            load()
        }
    }

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}

/** One grid cell, drawn by hand (thumbnail, selection check, favorite star, focus ring) to stay light on RAM. */
private class GalleryThumb(c: Context) : View(c) {
    var imageId = -1L
    var bmp: Bitmap? = null
    var fav = false
    var checked = false
    var job: Job? = null

    private val dp = c.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val rect = RectF()
    private val check = Path()

    init {
        isFocusable = true
        isClickable = true
        isLongClickable = true
        isFocusableInTouchMode = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * 9 / 16)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animate().scaleX(if (gainFocus) 1.07f else 1f).scaleY(if (gainFocus) 1.07f else 1f).setDuration(100).start()
        translationZ = if (gainFocus) 12 * dp else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Tv.CARD
        canvas.drawRect(0f, 0f, w, h, paint)
        bmp?.let { b ->
            val bw = b.width; val bh = b.height
            if (bw * 9 > bh * 16) { val sw = bh * 16 / 9; src.set((bw - sw) / 2, 0, (bw + sw) / 2, bh) }
            else { val sh = bw * 9 / 16; src.set(0, (bh - sh) / 2, bw, (bh + sh) / 2) }
            rect.set(0f, 0f, w, h)
            canvas.drawBitmap(b, src, rect, paint)
        }
        if (checked) {
            paint.color = 0x664FC3F7
            canvas.drawRect(0f, 0f, w, h, paint)
            val r = 14 * dp; val cx = w - r - 8 * dp; val cy = r + 8 * dp
            paint.color = Tv.ACCENT
            canvas.drawCircle(cx, cy, r, paint)
            check.reset()
            check.moveTo(cx - r * 0.5f, cy)
            check.lineTo(cx - r * 0.1f, cy + r * 0.4f)
            check.lineTo(cx + r * 0.55f, cy - r * 0.4f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f * dp
            paint.color = 0xFF101418.toInt()
            canvas.drawPath(check, paint)
            paint.style = Paint.Style.FILL
        }
        if (fav) {
            paint.textSize = 24 * dp
            paint.setShadowLayer(3 * dp, 0f, 0f, 0xFF000000.toInt())
            paint.color = 0xFFFFD54F.toInt()
            canvas.drawText("★", 8 * dp, 26 * dp, paint)
            paint.clearShadowLayer()
        }
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6 * dp
            paint.color = Tv.ACCENT
            canvas.drawRect(3 * dp, 3 * dp, w - 3 * dp, h - 3 * dp, paint)
            paint.strokeWidth = 2 * dp
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(6 * dp, 6 * dp, w - 6 * dp, h - 6 * dp, paint)
            paint.style = Paint.Style.FILL
        } else if (checked) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * dp
            paint.color = Tv.ACCENT
            canvas.drawRect(1.5f * dp, 1.5f * dp, w - 1.5f * dp, h - 1.5f * dp, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
