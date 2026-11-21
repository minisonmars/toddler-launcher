# Toddler Launcher

A simple, locked-down home screen for a toddler. The main screen shows only the
apps a parent has allowed. Everything else sits behind a PIN- or
pattern-protected parent area.

## What it does

- **Toddler screen**: big app icons in a grid on a colourful or photo wallpaper.
  Extra apps go onto more pages, with big arrows (or swipe sideways).
- **Parent button**: a gear in one corner. A tap does nothing; hold it (3 seconds
  by default, a yellow ring fills up) and enter the parent code.
  The code starts as **0000**.
- **Parent area**:
  - Lock-down checks at the top: a tick or cross for each setup step (home app,
    Toddler Guard, background use, updates, own code, apps chosen) with a button
    to fix any that have been switched off.
  - Choose the allowed apps and their order.
  - Layout: columns, rows, icon size, app names on or off, screen direction,
    hide the status and navigation bars, keep the screen on.
  - Wallpaper: colour presets or your own photo (optionally darkened).
  - Parent button corner and hold time.
  - PIN or pattern, plus a recovery word for a forgotten code.
  - Protection: Toddler Guard on or off, pause protection (15 minutes,
    1 hour, or until switched back on), optional screen pinning.
  - App updates: install the latest version or pick any earlier one.
  - Backup and restore of settings to a file.
  - Use your normal home screen for a while (15 minutes, 1 hour, or until you
    open Toddler Launcher from its app icon): protection pauses and Home goes to
    the home app you pick. Handy for system updates.
  - Switch back to your normal home app permanently, or uninstall.
- **Setup checklist** (shown on first start, and from the parent area): ticks
  each lock-down step as it's done.

## How the lock-down works (and its limits)

This is a normal app, not a "device owner" app (that needs a PC and a
factory-reset device), so it uses what ordinary Android apps can do:

1. **Default home app**: the Home button always comes back to the toddler screen.
2. **Toddler Guard** (an accessibility service): when any app that isn't allowed
   comes to the front (Settings, recent apps, the normal home screen, the Play
   Store, the Google app and so on), it sends the child straight back. If the
   notification shade or quick settings are pulled down it closes them.
   It also puts an invisible strip over the top edge of the screen so a swipe
   down there does nothing ("Block pulling down" in the parent area). Some
   phones still let the shade through, in which case it is closed straight away.
   It stands aside while you're in the parent area (for up to 15 minutes at a
   time) and while protection is paused.
3. **Screen pinning** (optional): locks the toddler screen itself in place.
   Android only lets one app be pinned, so opening an allowed app unpins it and
   it pins again on return, with Android's own confirmation each time.

What it can't stop: the power button menu, restarting into safe mode, and
anything an allowed app itself lets the child reach (for example a "rate us"
link that opens the Play Store will just bounce back home).

On Android 13 and newer, Android greys out accessibility for apps installed from
a browser. Fix: Settings > Apps > Toddler Launcher > three dots > Allow restricted
settings, then switch Toddler Guard on. Xiaomi, Redmi and POCO phones also need
Autostart switched on for the app (the setup screen has a button).

Forgot the code with no recovery word? Restart the device in safe mode (this
switches the guard and launcher off), then Settings > Apps > Toddler Launcher >
Storage > Clear data.

## Install

1. On the device, open the [Releases page](https://github.com/minisonmars/toddler-launcher/releases)
   and download the newest `ToddlerLauncher-v….apk`.
2. Open it and allow your browser to install apps if asked.
3. Open Toddler Launcher and follow the setup checklist.

Later updates: Parent area > App updates (or, for now, the download button on
the toddler screen). Android won't install an older version over a newer one,
so going back means: back up settings, download the older APK, uninstall,
install it, restore settings.

## Building

Builds run only in GitHub Actions (`.github/workflows/build.yml`) on every push to
`main`: JDK 17, Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24,
compileSdk/targetSdk 34, minSdk 26 (Android 8.0). Each successful build publishes
a GitHub Release `v0.1.<run number>` with the APK. A fixed debug keystore
(`app/debug.keystore`) signs every build so new versions install over old ones.

## Layout

```
app/src/main/java/com/toddlerlauncher/
  MainActivity.kt       toddler home screen (grid, pages, wallpaper, parent button)
  ParentActivity.kt     parent area
  AppPickerActivity.kt  choose and order the allowed apps
  SetupActivity.kt      first-run setup screen
  Checklist.kt          lock-down checks shared by setup and the parent area
  GuardService.kt       Toddler Guard accessibility service
  Gate.kt               PIN and pattern prompts, changing them
  PatternView.kt        3 by 3 pattern pad
  Settings.kt           saved options, PIN/pattern hashes, backup JSON
  Device.kt             checks and shortcuts to Android settings
  Apps.kt               app list, launching, app tiles
  Updater.kt            GitHub releases list, download and install
  InstallReceiver.kt    install result
  Icons.kt              icons drawn in code, hold-to-open button, page dots
  Ui.kt                 colours and row builders for settings screens
app/src/main/res/       launcher icon, accessibility service config, strings
```

UI is built in code (no XML layouts, no Compose) and icons are drawn in code.
