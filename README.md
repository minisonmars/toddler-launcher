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
- **Parent area**: a menu of sections, each with its own icon and opening its own screen.
  - **Lock-down checks**: a tick or cross for each setup step (home app, Toddler
    Guard, background use, updates, own code, apps chosen) with a button to fix
    any that have been switched off. The menu line turns red if anything needs attention.
  - **Apps and app time limits**: choose and order the allowed apps; a daily limit
    per app (the app turns grey once its time is used up).
  - **Screen time and allowed hours**: a daily limit for time in apps, "Give 15
    more minutes today", a "5 minutes left" warning, and allowed hours (separate
    weekday and weekend times). Outside them the toddler screen shows "Sleep
    time"; when the daily time is used it shows "Time's up for today".
  - **Usage report**: minutes per app for today, yesterday and the day before,
    plus a 7-day daily average. Only time in toddler mode is counted.
  - **Recommended apps**: other toddler apps made for this launcher (listed in
    `catalog.json` in this repo, so new ones appear without a launcher update).
    Install or update them from their GitHub releases, show them on the toddler
    screen, and open them to set up their own parent settings.
  - **Look and layout**: your child's name at the top in colourful letters (letter spacing, colours, and a bounce, wave, wiggle, spin, grow or jelly wobble when tapped), columns, rows (swapped automatically when sideways), icon
    size, app names, wallpaper (colours or your photo), screen direction, hiding
    the status and navigation bars, keeping the screen on.
  - **Parent code and button**: PIN or pattern, recovery word, gear corner and
    hold time, the temporary update button on the toddler screen.
  - **Protection and volume**: Toddler Guard on or off, pause protection, block the
    pull-down, media volume cap, optional screen pinning.
  - **Wi-Fi, mobile data and Bluetooth**: shows whether each is on and opens
    Android's own switches (apps can't switch them directly since Android 10).
  - **Remote access** (off by default): a small web page on the local network.
    On your phone's browser open `http://<device address>:8080` (shown in the
    parent area) and log in with the parent PIN to change settings, allowed apps
    and limits, give extra time, start "Break time", see the usage report and
    add Wi-Fi networks (as Android network suggestions). Same Wi-Fi or your
    phone's hotspot only. The parent area also shows a QR code of the address to scan. It runs in a foreground service (quiet notification)
    so it keeps working while the child is in another app. A small phone icon
    shows at the top of the screen, over other apps too, while connected. 5 wrong PINs lock it for 5 minutes.
  - **Updates and backup**: shows the installed version, each newer version with
    its changelog, and older versions in a drop-down; save or
    restore settings to a file.
  - **This device**: use your normal home screen for a while (15 minutes, 1 hour,
    or until you open Toddler Launcher from its icon), change the home app,
    uninstall. As the home app, Toddler Launcher starts by itself after a restart.
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

## Changelog

`CHANGELOG.md` lists what changed in each version. New changes go under
"## Next"; each build uses that section as its GitHub release notes, renames it
to the new version and commits the file back. The release notes on GitHub are
kept in step with this file, and the in-app updater shows them.

## Building

Builds run only in GitHub Actions (`.github/workflows/build.yml`) on every push to
`main`: JDK 17, Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24,
compileSdk/targetSdk 34, minSdk 26 (Android 8.0). Each successful build publishes
a GitHub Release `v0.1.<run number>` with the APK. A fixed debug keystore
(`app/debug.keystore`) signs every build so new versions install over old ones.
Builds are release builds (not debuggable), which Google Play Protect treats less
suspiciously.

## Layout

```
app/src/main/java/com/toddlerlauncher/
  MainActivity.kt       toddler home screen (grid, pages, wallpaper, parent button)
  ParentActivity.kt     parent area menu and its sections
  AppPickerActivity.kt  choose and order the allowed apps
  SetupActivity.kt      lock-down checks screen (also shown on first start)
  Checklist.kt          lock-down checks shared by setup and the parent area
  Usage.kt              time per app per day, and the screen-time rules
  GuardService.kt       Toddler Guard: blocks other apps, counts and limits screen time, volume cap
  Gate.kt               PIN and pattern prompts, changing them
  PatternView.kt        3 by 3 pattern pad
  Settings.kt           saved options, PIN/pattern hashes, backup JSON
  Device.kt             checks and shortcuts to Android settings
  Apps.kt               app list, launching, app tiles
  Updater.kt            GitHub releases list, download and install
  Catalog.kt            recommended apps list (catalog.json)
  RemoteServer.kt       remote parent web server, API and Wi-Fi suggestions
  RemoteService.kt      foreground service that keeps the remote page running
app/src/main/assets/remote.html   the remote parent web page
  InstallReceiver.kt    install result
  Icons.kt              icons drawn in code, hold-to-open button, page dots
  Ui.kt                 colours and row builders for settings screens
catalog.json            the recommended apps: name, package, GitHub repo, description
app/src/main/res/       launcher icon, accessibility service config, strings
```

UI is built in code (no XML layouts, no Compose) and icons are drawn in code.
