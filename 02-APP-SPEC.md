# 02 — App Spec: "TV Art" for Fire TV (French-first edition)

This is the build spec. It describes the app **as it must be when you hand it to the user**. A working reference implementation is in `source/` (Kotlin, Gradle). It was built for a different person and a different TV, so it does **not** match this spec yet. Section 12 lists every difference you must make.

Read `03-RISKS-AND-SECURITY.md` alongside this file. Fixes marked **[REQUIRED]** there are part of this spec.

---

## 1. What the app does

A sideloaded Fire TV app that:

1. **Screensaver:** replaces Amazon's (ad-supported) screensaver with the user's own wallpaper slideshow and a clock.
2. **Art over YouTube (overlay):** shows the same slideshow full-screen **on top of** the YouTube app while music keeps playing underneath. The remote still controls YouTube.
3. **Library:** a gallery to add, sort, select and delete wallpapers, from the TV remote or from a phone/PC web page on the home network.
4. **Slideshows:**
   - **By default there is one single slideshow** ("My slideshow") that plays every picture.
   - **Optional seasonal mode** (off by default): the app switches automatically between slideshows by date (Winter, Spring, Summer, Fall by default). The user can rename seasons, change their dates, add seasons and remove seasons, all from the TV.

Personal use only. No phone app, no screen mirroring, no internet access except the home-network web page. No analytics, no outbound calls.

**The user is French-speaking.** Every screen, message, notification and the web page must be in French by default (see section 9 and `04-FRENCH-STRINGS.md`).

---

## 2. Target device (fill in during setup)

The reference build targeted Fire OS 7 (Android 9 / API 28). The user's device is unknown until you ask (see `01-USER-SETUP.md`, step A). Record it here before you build:

| Item | Value |
|---|---|
| Model (e.g. Fire TV Stick 4K Max, Fire TV Cube, a Fire TV Edition television) | _ask_ |
| Fire OS version (Settings → My Fire TV → About) | _ask_ |
| Android API level (`adb shell getprop ro.build.version.sdk`) | _measure_ |
| Screen resolution (`adb shell wm size`) | _measure_ |
| Free storage (`adb shell df /sdcard`) | _measure_ |
| TV language setting | _ask_ |

**Stop conditions (tell the user plainly and do not build):**
- The device runs **Vega OS** (some newer Fire TV Sticks run it instead of Fire OS). Vega OS is not Android and cannot install Android apps. How to tell: the About screen says Vega OS, or `adb` cannot connect at all because there is no Developer Options/ADB debugging.
- No Developer Options and no ADB debugging can be enabled.

