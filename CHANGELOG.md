# Changelog

What changed in each version of Toddler Launcher. The newest is at the top.
Each build uses the "Next" section as its release notes on GitHub, then renames it to the new version.

## Next

- Fix: updates still showed Android's "Update?" screen every time. This update asks once more; after that they should go straight in (Android 12 and later)

## v0.1.34

- Updates now install without Android's extra "Update?" screen (Android 12 and later). The first update after this one still asks once; after that the launcher just restarts on the new version. Same for the Toddler Parent app and for updates to apps the launcher installed

## v0.1.33

- Parent area reordered: Apps, Screen time and Usage report at the top; Parent code, Remote access and Protection near the bottom
- Lock-down checks are now part of Protection (renamed "Protection and lock-down checks")
- New Volume section: see the volume now, turn it up or down, mute it or set it, and set the highest volume. Works from the Toddler Parent app and the web page too
- The usage report table has lines between every row and column, so it's easier to read (on the device and in the Toddler Parent app and web page)
- Remote access: each address now has buttons to show its QR code, open the web page in the browser and share the address
- Toddler Parent app: one saved device can now have several ways to connect (different Wi-Fi networks and its private Wi-Fi). Scanning the QR code on another Wi-Fi adds that Wi-Fi to the saved device without the PIN. Rename or remove them under Remote access > Ways to connect
- Pairing the same phone again no longer adds another copy to Paired phones, and old copies are tidied away

## v0.1.31

- Each release now also has ToddlerLauncherPlay-v….apk, the Google Play version to try out. It installs as a separate app next to this one. The normal ToddlerLauncher APK and its built-in updater are unchanged

## v0.1.30

- New Google Play version of Toddler Launcher (coming soon to Play). It's updated by Google Play: the parent area shows what's new and checks Play for a newer version
- Before opening accessibility settings, Toddler Guard now explains what it uses accessibility for and asks you to agree

## v0.1.29

- "Always allowed" apps (Apps and app time limits): they keep working when the day's time is used up, outside the allowed hours and at break time, and their time doesn't count towards the daily limit. They also show on the "Time's up" / "Sleep time" screen so they can still be opened. Toddler Camera is always allowed to start with. Also in the parent app and web page

## v0.1.28

