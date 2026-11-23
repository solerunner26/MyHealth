# MyHealth

A private, offline-first health record for you and your family. Add lab reports, prescriptions, pharmacy bills and
scans (PDF/JPG/PNG/WebP/HEIC); MyHealth reads them **on the phone**, lets you check every value, and turns the record into
trends and an explainable "what should I focus on" list to discuss with your doctor. It is a record and a
conversation starter, **never a diagnosis**.

- **No data leaves the phone.** No account, no analytics, no crash-reporting SDK. The only network use is the
  optional update check against this repository's GitHub releases (HTTPS, nothing about you is sent).
- **Encrypted at rest.** SQLCipher database and AES-GCM attachment vault, with keys wrapped by the Android Keystore.
  OS backup is disabled.
- **Family members.** Every screen is scoped to the selected member.
- **English, हिन्दी, ગુજરાતી**, light, dark and automatic (sunset) modes and eight colour themes; status is
  shown by icon, label and fill weight, never by colour alone.
- **Smart import.** The patient name on a report picks the family member; several files (e.g. a photographed hospital
  file) become one record; every value is reviewed before saving.
- **Mediclaim.** Add a policy from its PDF or photos: details are read on the phone and pre-filled for checking.
  Cover terms, claims and remaining cover, network hospitals from the policy PDF, renewal alerts.
- **Liquid glass look** with a moving gradient background; *Glass effects* and *Animations* can be turned off for
  slower phones (Settings → App settings, or the logo on Home for quick settings).
- **Updates in the background.** A newer GitHub release is offered while you use the app; releases are numbered
  "MyHealth X · build N".
- **Family tools.** Vaccinations with reminders, government/insurance health cards, a family history map, visit
  preparation, automatic encrypted backups to a folder, and sharing one member with another phone (file + QR code).
- **Trend alerts.** Values that keep rising or falling across visits are flagged early, with the readings as evidence.

## Build and test

Requirements: JDK 21, Android SDK with `platforms;android-37.2` and `build-tools;37.0.0`.

```bash
./scripts/verify.sh
```

This runs `assembleDebug`, every JVM/Robolectric test, `lintDebug` (errors fail the build) and compiles the
instrumented tests. On a device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

A release build is signed only when these environment variables are set (never commit a keystore):
`MYHEALTH_KEYSTORE`, `MYHEALTH_KEYSTORE_PASSWORD`, `MYHEALTH_KEY_ALIAS`, `MYHEALTH_KEY_PASSWORD`.

```bash
./gradlew assembleRelease bundleRelease
```

## Modules

| Module | Contents |
|---|---|
| `:domain` | Pure Kotlin: models, analyte catalogue, unit conversion, parsers, trends, focus engine, health score, doctor summary, repository interfaces |
| `:data` | SQLCipher database (hand-written schema, migrations, DAOs), Keystore key management, encrypted vault, encrypted backup |
| `:core:designsystem` | Design tokens and `MyHealth*` components (single-hue palette verified by tests) |
| `:app` | Compose UI, navigation, ingestion (PdfBox + ML Kit), reminders, Health Connect, settings, manual DI |

See `PLAN.md`, `DECISIONS.md` (every dependency and design decision) and `PROGRESS.md` (verification evidence per phase).
