# MyHealth

A private, offline-first health record for you and your family. Add lab reports, prescriptions, pharmacy bills and
scans (PDF/JPG/PNG); MyHealth reads them **on the phone**, lets you check every value, and turns the record into
trends and an explainable "what should I focus on" list to discuss with your doctor. It is a record and a
conversation starter, **never a diagnosis**.

- **No network.** The app has no INTERNET permission, no account, no analytics and no crash-reporting SDK.
- **Encrypted at rest.** SQLCipher database and AES-GCM attachment vault, with keys wrapped by the Android Keystore.
  OS backup is disabled.
- **Family members.** Every screen is scoped to the selected member.
- **English, हिन्दी, ગુજરાતી**, light and dark themes, one accent colour; status is shown by icon, label and fill weight.

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
