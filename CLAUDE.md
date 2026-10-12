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
.\gradlew.bat signedRelease      # → app\build\outputs\apk\release\Lifeline-v<versionName>.apk (needs keystore.properties)
```

Publish only the `signedRelease` output. Plain `assembleRelease` produces an unsigned APK.

There is no test suite. The SDK path lives in `local.properties` (git-ignored). An emulator AVD `Medium_Phone_API_35` exists at `%LOCALAPPDATA%\Android\Sdk`.

Dependency versions are pinned to what's already in the local Gradle cache (AGP 8.7.3, Gradle 8.9, Kotlin/Compose compiler 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.06.00, Room 2.6.1, Coil 2.6.0). Only the `android-34` platform is installed, so `compileSdk`/`targetSdk` are 34. Bumping either needs SDK/cache downloads.

## Release signing

v1.0.0 and v1.1.0 shipped signed with this PC's original debug key (cert SHA-256 `11e5…0bcc`). The release key (`0c13…42a1`, alias `lifeline`) took over through APK Signature Scheme v3 key rotation.

- `signedRelease` (in `app/build.gradle.kts`) runs `apksigner sign` with the old key, then `--next-signer` with the new key, plus `--lineage` and `--rotation-min-sdk-version 28`. Android 9+ verifies the new key through v3; Android 8.x verifies the old key through v2. **Every release needs both keystores and the lineage.** Never sign a release with only one key, or existing installs can't update.
- The secrets live outside the repo, in `%USERPROFILE%\.lifeline-signing\`: `lifeline-release.jks`, `legacy-debug.keystore` (a copy of the old debug key), `lineage.bin`, and `keystore.properties.backup`. The git-ignored `keystore.properties` at the repo root points to them. Losing either key means existing users can never update again, so the folder must stay backed up.
- To check a build: `apksigner verify --print-certs --min-sdk-version 26 --max-sdk-version 26 <apk>` should show `11e5…`, and the same check at 28 should show `0c13…`.

## Architecture

Code is in `app/src/main/java/com/lifeline/app/`.

- **Alarm loop (`alarm/Alarms.kt`):** `AlarmScheduler.scheduleNext()` arms exactly one `setAlarmClock` alarm for the next top-of-hour inside the user's window. `AlarmReceiver` re-arms the following hour first. If no entry exists for the fired hour, it calls `raise()`, which stores a `PendingAlarm` in `Prefs` and posts an ongoing `FLAG_INSISTENT` notification with a full-screen intent to `AlarmActivity`. Insistent means the sound loops until the notification is cancelled. `BootReceiver` re-arms after reboot, update or clock change, and re-posts an unanswered alarm. The app also re-arms in `LifelineApp.onCreate` and whenever settings change.
- **Resolving an alarm:** `CheckInForm` (`ui/CheckIn.kt`) is the only thing that calls `AlarmScheduler.resolve()`, after inserting an entry. That clears `pending` and cancels the notification. In ALARM mode the form has no cancel button and `AlarmActivity` blocks Back. `MainActivity` relaunches `AlarmActivity` on resume while `pending` is set. Mute re-posts the same notification on the silent channel. Channel sounds are immutable once created, which is why there are two channels.
- **State:** Room (`data/Entry.kt`, schema v2 with `MIGRATION_1_2`) has three tables:
  - `entries`: `date` (ISO yyyy-MM-dd local), `hour`, `tag`, `photoPath?` and `keptPlan?` (null = the hour had no plan when it was logged).
  - `plans`: morning plan, keyed by (`date`, `hour`).
  - `reviews`: night review, one per `date`.

  `Prefs` wraps SharedPreferences for `Settings` (alarm window, which may wrap past midnight; sound, `dailyGoal`, `photoForProductive`, `rewardSound`) and `pending` (date, hour and the coach line). Both are exposed as `StateFlow`s. `trackingSince` is set on first run, so hours before it never count as missed.
- **Hour accounting (`data/Stats.kt`):** all scoring is per *hour*, not per entry. `Stats.summarize()` turns a day's entries and plans into `HourStat`s: PRODUCTIVE / ENTERTAINMENT by majority, ties going to the most recent check-in; MISSED for window hours that ended unlogged after `trackingSince`. Lost = entertainment + missed. Plan kept = the latest non-null `keptPlan` in the hour; a missed planned hour counts as not kept. A day is won when productive > lost, and the streak and calendar color are built on that. `WeekReport` (Mon–Sun) holds the grade (A ≥ 75% productive … F), best and worst day, top time-waster and the comparison with last week. `Coach` writes the adaptive alarm line and the evening warning. `data/Repo.kt` wraps these for receivers and widgets; Compose screens call `Stats` directly on `observeBetween` flows.
- **Coach schedule:** `AlarmReceiver` handles two actions:
  - `HOURLY`: raises the alarm with `Coach.alarmLine`. Three hours before the last alarm it posts the streak warning; at the last hour it posts the night-review prompt (unless the alarm rang, because then `AlarmActivity` asks for the review after the check-in), and on Sundays the report card.
  - `PLAN_REMINDER`: 30 min before the window starts.

  `Prefs.once()` keeps each warning or report from posting twice. Coach notifications open `MainActivity` with `Actions.PLAN` / `REVIEW` / `REPORT` (see `Actions.kt`).
- **Rewards (`ui/Reward.kt`):** `buildReward()` runs after every insert. A first productive entry in an hour is "+1 REP", a further one is "+1 SET", reaching the goal is "GOAL CRUSHED", and entertainment gets an honest card. `RewardSound` synthesizes the level-up arpeggio with `AudioTrack` (no audio assets). Quotes live in `data/Quotes.kt`; they're all original, and picks never repeat back to back. `AlarmActivity` runs stages: CheckIn → Celebrate → optional Plan + MorningQuote (first 3 window hours with no plan) or Night review (last alarm).
- **Photos (`data/Photos.kt`):** camera captures go to `cache/captures` via a FileProvider (`${applicationId}.files`). They're then downscaled to 1600px, rotated upright from EXIF, and saved to `files/photos/`. Deleting an entry deletes its file.
- **Widgets (`widget/Widgets.kt`):** classic `AppWidgetProvider` + `RemoteViews`, not Glance, because Glance isn't in the offline Gradle cache. Both providers render from one `Snapshot` in `Widgets.renderAll()`. The hour strip is a bitmap drawn on a `Canvas`, sized from `OPTION_APPWIDGET_MIN_WIDTH`. Call `Widgets.refresh(context)` after anything that changes today's entries, alarm state or settings; it's already called on insert, delete, `raise`/`resolve`, every alarm fire, boot and settings changes. `updatePeriodMillis` (30 min) covers hour rollovers outside the alarm window. Launchers ignore app font resources in RemoteViews, so widgets use `sans-serif-condensed` bold instead of Bebas. "+ LOG" launches `MainActivity` with `ACTION_LOG`, which opens a manual check-in; while an alarm is pending it opens `AlarmActivity` instead.
- **UI:** single-activity Compose with two tabs, `DashboardScreen` and `CalendarScreen`, plus the manual check-in and setup screens as dialogs. Colors and fonts live in `ui/theme/Theme.kt`: volt `#C8FF00` = productivity, blaze `#FF4D1A` = entertainment, with Bebas Neue (bundled in `res/font`) as the display face. `dayColor()` blends blaze → volt by productive share, with opacity scaled by hours logged ÷ window length.
