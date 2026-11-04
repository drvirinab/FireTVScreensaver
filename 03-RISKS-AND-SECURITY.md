# 03 — Risks, Weaknesses and Security Review

Two parts:
1. **Findings already identified** in the reference source (`source/`) and in this plan. Items marked **[REQUIRED]** are part of the spec. Items marked **[RECOMMENDED]** should be done unless you have a good reason not to, and you must tell the user if you skip one.
2. **Your own review** (mandatory). These findings are a starting point, not a complete audit. Before Phase 1 and again before handing over, review the plan and the code yourself for weaknesses, vulnerabilities and likely problems, and add what you find to the log at the end of this file.

Threat model, briefly: a personal app on a home TV. The realistic risks are (a) other devices or people on the same home or shared network (guests, a compromised smart device, a neighbour on a weak Wi-Fi password), (b) malicious web pages opened on a phone or PC in the home, (c) bad or huge files, (d) **breaking the TV's own settings**, which is the most likely real-world harm, and (e) data loss in the user's picture library.

---

## 1. Web manager (`web/WebServer.kt`, `web/WebManagerService.kt`, `assets/web/index.html`)

**1.1 [REQUIRED] DNS-rebinding / Host-header check.** The server answers any `Host` header. A malicious web page opened on a phone in the home can use DNS rebinding to send requests to `http://<tv-ip>:8080` from its own origin and guess PINs from the victim's browser. Fix: reject any request whose `Host` header isn't the TV's current IP:port (or `localhost`) with 403. Also reject `POST` requests whose `Origin` header is present and doesn't match.

**1.2 [REQUIRED] PIN brute force across many addresses.** The lockout is per IP: 5 failures → 60 s. With 6 digits (1,000,000 PINs) and several IPs, that's thousands of guesses a day. Fix: add a **global** limit (e.g. after 20 failures from anywhere, lock the login for 15 minutes and show "Too many attempts. Change the PIN on the TV" on the TV). Keep the per-IP limit. Generate a new PIN each time the manager is switched on (the reference keeps one PIN forever).

**1.3 [REQUIRED] Session tokens never expire.** Tokens live in memory until the service stops. Fix: expire tokens after the 30-minute idle timeout, clear all tokens when the PIN changes, and set the cookie with `Max-Age`.

**1.4 [REQUIRED] Upload limits.** There's no size or count limit, so one upload can fill the TV's storage, and Fire TV devices have little. Fix: cap each file (e.g. 40 MB) and each request, and refuse uploads when free space would drop below a floor (e.g. 500 MB), with a clear French message. Clean up the temp folder (`cacheDir/upload`) on start and stop.

