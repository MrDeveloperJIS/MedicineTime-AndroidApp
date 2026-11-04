# Changelog

All notable changes to Medicine Time are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-04

First public release.

### Added

**Profiles**
- Multiple profiles in one app, with add, edit, switch and delete (with confirmation).
- Colored avatars and per-profile meal times (breakfast, lunch, snacks, dinner).

**Medicines and schedules**
- Medicines with name, form (tablet, capsule, syrup, injection, drop, other), dose unit, stock and notes.
- Schedule types: daily, specific weekdays, and every N days.
- Multi-phase schedules for tapering and dose changes, with a different schedule and dose per phase.
- Per-slot doses with decimal amounts and spoon conversions for syrups (1 tsp = 5 ml, 1 tbsp = 15 ml).
- Clock-time slots and meal-linked slots (before or after a meal by N minutes).
- As-needed medicines with an optional default dose and no alarm.

**Alarms**
- Exact alarms using the system alarm-clock API, with a foreground service that plays the alarm sound and vibration.
- Full-screen alarm screen over the lock screen with large, high-contrast Taken and Snooze buttons.
- One grouped alarm per profile and time, with individual checkboxes for partial intake.
- Taken and Snooze actions on the notification.
- Alarms re-planned after reboot, app update, clock change and time-zone change.
- Reminder setup checklist for notifications, exact alarms, full-screen alerts, battery optimization, and phone-brand background restrictions.

**Daily use**
- Today screen with a profile switcher and a timeline of the day's doses.
- Missed-dose warning card for doses unanswered past the missed window (default 2 hours).
- As-needed "Take now" with a confirmation sheet, an Undo option, and a "Last taken" time.
- Safety-net missed-dose sweep that runs when the app opens and once a day.

**Stock**
- Automatic stock deduction when a dose is taken.
- Days-remaining calculation across all remaining phases.
- Low-stock notifications at 7, 5, 3, 2 and 1 days and on the day stock runs out, each once per refill.
- "I bought more" refill flow, and colored stock badges on the medicine list.
- Amount-left display and an optional low-stock level for as-needed medicines.

**History**
- Timeline and calendar views with per-day detail.
- Adherence summary (as-needed doses are excluded).
- Delete a logged as-needed dose, with the stock added back.

**Settings**
- Theme: System, Light or Dark.
- Language: English or Bangla.
- Editable snooze options, missed window, meal times and alarm ringtone.
- In-app update check against GitHub Releases.

**General**
- Offline-first: all data is stored locally in Room, and alarms never depend on the network.
- Elder-friendly interface that respects the system font scale.
- Adaptive app icon, including a monochrome variant.

### Known limitations
- Some phone brands (Xiaomi, Realme, Oppo, Vivo, Samsung and others) stop background apps by default. Follow the in-app Reminder setup to get reliable alarms.
- The update check opens the release page in the browser. You download and install the APK yourself.
- No cloud sync or backup export yet. Uninstalling the app deletes its data.

[1.0.0]: https://github.com/mrjis/MedicineTime-AndroidApp/releases/tag/v1.0.0