package com.tvart.slideshow.web

import android.content.Context
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.ImageEntity
import com.tvart.slideshow.data.SlideshowKind
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** LAN web manager. All data goes through [ArtRepo]; every route except the page and /api/login needs the session cookie. */
class WebServer(private val context: Context, port: Int) : NanoHTTPD(port) {
    private val repo = ArtRepo.get(context)
    private val rnd = SecureRandom()
    private val tokens = ConcurrentHashMap.newKeySet<String>()
    private class Fail(var count: Int = 0, var lockUntil: Long = 0)
    private val fails = ConcurrentHashMap<String, Fail>()

    @Volatile var lastRequest = System.currentTimeMillis()
        private set

    init {
        setTempFileManagerFactory(TempFileManagerFactory { CacheTempFiles(File(context.cacheDir, "upload").apply { mkdirs() }) })
    }

    private class CacheTempFiles(private val dir: File) : TempFileManager {
        private val files = ArrayList<TempFile>()
        override fun createTempFile(filenameHint: String?): TempFile {
            val f = CacheTemp(File.createTempFile("up", ".tmp", dir)); files.add(f); return f
        }
        override fun clear() { files.forEach { try { it.delete() } catch (_: Exception) {} }; files.clear() }
    }

    private class CacheTemp(private val file: File) : TempFile {
        private var out: OutputStream? = null
        override fun open(): OutputStream = FileOutputStream(file).also { out = it }
        override fun delete() { try { out?.close() } catch (_: Exception) {}; file.delete() }
        override fun getName(): String = file.absolutePath
    }

    // ------------------------------------------------------------------ helpers

    private fun json(o: Any, status: Response.IStatus = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", o.toString()).apply { addHeader("Cache-Control", "no-store") }

    private fun err(status: Response.IStatus, msg: String) = json(JSONObject().put("error", msg), status)

    private fun cookieToken(s: IHTTPSession): String? =
        s.headers["cookie"]?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("tvsid=") }?.substringAfter('=')

    private fun authed(s: IHTTPSession) = cookieToken(s)?.let { it in tokens } == true

    private fun body(s: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        s.parseBody(files)
        val txt = files["postData"] ?: return JSONObject()
        return JSONObject(txt)
    }

    private fun ids(o: JSONObject): List<Long> {
        val a = o.optJSONArray("ids") ?: return emptyList()
        return (0 until a.length()).map { a.getLong(it) }
    }

    private fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    // ------------------------------------------------------------------ routing

