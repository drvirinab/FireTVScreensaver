# 00 — START HERE (for the agent building this app)

You're building **"TV Art"** for a user: a sideloaded **Fire TV** app that shows their own wallpapers as an ad-free screensaver and as a slideshow on top of YouTube (music keeps playing), with an optional set of **seasonal slideshows** that switch by date. **The user speaks French.** The app's screens and everything you say to the user must be in French.

A working reference implementation (Kotlin/Gradle) is in `source/`. It was written for someone else, scrubbed of their personal details, and **does not yet match this spec**. You'll adapt it; don't start from scratch.

## Files, in reading order

| File | What it is |
|---|---|
| `00-START-HERE.md` | This file: order of work, rules. |
| `01-USER-SETUP.md` | What to ask the user, developer-mode steps, where pictures go, sample wallpapers. Contains the **French text to send to the user**. |
| `02-APP-SPEC.md` | The full spec, including the list of changes from `source/` (§12) and the acceptance test (§13). |
| `03-RISKS-AND-SECURITY.md` | Known weaknesses and required fixes, plus **your mandatory review**. |
| `04-FRENCH-STRINGS.md` | Glossary and every UI string in English + French, date formats, France/Québec notes. |
| `source/` | Reference code: package `com.tvart.slideshow`, deploy scripts. No build outputs, no pictures. |

Read all five documents fully before doing anything else.

## Order of work

1. **Review first (mandatory).** Read the spec and the code, then proof the plan for weaknesses, vulnerabilities and potential problems: security, data loss, crashes inside the screensaver/overlay, Fire OS version differences, date and leap-year logic, translation and layout problems, anything unclear or contradictory in these documents. Add your findings to the log in `03-RISKS-AND-SECURITY.md` §7. If something in the spec is wrong or impossible on the user's device, **say so and propose a fix**; don't silently work around it.
2. **Talk to the user** (in French; use the prepared text in `01-USER-SETUP.md`):
   - Tell them **what you need from them before starting** (§A): Fire TV model and Fire OS version (photo of Settings → My Fire TV → About), a computer on the same Wi-Fi, their wallpapers, their date/clock preferences, one slideshow or seasons, whether they use YouTube.
   - **Check the device** (§A.1). Stop and explain if it can't run the app (Vega OS).
   - Walk them through **developer mode** (§B), one step at a time.
   - Tell them **where to put their wallpapers** (§D.2). If they have none, or don't want to provide any, you may use the four sample wallpapers in §D.4, following its rules exactly.
3. **Set up the build machine:** JDK 17, Android SDK (platform-tools, build-tools, platform 35), from official sources only. Build `source/` unchanged once to confirm the toolchain works.
4. **Build in phases** as listed in `02-APP-SPEC.md` §3. Phases 0 and 0b prove the overlay and the screensaver **on the user's device** before anything else. Report the results to the user and stop if a phase's checks fail.
5. **Apply every change** in `02-APP-SPEC.md` §12 and every **[REQUIRED]** fix in `03`.
6. **Translate:** move every user-visible string into `res/values/strings.xml` and `res/values-fr/strings.xml` using `04-FRENCH-STRINGS.md`. If you need a string that isn't in 04, follow 04's glossary and principles, and list every new French string for the user's review.
7. **Test:** unit tests (`./gradlew test`), then the acceptance test in `02` §13 **with the user**, then your second review pass (03 §7).
8. **Hand over:** the final French message (`01` §E), the developer-mode safety choice (`01` §B.3), and a short list of anything not done or not verified.

## Rules

- **Language:** French for the user and for all app text, using `vous`. Neutral French that reads well in both France and Québec (see 04). Plain words, short sentences, one step at a time. The user is not assumed to be technical; never ask them to type commands.
- **Language ≠ date format.** Language, date format and 12/24-hour clock are three separate settings. Never derive one from another.
- **Scope:** build exactly what the spec says. **No** favorites, holiday countdowns, clock tints, night/quiet-hours slideshows, special days or other extras, even though some exist in `source/`; remove them.
- **QR code:** generated on the TV at runtime from its current address. Never ship or commit a pre-made QR image or a hard-coded IP.
- **Privacy:** don't put the user's name, email, IP, Wi-Fi name or pictures into code, comments, logs or commits. The sample wallpapers are for the user's local testing only; never bundle or redistribute them.
- **Their TV settings:** always save the original screensaver and accessibility values before changing them (`screensaver_backup.txt` + in-app copy), and make sure "Off" restores them exactly.
- **Honesty:** if something didn't work or wasn't tested on the device, say so plainly. Don't claim a phase passed without running its checks.
