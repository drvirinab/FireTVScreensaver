# 04 — French strings: glossary, app name and full string table

**Who this is for:** you, the agent building the app. The prose is in English; the French in the tables is final wording for the user (a French speaker from **France or Québec, not known which**). Copy the French into `res/values-fr/strings.xml` (and the English into `res/values/strings.xml`), and serve the web-page strings from the same resources (02 §7.3).

Section numbers below are referenced from `01-USER-SETUP.md` and `02-APP-SPEC.md`.

---

## 1. Translation principles

| Topic | Rule |
|---|---|
| Register | **vous** everywhere. Imperatives in the *vous* form ("Sélectionnez", "Appuyez"). No *tu*, no slang. |
| Tone | Plain, short, concrete. Say what happens ("Vos images restent dans votre bibliothèque"), not how it works. No technical words on screen except the unavoidable **code PIN**, **Wi-Fi**, **USB**. |
| Case | **Sentence case** (only the first word and proper nouns capitalized): "Lancer le diaporama", not "Lancer Le Diaporama". Season names are proper nouns of the app: **Hiver, Printemps, Été, Automne** keep their capital when used as names (« Hiver »), but lower case in running text about seasons in general ("chaque saison : hiver, printemps…"). |
| Accented capitals | Always keep accents on capitals: **É**té, **À** propos, **DÉC.**, **FÉVR.**, **AOÛT**. Use `uppercase(locale)` (02 §6). |
| Quotes | French quotes **« »** with a **non-breaking space (U+00A0) inside**: `« Hiver »`. Same rule in France and Québec. |
| Space before punctuation | France puts a non-breaking space before `: ; ! ?`; Québec (OQLF) puts one only before `:`. **Handled by avoidance:** UI strings contain **no `;`, `!` or `?`** (every question is rephrased as a statement or a title). The **colon** is kept and always preceded by **U+00A0** (both countries agree). |
| Writing the spaces | The tables below show ordinary spaces for readability. When you paste into `strings.xml`, replace the space **before `:`**, **after `«`**, **before `»`**, **before `%`** (French writes `50 %`), and **between a number and its unit** (`80 s`, `1,5 Mo`) with ` `. A one-line script over `values-fr/strings.xml` does it; check the result once on the TV. |
| Apostrophe | Typographic **’** (U+2019) in `values-fr` (no `\'` escaping needed). The tables use ’. |
| Ellipsis | Single character **…** (U+2026). An item ending in "…" opens a choice or a dialog, as in English. |
| Numbers | Decimal **comma**: `1,5 s`, `2,4 Mo`. Format with `String.format(locale, …)` using the app locale, never `Locale.US`. Thousands: none needed (counts stay small); if ever needed, a non-breaking space (`1 250`). |
| Units | `s` (seconds), `min`, `Ko`, `Mo`, `Go` (not KB/MB/GB), `%` with a space before. These are correct in both France and Québec. |
| Times | Digital clock keeps the colon: `17:05` (24 h) or `5:05` (12 h, no AM/PM). Never write `17h05` or `5 PM` on screen. |
| Loanwords | No English when a plain French word exists: *ajouter / envoyer* (not *uploader*), *diaporama* (not *slideshow*), *aléatoire* (not *shuffle*), *fondu* (not *crossfade*), *doublon* (not *duplicate*), *clé USB*. Kept as-is because they are the everyday French words: **Wi-Fi, USB, code PIN, zoom, YouTube, OK, Menu, Web**. |
| On/off | Values **Activé / Désactivé** (the Fire TV and Android French convention; the invariable masculine form refers to "le réglage", so "Horloge : Activé" is the standard UI form). |
| Length | French runs ~20–30 % longer. Where a French string is more than ~30 % longer than English **and** sits in a tight place (row title, button, badge), the notes give a **short alternative**. Use it only if the long one is cut off on the TV. |
| Plurals | Use `<plurals>`. French `one` covers **0 and 1** ("0 image", "1 image"), `other` covers 2+. Android's `many` category for French (large round numbers like 1 000 000) never occurs here; if your tooling demands it, copy `other`. When a sentence would need several plural agreements, the tables use a **"Label : number"** pattern instead (e.g. "Ajoutées : 3"), which avoids agreement problems. |
| Gender | "image" is **feminine**: *ajoutée(s), sélectionnée(s), supprimée(s), floue*. "diaporama" and "économiseur" are masculine; "saison" is feminine. |
| Placeholders | Always positional (`%1$s`, `%2$d`) so French can reorder. Keep them exactly as written. |
| Locale | Use `Locale.FRENCH` (`fr`), never `fr-FR` or `fr-CA` (02 §9). |

---

## 2. Glossary

Use **exactly** these choices everywhere (TV, web page, notifications, setup messages).