- Fixed (properly this time): screen time, allowed hours and break time now close apps the child is already in. The launcher's own little windows (the pull-down strip, the "parent connected" icon and messages such as "A parent gave 15 more minutes") were being mistaken for the toddler screen, so the rules thought the child was on the home screen. Using the parent app made this happen almost every time
- When the time is up, outside the allowed hours or at break time, every app is treated as not allowed: it closes straight away, back to the toddler screen, and anything playing is paused. Checks every 5 seconds instead of 10
- Toddler Guard status ("Working, checked 3 s ago", or why it's standing aside) in Protection and volume, the parent app and the web page
- After the launcher updates itself the parent area is closed too, so protection is back on straight away

## v0.1.27

- Parent app and remote web page now match the parent area on the device: the same menu of sections with the same icons, colours and lines underneath (red when something needs attention), and inside each section the same headings in the same order
- Parent app and web page: new Lock-down checks (ticks and crosses), Wi-Fi/mobile data/Bluetooth status, updates and device sections; "Now" with extra time and break time stays at the top

## v0.1.26

- Fixed: screen time limits, allowed hours and break time now also stop an app the child is already in. Some phones (Xiaomi, Redmi and POCO especially) froze the launcher while another app was open, which stopped its clock. Toddler Guard now keeps itself awake with a quiet "Toddler Guard is on" notification
- After the launcher updates itself, protection comes straight back on (before, it could stay off for up to 5 minutes)

## v0.1.24

- Automatic update check: the launcher quietly looks for a new version on GitHub (when the parent area opens, and at most every 6 hours otherwise). When there is one, "Updates and backup" in the parent area turns red and says which version, and the update button on the toddler screen turns orange

## v0.1.23

- Parent app: App updates screen like the launcher's (new versions with what changed at the top, then the installed one, older versions behind a button), and it installs updates itself

## v0.1.22

- Parent app: a Connection box at the top of each child's screen shows how you're connected (same Wi-Fi or directly), the address and port, how quickly it answered, the check code, this phone's addresses, and the toddler device's Android version, battery, addresses, paired phones, home app and Toddler Guard status

## v0.1.21

- The parent PIN now has its own big number pad with dots, instead of the phone's keyboard. It opens as soon as the right PIN is in (or press the tick)
- Changing the PIN: the keyboard's Enter key now saves

## v0.1.20

- New Toddler Parent app for your own phone (ToddlerParent APK in each release): finds the toddler device on the same Wi-Fi by itself, pairs once with the parent PIN, then changes settings, apps and limits, gives extra time, starts break time, shows the usage report and adds Wi-Fi networks
- Pairing uses a secret key that only the two devices know; every message after that is locked with it, and the PIN isn't sent again
- "No Wi-Fi? Connect directly": the toddler device makes its own private Wi-Fi and shows a QR code for the parent app to scan and join
- The QR button on the toddler screen is now a "connect a parent's phone" button, shown all the time for testing (switch it off under Parent code and button)
- Parent area > Remote access lists paired phones, so you can remove one

## v0.1.19

- App updates: new versions are listed first, above the version you have

## v0.1.18

- App updates: older versions are now behind a "Show older versions" button

## v0.1.17

- New QR code button on the toddler screen, next to the update button, while remote access is on: tap it to show the code for your phone to scan (it can be hidden under Parent code and button)
- The child's name is now centred on the screen (it sat a little to one side before)

## v0.1.16

- Each section of the parent area now has its own colourful icon
- Child's name: choose the space between letters and the colours (rainbow, pastel, ocean, sunset, forest, candy, or one colour)
- Tap the name and it moves: bounce, wave, wiggle, spin, grow or wobble like jelly (or a different one each time, or nothing)

## v0.1.15

- Your child's name at the top of the toddler screen, in colourful letters (set it in the parent area under Look and layout)
- Apps whose daily time is used up now turn grey, not just faded
- Versions screen: shows your installed version, each newer version with what changed, and older versions in a drop-down
- Changelogs for every version, here and on the GitHub releases page

## v0.1.14

- Proper release builds (smaller, less likely to be flagged by Play Protect)
- One sensitive permission removed: "Keep Toddler Guard awake" now opens Android's battery list

## v0.1.12

- Remote access keeps working while your child is in another app
- Remote connection icon moved to the top, and shown over other apps too

## v0.1.11

- QR code for the remote access address

## v0.1.10

- Remote page: allowed-hours times appear as soon as they're switched on

## v0.1.9

- Remote parent access over the same Wi-Fi: change settings, apps and limits, give extra time, start break time, see the report and add Wi-Fi networks from your phone's browser
- "Break time" lock

## v0.1.8

- Toddler Doodle added to the recommended apps

## v0.1.7

- Recommended apps: install and update your other toddler apps from the parent area

## v0.1.6

- Parent area tidied into a menu of sections, with lock-down checks behind one button
- Daily screen-time limit, per-app limits and allowed hours (weekdays and weekends)
- "Give 15 more minutes today" and a "5 minutes left" warning
- "Time's up" and "Sleep time" screens
- Usage report: last 3 days and a 7-day average
- Media volume cap
- Wi-Fi, mobile data and Bluetooth status with shortcuts to Android's switches

## v0.1.5

- Rows and columns swap when the screen is turned sideways, so icons stay big
- Spare space in landscape moved to the sides

## v0.1.4

- Fixed: updates failed because Play Protect's check was closed by Toddler Guard

## v0.1.3

- Use your normal home screen for a while (15 minutes, 1 hour, or until you return)
- Block pulling down notifications and quick settings with an invisible strip at the top

## v0.1.2

- Setup no longer closes after choosing Toddler Launcher as the home app
- Lock-down checks (ticks and fix buttons) added to the parent area

## v0.1.1

First version.
- Toddler home screen with only the apps you allow, in a grid with pages
- Parent area behind a long-press gear button and PIN (default 0000) or pattern
- Setup checklist: home app, Toddler Guard, background use, updates, PIN, apps
- Toddler Guard sends your child back if a blocked app opens, and closes the notification shade
- Wallpapers, layout options, optional screen pinning
- Built-in updater: install the latest or choose a version
- Settings backup and restore