    override fun serve(session: IHTTPSession): Response {
        lastRequest = System.currentTimeMillis()
        return try {
            route(session)
        } catch (e: Exception) {
            err(Response.Status.INTERNAL_ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun route(s: IHTTPSession): Response {
        val uri = s.uri
        val get = s.method == Method.GET
        if (get && (uri == "/" || uri == "/index.html")) {
            val html = context.assets.open("web/index.html").use { it.readBytes() }
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html.inputStream(), html.size.toLong())
                .apply { addHeader("Cache-Control", "no-store") }
        }
        if (uri == "/api/login" && s.method == Method.POST) return login(s)
        if (!uri.startsWith("/api/") && !uri.startsWith("/thumb/") && !uri.startsWith("/image/"))
            return err(Response.Status.NOT_FOUND, "Not found")
        if (!authed(s)) return err(Response.Status.UNAUTHORIZED, "Login required")

        if (get && uri.startsWith("/thumb/")) {
            val img = imageOf(uri.substringAfterLast('/')) ?: return err(Response.Status.NOT_FOUND, "No such image")
            val f = runBlocking(Dispatchers.IO) { repo.thumb(img) } ?: return err(Response.Status.NOT_FOUND, "No thumbnail")
            return file(f, "image/jpeg", "private, max-age=3600")
        }
        if (get && uri.startsWith("/image/")) {
            val img = imageOf(uri.substringAfterLast('/')) ?: return err(Response.Status.NOT_FOUND, "No such image")
            val f = repo.fileOf(img)
            if (!f.exists()) return err(Response.Status.NOT_FOUND, "File missing")
            return file(f, mime(img.filename), "private, max-age=600")
        }

        return when {
            get && uri == "/api/state" -> state()
            get && uri == "/api/backup" -> backup()
            s.method != Method.POST -> err(Response.Status.METHOD_NOT_ALLOWED, "POST expected")
            uri == "/api/upload" -> upload(s)
            else -> post(uri, body(s))
        }
    }

    private fun post(uri: String, b: JSONObject): Response = runBlocking(Dispatchers.IO) {
        when (uri) {
            "/api/membership" -> {
                repo.setMembership(b.getLong("slideshowId"), ids(b), b.optBoolean("include", true)); ok()
            }
            "/api/favorite" -> { repo.setFavorite(ids(b), b.optBoolean("favorite", true)); ok() }
            "/api/delete" -> {
                if (b.optBoolean("permanent")) repo.deletePermanently(ids(b)) else repo.moveToTrash(ids(b)); ok()
            }
            "/api/restore" -> { repo.restore(ids(b)); ok() }
            "/api/empty-trash" -> { repo.emptyTrash(); ok() }
            "/api/slideshow/create" -> {
                val name = b.optString("name").trim()
                if (name.isEmpty()) return@runBlocking err(Response.Status.BAD_REQUEST, "Name required")
                val id = repo.createSlideshow(name, SlideshowKind.SEASON, 0xFF42A5F5.toInt(), "🖼")
                json(JSONObject().put("ok", true).put("id", id))
            }
            "/api/batch" -> {
                val id = repo.newBatch(b.optString("name"), "Web")
                json(JSONObject().put("batchId", id))
            }
            "/api/restore-backup" -> {
                val r = Backup.restore(repo, b)
                json(r.put("ok", true))
            }
            else -> err(Response.Status.NOT_FOUND, "Not found")
        }
    }

    private fun ok() = json(JSONObject().put("ok", true))

    private fun imageOf(idStr: String): ImageEntity? {
        val id = idStr.toLongOrNull() ?: return null
        return runBlocking(Dispatchers.IO) { repo.dao.image(id) }
    }

    private fun file(f: File, mime: String, cache: String): Response =
        newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(f), f.length()).apply { addHeader("Cache-Control", cache) }

    // ------------------------------------------------------------------ login

    private fun login(s: IHTTPSession): Response {
        val ip = s.remoteIpAddress ?: "?"
        val f = fails.getOrPut(ip) { Fail() }
        val now = System.currentTimeMillis()
        synchronized(f) {
            if (now < f.lockUntil) {
                return err(Response.Status.FORBIDDEN, "Too many attempts. Try again in ${(f.lockUntil - now) / 1000 + 1} s")
            }
            val pin = try { body(s).optString("pin") } catch (_: Exception) { "" }
            val good = MessageDigest.isEqual(pin.toByteArray(), repo.settings.webPin.toByteArray())
            if (!good) {
                f.count++
                if (f.count >= 5) { f.count = 0; f.lockUntil = now + 60_000 }
                return err(Response.Status.UNAUTHORIZED, "Wrong PIN")
            }
            f.count = 0
        }
        val raw = ByteArray(24).also { rnd.nextBytes(it) }
        val token = raw.joinToString("") { "%02x".format(it) }
        tokens.add(token)
        return ok().apply { addHeader("Set-Cookie", "tvsid=$token; Path=/; HttpOnly; SameSite=Strict") }
    }

    // ------------------------------------------------------------------ state / backup

    private fun state(): Response = runBlocking(Dispatchers.IO) {
        val dao = repo.dao
        val shows = dao.slideshows()
        val live = dao.liveImages()
        val rows = live + dao.deletedImages()
        val ms = dao.memberships().groupBy { it.imageId }

        val sa = JSONArray()
        for (s in shows) {
            val members = repo.imagesFor(s.id)
            val o = JSONObject().put("id", s.id).put("name", s.name).put("icon", s.icon).put("color", hex(s.color))
                .put("kind", s.kind).put("count", members.size)
            // Default is virtual (depends on a setting), so send its exact member list.
            if (s.kind == SlideshowKind.DEFAULT) o.put("memberIds", JSONArray().apply { members.forEach { put(it.id) } })
            sa.put(o)
        }
        val counts = live.groupingBy { it.batchId }.eachCount()
        val ba = JSONArray()
        for (b in dao.batches()) {
            ba.put(JSONObject().put("id", b.id).put("name", b.name).put("source", b.source).put("createdAt", b.createdAt)
                .put("count", counts[b.id] ?: 0))
        }
        val ia = JSONArray()
        for (i in rows) {
            val o = JSONObject().put("id", i.id).put("filename", i.filename).put("width", i.width).put("height", i.height)
                .put("sizeBytes", i.sizeBytes).put("addedAt", i.addedAt).put("favorite", i.favorite)
                .put("hash", i.hash).put("lowRes", i.isLowRes).put("not16x9", i.isNot16x9)
            o.put("batchId", i.batchId ?: JSONObject.NULL)
            o.put("deletedAt", i.deletedAt ?: JSONObject.NULL)
            val sids = JSONArray(); ms[i.id].orEmpty().forEach { sids.put(it.slideshowId) }
            o.put("slideshowIds", sids)
            ia.put(o)
        }
        val act = repo.resolveActive()
        json(JSONObject().put("slideshows", sa).put("batches", ba).put("images", ia)
            .put("activeSlideshow", JSONObject().put("name", act.slideshow.name).put("reason", act.reason.label)))
    }

    private fun backup(): Response {
        val o = runBlocking(Dispatchers.IO) { Backup.export(repo) }
        val name = "tvart-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString(2))
            .apply { addHeader("Content-Disposition", "attachment; filename=\"$name\""); addHeader("Cache-Control", "no-store") }
    }

    // ------------------------------------------------------------------ upload

    private fun upload(s: IHTTPSession): Response {
        val files = HashMap<String, String>()
        s.parseBody(files)
        val p = s.parameters
        fun param(k: String) = p[k]?.firstOrNull()
        val batchId = param("batchId")?.toLongOrNull()
        val slideshowId = param("slideshowId")?.toLongOrNull()?.takeIf { it >= 0 }
        val skip = param("skipDuplicates")?.let { it == "true" || it == "1" || it == "on" } ?: true
        val results = JSONArray()
        for ((key, path) in files) {
            val name = param(key) ?: continue
            val r = JSONObject().put("name", name)
            try {
                val res = runBlocking(Dispatchers.IO) {
                    FileInputStream(path).use { repo.importStream(it, name, batchId, slideshowId, skip) }
                }
                when (res) {
                    is ArtRepo.ImportResult.Added -> r.put("status", "added").put("id", res.image.id)
                    is ArtRepo.ImportResult.Duplicate -> r.put("status", "duplicate").put("id", res.existing.id)
                        .put("reason", "Already in library as ${res.existing.filename}")
                    is ArtRepo.ImportResult.Failed -> r.put("status", "failed").put("reason", res.reason)
                }
            } catch (e: Exception) {
                r.put("status", "failed").put("reason", e.message ?: "error")
            }
            results.put(r)
        }
        return json(JSONObject().put("results", results))
    }
}