| English term | French choice | Avoid (and why) |
|---|---|---|
| App name | **TV Art** (unchanged, see §3) | A new French name: the banner and icon art already say "TV Art". |
| slideshow | **diaporama** (m.) | *slideshow*, *présentation* (sounds like PowerPoint), *carrousel*. |
| My slideshow (single-mode name) | **Mon diaporama** | *Diaporama par défaut* (technical). |
| main slideshow (between-seasons fallback) | **diaporama principal** | *Default* / *défaut* on screen. |
| picture / wallpaper / art | **image(s)** (f.); in setup talk also **fond(s) d’écran** | *photo* (many are not photos), *art / œuvre* (odd for wallpapers), *papier peint* (that's for walls). |
| screensaver | **économiseur d’écran** | *écran de veille* is also common in France (Windows term) and understood in Québec, but Fire TV's French menus use "économiseur d’écran" (likely; confirm), so use that one only. |
| Art screensaver (feature switch) | **Économiseur d’écran** (row title; subtitle explains it replaces Amazon's) | *Économiseur d’écran artistique* (long, odd). |
| Art over YouTube (overlay) | **Diaporama sur YouTube** | *superposition*, *overlay*, *incrustation* (technical); *par-dessus YouTube* is fine in sentences but long as a title. |
| gallery | **Galerie** | *photothèque*. |
| library | **bibliothèque** | *médiathèque*, *collection*. |
| upload (verb) | **ajouter** (des images); **envoyer** for the network action | *télécharger* (means **download** in Québec and, ambiguously, either in France); *téléverser* (Québec only, unknown in France); *uploader*. |
| upload / import batch (noun) | **ajout** ("Ajout du 29 oct.") | *lot*, *batch*, *import* (less clear); *téléversement*. |
| import from USB | **ajouter depuis une clé USB** | *importer* (fine but more technical; not needed). |
| download | **télécharger** (only for the backup file on the web page) | *downloader*. Unambiguous here because "upload" never uses it. |
| backup / restore | **sauvegarde** / **restaurer** | *copie de sécurité* (QC variant, longer), *backup*. |
| restore (from Recently Deleted) | **restaurer** | *récupérer* (sounds like recovering a lost file). |
| PIN | **code PIN** | *NIP* (Québec banking term, unknown in France), *mot de passe*. |
| QR code | **code QR** (technical docs); on screen **ce code carré** | *QR code* (France usage, an anglicism), *code-barres*. |
| scan (a QR code) | **pointer l’appareil photo vers** | *scanner* (anglicism, though common), *balayer* (OQLF term, unknown in France), *flasher* (France slang). |
| Wi-Fi | **Wi-Fi** | *wifi*/*WiFi* (mixed spellings), *réseau sans fil* (long). |
| network | **réseau** | — |
| phone | **téléphone** | *portable* (France = phone, but Québec = laptop), *cellulaire / cell* (Québec only), *mobile*, *smartphone*. |
| computer | **ordinateur** | *PC* (excludes Mac), *ordi* (informal), *portable* (see above). |
| TV / the device | **téléviseur** | *télé* (informal), *TV* alone in sentences (fine but less natural), *poste*. |
| email | **not used** in the app. If ever needed: **adresse électronique** | *courriel* (Québec; understood in France but marked), *e-mail / mail* (France; disliked in Québec). |
| settings | **paramètres** | *réglages* (Apple/Québec habit; fine but mixing two words confuses), *configuration*. |
| More (settings page) | **Autres paramètres** | *Plus* (ambiguous: "more" or "plus sign"). |
| Look & timing (page) | **Affichage** | *Apparence et minutage* (long, *minutage* is rare). |
| on / off | **Activé / Désactivé** | *On/Off*, *Oui/Non* (fine but not the Fire TV style), *Allumé/Éteint* (power). |
| turn on / turn off | **activer / désactiver** | *allumer / fermer* (in Québec *fermer* = switch off, in France it means close). |
| Winter / Spring / Summer / Fall | **Hiver / Printemps / Été / Automne** | — (identical in both). |
| season | **saison** (f.) | — |
| seasonal slideshows (mode) | **Diaporamas par saison** | *saisonniers* (fine, but "par saison" is plainer). |
| between seasons | **entre deux saisons** | *hors saison* (means off-season, wrong sense). |
| Now playing (in a list) | **En cours** | *En lecture* (sounds like video). |
| Play slideshow | **Lancer le diaporama** | *Jouer* (anglicism in France), *Lire* (sounds like reading). |
| manual override | **Votre choix** (reason) / **Choisir le diaporama** (action) | *forçage*, *priorité manuelle*. |
| Return to the schedule | **Revenir aux saisons** | *horaire* (Québec = schedule, France = timetable/hours), *programme*, *planning*. |
| until I turn it off | **jusqu’à nouvel ordre** | *jusqu’à ce que je l’arrête* (long). |
| remote | **télécommande** | *manette* (game controller), *zappette* (slang). |
| Menu button (≡) | **bouton Menu (≡)** | — |
| Back button | **Retour** | *Précédent*. |
| Home button | **Accueil** | *Maison*. |
| OK / center button | **OK** / **bouton rond du centre** | *bouton central* is fine too; never *Entrée*. |
| Recently Deleted | **Suppressions récentes** (as in Apple Photos in French). Capitalized when it names the view ("va dans Suppressions récentes"), lower case after an article ("Vider les suppressions récentes") | *Corbeille* (implies files), *Récemment supprimées* (heavier). |
| delete (to Recently Deleted) | **supprimer** | *effacer* (reserved for permanent erase). |
| delete permanently | **supprimer définitivement** / message verb **effacer** | *détruire*. |
| remove from a season | **retirer de** | *supprimer de* (sounds like deleting the picture). |
| remove a season | **supprimer la saison** | *retirer* (here the season itself goes away). |
| select / clear selection | **sélectionner / tout désélectionner** | *effacer la sélection* (*effacer* = erase). |
| crop / gets cropped | **bords coupés** | *rogné(e)* (fine but less common), *cropper*. |
| low resolution / blurry | **basse résolution** (filter, badge); **peut paraître floue** (per-picture warning) | *faible définition*, *pixelisée*. |
| 16:9 | **16:9** (written with a colon, no space: it is a ratio) | *16/9*. |
| zoom (slow zoom) | **zoom lent** | *grossissement*. |
| pan / drift | **glissement lent** | *panoramique*, *pan*, *dérive*. |
| crossfade / transition | **fondu** ("Durée du fondu") | *crossfade*, *fondu enchaîné* (correct but long). |
| shuffle | **aléatoire** | *shuffle*, *mélanger*. |
| duplicates | **doublons** | *duplicata*, *copies*. |
| dark wall | **écran noir** | *mur sombre*. |
| clock | **horloge** | *montre*. |
| date format | **format de date** | — |
| storage | **stockage** / **espace libre** | *mémoire* (ambiguous with RAM). |
| USB drive | **clé USB** | *clef USB* (old spelling), *dongle*. |
| web page (manager) | **page web** | *gestionnaire web*, *interface web*. |
| default (seasons) | **par défaut** ("les 4 saisons par défaut") | — (neutral; used only there). |
| icon / color | **icône / couleur** | — |

---

## 3. App name decision

**Recommendation: keep "TV Art".**

- The launcher banner (`drawable-xhdpi/banner.png`) and the icon art already show "TV Art", and 02 §12 says to keep that art. A French name would contradict the banner.
- "TV" is the same word in French ("la TV", "téléviseur"), and "Art" is a French word. It reads as a short brand name, not as an English phrase, in both France and Québec. Brand names in English word order are common in French-speaking markets (e.g. "Fire TV" itself).
- It is short, which matters on the Fire TV app row.
- Rejected alternatives: *Art TV* (would need new banner art), *Galerie TV* (generic), *Mon diaporama* (clashes with the slideshow name).

| key | English | French | notes |
|---|---|---|---|
| `app_name` | TV Art | TV Art | Launcher label, dream (screensaver) label, web page `<title>`. Same in both locales. |
| `overlay_service_label` | TV Art – slideshow over YouTube | TV Art – diaporama sur YouTube | Shown in Android accessibility settings and in the Fire OS warning when the overlay is enabled. Was "TV Art overlay". |
| `overlay_service_desc` | Shows your slideshow on top of YouTube while music keeps playing. Press Menu (≡) twice to show or hide it. | Affiche votre diaporama par-dessus YouTube pendant que la musique continue. Appuyez deux fois sur Menu (≡) pour l’afficher ou le masquer. | Long text area; no limit. If the user picks another toggle button, this static text still says Menu: acceptable (it's the default). |
| `overlay_window_title` | TV Art slideshow | Diaporama TV Art | Internal window title; rarely visible. |

---

## 4. Full string table

Conventions for this section:
- `%1$s` etc. must stay as written. `[plural]` marks a `<plurals>` resource; its forms are given as **one / other**.
- "Max" is a suggested maximum visible length for the French on a 1080p TV row; if longer, use the short alternative.
- Strings for removed features (favorites, holiday countdown, clock tint, night slideshow, special days, the old season names) are **deliberately absent**. Do not translate leftovers; delete them.
- `icon + name` combinations ("❄ Hiver") are built in code, not translated.

### 4.1 Main screen

| key | English | French | notes |
|---|---|---|---|
| `main_title` | TV Art | TV Art | Screen title = app name. |
| `banner_storage_permission` | TV Art needs to read the pictures on this TV. Select to allow. | TV Art doit pouvoir lire les images de ce téléviseur. Sélectionnez pour autoriser. | Red banner. |
| `banner_finish_setup` [plural] | Finish setup (%d step) / (%d steps) | Terminer l’installation (%d étape) / (%d étapes) | Yellow banner. |
| `banner_screensaver_reset` | A Fire TV update turned off your screensaver. Select to turn it back on. | Une mise à jour de Fire TV a désactivé votre économiseur d’écran. Sélectionnez pour le réactiver. | System reset detected (02 §10). Replaces the spec's draft "…Le réactiver ?" to avoid `?`. |
| `banner_overlay_reset` | A Fire TV update turned off the slideshow over YouTube. Select to turn it back on. | Une mise à jour de Fire TV a désactivé le diaporama sur YouTube. Sélectionnez pour le réactiver. | |
| `banner_permission_missing` | A setup step must be redone from the computer. Ask the person who installed the app for help. | Une étape d’installation doit être refaite depuis l’ordinateur. Demandez de l’aide à la personne qui a installé l’application. | Missing `WRITE_SECURE_SETTINGS` (02 §10). No reference to INSTALL.md. |
| `banner_permission_helper` | For the helper: %1$s | Pour la personne qui vous aide : %1$s | Small text under the banner; `%1$s` = the adb command (not translated). |
| `banner_season_empty_now` | %1$s is playing but has no pictures. The main slideshow is shown instead. Select to add pictures. | %1$s est en cours mais n’a aucune image. Le diaporama principal est affiché à la place. Sélectionnez pour ajouter des images. | `%1$s` = season name. |
| `banner_season_empty_next` | %1$s starts on %2$s and has no pictures yet. Select to add some. | %1$s commence le %2$s et n’a pas encore d’images. Sélectionnez pour en ajouter. | `%2$s` = text date ("1er déc."). |
| `main_play` | Play slideshow | Lancer le diaporama | Row title. |
| `main_play_hint` | %1$s. Press Back to stop. | %1$s. Appuyez sur Retour pour arrêter. | `%1$s` = a reason string (below). |
| `reason_single` | All your pictures | Toutes vos images | Single-slideshow mode ("my slideshow"). |
| `reason_season` | Season in progress · until %1$s | Saison en cours · jusqu’au %1$s | `%1$s` = last day, text date ("jusqu’au 28 févr."). |
| `reason_between` | Between seasons · main slideshow | Entre deux saisons · diaporama principal | |
| `reason_override_until` | You chose it · until %1$s | Votre choix · jusqu’au %1$s | Override with an end date. |
| `reason_override_open` | You chose it · until you turn it off | Votre choix · jusqu’à nouvel ordre | Override with no end. |
| `reason_empty_fallback` | No pictures in %1$s · main slideshow | Aucune image dans %1$s · diaporama principal | Active season empty (02 §5 fallback). |
| `main_return_to_seasons` | Return to the schedule | Revenir aux saisons | Only during an override. |
| `main_return_to_seasons_hint` | Play what is planned for today | Reprendre le diaporama prévu pour aujourd’hui | |
| `main_choose_what_plays` | Choose what plays | Choisir le diaporama | Seasonal mode only. |
| `main_gallery` | Gallery | Galerie | Value: `pictures_count`. |
| `main_seasons` | Seasons | Saisons | Seasonal mode only. Value: `up_next_*`. |
| `up_next_in_season` [plural] | %1$s in progress → %2$s starts on %3$s (in %4$d day) / (in %4$d days) | %1$s en cours → %2$s commence le %3$s (dans %4$d jour) / (dans %4$d jours) | Spec example: "Automne en cours → Hiver commence le 1er déc. (dans 27 jours)". Long: if cut, short alt `%2$s le %3$s (dans %4$d jours)`. |
| `up_next_between` [plural] | Between seasons → %1$s starts on %2$s (in %3$d day/days) | Entre deux saisons → %1$s commence le %2$s (dans %3$d jour) / (dans %3$d jours) | |
| `up_next_tomorrow` | %1$s starts tomorrow | %1$s commence demain | Optional nicer form when the count is 1. |
| `main_web` | Add pictures from a phone or computer | Ajouter des images depuis un téléphone ou un ordinateur | 55 chars vs 37. Short alt: "Ajouter des images (téléphone, ordinateur)". |
| `main_web_hint` | Turns off by itself after 30 minutes of inactivity | Se désactive tout seul après 30 minutes sans activité | |
| `main_web_address` | On your phone, open: %1$s | Sur le téléphone, ouvrez l’adresse : %1$s | `%1$s` = "192.168.1.23:8080" (no translation). |
| `main_web_pin` | PIN %1$s | Code PIN %1$s | Row value. |
| `main_web_qr_caption` | Or point your phone's camera at this square code, then enter the PIN. | Ou pointez l’appareil photo de votre téléphone vers ce code carré, puis entrez le code PIN. | Two lines next to the QR (spec 7.4 draft used "scannez"; "pointez…vers" avoids the anglicism and the Québec *balayer*). |
| `main_web_no_network` | This TV is not connected to the network. Check the Wi-Fi, then try again. | Ce téléviseur n’est pas connecté au réseau. Vérifiez le Wi-Fi, puis réessayez. | Shown instead of the QR code. |
| `main_web_no_network_short` | No network | Pas de réseau | Row value / notification. |
| `main_change_pin` | Change PIN | Changer le code PIN | |
| `main_look` | Look & timing › | Affichage › | |
| `main_screensaver` | Screensaver & YouTube › | Économiseur d’écran et YouTube › | 31 vs 23; fits a full-width row. Short alt if cut: "Économiseur et YouTube ›". |
| `main_more` | More › | Autres paramètres › | |
| `main_empty_library_hint` | No pictures yet. Choose Add pictures from a phone or computer. | Aucune image pour l’instant. Choisissez « Ajouter des images depuis un téléphone ou un ordinateur ». | |

### 4.2 Gallery

| key | English | French | notes |
|---|---|---|---|
| `gallery_title` | Gallery | Galerie | |
| `gallery_selected_count` [plural] | %d selected | %d sélectionnée / %d sélectionnées | Top bar. "0 sélectionnée" is correct French. |
| `gallery_btn_show` | Show | Afficher | Opens the view menu. |
| `gallery_btn_filter` | Filter | Filtrer | |
| `gallery_btn_sort` | Sort | Trier | |
| `gallery_btn_actions` | Actions | Actions | |
| `gallery_hint` | OK: select · ▶❚❚: full screen · ≡: actions | OK : sélectionner · ▶❚❚ : plein écran · ≡ : actions | Bottom hint line. |
| `gallery_view_all` | All pictures | Toutes les images | |
| `gallery_view_unassigned` | In no season | Sans saison | Seasonal mode only. |
| `gallery_view_recent` [plural] | Recently added (last %d day/days) | Ajoutées récemment (%d dernier jour) / (%d derniers jours) | Used with 30. |
| `gallery_view_trash` | Recently deleted | Suppressions récentes | |
| `gallery_view_trash_count` | Recently deleted (%1$d) | Suppressions récentes (%1$d) | Count only, no plural needed. |
| `gallery_view_season_fallback` | Slideshow | Diaporama | If a season id is unknown. |
| `gallery_sort_newest` | Date added, newest first | Date d’ajout, plus récentes d’abord | |
| `gallery_sort_oldest` | Date added, oldest first | Date d’ajout, plus anciennes d’abord | |
| `gallery_sort_name` | Name (A to Z) | Nom (de A à Z) | |
| `gallery_sort_random` | Random | Aléatoire | |
| `gallery_sorted_by` | sorted: %1$s | tri : %1$s | Summary chip; `%1$s` = a sort label in lower case. |
| `gallery_count_of` | %1$d of %2$d | %1$d sur %2$d | |
| `gallery_filter_clear_hint` | Filter › Clear | Filtrer › Effacer les filtres | |
| `gallery_empty_trash` | Recently deleted is empty. | Les suppressions récentes sont vides. | |
| `gallery_empty_library` | No pictures yet. On the main screen, choose Add pictures from a phone or computer. | Aucune image pour l’instant. Sur l’écran principal, choisissez « Ajouter des images depuis un téléphone ou un ordinateur ». | |
| `gallery_empty_view` | No pictures in %1$s yet. | Aucune image dans %1$s pour l’instant. | |
| `gallery_empty_filters` | No pictures match these filters. | Aucune image ne correspond à ces filtres. | |
| `gallery_info_added` | Added %1$s | Ajoutée le %1$s | `%1$s` = text date. |
| `gallery_info_in` | In %1$s | Dans : %1$s | `%1$s` = comma-separated season names. |
| `gallery_info_in_none` | In no season | Dans aucune saison | Seasonal mode only; hide in single mode. |
| `gallery_info_deleted` [plural] | Deleted %1$s, erased in %2$d day/days | Supprimée le %1$s, effacée dans %2$d jour / dans %2$d jours | |
| `gallery_warn_lowres` | May look blurry | Peut paraître floue | |
| `gallery_warn_cropped` | Edges get cropped | Bords coupés | |
| `gallery_warn_duplicate` | Duplicate | Doublon | |
| `gallery_this_picture` | This picture | Cette image | Actions menu title when nothing is selected. |
| `gallery_n_selected_title` [plural] | %d picture selected / %d pictures selected | %d image sélectionnée / %d images sélectionnées | Actions menu title. |
| `gallery_nothing_selected` | Nothing selected | Aucune sélection | |
| `gallery_select_all` | Select all (%1$d) | Tout sélectionner (%1$d) | Respects the filter. |
| `gallery_clear_selection` | Clear selection | Tout désélectionner | |
| `gallery_invert_selection` | Invert selection | Inverser la sélection | |
| `gallery_add_to_season` | Add to a season… | Ajouter à une saison… | Seasonal mode only. |
| `gallery_remove_from_season` | Remove from a season… | Retirer d’une saison… | Seasonal mode only. |
| `gallery_pick_add_title` | Add to which season | Ajouter à la saison… | Picker title (no `?`). |
| `gallery_pick_remove_title` | Remove from which season | Retirer de la saison… | |
| `gallery_delete` | Delete (goes to Recently deleted) | Supprimer (va dans Suppressions récentes) | 41 vs 33. Short alt: "Supprimer". |
| `gallery_delete_permanently` | Delete permanently… | Supprimer définitivement… | |
| `gallery_restore` | Restore | Restaurer | Recently Deleted view. |
| `gallery_empty_trash_action` | Empty Recently deleted (%1$d)… | Vider les suppressions récentes (%1$d)… | |
| `gallery_preview_hint` | Press Back to return to the gallery | Appuyez sur Retour pour revenir à la galerie | Full-screen preview pill (optional). |
| `toast_select_first` | Select pictures with OK first | Sélectionnez d’abord des images avec OK | |
| `toast_trash_already_empty` | Recently deleted is already empty | Les suppressions récentes sont déjà vides | |
| `toast_failed` | Failed: %1$s | Échec : %1$s | `%1$s` = safe error text (03). |
| `toast_restored` [plural] | %d picture restored / %d pictures restored | %d image restaurée / %d images restaurées | |
| `toast_deleted_permanently` [plural] | %d picture deleted permanently / %d pictures … | %d image supprimée définitivement / %d images supprimées définitivement | |
| `toast_trash_emptied` | Recently deleted emptied | Suppressions récentes vidées | |
| `toast_added_to` [plural] | Added %d picture to %2$s / Added %d pictures to %2$s | %1$d image ajoutée à %2$s / %1$d images ajoutées à %2$s | Use `%1$d` in both forms. |
| `toast_removed_from` [plural] | Removed %d picture from %2$s / … | %1$d image retirée de %2$s / %1$d images retirées de %2$s | |
| `toast_moved_to_trash` [plural] | Moved %1$d picture to Recently deleted (kept %2$d days) | %1$d image déplacée dans Suppressions récentes (conservée %2$d jours) / %1$d images déplacées dans Suppressions récentes (conservées %2$d jours) | `%2$d` = 30. |

#### Gallery filters

| key | English | French | notes |
|---|---|---|---|
| `filter_title` | Filter | Filtrer | |
| `filter_row_date` | Date added: %1$s | Date d’ajout : %1$s | |
| `filter_row_batch` | Upload: %1$s | Ajout : %1$s | |
| `filter_row_season` | In a season: %1$s | Saison : %1$s | Seasonal mode only. |
| `filter_row_quality` | Picture quality: %1$s | Qualité : %1$s | |
| `filter_row_dupes` | Duplicates: %1$s | Doublons : %1$s | Value `on_off_on` / `on_off_off`. |
| `filter_clear` | Clear filters | Effacer les filtres | |
| `filter_any_date` | any | toutes | Value for Date (les dates). |
| `filter_any_batch` | any | tous | Value for Ajout (les ajouts). |
| `filter_any_season` | any | toutes | |
| `filter_any_quality` | any | toutes | |
| `filter_date_title` | Date added | Date d’ajout | |
| `filter_date_any` | Any date | Toutes les dates | |
| `filter_date_today` | Added today | Ajoutées aujourd’hui | |
| `filter_date_week` | Added this week | Ajoutées cette semaine | |
| `filter_date_month` | Added this month | Ajoutées ce mois-ci | |
| `filter_date_on` | On a date… | À une date précise… | |
| `filter_date_before` | Before a date… | Avant une date… | |
| `filter_date_after` | After a date… | Après une date… | |
| `filter_date_between` | Between two dates… | Entre deux dates… | |
| `filter_label_today` | today | aujourd’hui | Summary value. |
| `filter_label_week` | this week | cette semaine | |
| `filter_label_month` | this month | ce mois-ci | |
| `filter_label_on` | on %1$s | le %1$s | |
| `filter_label_before` | before %1$s | avant le %1$s | |
| `filter_label_after` | after %1$s | après le %1$s | |
| `filter_label_between` | %1$s to %2$s | du %1$s au %2$s | |
| `pick_date_on` | Date | Date | Title prefix for the 3-step picker. |
| `pick_date_before` | Before | Avant le | |
| `pick_date_after` | After | Après le | |
| `pick_date_from` | From | Du | |
| `pick_date_to` | To | Au | |
| `pick_date_year` | %1$s: year | %1$s : année | |
| `pick_date_month` | %1$s: month | %1$s : mois | |
| `pick_date_day` | %1$s: day (%2$s %3$d) | %1$s : jour (%2$s %3$d) | `%2$s` = full month name, `%3$d` = year: "jour (octobre 2026)". |
| `filter_batch_title` | Upload | Ajout | |
| `filter_batch_any` | Any upload | Tous les ajouts | |
| `filter_season_title` | In a season | Saison | |
| `filter_season_any` | Any | Toutes | |
| `filter_season_in` | In a season… | Dans une saison… | |
| `filter_season_not_in` | Not in a season… | Pas dans une saison… | |
| `filter_season_none` | In no season | Dans aucune saison | |
| `filter_season_pick` | Season | Saison | |
| `filter_quality_title` | Picture quality | Qualité de l’image | |
| `filter_quality_any` | Any | Toutes | |
| `filter_quality_lowres` | Low resolution (below 1920×1080) | Basse résolution (moins de 1920×1080) | |
| `filter_quality_cropped` | Edges get cropped (not 16:9) | Bords coupés (pas en 16:9) | |
| `filter_quality_both` | Low resolution and cropped | Basse résolution et bords coupés | |
| `chip_added` | added %1$s | ajoutées %1$s | Summary chips joined with " · ". `%1$s` = a `filter_label_*`. |
| `chip_batch` | upload %1$s | ajout du %1$s | |
| `chip_in` | in %1$s | dans %1$s | |
| `chip_not_in` | not in %1$s | pas dans %1$s | |
| `chip_none` | in no season | sans saison | |
| `chip_lowres` | low resolution | basse résolution | |
| `chip_cropped` | edges cropped | bords coupés | |
| `chip_dupes` | duplicates | doublons | |

#### Batch (import) names and labels

| key | English | French | notes |
|---|---|---|---|
| `batch_source_web` | Phone or computer | Téléphone ou ordinateur | Was "Web". |
| `batch_source_usb` | USB drive | Clé USB | |
| `batch_source_folder` | Folder | Dossier | Files copied by the setup script / found by a rescan. |
| `batch_name_folder` | Found in folder %1$s | Trouvées dans le dossier, %1$s | `%1$s` = text date with year. |
| `batch_name_usb` | USB drive · %1$s | Clé USB · %1$s | `%1$s` = short text date. |
| `batch_name_web_default` | Upload %1$s | Ajout du %1$s | Web page default name; `%1$s` = numeric date + time per the date setting. |
| `batch_label` | %1$s — %2$s%3$s, %4$s | %1$s — %2$s%3$s, %4$s | date — source (name), `pictures_count`. Layout only. |
| `batch_label_name` | (%1$s) | (%1$s) | Optional custom name. |

### 4.3 Slideshows and seasons editor

| key | English | French | notes |
|---|---|---|---|
| `slideshow_my` | My slideshow | Mon diaporama | Name of the single (MAIN) slideshow, created in the app language at first launch. |
| `slideshow_main_role` | main slideshow | diaporama principal | Lower case, used inside sentences and gap rows. |
| `season_winter` | Winter | Hiver | Default names, created once in the app language. |
| `season_spring` | Spring | Printemps | |
| `season_summer` | Summer | Été | |
| `season_fall` | Fall | Automne | |
| `seasonal_mode` | Seasonal slideshows | Diaporamas par saison | Toggle in "Autres paramètres". |
| `seasonal_mode_hint` | Switches slideshow automatically by date (winter, spring, summer, fall). | Change de diaporama tout seul selon la date (hiver, printemps, été, automne). | One-line explanation. |
| `seasonal_mode_on_first` | The seasons Winter, Spring, Summer and Fall were created. Add pictures to them in the gallery. | Les saisons Hiver, Printemps, Été et Automne ont été créées. Ajoutez-leur des images dans la galerie. | Info dialog, first activation only. |
| `seasonal_mode_off_info` | Your seasons are kept. All pictures now play as one slideshow. | Vos saisons sont conservées. Toutes les images passent maintenant dans un seul diaporama. | Toast on turning off. |
| `seasons_title` | Seasons | Saisons | Screen title. |
| `seasons_header_now` | Now | Maintenant | |
| `seasons_header_list` | Your seasons | Vos saisons | |
| `seasons_now_playing` | ▶ Now playing | ▶ En cours | Marker on the current season. |
| `seasons_no_pictures` | ⚠ no pictures | ⚠ aucune image | |
| `seasons_row_value` | %1$s · %2$s | %1$s · %2$s | range · `pictures_count`. |
| `seasons_gap_row` | %1$s: main slideshow | %1$s : diaporama principal | `%1$s` = a range: "1er mars – 14 mars : diaporama principal". |
| `seasons_add` | + Add a season | + Ajouter une saison | |
| `seasons_max_reached` | You can have up to 12 seasons. Remove one to add another. | Vous pouvez avoir 12 saisons au maximum. Supprimez-en une pour en ajouter une autre. | |
| `seasons_none` | No seasons — everything plays as one slideshow | Aucune saison : toutes les images passent dans un seul diaporama | |
| `seasons_restore_defaults` | Restore the 4 default seasons | Rétablir les 4 saisons par défaut | |
| `seasons_restore_confirm_title` | Restore the default seasons | Rétablir les saisons par défaut | Dialog title (no `?`). |
| `seasons_restore_confirm_msg` | Winter, Spring, Summer and Fall will be added back if their names and dates are free. Your other seasons don't change. | Les saisons Hiver, Printemps, Été et Automne seront ajoutées de nouveau si leurs noms et leurs dates sont libres. Vos autres saisons ne changent pas. | |
| `seasons_restore_btn` | Restore | Rétablir | |
| `seasons_restored` [plural] | %d season restored / %d seasons restored | %d saison rétablie / %d saisons rétablies | |
| `seasons_restore_nothing` | Nothing to restore: the names or dates are already used. | Rien à rétablir : les noms ou les dates sont déjà utilisés. | |
| `timeline_today` | Today | Aujourd’hui | Year bar marker. |
| `timeline_plays` | Plays | Affiché | Row showing what plays each day. |
| `timeline_main` | Main | Principal | Gap segments on the bar (short). |

#### Per-season screen

| key | English | French | notes |
|---|---|---|---|
| `season_edit_title` | %1$s | %1$s | Title = icon + name. |
| `season_summary` | %1$s · %2$s | %1$s · %2$s | `pictures_count` · range. |
| `season_summary_empty` | ⚠ Add pictures in the gallery before its dates | ⚠ Ajoutez des images dans la galerie avant ses dates | |
| `season_name` | Name | Nom | |
| `season_rename_title` | Rename | Renommer | Keyboard dialog title. |
| `season_name_invalid_length` | The name must have 1 to 30 characters. | Le nom doit contenir de 1 à 30 caractères. | |
| `season_name_taken` | A season already has this name. | Une saison porte déjà ce nom. | Comparison ignores case and accents. |
| `season_icon` | Icon | Icône | |
| `season_color` | Color | Couleur | |
| `season_color_custom` | Custom | Personnalisée | |
| `color_red` / `color_pink` / `color_purple` / `color_blue` / `color_teal` / `color_green` / `color_amber` / `color_orange` / `color_brown` / `color_gray` | Red / Pink / Purple / Blue / Teal / Green / Amber / Orange / Brown / Gray | Rouge / Rose / Violet / Bleu / Bleu-vert / Vert / Ambre / Orange / Brun / Gris | *Brun* not *Marron* (see §6). *Bleu-vert* not *Sarcelle* (rare word). |
| `season_starts` | Starts on | Commence le | Row; value = text date. |
| `season_last_day` | Last day | Dernier jour | |
| `season_range_preview` | From %1$s to %2$s | Du %1$s au %2$s | Live preview: "Du 1er déc. au 28 févr.". |
| `season_range_single` | Only on %1$s | Seulement le %1$s | Start = last day. |
| `range_dash` | %1$s – %2$s | %1$s – %2$s | Compact range for lists (en dash with spaces). |
| `pick_day_start` | %1$s: first day | %1$s : premier jour | Picker title prefix. |
| `pick_day_last` | %1$s: last day | %1$s : dernier jour | |
| `pick_month` | %1$s — month | %1$s — mois | |
| `pick_day` | %1$s — day | %1$s — jour | Order of the two pickers follows the date-format setting. |
| `season_feb29_title` | February 29 | 29 février | |
| `season_feb29_msg` | February 29 only happens every 4 years. Choose February 28 or March 1 instead. | Le 29 février n’existe qu’une année sur quatre. Choisissez plutôt le 28 février ou le 1er mars. | |
| `season_feb29_keep` | Keep February 29 | Garder le 29 février | |
| `season_feb29_use28` | Use February 28 | Prendre le 28 février | |
| `season_feb29_use1` | Use March 1 | Prendre le 1er mars | |
| `season_overlap_title` | Dates already used | Dates déjà prises | |
| `season_overlap_msg` | These dates overlap %1$s (%2$s). | Ces dates empiètent sur la saison « %1$s » (%2$s). | `%1$s` = other season, `%2$s` = its range. |
| `season_overlap_suggest` | Nearest free dates: from %1$s to %2$s. | Dates libres les plus proches : du %1$s au %2$s. | Second paragraph. |
| `season_overlap_use_suggestion` | Use these dates | Prendre ces dates | |
| `season_overlap_change` | Change the dates | Changer les dates | |
| `season_no_free_dates` | No free dates: other seasons already cover the whole year. | Aucune date libre : les autres saisons couvrent déjà toute l’année. | |
| `season_open_gallery` | Open in gallery | Ouvrir dans la galerie | |
| `season_pictures` | Pictures | Images | Row; value = `pictures_count`. |
| `season_own_settings` | Settings just for %1$s | Paramètres propres à %1$s | Header. |
| `season_same_as_others` | Same as others | Comme les autres | |
| `season_same_as_others_value` | Same as others (%1$s) | Comme les autres (%1$s) | |
| `season_play_now` | Play now… | Lancer maintenant… | |
| `season_remove` | Remove season | Supprimer la saison | |
| `season_remove_hint` | Your pictures stay in the library | Vos images restent dans la bibliothèque | |
| `season_remove_title` | Remove %1$s | Supprimer « %1$s » | Dialog title (spec draft "Remove 'Hiver'?" rephrased without `?`). |
| `season_remove_msg` | Its pictures stay in your library. | Ses images restent dans votre bibliothèque. | |
| `season_remove_btn` | Remove | Supprimer | |
| `season_removed` | %1$s removed | « %1$s » supprimée | Toast (saison = f.). |
| `season_add_name_title` | New season name | Nom de la nouvelle saison | Step 1 of "+ Ajouter une saison". |
| `season_added` | %1$s added | « %1$s » ajoutée | Toast. |

#### Manual override ("Play X now")

| key | English | French | notes |
|---|---|---|---|
| `override_title` | Play %1$s… | Lancer %1$s… | Picker title. |
| `override_until_next` | Until the next season change | Jusqu’au prochain changement de saison | 38 vs 28. Short alt: "Jusqu’à la prochaine saison". |
| `override_for_days` [plural] | For %d day / For %d days | Pendant %d jour / Pendant %d jours | Offered with 1 and 7. |
| `override_until_off` | Until I turn it off | Jusqu’à nouvel ordre | |
| `override_choose_title` | Choose what plays | Choisir le diaporama | |

### 4.4 Look & timing ("Affichage")

| key | English | French | notes |
|---|---|---|---|
| `page_look` | Look & timing | Affichage | Page title. |
| `look_slide_time` | Seconds per picture | Durée par image | Values: `dur_*`. |
| `look_transition` | Crossfade | Durée du fondu | |
| `look_transition_none` | None | Aucun | (fondu = m.) |
| `look_zoom` | Slow zoom | Zoom lent | |
| `look_zoom_amount` | Zoom amount | Intensité du zoom | Values `%1$d %` ("5 %"). |
| `look_drift` | Slow drift | Glissement lent | |
| `look_drift_on` | Drift on | Images qui glissent | |
| `drift_none` | None of the pictures | Aucune | |
| `drift_some` | Some of the pictures | Quelques-unes | |
| `drift_half` | Half the pictures | La moitié | |
| `drift_most` | Most of the pictures | La plupart | |
| `drift_all` | All the pictures | Toutes | |
| `drift_percent` | %1$d%% | %1$d %% | Other values. French has a space before %. |
| `look_motion_length` | Motion lasts | Durée du mouvement | |
| `look_order` | Order | Ordre | |
| `order_shuffle` | Shuffle | Aléatoire | |
| `order_by_name` | By file name | Par nom de fichier | |
| `look_clock` | Clock | Horloge | Toggle. |
| `look_clock_style` | Clock over bright pictures | Horloge sur image claire | |
| `clock_style_amazon` | Like Amazon's | Comme celle d’Amazon | |
| `clock_style_glow` | Soft glow behind it | Halo doux derrière | |
| `clock_style_shadow` | Stronger shadow | Ombre plus marquée | |
| `look_header_language` | Language and dates | Langue et dates | Header in this page. |
| `look_language` | Language | Langue | |
| `language_fr` | Français | Français | Always shown in its own language. |
| `language_en` | English | English | Always shown in its own language. |
| `language_system` | Same as TV | Comme le téléviseur | If the TV language is neither French nor English, use English (or French: your choice, document it). |
| `look_date_format` | Date format | Format de date | |
| `date_format_dm` | Day/Month (31/12) | Jour/mois (31/12) | |
| `date_format_md` | Month/Day (12/31) | Mois/jour (12/31) | |
| `date_format_ymd` | Year-Month-Day (2026-12-31) | Année-mois-jour (2026-12-31) | |
| `look_clock_hours` | Clock | Format de l’heure | Separate from `look_clock`. |
| `clock_24h` | 24-hour (17:05) | 24 heures (17:05) | |
| `clock_12h` | 12-hour (5:05) | 12 heures (5:05) | |
| `dur_seconds` | %1$d sec | %1$d s | Space = U+00A0. |
| `dur_minutes` | %1$d min | %1$d min | |
| `dur_min_sec` | %1$d min %2$d sec | %1$d min %2$d s | |
| `dur_decimal_seconds` | %1$.1f sec | %1$.1f s | Format with the French locale → "1,5 s". |

### 4.5 Screensaver & YouTube

| key | English | French | notes |
|---|---|---|---|
| `page_screensaver` | Screensaver & YouTube | Économiseur d’écran et YouTube | Page title. |
| `ss_screensaver` | Art screensaver | Économiseur d’écran | Row title. |
| `ss_screensaver_hint` | Starts after your TV's usual idle time. Off brings back Amazon's. | Démarre après le délai habituel d’inactivité du téléviseur. Désactivé, celui d’Amazon revient. | |
| `ss_overlay` | Art over YouTube | Diaporama sur YouTube | |
| `ss_overlay_hint` | Press %1$s twice in YouTube to show or hide your pictures. | Appuyez deux fois sur %1$s dans YouTube pour afficher ou masquer vos images. | `%1$s` = button name. |
| `ss_starting` | Starting… | Démarrage… | Value while the service starts. |
| `ss_remote_button` | Remote button | Bouton de la télécommande | |
| `ss_remote_button_value` | %1$s (press twice) | %1$s (deux appuis) | |
| `ss_remote_button_hint` | Back or Home also hides your pictures. | Retour ou Accueil masquent aussi vos images. | |
| `ss_button_menu_recommended` | Menu ≡ (recommended) | Menu ≡ (recommandé) | |
| `ss_button_other` | Another button… | Un autre bouton… | |
| `ss_button_dialog_title` | Remote button | Bouton de la télécommande | |
| `ss_button_dialog_msg` | Press the remote button you want to use (pressed twice) to show or hide your pictures.\n\nCurrent: %1$s\n\n(Back cancels.) | Appuyez sur le bouton de la télécommande à utiliser (deux appuis) pour afficher ou masquer vos images.\n\nActuel : %1$s\n\n(Retour pour annuler.) | |
| `ss_button_reserved` | YouTube needs that button for playback. Try another. | YouTube a besoin de ce bouton pour la lecture. Choisissez-en un autre. | Toast. |
| `ss_how_to` | To use it: play music in YouTube, then press %1$s twice for your pictures, or three times for a dark screen with a faint clock. | Mode d’emploi : lancez de la musique dans YouTube, puis appuyez deux fois sur %1$s pour voir vos images, ou trois fois pour un écran noir avec une horloge discrète. | Note under the rows. |
| `ss_status` | Status | État | |
| `ss_status_ok` | Everything works | Tout fonctionne | |
| `ss_status_overlay_off` | Slideshow over YouTube is off | Le diaporama sur YouTube est désactivé | |
| `ss_status_setup_needed` | A setup step is missing | Une étape d’installation manque | Pair with `banner_permission_missing`. |
| `overlay_hint` | Press Back to return to YouTube · press %1$s twice to bring your pictures back | Retour pour revenir à YouTube · %1$s deux fois pour revoir vos images | Pill on the overlay; keep it short. |
| `key_menu` | Menu ≡ | Menu ≡ | |
| `key_back` | Back | Retour | |
| `key_home` | Home | Accueil | |
| `key_ok` | OK | OK | |
| `key_rewind` | Rewind | Retour rapide | |
| `key_fast_forward` | Fast-forward | Avance rapide | |
| `key_play_pause` | Play/Pause | Lecture/Pause | |
| `key_next` | Next | Suivant | |
| `key_previous` | Previous | Précédent | |
| `key_other` | Button %1$s | Bouton %1$s | Unknown key code; `%1$s` = raw name. |

### 4.6 More, storage, USB, setup

| key | English | French | notes |
|---|---|---|---|
| `page_more` | More | Autres paramètres | Page title. |
| `more_header_slideshows` | Slideshows | Diaporamas | |
| `more_between_seasons` | Between seasons, show | Entre deux saisons, afficher | Seasonal mode only. |
| `between_unassigned` | Pictures not in any season | Les images sans saison | Default. |
| `between_all` | All pictures | Toutes les images | |
| `more_header_library` | Library | Bibliothèque | |
| `more_usb` | Add pictures from a USB drive | Ajouter des images depuis une clé USB | Only when a drive with `TVArt` is present. |
| `more_usb_hint` | Copies the TVArt folder from the drive | Copie le dossier TVArt de la clé | |
| `usb_pick_folder` | Choose the folder | Choisir le dossier | Was "Which folder?". |
| `usb_pick_season` | Add to a season | Ajouter à une saison | Seasonal mode only. |
| `usb_no_season` | Don't add to a season | Aucune saison | |
| `usb_skip_duplicates` | Skip duplicates | Ignorer les doublons | Toggle, default on. |
| `usb_progress` | Adding pictures from the USB drive… | Ajout des images depuis la clé USB… | |
| `usb_result_title` | Add pictures from a USB drive | Ajouter des images depuis une clé USB | |
| `usb_result_msg` | Added: %1$d\nAlready in your library: %2$d\nFailed: %3$d | Ajoutées : %1$d\nDéjà dans votre bibliothèque : %2$d\nÉchecs : %3$d | "Label : number" avoids plurals. |
| `more_check_new` | Check for new pictures | Rechercher de nouvelles images | |
| `more_check_new_hint` | Finds pictures copied onto the TV | Trouve les images copiées sur le téléviseur | |
| `check_new_none` | No new pictures found. | Aucune nouvelle image trouvée. | |
| `check_new_found` [plural] | %d new picture found. / %d new pictures found. | %d nouvelle image trouvée. / %d nouvelles images trouvées. | |
| `more_header_storage` | Storage | Stockage | |
| `storage_your_pictures` | Your pictures | Vos images | Value: `%1$s · %2$s` (count · size). |
| `storage_free` | Free space on the TV | Espace libre sur le téléviseur | 30 vs 20. Short alt: "Espace libre". |
| `storage_trash` | Recently deleted | Suppressions récentes | Value: count · size. |
| `storage_trash_hint` | Kept 30 days, then erased | Conservées 30 jours, puis effacées | |
| `storage_empty_trash` | Empty Recently deleted now | Vider les suppressions récentes | |
| `storage_empty_trash_title` | Empty Recently deleted | Vider les suppressions récentes | Dialog title (no `?`). |
| `storage_empty_trash_msg` [plural] | %d picture will be erased permanently. This can't be undone. / %d pictures will be … | %d image sera effacée définitivement. Cette action est irréversible. / %d images seront effacées définitivement. Cette action est irréversible. | |
| `storage_empty_trash_btn` | Erase permanently | Effacer définitivement | |
| `more_backup` | Backup | Sauvegarde | |
| `more_backup_hint` | Download or restore a backup from the web page (phone or computer). | Téléchargez ou restaurez une sauvegarde depuis la page web (téléphone ou ordinateur). | Info row. |
| `size_kb` | %1$.0f KB | %1$.0f Ko | French locale formatting. |
| `size_mb` | %1$.1f MB | %1$.1f Mo | → "2,4 Mo". |
| `size_gb` | %1$.2f GB | %1$.2f Go | → "1,25 Go". |
| `pictures_count` [plural] | %d picture / %d pictures | %d image / %d images | Used everywhere. |
| `page_setup` | Finish setup | Terminer l’installation | Page title. |
| `setup_all_done` | Everything is set up. | Tout est prêt. | |
| `setup_step_permission` | A setup step must be redone from the computer | Une étape d’installation doit être refaite depuis l’ordinateur | Step title; subtitle = `setup_step_permission_hint`. |
| `setup_step_permission_hint` | Ask the person who installed the app for help. Select to see the command. | Demandez de l’aide à la personne qui a installé l’application. Sélectionnez pour voir la commande. | |
| `setup_command_title` | Setup step | Étape d’installation | |
| `setup_command_msg` | On a computer connected to this TV, run:\n\n%1$s\n\nThen reopen TV Art. | Sur un ordinateur relié à ce téléviseur, lancez :\n\n%1$s\n\nPuis rouvrez TV Art. | For the helper. |
| `setup_step_add_pictures` | Add your pictures | Ajoutez vos images | |
| `setup_step_add_pictures_hint` | Select to turn on Add pictures from a phone or computer | Sélectionnez pour activer « Ajouter des images depuis un téléphone ou un ordinateur » | |

### 4.7 Dialogs and common buttons

| key | English | French | notes |
|---|---|---|---|
| `btn_ok` | OK | OK | |
| `btn_cancel` | Cancel | Annuler | |
| `btn_delete` | Delete | Supprimer | |
| `btn_erase` | Erase | Effacer | Permanent deletion only. |
| `btn_empty` | Empty | Vider | |
| `btn_restore` | Restore | Restaurer | |
| `btn_close` | Close | Fermer | Closes a dialog (never "turn off"). |
| `on_off_on` | On | Activé | |
| `on_off_off` | Off | Désactivé | |
| `confirm_delete_perm_title` | Delete permanently | Supprimer définitivement | |
| `confirm_delete_perm_msg_one` | Erase this picture? This can't be undone. | Cette image sera effacée définitivement. Cette action est irréversible. | Single focused picture. |
| `confirm_delete_perm_msg` [plural] | Erase %d picture / %d pictures? This can't be undone. | %d image sera effacée définitivement. Cette action est irréversible. / %d images seront effacées définitivement. Cette action est irréversible. | |
| `confirm_empty_trash_msg` [plural] | Erase all %d picture(s)? This can't be undone. | %d image sera effacée définitivement. Cette action est irréversible. / Les %d images seront effacées définitivement. Cette action est irréversible. | |
| `input_ok` / `input_cancel` | OK / Cancel | OK / Annuler | On-screen keyboard dialog. |

### 4.8 Errors, banners and empty states (TV)

| key | English | French | notes |
|---|---|---|---|
| `slideshow_empty` | No pictures yet.\n\nOpen TV Art on this TV and choose Add pictures from a phone or computer. | Aucune image pour l’instant.\n\nOuvrez TV Art sur ce téléviseur et choisissez « Ajouter des images depuis un téléphone ou un ordinateur ». | Centered on the slideshow (02 §5). |
| `slideshow_loading` | Getting your pictures ready… | Préparation de vos images… | |
| `error_storage_unavailable` | The pictures can't be read right now. Your library is unchanged. | Les images sont illisibles pour le moment. Votre bibliothèque n’a pas changé. | Rescan guard (storage not mounted). |
| `error_generic` | Something went wrong. Try again. | Un problème est survenu. Réessayez. | Safe fallback (03: no raw exception text). |
| `import_unsupported` | Not a supported picture type: %1$s | Type de fichier non pris en charge : %1$s | `%1$s` = file name. |
| `import_copy_failed` | Copy failed: %1$s | Échec de la copie : %1$s | Pass a safe file name, not an exception message. |
| `import_unreadable` | Unreadable picture: %1$s | Image illisible : %1$s | |
| `import_could_not_save` | Couldn't save %1$s | Impossible d’enregistrer %1$s | |
| `import_storage_full` | Not enough space on the TV | Espace insuffisant sur le téléviseur | |
| `import_duplicate` | Already in your library as %1$s | Déjà dans votre bibliothèque (%1$s) | |

### 4.9 Notifications

| key | English | French | notes |
|---|---|---|---|
| `notif_channel_web` | Adding pictures from a phone or computer | Ajout d’images depuis un téléphone ou un ordinateur | Channel name (Android settings). |
| `notif_web_title` | TV Art: adding pictures is on | TV Art : ajout d’images activé | Ongoing notification. |
| `notif_web_text` | %1$s   PIN %2$s | %1$s   Code PIN %2$s | `%1$s` = address. |
| `notif_web_text_no_network` | No network   PIN %1$s | Pas de réseau   Code PIN %1$s | |

### 4.10 Web page (phone/computer)

Served from the app (02 §7.3). Browser-native controls (the file picker button text, `confirm()` OK/Cancel) are translated by the user's browser, not by us.

| key | English | French | notes |
|---|---|---|---|
| `web_title` | TV Art | TV Art | `<title>` and `<h1>`. |
| `web_login_prompt` | Enter the PIN shown on your TV | Entrez le code PIN affiché sur votre téléviseur | |
| `web_login_btn` | Unlock | Valider | |
| `web_upload_btn` | Upload | Ajouter des images | Header button. |
| `web_refresh` | Refresh | Actualiser | |
| `web_playing_now` | Playing now: %1$s (%2$s) | En cours sur le téléviseur : %1$s (%2$s) | `%2$s` = a `web_reason_*`. |
| `web_reason_single` | all pictures | toutes les images | |
| `web_reason_season` | season | saison en cours | |
| `web_reason_override` | you chose it | votre choix | |
| `web_reason_between` | between seasons | entre deux saisons | |
| `web_upload_panel` | Add pictures | Ajouter des images | `<summary>`. |
| `web_drop_here` | Drop pictures here or | Déposez vos images ici ou | Followed by the file input. |
| `web_upload_name` | Upload name | Nom de l’ajout | |
| `web_upload_to` | Add to | Ajouter à | Seasonal mode only. |
| `web_upload_to_none` | (none) | (aucune saison) | |
| `web_upload_skip_note` | Pictures already in your library are skipped automatically. | Les images déjà présentes dans votre bibliothèque sont ignorées automatiquement. | |
| `web_upload_default_name` | Upload %1$s | Ajout du %1$s | Same as `batch_name_web_default`. |
| `web_upload_progress` | Uploading %1$d / %2$d: %3$s | Envoi %1$d / %2$d : %3$s | `%3$s` = file name. |
| `web_upload_done` | Done. Added: %1$d · Duplicates: %2$d · Failed: %3$d | Terminé. Ajoutées : %1$d · Doublons : %2$d · Échecs : %3$d | |
| `web_status_added` | added | ajoutée | Per-file result line "nom.jpg — ajoutée". |
| `web_status_duplicate` | duplicate | doublon | |
| `web_status_failed` | failed | échec | |
| `web_filters_btn` | Filters (%1$d) | Filtres (%1$d) | |
| `web_f_date` | Date added | Date d’ajout | |
| `web_f_date_any` | Any date | Toutes les dates | |
| `web_f_date_on` | On | Le | |
| `web_f_date_before` | On or before | Le ou avant le | |
| `web_f_date_after` | On or after | Le ou après le | |
| `web_f_date_between` | Between | Entre | |
| `web_f_quick` | Quick | Raccourcis | |
| `web_f_today` | Today | Aujourd’hui | |
| `web_f_week` | This week | Cette semaine | |
| `web_f_month` | This month | Ce mois-ci | |
| `web_f_batch` | Upload | Ajout | |
| `web_f_batch_all` | All uploads | Tous les ajouts | |
| `web_f_season` | Season | Saison | Seasonal mode only. |
| `web_f_season_any` | Any | Toutes | |
| `web_f_season_none` | In no season | Dans aucune saison | |
| `web_f_season_in` | In %1$s | Dans %1$s | |
| `web_f_season_not_in` | Not in %1$s | Pas dans %1$s | |
| `web_f_lowres` | Low resolution | Basse résolution | Checkbox. |
| `web_f_cropped` | Edges cropped | Bords coupés | |
| `web_f_dupes` | Duplicates | Doublons | |
| `web_f_sort` | Sort | Tri | |
| `web_sort_new` | Newest | Plus récentes | |
| `web_sort_old` | Oldest | Plus anciennes | |
| `web_sort_name` | Name | Nom | |
| `web_sort_random` | Random | Aléatoire | |
| `web_f_reset` | Reset filters | Réinitialiser les filtres | |
| `web_selected` [plural] | %1$d selected | %1$d sélectionnée / %1$d sélectionnées | |
| `web_selected_of` | of %1$d | sur %1$d | Optional suffix: "3 sélectionnées sur 120". |
| `web_select_all` | Select all | Tout sélectionner | |
| `web_select_none` | Clear | Tout désélectionner | |
| `web_select_invert` | Invert | Inverser | |
| `web_add_to` | Add to ▾ | Ajouter à ▾ | Seasonal mode only. |
| `web_remove_from` | Remove from ▾ | Retirer de ▾ | Seasonal mode only. |
| `web_delete` | Delete | Supprimer | |
| `web_restore` | Restore | Restaurer | |
| `web_delete_perm` | Delete permanently | Supprimer définitivement | |
| `web_empty_trash` | Empty Recently deleted | Vider les suppressions récentes | |
| `web_nav_library` | Library | Bibliothèque | |
| `web_nav_all` | All pictures | Toutes les images | |
| `web_nav_unassigned` | In no season | Sans saison | Seasonal mode only. |
| `web_nav_recent` | Recently added | Ajoutées récemment | |
| `web_nav_trash` | Recently deleted | Suppressions récentes | |
| `web_nav_seasons` | Seasons | Saisons | Seasonal mode only. |
| `web_badge_lowres` | low resolution | basse résolution | Thumbnail badge. Short alt: "basse rés.". |
| `web_badge_cropped` | edges cropped | bords coupés | |
| `web_badge_duplicate` | duplicate | doublon | |
| `web_view_full` | View full size | Voir en taille réelle | Tooltip. |
| `web_image_tooltip` | %1$s  %2$d×%3$d  %4$s | %1$s  %2$d×%3$d  %4$s | `%4$s` = `size_mb` (French "2,4 Mo"). |
| `web_empty_nothing` | Nothing here. | Rien à afficher ici. | |
| `web_empty_library` | No pictures yet. Use Add pictures to add some. | Aucune image pour l’instant. Utilisez « Ajouter des images » pour en ajouter. | |
| `web_empty_filters` | No pictures match these filters. | Aucune image ne correspond à ces filtres. | |
| `web_no_seasons` | No seasons yet. Turn on seasonal slideshows on the TV (More › Seasonal slideshows). | Aucune saison pour l’instant. Activez les saisons sur le téléviseur (Autres paramètres › Diaporamas par saison). | Replaces "No slideshows yet. Create one under Slideshows below." |
| `web_toast_moved_trash` [plural] | %d picture moved to Recently deleted / … | %d image déplacée dans Suppressions récentes / %d images déplacées dans Suppressions récentes | |
| `web_toast_deleted_perm` | Deleted permanently | Supprimées définitivement | |
| `web_toast_restored` | Restored | Restaurées | |
| `web_toast_trash_emptied` | Recently deleted emptied | Suppressions récentes vidées | |
| `web_toast_added_to` [plural] | Added %1$d picture to %2$s / … | %1$d image ajoutée à %2$s / %1$d images ajoutées à %2$s | Same as `toast_added_to`. |
| `web_toast_removed_from` [plural] | Removed %1$d picture from %2$s / … | %1$d image retirée de %2$s / %1$d images retirées de %2$s | |
| `web_toast_failed` | Failed: %1$s | Échec : %1$s | |
| `web_toast_upload_failed` | Upload failed: %1$s | Échec de l’ajout : %1$s | |
| `web_confirm_delete_perm` [plural] | Permanently delete %d picture(s)? This can't be undone. | %d image sera effacée définitivement. Cette action est irréversible. / %d images seront effacées définitivement. Cette action est irréversible. | Browser `confirm()`: OK/Cancel come from the browser. |
| `web_confirm_empty_trash` | Permanently delete everything in Recently deleted? | Tout le contenu des suppressions récentes sera effacé définitivement. | |
| `web_backup_panel` | Backup | Sauvegarde | |
| `web_backup_download` | Download backup | Télécharger la sauvegarde | |
| `web_backup_restore_from` | Restore from a file | Restaurer depuis un fichier | |
| `web_backup_restore_btn` | Restore | Restaurer | |
| `web_backup_choose_file` | Choose a backup file | Choisissez un fichier de sauvegarde | |
| `web_backup_confirm` | Restore this backup? Matching seasons and settings will be replaced. | Les saisons et les paramètres correspondants seront remplacés par ceux de cette sauvegarde. Cliquez sur OK pour continuer. | Avoids `?`. |
| `web_backup_done` | Backup restored. Pictures linked to seasons: %1$d (not found: %2$d). Seasons created: %3$d, updated: %4$d. | Sauvegarde restaurée. Images reliées aux saisons : %1$d (introuvables : %2$d). Saisons créées : %3$d, mises à jour : %4$d. | Favorites counts removed. |
| `web_backup_invalid` | This file is not a valid backup. | Ce fichier n’est pas une sauvegarde valide. | Replaces "Not valid JSON" (and covers failed validation, 03). |
| `web_backup_wrong_app` | This backup is not from TV Art. | Cette sauvegarde ne provient pas de TV Art. | |
| `web_backup_too_large` | This backup file is too large. | Ce fichier de sauvegarde est trop volumineux. | |
| `web_slideshow_create_*` | (New slideshow name / Create / Created / Name required) | (Nom du diaporama / Créer / Créé / Indiquez un nom) | **Only if you keep** the reference's "create slideshow" panel. The spec puts season creation on the TV, so it should be removed. |

#### Web manager errors (server JSON `error`, shown to the user)

| key | English | French | notes |
|---|---|---|---|
| `web_err_wrong_pin` | Wrong PIN. | Code PIN incorrect. | |
| `web_err_too_many_attempts` [plural] | Too many attempts. Try again in %d second / %d seconds. | Trop d’essais. Réessayez dans %d seconde. / Trop d’essais. Réessayez dans %d secondes. | |
| `web_err_login_required` | Enter the PIN shown on the TV. | Entrez le code PIN affiché sur le téléviseur. | Was "Login required". |
| `web_err_session_expired` | Your session has ended. Enter the PIN again. | Votre session a pris fin. Entrez de nouveau le code PIN. | |
| `web_err_upload_too_large` | This file is too large (maximum %1$s). | Ce fichier est trop volumineux (maximum %1$s). | `%1$s` = `size_mb`, e.g. "30 Mo". |
| `web_err_too_many_files` | Too many files at once (maximum %1$d). | Trop de fichiers à la fois (maximum %1$d). | |
| `web_err_unsupported` | Unsupported file. Use JPG, PNG, WebP or BMP pictures. | Fichier non pris en charge. Utilisez des images JPG, PNG, WebP ou BMP. | |
| `web_err_no_supported` | No supported pictures (JPG, PNG, WebP or BMP). | Aucune image prise en charge (JPG, PNG, WebP ou BMP). | Client-side check. |
| `web_err_duplicate` | Already in your library. | Déjà dans votre bibliothèque. | |
| `web_err_upload_busy` | An upload is already in progress. | Un ajout est déjà en cours. | |
| `web_err_storage_full` | Not enough space on the TV. | Espace insuffisant sur le téléviseur. | |
| `web_err_not_found` | Not found. | Introuvable. | |
| `web_err_image_not_found` | This picture no longer exists. | Cette image n’existe plus. | Covers "No such image", "No thumbnail", "File missing". |
| `web_err_bad_request` | Invalid request. | Requête non valide. | Covers "POST expected", malformed body. |
| `web_err_forbidden_host` | Use the address shown on the TV. | Utilisez l’adresse affichée sur le téléviseur. | Host-header check (03). |
| `web_err_server` | Something went wrong on the TV. Try again. | Un problème est survenu sur le téléviseur. Réessayez. | Generic safe error (03). |
| `web_err_network` | Connection lost. Check that this device is on the same Wi-Fi as the TV. | Connexion perdue. Vérifiez que cet appareil est sur le même Wi-Fi que le téléviseur. | Client-side (`Network error`). |
| `web_err_manager_off` | Adding pictures is turned off on the TV. | L’ajout d’images est désactivé sur le téléviseur. | When the switch is off / auto-off after 30 min. |
| `web_err_name_required` | Enter a name. | Indiquez un nom. | |
| `web_err_http` | Error %1$d | Erreur %1$d | Last-resort fallback. |

### 4.11 Clock and date formats (string arrays)

Build dates with **one helper** (02 §9) and these resources. Prefer the resources over `DateFormatSymbols` if a device's ICU data differs (old Fire OS builds can lack the period on some abbreviations).

| key | English | French | notes |
|---|---|---|---|
| `months_full` (array) | January … December | janvier, février, mars, avril, mai, juin, juillet, août, septembre, octobre, novembre, décembre | Lower case in French (capitalize only at the start of a title). |
| `months_short` (array) | Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec | janv., févr., mars, avr., mai, juin, juil., août, sept., oct., nov., déc. | Short forms keep their period; mars, mai, juin, août are written in full. |
| `weekdays_short` (array, Mon→Sun) | Mon, Tue, Wed, Thu, Fri, Sat, Sun | lun., mar., mer., jeu., ven., sam., dim. | |
| `weekdays_full` (array) | Monday … Sunday | lundi, mardi, mercredi, jeudi, vendredi, samedi, dimanche | Lower case. |
| `clock_date_dm` | %1$s, %2$d %3$s | %1$s %2$d %3$s | Uppercased: `THU, 29 OCT` / `JEU. 29 OCT.` (French: no comma; the period of the abbreviation stays). |
| `clock_date_md` | %1$s, %3$s %2$d | %1$s %3$s %2$d | `THU, OCT 29` / `JEU. OCT. 29`. |
| `date_text_dm` | %1$d %2$s | %1$s %2$s | French `%1$s` = day with "1er" rule (§5). "29 oct." / "1er déc.". |
| `date_text_md` | %2$s %1$d | %2$s %1$d | "Oct 29" / "oct. 29". |
| `date_text_dm_year` | %1$d %2$s %3$d | %1$s %2$s %3$d | "1er déc. 2026". |
| `date_text_md_year` | %2$s %1$d, %3$d | %2$s %1$d, %3$d | "déc. 1, 2026" (spec 02 §9). |
| `date_num_dm` | %1$02d/%2$02d/%3$d | %1$02d/%2$02d/%3$d | 31/12/2026, 01/03/2026. |
| `date_num_md` | %2$02d/%1$02d/%3$d | %2$02d/%1$02d/%3$d | 12/31/2026. |
| `date_num_ymd` | %3$d-%2$02d-%1$02d | %3$d-%2$02d-%1$02d | 2026-12-31. |
| `day_first_fr` | — | 1er | French only: replaces "1" in day-first text dates (§5). |

---

## 5. Dates in French

**Month abbreviations (text dates, lists, pickers' preview):** janv., févr., mars, avr., mai, juin, juil., août, sept., oct., nov., déc.
**Weekday abbreviations:** lun., mar., mer., jeu., ven., sam., dim.

**Clock line (uppercase with the French locale, accents kept):**
JANV. · FÉVR. · MARS · AVR. · MAI · JUIN · JUIL. · AOÛT · SEPT. · OCT. · NOV. · DÉC.
LUN. · MAR. · MER. · JEU. · VEN. · SAM. · DIM.
Examples: `JEU. 29 OCT.`, `MAR. 1 DÉC.`, `SAM. 14 FÉVR.`, `SAM. 15 AOÛT`.

**Examples for each date-format option** (date: Thursday, October 29, 2026; and December 1, 2026):

| Option | Where | French | English |
|---|---|---|---|
| Day/Month | Clock | `JEU. 29 OCT.` · `MAR. 1 DÉC.` | `THU, 29 OCT` · `TUE, 1 DEC` |
| Day/Month | Text date | 29 oct. · 1er déc. · 1er déc. 2026 | 29 Oct · 1 Dec · 1 Dec 2026 |
| Day/Month | Range | Du 1er déc. au 28 févr. | From 1 Dec to 28 Feb |
| Day/Month | Numeric | 29/10/2026 · 01/12/2026 | 29/10/2026 · 01/12/2026 |
| Month/Day | Clock | `JEU. OCT. 29` · `MAR. DÉC. 1` | `THU, OCT 29` · `TUE, DEC 1` |
| Month/Day | Text date | oct. 29 · déc. 1 · déc. 1, 2026 | Oct 29 · Dec 1 · Dec 1, 2026 |
| Month/Day | Range | Du déc. 1 au févr. 28 | From Dec 1 to Feb 28 |
| Month/Day | Numeric | 10/29/2026 · 12/01/2026 | 10/29/2026 · 12/01/2026 |
| Year-Month-Day | Clock | `JEU. 29 OCT.` (day-month, per 02 §6) | `THU, 29 OCT` |
| Year-Month-Day | Text date | 29 oct. · 1er déc. 2026 (day-month) | 29 Oct · 1 Dec 2026 |
| Year-Month-Day | Numeric | 2026-10-29 · 2026-12-01 | 2026-10-29 · 2026-12-01 |

Numeric short form without a year (filters, compact lists): `29/10`, `10/29`, `10-29`.

**The "1er" question — recommendation:** use **"1er"** for the first of the month **in French text dates where the day comes before the month** ("1er déc.", "Du 1er déc. au 28 févr.", "commence le 1er mars"). Every French reader in France and Québec expects it there, and "le 1 déc." looks like a translation error. Do **not** use it:
- on the **clock line** (`MAR. 1 DÉC.`: compact device style, also avoids the odd uppercase "1ER");
- in **month-first** dates ("déc. 1") and **numeric** dates (01/12);
- in English.

This is one simple rule in the date helper: `if (lang == fr && dayFirst && day == 1 && !clock) "1er" else day.toString()`. It is consistent, which is what 02 §9 asks.

Time: `17:05` (24-hour, default) or `5:05` (12-hour, no AM/PM). Same digits in both languages.

---

## 6. France vs Québec notes

| Concept | France | Québec | Neutral choice made |
|---|---|---|---|
| upload | télécharger (vers), uploader, envoyer | téléverser | **ajouter / envoyer** |
| download | télécharger | télécharger | **télécharger** (only for the backup file) |
| phone | portable, mobile | cellulaire, cell | **téléphone** |
| laptop | ordinateur portable | portable | **ordinateur** (never *portable*) |
| email | e-mail, mail, courriel (official) | courriel | not used; if ever needed, **adresse électronique** |
| QR code | QR code | code QR | **code QR** in docs, **code carré** on screen |
| scan a code | scanner, flasher | balayer (official), scanner | **pointer l’appareil photo vers** |
| PIN | code PIN | NIP | **code PIN** |
| screensaver | écran de veille (very common), économiseur d’écran | économiseur d’écran | **économiseur d’écran** (also Fire TV's term, likely) |
| settings | paramètres, réglages | paramètres, réglages | **paramètres** only |
| turn off | éteindre, désactiver | fermer, désactiver | **désactiver** (avoid *fermer*, which means "close" in France) |
| schedule | programme, planning, calendrier | horaire, calendrier | avoided: **saisons**, **dates** |
| brown | marron | brun | **brun** (correct and understood in France) |
| weekend | week-end | fin de semaine | not used ("cette semaine" only) |
| TV | télé, téléviseur | télé, téléviseur, TV | **téléviseur** |
| computer | ordinateur, PC, ordi | ordinateur, ordi | **ordinateur** |
| app | appli, application | appli, application | **application** |
| Wi-Fi spelling | Wi-Fi, wifi | Wi-Fi, wifi (OQLF) | **Wi-Fi** |
| backup | sauvegarde | sauvegarde, copie de sécurité | **sauvegarde** |
| space before `; ! ?` | non-breaking space | none | **avoid those signs in UI**; `:` with U+00A0 in both |
| guillemets | « Hiver » with spaces | « Hiver » with spaces | same: « » with U+00A0 |
| time | 17:05 on devices (17 h 05 in prose) | 17:05 on devices (17 h 05 in prose, 12-hour common in speech) | **17:05** or **5:05** per the setting |
| numeric dates | 31/12/2026 | 2026-12-31 (official), 31/12 and 12/31 both seen | user setting, default **Day/Month** |
| first of the month | 1er | 1er | **1er** in day-first text dates (§5) |
| fall season | automne | automne | **Automne** (same) |
| Recently Deleted | Suppressions récentes (Apple) | Suppressions récentes (Apple) | **Suppressions récentes** |
| units | Ko, Mo, Go | Ko, Mo, Go | **Ko, Mo, Go** |

**Fire TV menu names (01 §B):** Amazon's French labels could not be verified here, and Fire TV builds sold in France and in Canada may word them differently ("Ma Fire TV" vs "Mon Fire TV"; "Options pour les développeurs" vs "Options de développement"). Treat every French Fire TV label in 01 as **likely**, and if the user can't find one, ask them to describe or photograph the screen.
