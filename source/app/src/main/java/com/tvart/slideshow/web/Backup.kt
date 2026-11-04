package com.tvart.slideshow.web

import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.MembershipEntity
import com.tvart.slideshow.data.RangeEntity
import com.tvart.slideshow.data.SlideshowEntity
import com.tvart.slideshow.data.SlideshowKind
import org.json.JSONArray
import org.json.JSONObject

/** JSON backup / restore. Images are referenced by content hash so a backup survives a re-import of the files. */
object Backup {
    private fun JSONObject.putNullable(k: String, v: Any?) { put(k, v ?: JSONObject.NULL) }

    suspend fun export(repo: ArtRepo): JSONObject {
        val dao = repo.dao
        val root = JSONObject()
        root.put("app", "tvart")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val settings = JSONObject()
        for ((k, v) in repo.settings.exportMap()) {
            when (v) {
                is Boolean, is Int, is Long, is String -> settings.put(k, v)
                is Float -> settings.put(k, v.toDouble())
                is Double -> settings.put(k, v)
            }
        }
        root.put("settings", settings)

        val live = dao.liveImages()
        val hashById = live.associate { it.id to it.hash }
        val ranges = dao.ranges().groupBy { it.slideshowId }
        val members = dao.memberships().groupBy { it.slideshowId }

        val shows = JSONArray()
        for (s in dao.slideshows()) {
            val o = JSONObject()
            o.put("name", s.name)
            o.put("color", s.color)
            o.put("icon", s.icon)
            o.put("kind", s.kind)
            o.put("sortOrder", s.sortOrder)
            o.putNullable("displaySec", s.displaySec)
            o.putNullable("panPercent", s.panPercent)
            o.putNullable("clockOn", s.clockOn)
            o.putNullable("clockTint", s.clockTint)
            o.put("countdownOn", s.countdownOn)
            val ra = JSONArray()
            for (r in ranges[s.id].orEmpty()) {
                ra.put(JSONObject().put("startMonth", r.startMonth).put("startDay", r.startDay)
                    .put("endMonth", r.endMonth).put("endDay", r.endDay))
            }
            o.put("ranges", ra)
            val hs = JSONArray()
            members[s.id].orEmpty().mapNotNull { hashById[it.imageId] }.distinct().forEach { hs.put(it) }
            o.put("images", hs)
            shows.put(o)
        }
        root.put("slideshows", shows)

        val favs = JSONArray()
        live.filter { it.favorite }.map { it.hash }.distinct().forEach { favs.put(it) }
        root.put("favorites", favs)

        val batches = JSONArray()
        for (b in dao.batches()) {
            batches.put(JSONObject().put("name", b.name).put("source", b.source).put("createdAt", b.createdAt))
        }
        root.put("batches", batches)
        return root
    }

    private fun JSONObject.intOrNull(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.boolOrNull(k: String): Boolean? = if (isNull(k)) null else optBoolean(k)

    /** Returns counts for the UI. */
    suspend fun restore(repo: ArtRepo, root: JSONObject): JSONObject {
        val dao = repo.dao
        repo.ensureSeeded()
        var linked = 0; var notFound = 0; var favLinked = 0; var favNotFound = 0
        var created = 0; var updated = 0

        root.optJSONObject("settings")?.let { so ->
            val map = HashMap<String, Any?>()
            for (k in so.keys()) {
                val v = so.get(k)
                if (v != JSONObject.NULL) map[k] = v
            }
            repo.settings.importMap(map)
        }

        val live = dao.liveImages()
        val idByHash = HashMap<String, Long>()
        for (i in live) idByHash.putIfAbsent(i.hash, i.id)

        val shows = root.optJSONArray("slideshows") ?: JSONArray()
        for (n in 0 until shows.length()) {
            val o = shows.optJSONObject(n) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val kind = o.optInt("kind", SlideshowKind.SEASON)
            val all = dao.slideshows()
            val existing = if (kind == SlideshowKind.DEFAULT) all.firstOrNull { it.kind == SlideshowKind.DEFAULT }
            else all.firstOrNull { it.kind != SlideshowKind.DEFAULT && it.name.equals(name, ignoreCase = true) }
            val fields = SlideshowEntity(
                id = existing?.id ?: 0,
                name = if (existing != null && existing.kind == SlideshowKind.DEFAULT) existing.name else name,
                color = o.optInt("color", 0xFF607D8B.toInt()), icon = o.optString("icon", ""),
                kind = existing?.kind ?: kind, sortOrder = o.optInt("sortOrder", 0),
                displaySec = o.intOrNull("displaySec"), panPercent = o.intOrNull("panPercent"),
                clockOn = o.boolOrNull("clockOn"), clockTint = o.intOrNull("clockTint"),
                countdownOn = o.optBoolean("countdownOn", false),
            )
            val id: Long
            if (existing != null) { dao.updateSlideshow(fields); id = existing.id; updated++ }
            else { id = dao.insertSlideshow(fields); created++ }

            dao.clearRanges(id)
            val ra = o.optJSONArray("ranges") ?: JSONArray()
            for (r in 0 until ra.length()) {
                val ro = ra.optJSONObject(r) ?: continue
                dao.insertRange(RangeEntity(slideshowId = id, startMonth = ro.optInt("startMonth"), startDay = ro.optInt("startDay"),
                    endMonth = ro.optInt("endMonth"), endDay = ro.optInt("endDay")))
            }

            val hs = o.optJSONArray("images") ?: JSONArray()
            val ms = ArrayList<MembershipEntity>()
            for (h in 0 until hs.length()) {
                val imgId = idByHash[hs.optString(h)]
                if (imgId != null) { ms.add(MembershipEntity(id, imgId)); linked++ } else notFound++
            }
            if (ms.isNotEmpty()) dao.addMemberships(ms)
        }

        val favs = root.optJSONArray("favorites") ?: JSONArray()
        val favIds = ArrayList<Long>()
        for (i in 0 until favs.length()) {
            val imgId = idByHash[favs.optString(i)]
            if (imgId != null) { favIds.add(imgId); favLinked++ } else favNotFound++
        }
        if (favIds.isNotEmpty()) repo.setFavorite(favIds, true)

        repo.touch()
        return JSONObject().put("slideshowsCreated", created).put("slideshowsUpdated", updated)
            .put("linked", linked).put("notFound", notFound)
            .put("favoritesLinked", favLinked).put("favoritesNotFound", favNotFound)
    }
}