**Expected differences by Fire OS version (verify, don't assume):**
- Fire OS 7 (API 28): everything in this spec was proven on it.
- Fire OS 8 (API 30): sideloading and the overlay are expected to work. The **screensaver replacement may be blocked or reset by Amazon** on some versions. Build Phase 0b (screensaver proof) early and report the result to the user before building the rest. If it can't work, the app still has value through the overlay and the "Play slideshow" button. Say so and ask whether to continue.
- Storage: keep `targetSdk = 28`, so legacy storage access keeps working on API 29/30 devices. Do not raise `targetSdk` unless the device forces it. If you must, switch file access to app-specific storage + MediaStore, which is a real redesign, so ask the user first.

---

## 3. Build phases (do in order; stop and report if a phase's checks fail)

- **Phase −1, device check:** connect over ADB, fill in section 2, check the stop conditions.
- **Phase 0, overlay proof:** one hard-coded image plus the double-press toggle over YouTube.
- **Phase 0b, screensaver proof:** minimal `DreamService` set via ADB, then idle-timeout test.
- **Phase 1:** slideshow engine, clock, on-TV settings, Enable/Disable buttons, **French UI and language/date settings**.
- **Phase 2:** library, gallery, the single slideshow, web manager (with the security fixes), USB import.
- **Phase 3:** optional seasonal mode, the seasons editor, manual override, timeline.
- **Phase 4:** the full review in `03-RISKS-AND-SECURITY.md`, then the user acceptance test with the user (section 13).

There is **no "nice-to-have" phase**. Do not add favorites, countdowns, clock tints, night/quiet-hours slideshows, special days or anything else not in this spec, even though some exist in `source/`.

---

## 4. Architecture (keep the reference design)

- **AccessibilityService** (`system/OverlayService.kt`), the overlay engine:
  - Draws with `TYPE_ACCESSIBILITY_OVERLAY`, so `SYSTEM_ALERT_WINDOW` is not needed.
  - Listens to remote keys (`canRequestFilterKeyEvents`) **without taking focus from YouTube**. Every key except the toggle passes through (`onKeyEvent` returns `false`).
  - Window flags: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_FULLSCREEN`, plus `keepScreenOn = true` on the root view.
- **DreamService** (`system/ArtDreamService.kt`): the screensaver, using the same slideshow view.
- **One shared `SlideshowView`** (`engine/`) used by the overlay, the screensaver and the preview, with no duplicated logic.
- **Launcher Activity** (Leanback launcher intent + banner): the app's tile in "Your Apps", with all settings.
- **Room (SQLite) database** for image metadata, slideshows, seasons, memberships. Image files live in one folder; slideshows are *virtual* (one image can belong to several).
- **SharedPreferences** (`data/Settings.kt`) for settings.
- **NanoHTTPD** web manager, running only while switched on.
- **ZXing core** for the QR code (generated at runtime, see 7.4).

---

## 5. Slideshow engine (unchanged from the reference; it imitates Fire TV's "Dissolve" screensaver)

All values are named constants, editable in settings, with optional per-slideshow overrides.

- **Fit:** center-crop (fill the screen, crop the edges, never stretch).
- **Decode:** `inSampleSize`, targeting at most **1.15× screen size**. Never load the full-resolution bitmap. Hold at most the current and next image. Decode the next image **before** the crossfade starts. This is memory-critical on Fire TV.
- **Timing:** `DISPLAY_MS = 80_000` (80 s per image), `CROSSFADE_MS = 1_000` (1 s dissolve).
- **Zoom-out:** `s0 = max(screenW/imgW, screenH/imgH)`; start zoom `z = min(1.15, 1/s0)` (never upscale past 1 screen px per image px); skip if `z < 1.02`; animate `z → 1.0` over the first 30 s with ease-out, then hold.
- **Pan:** only for images whose center-crop cuts something off. Start offset toward a random end of the overflow axis, drift toward the center during the same 30 s, cap movement at the overflow, apply to ~50% of eligible images (tunable).
- **Rendering:** `Matrix` + `ValueAnimator`, hardware-accelerated, 60 fps, no stutter.
- **Order:** shuffle by default, no repeats until every image has shown once (`ShuffleBag`).
- **Empty slideshow:** fall back to the main slideshow. If that's empty too, show a centered message in French explaining how to add pictures.
- **Live switching:** when the active slideshow changes (season boundary, override, edits), the running overlay or screensaver crossfades to the new slideshow at the next image change, without restarting.

**Dark wall** (exists in the reference, keep it): triple-press the toggle key in YouTube for a black screen with a large, faint (10%) clock in the center. Music keeps playing.

---

## 6. Clock overlay

- **Font:** Amazon Ember from the device if present (`/system/fonts`, loaded with `Typeface.createFromFile`, never bundled), else `sans-serif`.
- **Two lines, right-aligned, top-right** (values on a 1920×1080 canvas; scale to the device): right edge ≈ 130 px from the screen edge, top ≈ 65 px, ≈ 10 px gap. Date ~46 px Regular; time ~124 px Light. White with shadow `setShadowLayer(6px, 0, 2px, #80000000)`. Keep the reference's clock style options (like Amazon / glow / strong shadow).
- **Date line content depends on two independent settings** (section 9):
  - *Language* picks the words (French: `JEU. 29 OCT.`; English: `THU, OCT 29`).
  - *Date format* picks the order: day-month (`JEU. 29 OCT.` / `THU, 29 OCT`) or month-day (`JEU. OCT. 29` / `THU, OCT 29`). For ISO, use day-month order on the clock; ISO only changes numeric dates.
  - Build it from `DateFormatSymbols` for the chosen locale, not by string concatenation of English names. Uppercase with the chosen locale (`uppercase(locale)`), so French accents survive (`DÉC.`, `FÉVR.`, `AOÛT`).
- **Time line:** a separate **24-hour / 12-hour** setting. **Default 24-hour** (`HH:mm`, e.g. `17:05`); 12-hour shows `h:mm` with no AM/PM, as in the reference.
- Updates on the minute boundary (`ACTION_TIME_TICK`), system time zone. Not affected by zoom or crossfade. On/off setting, default on.

---

## 7. Library, gallery, web manager

### 7.1 Storage model (unchanged)
- All images live in `/sdcard/Pictures/TVArt/` plus **one level** of subfolders.
- DB per image: id, relative filename, content hash (SHA-1, used for duplicates and backups), `addedAt` (when the app first saw the file), width/height, size, import batch id, memberships, `deletedAt` (Recently Deleted).
- ~320×180 thumbnail cache, made at import.
- **Rescan** on app open and slideshow start: new files get `addedAt = now`; missing files are removed from the DB. Keep the reference's guard: if storage isn't mounted or listing fails, do **not** treat it as "everything deleted".
- **Subfolder → slideshow auto-assignment:** a new file inside a subfolder whose name matches a season name (e.g. `TVArt/Hiver/neige.jpg` → "Hiver") is added to that season once. **Matching must ignore case, accents and apostrophes** (normalize with `java.text.Normalizer` NFD and strip combining marks), so `Ete`, `été` and `Été` all match "Été". The reference only lowercases ASCII, which breaks French names.
- **File names:** the reference replaces every non-ASCII character in uploaded names with `_` (so `été.jpg` becomes `_t_.jpg`). Change it to keep Unicode letters and digits (`\p{L}\p{N}`) and only strip path separators, control characters and `<>:"|?*`. Keep the rule that only the last path segment is used. See 03 for the full rule.

### 7.2 On-TV gallery (remote-friendly; keep the reference behavior)
- Thumbnail grid, D-pad focus, OK toggles selection with a clear checkmark.
- **Top bar:** current view, filter, sort, selected count.
- **Views:** All pictures, Unassigned (in no season), Recently added, and each slideshow/season.
- **Filters** (combinable): date added (on/before/after/between, quick picks "today" and "this month"), import batch, slideshow membership (in X / not in X / unassigned), quality (low resolution below 1920×1080 = soft; not 16:9 = will be cropped), duplicates.
- **Sort:** date added (newest/oldest), name, random.
- **Bulk actions:** select all (respects the filter), clear, invert, **add to / remove from** a season, **delete** (to Recently Deleted, kept 30 days, restorable) and **delete permanently** (with confirmation).
- In **single-slideshow mode**, hide the season-related views, filters and actions (there's only one slideshow).
- **Full-screen preview:** long-press or Play shows a picture exactly as the slideshow will (crop + zoom).
- **All dates shown here follow the date-format setting.**

### 7.3 Web manager ("Add pictures from a phone or computer")
- Small LAN server (NanoHTTPD, port 8080), running **only** while the switch is on. Auto-off after 30 minutes idle or when switched off.
- The TV shows the address, a **QR code** and a **6-digit PIN**. The page requires the PIN.
- The page mirrors the gallery: thumbnails, filters, sort, multi-select (click, shift-click, select all, clear), add to/remove from seasons, delete, restore.
- **Upload:** drag-and-drop or multi-file. Each upload is one named import batch. An optional "Add to:" picker (season) appears only in seasonal mode.
- **Backup:** download/restore a JSON file of settings, seasons and memberships, keyed by content hash.
- **The page is in the app's current language** (French by default). Serve the strings to the page from the app (e.g. `/api/strings`, or inject a JSON object into `index.html` at serve time) so there is one translation source. Format dates on the page with the same date-format setting.
- The security fixes in 03 (Host-header check, upload limits, safe errors, validated restore, etc.) are **required**.

### 7.4 QR code — generated per install, never shipped
- The QR code is **generated at runtime** on the TV with ZXing from the TV's **current** address (`http://<tv-ip>:8080`). The reference already does this in `ui/MainActivity.kt` (`qrBlock()`).
- **Do not ship any pre-made QR image** in the APK, the repo or the handoff, and do not hard-code any address. Before release, check that `res/` and `assets/` contain no QR images and that no IP address literal appears in the code (03 has the check).
- Regenerate on every screen refresh, so a changed IP (router restart) gives a correct code.
- Encode the **URL only, never the PIN**. Keep the 4-module quiet zone and black-on-white. Make it **at least 300 dp** (the reference's 220 dp is too small to scan from a sofa) and label it in French ("Ou scannez ce code avec l'appareil photo de votre téléphone, puis entrez le code PIN.").
- If there is no network, show a French message instead of the QR code.

### 7.5 USB import (keep)
If a USB drive has a `TVArt` folder, settings offers "Add pictures from a USB drive". It imports into a new batch, with an optional target season (seasonal mode only) and a skip-duplicates option.

---

## 8. Slideshows and seasons (the main change)

### 8.1 Default: single-slideshow mode
- Out of the box there is **one slideshow**, called **"My slideshow"** (French: see 04), that plays **every picture** in the library.
- No schedule, no seasons, no timeline is shown. The main screen shows "Play slideshow", Gallery, "Add pictures…", settings. It must feel simple.
- Setting: **Seasonal slideshows: Off / On** (in "More" or a "Slideshows" page), with a one-line explanation.

### 8.2 Seasonal mode (optional)
When the user turns it **On** for the first time, create these four seasons (names in the app language; French names in 04):

| Season | Starts | Last day (inclusive) | Stored end (exclusive) |
|---|---|---|---|
| Winter / Hiver | Dec 1 | Feb 28 (Feb 29 in leap years) | Mar 1 |
| Spring / Printemps | Mar 1 | May 31 | Jun 1 |
| Summer / Été | Jun 1 | Aug 31 | Sep 1 |
| Fall / Automne | Sep 1 | Nov 30 | Dec 1 |

- The switch happens at **midnight** at the start of the next season's first day, in the TV's time zone.
- **Leap years:** store ends as exclusive month/day (`Mar 1`). A leap day (Feb 29) falls inside Winter automatically. Never store `Feb 29` as a boundary. The date picker may offer Feb 29 as a start/last day, but warn that it only happens every 4 years and suggest Feb 28 or Mar 1 instead. Unit-test Feb 28, Feb 29 and Mar 1 in both leap and non-leap years.
- Turning seasonal mode **Off** keeps the seasons and their picture assignments (hidden), and plays everything as one slideshow again. Turning it **On** again restores them. Never delete user data when toggling.
- The four defaults are created **once**. Never re-create them after the user has deleted or renamed them (keep a "seeded" flag; the reference's `ensureSeeded()` re-seeds whenever Default is missing, so change that).

### 8.3 What plays when (seasonal mode)
Priority, highest first:
1. **Manual override** ("Play X now", see 8.5)
2. **The season whose dates contain today**
3. **Between seasons** (a date no season covers): the **main slideshow**. The setting "Between seasons, show:" chooses *pictures not in any season* (default) or *all pictures*.

**No overlaps allowed.** Seasons can't overlap, so there's never a "which wins" question. The editor rejects an overlapping range with a clear French message naming the season it collides with, and suggests the nearest valid dates. This replaces the reference's priority/shorter-range rules. Remove the SPECIAL kind and priorities.

**Gaps are allowed** and fall back to the main slideshow. The seasons list and timeline show gaps explicitly ("1–14 mars : diaporama principal").

### 8.4 Seasons editor (on the TV, remote only)
A "Seasons" screen (visible only in seasonal mode):
- **List** of seasons in date order, each showing icon, name, date range (in the chosen date format), picture count, and "⚠ no pictures" when empty. The current season is marked "Now playing".
- **Per season:** Rename (on-screen keyboard; 1–30 characters; names unique ignoring case and accents), Icon, Color, **Starts on**, **Last day**, pictures ("Open in gallery"), per-season overrides (seconds per image, pan %, clock on/off), **Play now…**, **Remove season**.
- **Date picking:** two D-pad pickers (day, month), **ordered by the date-format setting** (day first for European/ISO, month first for North American). Month names in the app language. Days limited to the month's length (Feb: 29). Show a live preview line ("Du 1 déc. au 28 févr.").
- **Ranges may wrap the new year** (Dec 1 → Feb 28). Each season has **exactly one** range (simpler for the user; the DB may keep a ranges table, but the UI edits one).
- **Add season:** "+ Add a season" → name → start → last day → validated (no overlap) → created with a distinct color. Maximum 12 seasons.
- **Remove season:** confirmation dialog: "Remove 'Hiver'? Its pictures stay in your library." Pictures that were only in that season become unassigned (and play "between seasons" if that setting is on). If it was the override target, clear the override.
- **Removing the last season** is allowed. Then show "No seasons — everything plays as one slideshow" and offer "Restore the 4 default seasons".
- **Restore default seasons** button (asks first; only adds back the default names that don't exist and whose dates don't overlap).
- **Timeline:** a year bar with each season's color, today's marker and gaps. Keep the reference `TimelineView`, minus overlaps and special days. Month labels in the app language.
- **Up next** line on the main screen: e.g. "Automne en cours → Hiver commence le 1 déc. (dans 27 jours)".
- **Empty-season warning:** if the current or next season has 0 pictures, show a banner on the main screen so the user can add pictures before the switch.

### 8.5 Manual override (keep)
"Play [slideshow/season] now" with a duration: **until the next season change**, **for N days**, or **until I turn it off**. While active, the main screen shows it clearly with a "Return to the schedule" button (in single-slideshow mode this feature is hidden; there's nothing to override). Clear the override automatically when its time is up or its target is removed.

---

## 9. Language and date format (independent settings)

Three **separate** settings in "Look & timing" (or a "Language & dates" page). **None of them changes another.**

| Setting | Options | Default |
|---|---|---|
| **Language** (`appLanguage`) | Français · English · Same as TV | **Français** |
| **Date format** (`dateFormat`) | Day/Month (31/12) · Month/Day (12/31) · Year-Month-Day (2026-12-31) | **Day/Month** |
| **Clock** (`clockHours`) | 24-hour · 12-hour | **24-hour** |

- The user may be from France or Québec. Québec French often uses year-month-day for numeric dates, and North American habits (12-hour clocks, month-first) are common there. That's why language never forces a date format: a French-language user can pick any format.
- **Implementation:** all user-visible strings in `res/values/strings.xml` (English) and `res/values-fr/strings.xml` (French), with plurals in `<plurals>`. Apply the chosen language with a `Configuration` locale override in `attachBaseContext` of every Activity and Service (API 25–30 compatible), plus the web page strings. Use `Locale.FRENCH` (`fr`) generically, **not** `fr-FR` or `fr-CA`, so wording stays neutral.
- **One date-formatting helper** (e.g. `DateText.kt`) used everywhere (TV screens, web page, batch names, clock, timeline). No `SimpleDateFormat("MMM d", Locale.US)` calls left in the code (the reference has several, so grep for them).
- Numeric dates: `31/12/2026` · `12/31/2026` · `2026-12-31`. Text dates: French `1 déc. 2026` (day-first) or `déc. 1, 2026` (month-first). Use `1er` for the first of the month in French only if you do it consistently; plain `1` is acceptable and simpler.
- Changing any of these applies immediately (recreate the current screen; the overlay/screensaver picks it up at the next image).

---

## 10. On-TV settings (the app's tile)

Leanback-style list, fully remote-driven, in French. Main screen (top to bottom):
- Banners only when something is wrong (storage permission, setup step missing, system reset detected, empty season coming up).
- **Play slideshow** (shows what's playing and why: "my slideshow", "season", "you chose it", "between seasons").
- Return to the schedule (only during an override) / Choose what plays (seasonal mode only).
- **Gallery** · **Seasons** (seasonal mode only) · **Add pictures from a phone or computer** (switch + address + QR + PIN + Change PIN).
- **Look & timing ›**: seconds per image, zoom on/off and max zoom, pan on/off and %, crossfade length, shuffle, clock on/off and style, **Language, Date format, Clock 24h/12h**.
- **Screensaver & YouTube ›**: Art screensaver on/off, Art over YouTube on/off, remote button for the toggle, status.
- **More ›**: **Seasonal slideshows on/off**, Between seasons show…, Add pictures from a USB drive, storage (used, free, Recently Deleted, empty now), check for new pictures, backup info.

### System integration: Enable/Disable buttons (keep the reference `SystemHooks.kt`)
- `WRITE_SECURE_SETTINGS` is granted once by ADB during setup.
- **Screensaver:** on enable, save the current `screensaver_components` and `screensaver_enabled` values, then write ours. On disable, restore exactly what was saved.
- **Overlay:** add/remove our service in `enabled_accessibility_services`, **preserving other entries** (`:`-separated).
- On launch, compare actual vs expected; if Fire OS reset something, show a banner ("Le système a réinitialisé l'économiseur d'écran. Le réactiver ?").
- If the permission is missing, show **plain French guidance** ("Une étape d'installation doit être refaite depuis l'ordinateur. Demandez de l'aide à la personne qui a installé l'application."), with the ADB command in small text for the helper. Do not refer to `INSTALL.md` on the TV (the user won't see it).

---

## 11. Setup and deploy scripts

Keep `source/deploy.ps1` (Windows) and `source/deploy.sh` (macOS/Linux), updated:
- Package `com.tvart.slideshow` (already renamed in `source/`).
- Image folder layout: loose files = main slideshow; subfolders named after seasons (`Hiver/`, `Printemps/`, `Été/`, `Automne/`) are auto-assigned in seasonal mode. **Test that accented folder names (`Été`) survive `adb push` from Windows**; if they don't, also accept `Ete/` (the accent-insensitive matching in 7.1 covers it).
- Print the device model, Fire OS version and API level at connect.
- Save the original `screensaver_components`, `screensaver_enabled` and `enabled_accessibility_services` values to `screensaver_backup.txt` **before** anything changes (keep the "first copy wins" rule).
- At the end, print French next steps for the user.
- Don't hard-code `JAVA_HOME` to an Android Studio path without checking it exists; detect a JDK 17 or tell the user what's missing.

---

## 12. Changes from `source/` (checklist)

**Remove completely** (code, DB fields, settings keys, backup fields, web routes, UI rows, strings):
- [ ] Favorites (`ImageEntity.favorite`, `FAVORITE_WEIGHT`, `favoriteWeight`, `/api/favorite`, backup `favorites`, weighting in the shuffle).
- [ ] Holiday countdown (`countdownOn`, `countdownText()`, `nextStartOf` if unused).
- [ ] Per-slideshow clock tint (`clockTint`).
- [ ] Night / quiet hours slideshow (`quietOn`, `quietStartHour`, `quietEndHour`, `quietSlideshowId`, `Reason.QUIET`, the "Night" section).
- [ ] Special days (`SlideshowKind.SPECIAL`, priorities, "+ Add a special day…", the conflict rule).
- [ ] The old six-season calendar in `ArtRepo.ensureSeeded()` (Christmas, Valentine's, Spring, Anime, Summer, Halloween).
- [ ] References to `INSTALL.md` on TV screens.

**Change:**
- [ ] Default = single slideshow ("My slideshow"); seasonal mode off by default (8.1).
- [ ] Seasonal mode with the 4 default seasons, editor, add/remove, no overlaps, gaps fall back (8.2–8.4).
- [ ] `SlideshowKind`: keep MAIN (the old DEFAULT) and SEASON only (MANUAL may stay as "not scheduled" if you keep the reference's manual slideshows; otherwise remove it).
- [ ] Seed once with a flag; never re-create deleted seasons.
- [ ] Language, date format, 24h/12h settings, all independent (9).
- [ ] Extract **every** hard-coded user-visible string (Kotlin + `assets/web/index.html` + notifications + toasts + dialogs) into resources; add the French translations from 04.
- [ ] Accent-insensitive folder matching; Unicode-safe upload file names (7.1).
- [ ] QR code at least 300 dp, French caption, URL only, regenerated per refresh (7.4).
- [ ] All security fixes marked [REQUIRED] in 03.
- [ ] Database: this is a new install, so start the Room schema at version 1 with `exportSchema = true` and a schema directory. If you ever ship an update to this user, write real migrations (never `fallbackToDestructiveMigration`, which would wipe their library).
- [ ] App name and launcher label: see 04 for the French name decision; keep the banner/icon art (they contain no personal data).

**Keep as is:** slideshow engine, decode limits, overlay mechanics, dark wall, screensaver hooks and restore logic, gallery, Recently Deleted, USB import, backup/restore (minus removed fields, plus validation), manual override, web manager (plus fixes), timeline (minus special days/overlaps), deploy scripts (updated).

---

## 13. Done when (acceptance test, with the user)

- [ ] Phase 0/0b checks pass on the user's device: music keeps playing 30+ minutes under the overlay; screen never sleeps with the overlay up; play/pause/skip reach YouTube; overlay survives a reboot; after the user's normal idle time the art screensaver starts with **no Amazon ad first**; any key exits.
- [ ] Every screen, dialog, notification, error and the web page is in French, with no English left (switch the language to English and back to check both). No text is cut off (French runs ~20–30% longer than English).
- [ ] Out of the box: one slideshow, all pictures, no seasons visible.
- [ ] Turning seasonal mode on creates Winter/Spring/Summer/Fall with the right dates; renaming, changing dates, adding and removing work; overlaps are refused with a clear message; turning the mode off and on keeps everything.
- [ ] Changing the device date (or using a unit test with a fixed clock) to Nov 30 → Dec 1, Feb 28 → Feb 29 → Mar 1 (leap and non-leap), May 31 → Jun 1, Aug 31 → Sep 1 switches correctly at midnight.
- [ ] Date format and language are independent: French + Month/Day and English + Day/Month both display correctly everywhere (TV, web page, clock).
- [ ] Web manager: upload from a phone using the QR code, PIN required, upload/filter/add to season/delete/restore/backup work; the security tests in 03 pass.
- [ ] Enable/Disable buttons work; turning the art screensaver off restores Amazon's; a system reset is detected and shown.
- [ ] Unit tests pass (`./gradlew test`), including the new scheduler and date-format tests.

---

## 14. Tech

- Kotlin, Gradle (versions as in `source/`: AGP 8.7.3, Kotlin 2.0.21, KSP, JDK 17), `minSdk 25`, `targetSdk 28`, `compileSdk 35`.
- Room, NanoHTTPD 2.3.1, ZXing core 3.5.3, coroutines. No other network libraries.
- `INTERNET` permission only for the LAN web manager.
- Debug-signed APK is fine for personal sideloading. **Keep the same signing key for updates**: back up the debug keystore (`~/.android/debug.keystore`) next to the project, or updates will fail to install over the existing app (and uninstalling would delete the library database).
