# Medicine Time

**Medicine Time** is an Android app that reminds you and your family to take medicines on time. It works like an alarm clock: it rings reliably even when the phone is locked, the app is closed, or the phone has restarted. It is built for elders, with readable text and large touch targets.

Everything stays on your phone. No account, no internet, and no tracking.

[![Latest release](https://img.shields.io/github/v/release/mrjis/MedicineTime-AndroidApp)](https://github.com/mrjis/MedicineTime-AndroidApp/releases/latest)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84)
![License: MIT](https://img.shields.io/badge/License-MIT-blue)

---

## Features

### Reminders
- **Real alarms.** Uses Android's alarm-clock system, with a notification and a full-screen alarm over the lock screen. It rings until you answer.
- **Survives restarts.** Alarms are re-planned after a reboot, an app update, and a clock or time-zone change.
- **Grouped alarms.** If several medicines are due at the same time, you get one alarm listing all of them.
- **Taken or Snooze.** Mark doses as taken, or snooze for 15, 30 or 60 minutes (editable in Settings). Both are also available as notification buttons.
- **Missed-dose warning.** A large warning card appears on the Today screen when a dose goes unanswered past the missed window (default 2 hours).

### Medicines and schedules
- **Multiple profiles** on one phone, for example to manage a parent's or a child's medicines. Each profile has its own meal times.
- **Flexible schedules:** daily, specific weekdays, or every N days.
- **Phases for tapering and dose changes**, for example 2 tablets for 5 days, then 1 tablet for 5 days, then stop.
- **Per-slot doses**, including decimals (0.5 tablet, 7.5 ml) and spoon units for syrups (1 tsp = 5 ml, 1 tbsp = 15 ml).
- **Meal-linked reminders:** "30 minutes before dinner" follows the profile's dinner time.
- **As-needed medicines** (pain relief, antacid, inhaler). There is no alarm. Press **Take now** to log the dose, with a confirmation, an Undo option, and a "Last taken" time on the card.

### Stock
- Stock is deducted automatically when you mark a dose as taken.
- Low-stock notifications at 7, 5, 3, 2 and 1 days before a medicine runs out, and on the day it runs out. Each fires once per refill.
- An **I bought more** button adds stock and resets the warnings.
- As-needed medicines show the amount left and can warn at a level you choose.

### History
- Timeline and calendar views, with per-day detail.
- An adherence summary (as-needed doses are excluded from it).
- Mistaken as-needed entries can be deleted, and the stock is added back.

### Settings
- Theme: System, Light or Dark.
- Language: English or Bangla (বাংলা).
- Snooze options, missed window, meal times, and alarm ringtone.
- In-app **update check** against GitHub Releases.

---

## Install

1. On your phone, open the [latest release](https://github.com/mrjis/MedicineTime-AndroidApp/releases/latest).
2. Download `MedicineTime-vX.Y.Z.apk` and open it.
3. If Android asks, allow your browser to **install unknown apps**.
4. Open Medicine Time and finish the **Reminder setup** checklist.

**Requires Android 12 (API 31) or newer.**

### Important: finish the reminder setup
Alarms only work reliably if Android allows them. The setup screen walks you through:

| Item | Why |
|---|---|
| Notifications | Without this, no reminder can appear |
| Exact alarms | Rings at the exact minute |
| Show over the lock screen | Opens the alarm screen when the phone is locked |
| Battery: no restrictions | Stops Android from putting the app to sleep |
| Phone brand settings | Xiaomi, Realme, Oppo, Vivo, Samsung and others kill background apps. Turn on **Autostart** and lock the app in the recent apps list. See [dontkillmyapp.com](https://dontkillmyapp.com) |

### Updating
Open **Settings > Check for updates**. If a newer version exists, the app opens the release page so you can download the new APK and install it over the old one. Your data is kept.

> Updates only work between builds signed with the same key. Release APKs from this repository are all signed with the same key.

---

## Privacy

- All data (profiles, medicines, history, settings) is stored **only on your phone**.
- There are no accounts, no analytics, and no ads.
- The only network request the app makes is the optional update check, which asks the GitHub API for the latest release.
- Uninstalling the app deletes its data.

## Not a medical device

Medicine Time is a reminder tool. It does **not** recommend doses, check drug interactions, or replace advice from a doctor or pharmacist. Always follow your doctor's instructions.

---

## Tech stack

Kotlin, Jetpack Compose (Material 3), Navigation Compose, Room, DataStore, WorkManager, and `AlarmManager.setAlarmClock()`. Architecture is MVVM (UI, ViewModel, Repository, Room) with a manual `AppContainer` for dependency injection.

```
com.mrjis.medicinetime
├── data/      Room database, DAOs, repositories, preferences
├── domain/    schedule resolver, stock calculator, dose and history logic
├── alarm/     scheduler, receivers, foreground service, alarm screen, notifications
├── ui/        theme, navigation, screens, view models
├── work/      WorkManager workers (stock check, missed-dose sweep)
├── update/    GitHub release update checker
└── util/
```

## Build from source

1. Install the latest Android Studio.
2. Clone the repository and open it.
3. Let Gradle sync, then run the `app` configuration on a device or emulator running Android 12+.

For signed release builds, see [RELEASING.md](RELEASING.md). Never commit your keystore or `keystore.properties`.

## Roadmap

- **v2:** optional Firebase sync (Google sign-in) and a PDF report for doctors.
- **Later:** expiry date tracking and a home-screen widget showing the next dose.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Contributing

Bug reports and suggestions are welcome through GitHub Issues. Please include your phone model and Android version for alarm problems, because behavior differs between phone brands.

## License

[MIT](LICENSE) © 2026 MD. Jahidul Islam Sujan