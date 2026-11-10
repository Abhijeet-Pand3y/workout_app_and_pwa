# Workout tracker: project notes

Personal workout tracker, built twice with **feature parity**:
- **Android app** (`app/`): Kotlin + Jetpack Compose, installed as a sideloaded APK.
- **PWA** (`pwa/`): plain HTML/CSS/JS, no build step, for iPhone friends (and Android). Live at
  https://abhijeet-pand3y.github.io/workout_app_and_pwa/

Repo: `git@github.com:Abhijeet-Pand3y/workout_app_and_pwa.git` (public, required for free Pages). Push over SSH.

**Rule: every user-facing feature goes into BOTH apps**, and the JSON file formats must stay compatible
(shared plan files and full backups move between Android and PWA in both directions).

## What the app does (current features)
- **Custom split plans**: any number of days, any names (e.g. Push 1 … Legs 2). Multiple plans, one active.
  Days can be reordered, renamed and deleted; "Push/Pull/Legs ×2" is only an optional template, never hardcoded.
- **Rotation, not calendar**: `Plan.nextDayIndex` advances only when a workout is **finished**
  (next = day after the one done, wrapping). Doing a different day continues the rotation from that day.
  Discarding a workout does not move it. "Make this the next workout" sets it manually.
  Reordering or deleting days keeps "next" on the same day (`editDays` / `setDays`).
- **Per-day saved exercises** (template). These load automatically when a day starts. Exercises added during a workout:
  - strength defaults to "also add to <day> plan" = **checked**
  - timed/cardio defaults to **unchecked** ("today only" tag); it can be added later via the card menu.
  - Exercises with 0 sets are dropped when a workout is finished.
- **Strength sets**: weight (optional), reps, partial reps, drop sets (list of weight×reps drops).
  New set is prefilled from the previous set (no partials/drops); "Repeat last" copies everything.
- **Timed / cardio exercises** (plank, treadmill…): rounds of duration (seconds) + optional distance,
  with a stopwatch in the round dialog. The kind is remembered globally by exercise name (`timedExercises`).
- **"Last time"**: shows the most recent previous performance of the same exercise (by name, case-insensitive).
- **History**: every workout, editable/deletable afterwards.
- **Progress**: week (Mon–Sun) / month, with prev/next navigation:
  - tiles: workouts, time, sets, reps, partials, drop sets, volume, cardio time, distance
  - split days done, per-exercise stats (best weight / best time), and a last-8-periods bar chart
- **Plan sharing** (structure only, no weights): Android uses the share sheet + FileProvider; tapping a received
  file opens the app (VIEW/SEND intent filters). The PWA uses Web Share with a file, falling back to a download,
  and imports via a file picker. An imported plan is added as a new plan, with " (2)" appended on a name clash.
- **Backup**: export/import the full data JSON (Plans → Settings). kg/lb unit; distance shows km/mi to match.

## Data formats (keep compatible!)
Full data (`workout_data.json` on Android in `filesDir`; `localStorage['workout-data-v1']` in the PWA):
```
{ version:1, plans:[{id,name,nextDayIndex,days:[{id,name,exercises:[string]}]}],
  activePlanId, weightUnit:"kg"|"lb", timedExercises:[lowercase names],
  current: Session|null, sessions:[Session] }
Session  = {id, planId?, planName, dayId?, dayName, startedAt(ms), finishedAt?, exercises:[Exercise]}
Exercise = {id, name, kind?:"timed", sets:[Set]}            // kind absent = strength
Set      = {id, w?, r, p, drops:[{w?, r}], t?(seconds), d?(distance)}
```
Shared plan file: `{type:"workout-plan", version:1, name, days:[{name, exercises:[...]}], timed?:[names]}`.
Parsers must tolerate missing fields (old files) and unknown fields.

