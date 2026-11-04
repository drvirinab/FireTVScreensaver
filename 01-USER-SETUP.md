# 01 — User Setup: what to ask, what the user does, where the pictures go

**Who this is for:** you, the agent. The person you're helping (the "user") is a **French speaker**, possibly from France, possibly from Québec, and possibly not technical.

**How to use this file:**
- Talk to the user **in French**. Every block marked **Say to the user** has an English version (for you) and a **French version (read this one to the user)**. Use the French text as written. It was prepared by a French-English linguist to be clear in both France and Québec.
- **Keep it short:** one step at a time, wait for "c'est fait" (done) before the next.
- Never ask the user to type commands. You run the commands; the user only uses the TV remote and, if needed, drags pictures into a folder.
- Fire TV menus may be in French or English depending on their TV. Menu names below give **both**. The French Fire TV menu names are our best knowledge and **may differ slightly** on their device. If the user can't find one, ask them to describe or photograph what they see, and guide them from that.

---

## A. Before starting: what the user must provide

Ask for all of this first, in one message. Don't start building until you have items 1–4.

1. **Their Fire TV model and Fire OS version.** Easiest: a photo of the "About" screen.
2. **A computer** (Windows or Mac) **on the same Wi-Fi/home network as the TV**, which they can install software on, with about 5 GB free and internet access. You'll install the build tools (JDK 17, Android SDK platform-tools) on it.
3. **Their wallpapers** (see section D): where they are, and whether they want one slideshow or seasons.
4. **Their preferences:** date format (31/12, 12/31 or 2026-12-31), 24-hour or 12-hour clock, and whether they want seasonal slideshows now or later.
5. Whether they use **YouTube** on the TV (that's for the "art over YouTube" feature; if not, skip that part).
6. **Time near the TV** during setup: the TV shows a prompt that must be accepted with the remote.

**Say to the user (EN):**
> Before we start, I need a few things from you:
> 1. On your TV, open **Settings**, then **My Fire TV**, then **About**. Take a photo of that screen and send it to me (or tell me the device name and the "Fire OS" version).
> 2. A computer (Windows or Mac) connected to the **same Wi-Fi** as the TV. I'll install what's needed on it.
> 3. Your pictures (wallpapers) for the TV. Tell me where they are on the computer. If you don't have any yet, that's fine: I can use four sample pictures (one per season) so we can test.
> 4. How you like to see dates: **31/12** (day first), **12/31** (month first) or **2026-12-31**? And the clock: **17:05** (24-hour) or **5:05** (12-hour)?
> 5. Do you want **one single slideshow** (all your pictures), or **a different slideshow for each season** (winter, spring, summer, fall)? You can change this later in the app.
> 6. Do you watch or listen to **YouTube** on this TV?

**Say to the user (FR):**
> Avant de commencer, j'ai besoin de quelques renseignements :
> 1. Sur votre téléviseur, ouvrez **Paramètres**, puis **Ma Fire TV**, puis **À propos**. Prenez une photo de cet écran et envoyez-la-moi (ou indiquez-moi le nom de l'appareil et la version de « Fire OS »).
> 2. Un ordinateur (Windows ou Mac) connecté au **même Wi-Fi** que le téléviseur. J'y installerai ce qu'il faut.
> 3. Vos images (fonds d'écran) pour le téléviseur. Dites-moi où elles se trouvent sur l'ordinateur. Si vous n'en avez pas encore, ce n'est pas grave : je peux utiliser quatre images d'exemple (une par saison) pour faire un essai.
> 4. Votre préférence pour les dates : **31/12** (le jour d'abord), **12/31** (le mois d'abord) ou **2026-12-31**. Et pour l'heure : **17:05** (sur 24 heures) ou **5:05** (sur 12 heures).
> 5. Votre choix : **un seul diaporama** (toutes vos images) ou **un diaporama différent pour chaque saison** (hiver, printemps, été, automne). Vous pourrez changer ce choix plus tard dans l'application.
> 6. **YouTube** : dites-moi si vous le regardez ou l'écoutez sur ce téléviseur.

### A.1 Check the device before going further
Use the answer to item 1 and `02-APP-SPEC.md` §2.
- **"Vega OS"** on the About screen, or no "Developer Options" can be found at all → **stop**. Explain kindly that this model can't install this kind of app, and that it would need a Fire TV running Fire OS (most Fire TV Sticks, Cubes and Fire TV televisions).
- **Fire OS 7.x** → proven path.
- **Fire OS 8.x** → expected to work; the screensaver part must be tested early (Phase 0b). Tell the user it will be tested and may not be possible on their version.

**Say to the user if the device can't be used (EN):**
> Thank you. Unfortunately this TV model uses a system (Vega OS) that can't install this kind of app. It works on Fire TV devices with "Fire OS" (most Fire TV Sticks, the Fire TV Cube and Fire TV televisions).

**(FR):**
> Merci. Malheureusement, ce modèle de téléviseur utilise un système (Vega OS) qui ne permet pas d'installer ce type d'application. L'application fonctionne sur les appareils Fire TV équipés de « Fire OS » (la plupart des Fire TV Stick, le Fire TV Cube et les téléviseurs Fire TV).

---

## B. Turning on developer mode (on the TV, with the remote)

You need **ADB debugging** turned on so the computer can install the app over the network. "Apps from unknown sources" is **not** needed for installing over ADB; don't ask the user to turn it on.

| Step | English menus | French menus (likely; confirm with the user) |
|---|---|---|
| 1 | Home → **Settings** (gear icon, far right) | Accueil → **Paramètres** (icône d'engrenage, tout à droite) |
| 2 | **My Fire TV** | **Ma Fire TV** (may appear as « Mon Fire TV », « Appareil » or with the device name; French Canada builds may differ from France) |
| 3 | **About** | **À propos** |
| 4 | Highlight the **device name** (first line, e.g. "Fire TV Stick 4K") and press the **center/OK button 7 times** | Sélectionner le **nom de l'appareil** (première ligne, p. ex. « Fire TV Stick 4K ») et appuyer **7 fois** sur le bouton rond du centre (OK) |
| 5 | Message: "No need, you are already a developer" or "You are now a developer" | Message du type « Vous êtes maintenant développeur » ou « Vous êtes déjà développeur » (wording unverified) |
| 6 | **Back** once → a new item **Developer Options** appears in My Fire TV | **Retour** une fois → un nouvel élément **Options pour les développeurs** (or « Options de développement », « Options développeurs ») apparaît dans Ma Fire TV |
| 7 | Developer Options → **ADB Debugging** → **ON**. Confirm the warning. | Options pour les développeurs → **Débogage ADB** → **Activé** (may show « ACTIVÉ » / « OUI »). Confirmer l'avertissement. |
| 8 | Get the **IP address**: My Fire TV → About → **Network** → "IP Address" (e.g. 192.168.x.x) | Ma Fire TV → À propos → **Réseau** → « Adresse IP » (p. ex. 192.168.x.x) |
| — | Other labels you may need: **Your Apps**, **Home** button, the **Allow USB debugging?** prompt and its **Always allow from this computer** checkbox | « Vos applications » (may appear as « Vos applis et chaînes »), bouton **Accueil** (maison), « Autoriser le débogage USB ? », « Toujours autoriser sur cet ordinateur » (all likely; confirm) |

On some older Fire OS versions, Developer Options is already visible (skip steps 3–6).

**Say to the user (EN):**
> Let's turn on "developer mode" on the TV. It's an official Fire TV setting, and it lets the computer install the app. It doesn't change anything else.
> 1. With the remote, go to **Settings** (the gear icon on the far right), then **My Fire TV**, then **About**.
> 2. Select the first line (the name of your Fire TV) and press the **round center button 7 times**. A message says you are now a developer.
> 3. Press **Back** once. Open the new menu **Developer Options**.
> 4. Turn on **ADB Debugging** and confirm.
> 5. Go back to **About**, then **Network**, and tell me the **IP address** shown (numbers like 192.168.1.23).

**(FR):**
> Nous allons activer le « mode développeur » sur le téléviseur. C'est un réglage officiel de Fire TV : il permet à l'ordinateur d'installer l'application, et il ne change rien d'autre.
> 1. Avec la télécommande, allez dans **Paramètres** (l'icône d'engrenage, tout à droite), puis **Ma Fire TV**, puis **À propos**.
> 2. Sélectionnez la première ligne (le nom de votre Fire TV) et appuyez **7 fois sur le bouton rond du centre**. Un message indique que vous êtes maintenant développeur.
> 3. Appuyez une fois sur **Retour**. Ouvrez le nouveau menu **Options pour les développeurs**.
> 4. Activez **Débogage ADB** et confirmez.
> 5. Revenez à **À propos**, puis **Réseau**, et donnez-moi l'**adresse IP** affichée (des chiffres comme 192.168.1.23).

### B.1 The connection prompt
When you run `adb connect <IP>:5555` (or `deploy.ps1`), the TV shows **"Allow USB debugging?"** (French: probably « Autoriser le débogage USB ? »), even over Wi-Fi.

**Say to the user (EN):**
> A message will appear on the TV asking to allow debugging from this computer. Check **"Always allow from this computer"**, then select **OK**.

**(FR):**
> Un message va s'afficher sur le téléviseur pour autoriser le débogage depuis cet ordinateur. Cochez la case **« Toujours autoriser sur cet ordinateur »** (ou une formulation proche), puis sélectionnez **OK**.

### B.2 Troubleshooting connection (for you)
- `adb connect` times out: wrong IP; the TV is asleep; the PC is on a guest network, or the router isolates devices ("AP/client isolation"); a VPN or firewall on the PC. Ask the user to check that both are on the same Wi-Fi name.
- "unauthorized": the prompt wasn't accepted. Run `adb disconnect`, connect again, and ask the user to look at the TV.
- The IP changes after a router restart. Ask again from About → Network.

### B.3 After setup: developer mode and safety
Leaving ADB debugging on lets any computer **that the user approves** on the home network control the TV. Approval is required, but users tend to click OK. At the end, explain the choice:
- **Turn ADB Debugging off** (safer). The app keeps working, including the Enable/Disable buttons (the permission granted during setup stays). The user turns it back on only if a helper needs to update the app.
- **Leave it on** (easier future updates).
Recommend turning it off, and let the user choose.

**Say to the user (EN):**
> Everything is installed. For safety, I recommend turning **ADB Debugging** back **off** (Settings → My Fire TV → Developer Options). The app will keep working. You'd only turn it back on if someone needs to update the app.

**(FR):**
> Tout est installé. Par sécurité, je vous conseille de **désactiver** le **Débogage ADB** (Paramètres → Ma Fire TV → Options pour les développeurs). L'application continuera de fonctionner. Il faudrait le réactiver seulement si quelqu'un doit mettre l'application à jour.

---

## C. One-time permissions and system switches (you do this)

`deploy.ps1` / `deploy.sh` (in `source/`) install the app, copy the pictures, and grant the storage and `WRITE_SECURE_SETTINGS` permissions. They save the original screensaver values to `screensaver_backup.txt` first. **Keep that file**; it's the manual way back to Amazon's screensaver.

Then, on the TV, in the app, the user turns on **Art screensaver** and **Art over YouTube** under "Screensaver & YouTube" (French labels in 04). Fire OS may show an accessibility warning when the overlay is enabled; that's expected.

**Say to the user (EN):**
> The app is now on your TV. It's called **TV Art** in "Your Apps". Open it, go to **Screensaver & YouTube**, and turn on **Art screensaver**. If you use YouTube, also turn on **Art over YouTube**.

**(FR):**
> L'application est maintenant sur votre téléviseur. Elle s'appelle **TV Art** dans « Vos applications ». Ouvrez-la, allez dans **Économiseur d’écran et YouTube**, et activez **Économiseur d'écran**. Si vous utilisez YouTube, activez aussi **Diaporama sur YouTube**.

---

## D. Where the wallpapers go (initial setup and testing)

### D.1 What makes a good wallpaper
- **JPG or PNG**, landscape. **1920×1080** (Full HD) or **3840×2160** (4K) is ideal. Smaller pictures look blurry; non-16:9 pictures get their edges cropped. The gallery flags both.
- Avoid huge files (over ~30 MB each); they slow the TV down.

### D.2 Where to put them on the computer
Create a folder **`Walls`** next to `source/` (or anywhere; pass it to the script with `-ImageFolder`). The layout decides where pictures go:

**One single slideshow (default):** put all pictures directly in `Walls/`.
```
Walls/
  photo1.jpg
  photo2.jpg
```

**Seasonal slideshows:** one subfolder per season, named exactly like the season in the app (accents optional; `Ete` works for `Été`). Loose files go to the main slideshow ("between seasons").
```
Walls/
  Hiver/       (Winter)
  Printemps/   (Spring)
  Été/         (Summer)
  Automne/     (Fall)
```
The script copies `Walls/` to the TV at `/sdcard/Pictures/TVArt/`. The app scans it on open.

**Say to the user (EN):**
> On your computer, I created a folder called **Walls** here: [path]. Please copy your pictures into it.
> - For one slideshow: put them all directly in **Walls**.
> - For seasons: put them in the folders **Hiver**, **Printemps**, **Été** and **Automne** inside **Walls**.
> Tell me when it's done.

**(FR):**
> Sur votre ordinateur, j'ai créé un dossier nommé **Walls** à cet endroit : [chemin]. Copiez-y vos images, s'il vous plaît.
> - Pour un seul diaporama : mettez-les toutes directement dans **Walls**.
> - Pour les saisons : mettez-les dans les dossiers **Hiver**, **Printemps**, **Été** et **Automne**, à l'intérieur de **Walls**.
> Dites-moi quand c'est fait.

### D.3 Later: adding pictures without the computer
Once the app runs, the user can add pictures from their phone: in the app, turn on **Add pictures from a phone or computer**, scan the QR code with the phone's camera, enter the PIN shown on the TV, and upload. A USB drive with a `TVArt` folder also works. Show the user this once during the acceptance test.

**Say to the user (EN):**
> Later, to add pictures from your phone: open the app, turn on **Add pictures from a phone or computer**, scan the square code shown on the TV with your phone's camera, type the PIN shown on the TV, then choose your pictures.

**(FR):**
> Plus tard, pour ajouter des images depuis votre téléphone : ouvrez l'application, activez **Ajouter des images depuis un téléphone ou un ordinateur**, pointez l'appareil photo de votre téléphone vers le code carré affiché sur le téléviseur, entrez le code PIN affiché sur le téléviseur, puis choisissez vos images.

### D.4 If the user has no pictures, or doesn't want to provide them
You may download these **four sample wallpapers**, one per season, to test with. They were checked on 2026-11-04: direct download works, real JPEG files, 1920×1080, about 0.5–1 MB each.

| Season | URL | Save as |
|---|---|---|
| Winter (snowy lake) | https://w.wallhaven.cc/full/zx/wallhaven-zxd9yv.jpg | `Walls/Hiver/` |
| Spring (blossoming tree, dandelions) | https://w.wallhaven.cc/full/95/wallhaven-95d1wk.jpg | `Walls/Printemps/` |
| Summer (wheat field, blue sky) | https://w.wallhaven.cc/full/0j/wallhaven-0jkmdm.jpg | `Walls/Été/` (or `Ete/`) |
| Fall (autumn trees over a stream) | https://w.wallhaven.cc/full/od/wallhaven-od3rjp.jpg | `Walls/Automne/` |

Rules:
- In **single-slideshow** mode, put all four directly in `Walls/`.
- Download **only these four URLs**, into a new empty folder, and check each file before using it: the HTTP status is 200, the content type is `image/jpeg`, the first bytes are `FF D8 FF`, the size is under 5 MB, and it decodes as an image. If any check fails, don't use that file, and tell the user.
- Some servers refuse requests without a browser-like User-Agent. Use a generic one, never the user's personal information.
- These pictures come from a public wallpaper site and their copyright status is unknown. They are **for the user's personal testing only**: never bundle them in the APK, never commit or redistribute them. Tell the user they can replace them with their own pictures at any time.

**Say to the user (EN):**
> No problem. To test, I'll use four sample landscape pictures (winter, spring, summer, fall) downloaded from a wallpaper site. They're only for testing on your TV, and you can replace them with your own pictures whenever you like.

**(FR):**
> Pas de problème. Pour faire un essai, je vais utiliser quatre images de paysages (hiver, printemps, été, automne) téléchargées depuis un site de fonds d'écran. Elles servent seulement aux essais sur votre téléviseur, et vous pourrez les remplacer par vos propres images quand vous le voudrez.

---

## E. Wrap-up message to the user (after the acceptance test)

Explain in French, briefly:
- How to start the slideshow over YouTube: **press the Menu button (≡) twice quickly**; press it twice again (or Back) to return.
- The screensaver starts by itself after the TV's usual idle time.
- How to add pictures from the phone (D.3).
- Where to turn seasons on/off, rename them and change their dates.
- That a Fire TV system update can sometimes switch the screensaver back to Amazon's. The app then shows a yellow message, and selecting it fixes it.

**Say to the user (EN):**
> All set! A few tips:
> - **Slideshow over YouTube:** while music plays, press the **Menu button (≡) twice quickly**. Press it twice again, or press Back, to return to YouTube.
> - **Screensaver:** it starts by itself when the TV is idle.
> - **Add pictures:** in the app, **Add pictures from a phone or computer**, then scan the code.
> - **Seasons:** in the app, **More**, then **Seasonal slideshows**. You can rename seasons, change their dates, add or remove them.
> - If a Fire TV update puts Amazon's screensaver back, the app shows a yellow message: select it to fix it.

**(FR):**
> Tout est prêt. Quelques conseils :
> - **Diaporama sur YouTube :** pendant que vous écoutez de la musique, appuyez **deux fois rapidement sur le bouton Menu (≡)**. Appuyez de nouveau deux fois, ou sur Retour, pour revenir à YouTube.
> - **Économiseur d'écran :** il démarre tout seul quand le téléviseur reste inutilisé.
> - **Ajouter des images :** dans l'application, choisissez **Ajouter des images depuis un téléphone ou un ordinateur**, puis pointez votre téléphone vers le code.
> - **Saisons :** dans l'application, allez dans **Autres paramètres**, puis **Diaporamas par saison**. Vous pouvez renommer les saisons, changer leurs dates, en ajouter ou en supprimer.
> - Si une mise à jour de Fire TV remet l'économiseur d'écran d'Amazon, l'application affiche un message jaune : sélectionnez-le pour régler le problème.
