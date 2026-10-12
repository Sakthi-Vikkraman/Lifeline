<div align="center">

# LIFELINE

**Every hour, it asks what you're doing. No proof, no peace.**

An Android app that keeps your day honest. An alarm rings at the top of every hour and won't stop until you log what you're actually doing: snap a photo or type it, then tag it as productivity or entertainment.

[**Download the latest APK**](https://github.com/Sakthi-Vikkraman/Lifeline/releases/latest) · [Report a bug](https://github.com/Sakthi-Vikkraman/Lifeline/issues/new) · [License](LICENSE)

</div>

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/alarm.png" width="200" alt="Hourly alarm screen"><br><sub><b>Hourly alarm</b></sub></td>
    <td align="center"><img src="docs/screenshots/dashboard.png" width="200" alt="Dashboard with hour-by-hour log"><br><sub><b>Dashboard</b></sub></td>
    <td align="center"><img src="docs/screenshots/calendar.png" width="200" alt="Calendar colored by productivity"><br><sub><b>Calendar</b></sub></td>
    <td align="center"><img src="docs/screenshots/setup.png" width="200" alt="Setup screen"><br><sub><b>Setup</b></sub></td>
  </tr>
</table>

## Features

- **Hourly alarm that means it.** Rings at the top of every hour in your chosen window, even when the app is closed, after a reboot, or with the phone locked. It wakes the screen and takes it over.
- **Proof to dismiss.** The only way to stop it is to submit a check-in: a camera photo, a gallery photo, or a line of text, plus a tag. Back is blocked, the notification can't be swiped away, and Home leaves it ringing. **Mute** silences the sound but keeps the alarm until you log.
- **Productivity vs. entertainment.** Every entry is tagged, and the whole app is colored by it: neon green for productivity, blaze orange for entertainment.
- **Dashboard.** Your day split hour by hour, with photos, notes, tags and times. Missed hours are flagged so you can fill them in later. Shows totals, the productive/entertainment split, and your **win streak** (days in a row where productivity beat entertainment).
- **Calendar.** Each day shows how many things you logged and is colored from orange to green by how productive it was. The more hours you logged, the brighter the day. Tap any day to open it.
- **Your data stays on your phone.** No account, no server, no tracking. Entries and photos are stored only on the device.

## Install

1. Download `Lifeline-vX.Y.Z.apk` from the [latest release](https://github.com/Sakthi-Vikkraman/Lifeline/releases/latest) on your Android phone.
2. Open it. If Android asks, allow installs from your browser or file manager ("Install unknown apps").
3. Open Lifeline and allow **notifications** when asked.
4. Tap the **setup** button (top right) and check that every permission shows ✓. Tap **FIX** next to anything that doesn't.
5. Tap **TEST ALARM** to see it in action.

**Requirements:** Android 8.0 (Oreo) or newer.

### Permissions

| Permission | Why |
|---|---|
| Notifications | Shows the hourly alarm |
| Alarms & reminders (exact alarms) | Rings exactly on the hour |
| Full-screen notifications | Shows the alarm over the lock screen |
| Run at startup | Re-arms the alarm after you restart your phone |
| Vibrate | Vibrates with the alarm |

Lifeline doesn't request internet, location, contacts or storage access. Photos you take are saved inside the app.

### Tips

- **Pick your hours.** By default alarms ring from 07:00 to 23:00. Change the window in setup, and it can wrap past midnight (for example 20:00 → 02:00).
- **Battery savers.** Some phones (notably Xiaomi, Oppo, Vivo, OnePlus and Samsung) aggressively stop background apps. If alarms arrive late or not at all, set Lifeline's battery usage to *Unrestricted* in Android settings.
- **Log ahead.** Logging an hour from the dashboard before its alarm fires means the alarm won't ring for that hour.

## Build from source

You'll need [Android Studio](https://developer.android.com/studio) (or JDK 17+ and the Android SDK with platform 34).

```sh
git clone https://github.com/Sakthi-Vikkraman/Lifeline.git
cd Lifeline
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
./gradlew installDebug         # install on a connected device or emulator
```

The APK is written to `app/build/outputs/apk/debug/`.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Coil · AlarmManager.

## Contributing

Bug reports, ideas and fixes are welcome.

- **Found a bug?** [Open an issue](https://github.com/Sakthi-Vikkraman/Lifeline/issues/new). Include your phone model, Android version, what you expected and what happened. Screenshots help.
- **Want to fix something?** Fork the repo, make your change on a branch, and open a pull request against `main`. Keep each PR focused on one fix or feature, and describe how you tested it.

Under the [license](LICENSE), forks may only be used to prepare pull requests to this repository. By submitting a contribution, you agree it can be used as part of Lifeline (see Section 4 of the license).

## License

Lifeline is **source-available, not open source.** © 2026 Sakthi Vikkraman R. All rights reserved.

You **may** read the code, install official releases for personal use, open issues, and submit pull requests to improve this repository.

You **may not** copy, redistribute, or republish the code or app, publish modified versions, use it commercially, or use it as a basis to recreate a similar app.

See [LICENSE](LICENSE) for the full terms and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for bundled third-party components.