## Code map
- Android `app/src/main/java/com/abhijeet/workout/`
  - `data/Model.kt`: data classes and all JSON (de)serialization, including the shared-plan format
  - `data/WorkoutViewModel.kt`: single source of truth (`data` Compose state); every change goes through
    `update {}`, which saves atomically on a background thread. Holds the rotation logic.
  - `data/Stats.kt`: `computeStats`, week/month `period()`
  - `ui/WorkoutScreens.kt` (start, active workout, session editor, exercise cards), `ui/PlanScreens.kt`,
    `ui/HistoryStatsScreens.kt`, `ui/Components.kt` (dialogs, set/timed dialogs, formatting, theme),
    `ui/PlanShare.kt`
  - `MainActivity.kt`: tabs + sub-screen nav (rememberSaveable string `"plan:<id>"` / `"session:<id>"`),
    incoming plan-file intents
- PWA `pwa/`: `app.js` (all logic and rendering; string templates + `data-a` click delegation; modals via
  `openModal(html, handlers)`), `styles.css`, `sw.js` (stale-while-revalidate cache), `manifest.webmanifest`, `icons/`.

## Build / run / deploy
- **Android build** (no Android Studio GUI needed). Android Studio's bundled JDK is **Java 25**:
  ```
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  .\gradlew.bat assembleDebug
  ```
  APK: `app/build/outputs/apk/debug/app-debug.apk`. The user installs it by copying it to the phone's Download folder.
  - Toolchain: Gradle 9.9.0, AGP 9.5.1 (**built-in Kotlin**, so do NOT add `org.jetbrains.kotlin.android`
    or `kotlinOptions`), compose compiler plugin 2.4.21, compileSdk 35, minSdk 26.
  - Gradle 8.x fails on Java 25. Keep Gradle/AGP current if Android Studio's JBR is upgraded.
- **Site deploy**: push to `main`, and `.github/workflows/pages.yml` assembles and publishes (Pages source = GitHub Actions):
  `/` = `pwa/` (web app), `/android/` = `downloads/android/` (APK download page).
  **Bump `CACHE` in `pwa/sw.js` on every PWA change** so installed copies update. `sw.js` ignores `/android/`.
- **Publishing a new Android version** (friends install from https://abhijeet-pand3y.github.io/workout_app_and_pwa/android/):
  run `powershell -File tools\publish-android.ps1 -Bump`. It bumps versionCode/versionName, builds the release APK,
  and writes `downloads/android/workout.apk` + `version.json`. Then commit + push.
  - The release build is signed with the **debug keystore** (`~/.android/debug.keystore` on this PC), the same key
    as the user's phone install, so APKs update in place. Never change the signing key, or every install would need
    an uninstall (data loss). versionCode must always increase.
  - `.gitignore` ignores `*.apk` except `downloads/android/workout.apk`.
  - The PWA's install hint (browser only, Android UA) links to `android/`. The installed PWA never shows it.
- **PWA tests**: `tools/pwa-test.html` drives the app in an iframe (43 checks). To run it:
  copy `pwa/` to a temp dir, add the test file, serve it with `python -m http.server`, then run
  `chrome --headless=new --dump-dom --virtual-time-budget=20000 http://127.0.0.1:PORT/test.html`.
  `?stop=<step>` + `--screenshot --window-size=390,844` gives phone screenshots.
  The app's `let data` isn't on `window`, so tests use `w.eval('...')` to reach state.
- No node on this machine; python, Chrome, Edge and git are available.

## Future plans (backlog)
- **📌 Estimated 1RM (e1RM)**: discussed and agreed, not built yet. Use **Epley**: `e1RM = weight × (1 + reps/30)`.
  - Show the best e1RM per exercise in Progress with a week-over-week trend, show e1RM next to each set
    and in "Last time".
  - Only full reps count (ignore partials/drops). It's reliable for ~1–12 reps near failure, so flag or
    de-emphasize higher-rep sets. Not meaningful for timed exercises.
  - Rationale: weight×reps is misleading (45×8=360 vs 50×5=250, yet e1RM 57.0 → 58.3 is a ~2% strength gain).
- **RIR / RPE** (optional per-set "reps in reserve" field) for logging subjective effort.
- Ideas mentioned, not committed: rest timer, notifications, home-screen widget (native Android only).