**1.5 [REQUIRED] Image validation and decompression bombs.** Files are accepted by extension, then `Decode.bounds()` reads only the header. A tiny file that claims huge dimensions (e.g. 50,000×50,000) can still cause out-of-memory errors during thumbnail creation or display. Fix: check that the magic bytes match the extension (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`, BMP `BM`); reject files above ~16,000 px on either side or ~100 megapixels. Decode thumbnails with `inSampleSize` computed from the bounds (never full size). Catch `OutOfMemoryError` everywhere a bitmap is decoded (the reference does in one place only).

**1.6 [REQUIRED] Error messages leak internals.** `serve()` returns `e.message` to the client, which can expose file paths and stack details. Fix: log the details on the device, and return a generic, translated message.

**1.7 [REQUIRED] Backup restore trusts the file.** `Settings.importMap()` writes **any** key from the JSON, of any type. A wrong type (e.g. `"toggleKey": "x"`) causes a `ClassCastException` on the next `getInt`, so the overlay, screensaver or main screen can crash on every start. Ranges with `month = 13` or `day = 0` crash `Scheduler.dayOfYear()` (array index out of range). Fix: restore only an **allow-list** of known keys with type and range checks (e.g. display time 5–600 s, pan 0–100, month 1–12, day 1–days-in-month); reject the whole file with a clear message if `app != "tvart"` or the version is unknown; validate season names (length, uniqueness) and **reject overlapping seasons**; cap the file at ~2 MB. Drop the removed features' fields (favorites, countdown, clock tint, quiet hours, special days).

**1.8 [REQUIRED] Defensive scheduler.** Even with validation, `Scheduler` must never crash on bad data, because it runs inside the screensaver and overlay. Clamp or skip invalid ranges and log them. Add unit tests with invalid input.

**1.9 [RECOMMENDED] Plain HTTP.** The PIN and session cookie travel unencrypted on the home Wi-Fi. HTTPS with a self-signed certificate would scare a non-technical user with browser warnings, so accept HTTP for a home network, but: keep the server off by default with the 30-minute auto-off; mention on the TV screen that it should only be used on the home Wi-Fi; and never enable it on public or shared networks. Tell the user this in plain words.

**1.10 [RECOMMENDED] Security headers.** Add `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (or a CSP `frame-ancestors 'none'`), `Referrer-Policy: no-referrer`, and a simple `Content-Security-Policy` (`default-src 'self'; img-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'`, tightened if you move the inline script to a file).

**1.11 [REQUIRED] Keep HTML escaping.** `index.html` builds HTML strings and escapes names with `esc()`. Once Unicode file names are allowed (7.1 in 02), file names, season names, batch names and translated strings must still **always** go through `esc()` or `textContent`. Audit every `innerHTML`/`insertAdjacentHTML` call. Season names come from the TV keyboard **and** from backup files, so they're untrusted.

**1.12 [RECOMMENDED] PIN visibility.** The PIN appears in the persistent notification. On a TV that's fine, but don't log it, and don't put it in the QR code or the URL.

**1.13 [RECOMMENDED] Bind scope.** NanoHTTPD binds to all interfaces. If the device has several (Ethernet + Wi-Fi, VPN), show the address of the one in use. Consider binding only to the Wi-Fi/Ethernet address.

---

## 2. File handling (`data/ArtRepo.kt`)

**2.1 [REQUIRED] Unicode-safe file names without path traversal.** The reference keeps only the last path segment (good) but replaces all non-ASCII characters with `_`, which breaks French names (`été.jpg` → `_t_.jpg`, and several files may collapse to the same name). New rule: take the last segment after `/` and `\`; apply Unicode NFC normalization; remove control characters and `<>:"|?*`; trim dots and spaces at both ends; reject `.` and `..`; limit to 120 characters (keeping the extension); fall back to `image.jpg`. `uniqueName()` already avoids overwriting; keep it.

**2.2 [REQUIRED] Accent-insensitive folder → season matching** (see 02 §7.1). Otherwise `Été/` pictures silently go to the main slideshow.

**2.3 [RECOMMENDED] Trash collisions.** Files in subfolders go to `trash/<sub>/<name>`; restoring when the original name is taken uses `uniqueName(folder, name)` with a relative path. Test delete → restore for a file inside a subfolder whose name exists again.

**2.4 [RECOMMENDED] Hash choice.** SHA-1 is fine for duplicate detection (not security). Keep it; don't use it for anything security-related.

**2.5 [REQUIRED] Big first scan.** The first scan hashes every file on the main thread's coroutine. With hundreds of 4K files that can take minutes, and the reference shows the screen first (good). Make sure the screensaver and overlay don't block on a scan, and show "Scan en cours…" progress.

**2.6 [RECOMMENDED] Storage permission on Fire OS 8.** Legacy storage works with `targetSdk 28`. Verify on the device that `/sdcard/Pictures/TVArt` is readable and writable after install, and that the runtime permission prompt (if shown) is in French.

---

## 3. System settings (`system/SystemHooks.kt`): highest real-world risk

**3.1 [REQUIRED] Never lose the user's original values.** Keep the "save before first change" logic and `screensaver_backup.txt`. Add the original values to the app's own storage **and** show them on a hidden "Restore original screensaver" screen, so a helper can recover without a PC.

**3.2 [REQUIRED] Uninstall leaves settings pointing at a missing app.** If the app is uninstalled while the art screensaver is on, `screensaver_components` points to a component that no longer exists. Fire OS usually falls back, but not always. Tell the user (in French) to turn **Art screensaver Off** in the app before uninstalling. Include the manual ADB restore command in the helper notes.

**3.3 [REQUIRED] Accessibility list preservation.** Keep the `:`-separated merge. Test with another accessibility service already enabled (e.g. VoiceView or a screen reader, if available) that enabling **and** disabling ours leaves it intact. Don't set `accessibility_enabled = 0` if any other entry remains (the reference handles this; keep the test).

**3.4 [REQUIRED] Overlay key handling must not trap the user.** The overlay doesn't take focus and passes every key through except the toggle. Make sure Back/Home always hide it (the safety net), and that if the slideshow view crashes, the overlay window is removed, not left as a frozen full-screen image. Wrap the overlay's view creation and rendering in try/catch that removes the window.

**3.5 [RECOMMENDED] Accessibility service description.** Fire OS shows the service description when it's enabled. It must be in French and honest ("Affiche vos images par-dessus YouTube. Ne lit pas le contenu de l'écran."). The reference `accessibility_service.xml` already requests no content access (`canRetrieveWindowContent="false"`, only `typeWindowStateChanged` events, key filtering). Keep it that minimal: key filtering is powerful (the service sees every remote press), so never log key events and never add content access.

**3.6 [REQUIRED] Amazon may block or reset this.** Some Fire OS updates reset or block third-party screensavers. The app must detect it (banner) and never fight the system in a loop: no automatic re-enable in the background. Only re-enable when the user selects the banner.

---

## 4. Seasons, dates and language

**4.1 [REQUIRED] Overlaps and gaps.** The editor must reject overlaps (02 §8.3). Also validate on restore and on DB load (defense in depth). Gaps are allowed and must show in the list and timeline.

**4.2 [REQUIRED] Leap years and midnight.** Unit-test Feb 28/29/Mar 1 in leap and non-leap years; Nov 30 → Dec 1 (wrap); a season that covers the whole year (the reference loses one day on exclusive storage; decide and test); and a single-day season. The switch time is local midnight, so test a time-zone change and a DST change night (the Calendar-based code should handle it; prove it).

**4.3 [REQUIRED] Re-seeding bug.** The reference re-creates the default seasons whenever the Default slideshow is missing. Use a one-time flag so deleted or renamed seasons never come back by themselves.

**4.4 [REQUIRED] Language and date format are independent.** Test French + Month/Day, French + ISO, English + Day/Month. No `Locale.US` date formatting left in user-visible code (grep `Locale.US` and `SimpleDateFormat`). `Locale.US` stays correct for **non-visible** things (file names, hex, backup dates).

**4.5 [REQUIRED] Text overflow.** French is ~20–30% longer. TV rows, buttons and dialog titles must wrap or ellipsize, never clip. Check every screen at the device's real resolution.

**4.6 [RECOMMENDED] Season name collisions with folder names.** A season named `Hiver/2` or with characters illegal in folder names can't be matched to a folder. Either forbid `/ \ : * ? " < > |` in season names, or document that folder matching uses a normalized name.

---

## 5. Device, performance, reliability

**5.1 [REQUIRED] Memory.** Fire TV Sticks have 1–2 GB of RAM. Keep the decode limits (≤1.15× screen, two bitmaps max). On a 4K device, `1.15 × 3840×2160` ARGB is ~44 MB per bitmap. Decode with `RGB_565` when the image has no alpha, or cap the decode at 1080p if the device reports `isLowRamDevice` or a small `memoryClass`. Test 1 hour of overlay with 4K images and watch `adb shell dumpsys meminfo com.tvart.slideshow`.

**5.2 [REQUIRED] `largeHeap` is not a fix.** Keep it if it helps, but the decode rules above are what prevent crashes.

**5.3 [RECOMMENDED] Wake lock.** The web manager holds a partial wake lock for up to 31 minutes. That's fine; make sure it's released on every stop path (the reference does in `onDestroy`).

**5.4 [RECOMMENDED] Burn-in.** OLED TVs plus a static clock for hours could cause burn-in. The art changes every 80 s, but the clock doesn't move. Consider shifting the clock by a few pixels every hour (invisible to the user). Mention it if the user's TV is OLED.

**5.5 [REQUIRED] Signing key continuity.** Updates must be signed with the same key, or Android refuses to install them over the old version. The only way around that is to uninstall, which **deletes the database** (seasons, memberships). Back up the keystore (02 §14).

---

## 6. Build and setup process

**6.1 [REQUIRED] ADB left on.** See `01-USER-SETUP.md` §B.3: explain and recommend turning ADB debugging off after setup.

**6.2 [REQUIRED] Downloads.** Only the four sample-wallpaper URLs in 01 §D.4 are allowed, with the checks listed there. Get build dependencies only from official sources (Google Maven, Maven Central, Gradle, Adoptium/Microsoft for the JDK, Google for platform-tools). Don't run scripts from elsewhere.

**6.3 [REQUIRED] No identifying data.** `source/` was scrubbed of the previous owner's details and the package was renamed. Don't add the current user's name, email, home IP, Wi-Fi name or photos to code, comments, commit messages or logs. Before delivering, run:
```
grep -rnaiE "192\.168\.|10\.[0-9]+\.[0-9]+\.[0-9]+|@gmail|@hotmail|password|ssid" source/app/src
find source/app/src -iname "*qr*"
```
Expected: no hits except the code that **generates** the QR code (`qrBlock`). There must be **no pre-made QR image**, and no hard-coded address.

**6.4 [RECOMMENDED] Debug build.** A debug-signed APK is acceptable for personal sideloading. `android:allowBackup="false"` is already set; keep it. Don't enable `debuggable` features you don't need in release builds if you produce one.

**6.5 [RECOMMENDED] Dependency versions.** NanoHTTPD 2.3.1 is old and unmaintained. It's acceptable on a LAN-only, off-by-default server with the fixes above. Don't expose it beyond the home network. Check that ZXing, Room and AndroidX versions build with the current Gradle; if you upgrade, re-run all tests.

---

## 7. Your review log (fill this in)

Before Phase 1, and again before the acceptance test, review the plan and code for weaknesses, vulnerabilities and potential problems. Cover at least: every network route, every file write, every system-settings write, every parser (JSON, image, HTTP), every date calculation, every user-input field (TV keyboard and web), and every place the app could crash inside the overlay or screensaver. Record:

| # | Area | Finding | Severity (high/med/low) | Fix / decision | Test that proves it |
|---|---|---|---|---|---|
| 1 | | | | | |

Report the **high** items and anything you chose not to fix to the user, in simple French, before the acceptance test.
