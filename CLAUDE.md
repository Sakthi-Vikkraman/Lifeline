# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Product spec: Lifeline

Lifeline is an Android app that keeps the user's daily timeline honest by asking every hour what they're doing.

- **Hourly check-in:** an alarm rings every hour.
- **Dismissal requires proof:** the alarm only stops after an entry is submitted, either a photo of the work or a typed description.
- **Tagging:** every entry is tagged **productivity** or **entertainment**.
- **Dashboard tab:** a day split by hour, with the work logged in each hour.
- **Calendar tab:** each day shows how many works were logged, colored by the productivity/entertainment mix.
- **Theme:** bold, confident and daring, with a gym/fitness feel. The app is meant for the user's boyfriend.

## Commands

Native Android app: Kotlin, Jetpack Compose, Room. Android only, by the user's choice. Run from the repo root on Windows.

```
.\gradlew.bat assembleDebug      # → app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat installDebug       # install on a connected device/emulator (adb)
.\gradlew.bat assembleRelease    # signed with the debug key, so it installs without keystore setup
```

There is no test suite. The SDK path lives in `local.properties` (git-ignored). An emulator AVD `Medium_Phone_API_35` exists at `%LOCALAPPDATA%\Android\Sdk`.

Dependency versions are pinned to what's already in the local Gradle cache (AGP 8.7.3, Gradle 8.9, Kotlin/Compose compiler 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.06.00, Room 2.6.1, Coil 2.6.0). Only the `android-34` platform is installed, so `compileSdk`/`targetSdk` are 34. Bumping either needs SDK/cache downloads.

## Architecture

Code is in `app/src/main/java/com/lifeline/app/`.

- **Alarm loop (`alarm/Alarms.kt`):** `AlarmScheduler.scheduleNext()` arms exactly one `setAlarmClock` alarm for the next top-of-hour inside the user's window. `AlarmReceiver` re-arms the following hour first. If no entry exists for the fired hour, it calls `raise()`, which stores a `PendingAlarm` in `Prefs` and posts an ongoing `FLAG_INSISTENT` notification with a full-screen intent to `AlarmActivity`. Insistent means the sound loops until the notification is cancelled. `BootReceiver` re-arms after reboot, update or clock change, and re-posts an unanswered alarm. The app also re-arms in `LifelineApp.onCreate` and whenever settings change.
- **Resolving an alarm:** `CheckInForm` (`ui/CheckIn.kt`) is the only thing that calls `AlarmScheduler.resolve()`, after inserting an entry. That clears `pending` and cancels the notification. In ALARM mode the form has no cancel button and `AlarmActivity` blocks Back. `MainActivity` relaunches `AlarmActivity` on resume while `pending` is set. Mute re-posts the same notification on the silent channel. Channel sounds are immutable once created, which is why there are two channels.
- **State:** Room (`data/Entry.kt`) stores entries as `date` (ISO yyyy-MM-dd local), `hour`, `tag` and an optional `photoPath`. `observeDayCounts` aggregates per-day tag counts for the calendar and the win streak. `Prefs` wraps SharedPreferences for `Settings` (alarm window, which may wrap past midnight, plus sound) and `pending`, exposed as `StateFlow`s that UI and receivers share.
- **Photos (`data/Photos.kt`):** camera captures go to `cache/captures` via a FileProvider (`${applicationId}.files`). They're then downscaled to 1600px, rotated upright from EXIF, and saved to `files/photos/`. Deleting an entry deletes its file.
- **UI:** single-activity Compose with two tabs, `DashboardScreen` and `CalendarScreen`, plus the manual check-in and setup screens as dialogs. Colors and fonts live in `ui/theme/Theme.kt`: volt `#C8FF00` = productivity, blaze `#FF4D1A` = entertainment, with Bebas Neue (bundled in `res/font`) as the display face. `dayColor()` blends blaze → volt by productive share, with opacity scaled by hours logged ÷ window length.
