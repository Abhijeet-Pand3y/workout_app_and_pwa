# Workout (Android)

Personal workout tracker for Android: Kotlin + Jetpack Compose. All data stays on the phone.

## Features
- **Custom split plans**: any number of days, any names (Push 1, Pull 1, Legs 1, Push 2…). Reorder, rename, delete. Multiple plans, one active.
- **Rotation, not calendar**: finishing a day moves "Next up" to the following day and loops back to the start. Train 5×/week and next week continues where you stopped. You can also do any other day (the rotation continues from that one) or change "next" by hand.
- **Saved exercises per day**: these load automatically when you start that day. Add new exercises during a workout and tick *"Also save to <day>"* to keep them, or leave it unticked for a one-off ("extra").
- **Per set**: weight, reps, **partial reps**, and **drop sets** (one or more drops, each with its own weight × reps). "Repeat last" copies a set in one tap. Each exercise shows **last time's** numbers.
- **History**: every workout, editable afterwards.
- **Progress**: week/month view (Mon–Sun weeks). Shows workouts, time, sets, reps, partials, drop sets, volume, split days done, per-exercise breakdown with best weight, and a last-8-weeks/months chart.
- **Backup**: export/import a JSON file (Plans tab → Settings). kg/lb toggle.

## Install on your phone
1. Install **Android Studio** (https://developer.android.com/studio).
2. *File → Open* → select this `Workout` folder. Let Gradle sync; it downloads everything it needs the first time. If it offers to upgrade AGP/Gradle you can accept or skip.
3. On the phone: *Settings → About phone →* tap **Build number** 7× to unlock Developer options, then turn on **USB debugging** (Settings → System → Developer options).
4. Plug the phone in over USB, pick it in the device dropdown, and press **Run ▶**. The app gets installed and stays installed.

To make a standalone APK instead: *Build → Build App Bundle(s) / APK(s) → Build APK(s)*. The file is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and open it to install.

## Web app version (iPhone + Android): `pwa/`
Same features as the Android app, written as a Progressive Web App (plain HTML/CSS/JS, no build step).
Plan files and backups use the same format as the Android app, so they move between the two.

Hosted free on GitHub Pages: **https://abhijeet-pand3y.github.io/workout_app_and_pwa/**
The workflow `.github/workflows/pages.yml` publishes `pwa/` on every push to `main`.
One-time setup: repo → Settings → Pages → Source: **GitHub Actions**.

Install on iPhone: open the link in **Safari** → Share → **Add to Home Screen**. Always open it from the home-screen icon, because Safari and the installed app keep separate data.
After changing the code, bump `CACHE` in `pwa/sw.js` so installed copies pick up the update.

## Code layout
- `app/src/main/java/com/abhijeet/workout/data/`: model + JSON (`Model.kt`), state, rotation and storage (`WorkoutViewModel.kt`), weekly/monthly stats (`Stats.kt`)
- `app/src/main/java/com/abhijeet/workout/ui/`: screens (Workout, History/Progress, Plans) and shared dialogs
