# HANDOFF.md — session ledger

Newest entry first. Every agent appends `## <ISO date> — <agent> — <task id>` with: what changed,
what is left, gotchas. Keep it short and factual; the code is the source of truth.

---

## 2026-11-22 — cline (session 2) — CHECKPOINT: full UI slice + verified build

### Verified state (evidence, not assumption)
- `:app:assembleDebug` → **BUILD SUCCESSFUL**; APK at `app/build/outputs/apk/debug/app-debug.apk`
  (78.8 MB debug build).
- `:app:testDebugUnitTest` → **BUILD SUCCESSFUL**, **26 tests, 0 failures**
  (`NumbersTest`, `LabTextParserTest`, `BiomarkerCatalogTest`, `MedicineBillParserTest`,
  `AnalyticsTest`, `CsvExporterTest`).
- No device/emulator here: **the UI has not been exercised on hardware yet**. Instrumented tests do not
  exist yet (T-038).

### What changed in this session
- **Toolchain made reproducible:** `tools/bootstrap-env.sh` (JDK 21 + Gradle 9.9.2 + Android SDK
  37.2/build-tools 37 + proxy truststore), `tools/env.sh`, `docs/BUILD_AND_RELEASE.md`, committed
  Gradle wrapper. `compileSdkMinor = 2` is required because Compose 1.13 AAR metadata needs 37.1+.
- **Design system (T-002):** `design/components/{Surfaces,Rows,Tiles,States,Inputs,DateField,Dialogs,Charts,Gauges}.kt`.
- **Resources/localisation scaffolding:** complete English `strings.xml`, `themes.xml` (+ splash),
  `locales_config.xml`, `file_paths.xml`, `data_extraction_rules.xml`, adaptive + monochrome icon,
  notification icon. `hi`/`gu` files are **still missing** (T-035).
- **App layer:** `MyHealthApplication`, `MainActivity` (locale-wrapped base context, splash, edge-to-edge),
  `di/AppContainer` (manual DI, background DB open), `ui/MyHealthApp` (theme, FLAG_SECURE, lock gate),
  `MainScaffold` (NavHost + bottom bar/rail), onboarding (name/PIN, optional Google Credential Manager),
  unlock screen with PIN + BiometricPrompt.
- **Data layer added:** `SettingsRepository` (encrypted prefs), `AppLockManager`, `DocumentReader`
  (SAF → PDFBox text → PdfRenderer raster → ML Kit OCR), `OcrEngine`, `ImportService` (parse → review →
  save + SHA-256 duplicate guard + vault), `HealthSnapshot` loader (3-year window), `CsvExporter`,
  `ExportService`, `DoctorSummaryPdf`, `DoctorSummaryBuilder`.
- **Screens:** Dashboard, Records + report detail, Import + review, Trends + detail, Vitals (+ add dialog),
  Medicines (+ editor), Treatments, Insights, Profile, Settings (+ change PIN), Doctor summary.
- **Tests:** six JVM test classes covering number parsing, lab text parsing, catalog, bill parsing,
  trends/focus/score, CSV.

### Real bugs found and fixed (worth knowing)
1. `Numbers.parse` recursed forever on 4+ digit integers (`1000` → `parse("1000")` → …) and injected
   digits into words (`o`→`0` on any text). Rewritten with a strict digit-token grammar, OCR fixes only
   for short tokens, and correct Indian (`1,23,456`) / Western grouping.
2. `VitalExtractor.bpPattern` had an under-escaped character class (`[/\]` → unclosed class), crashing
   class initialisation the first time any report was parsed. Now `[/\\]`.
3. Lab-vs-bill detection matched `chemist` inside **biochemistry** → lab reports became pharmacy bills.
   Keyword detection now uses word boundaries and lab markers take precedence.
4. `MEDICINE_COURSE_ENDED` could never fire (it only looked at *active* medicines). Now inspects courses
   that ended in the last 60 days.
5. Printed units lost their case (`g/dl`); conventional casing is restored for traceability (`g/dL`, `/µL`).
6. Pharmacy strength was rendered `500mg`; now `500 mg`.

### Gotchas for the next agent
- **Do not add `org.jetbrains.kotlin.android`** (AGP 9 has built-in Kotlin). Applied plugins are only
  `android.application`, `kotlin.plugin.compose`, `kotlin.plugin.serialization`.
- **`compileSdk = 37` alone is not enough** — keep `compileSdkMinor = 2` and `platforms;android-37.2`.
- **Background builds only:** `setsid nohup … &` then poll the log; foreground Gradle runs get killed at ~30 s.
  One build at a time (8 GiB cap).
- **`.toolchain/` is git-ignored (3 GB).** A fresh checkout must run `tools/bootstrap-env.sh`.
  The wrapper is committed but `validateDistributionUrl=false` was necessary behind the proxy.
- **Manifest debt:** `AndroidManifest.xml` declares `.reminders.MedicineReminderReceiver` and
  `.reminders.BootCompletedReceiver`. Those classes do **not exist yet**; implement T-034 before release.
- **Language debt:** Settings offers हिन्दी / ગુજરાતી, but without `values-hi`/`values-gu` Android falls
  back to English. Do not demo the language switch until T-035 lands.
- **No KVM/emulator:** verify with `assembleDebug` + `testDebugUnitTest` + `assembleDebugAndroidTest` +
  `lintDebug`; never claim instrumented tests ran.

### Resume here
1. **T-037/T-043** — remaining unit tests: `VitalExtractor`, `PinHasher`, i18n parity (the parity test
   will fail until step 2 is done).
2. **T-035** — `values-hi/strings.xml` + `values-gu/strings.xml` for every key (English is complete).
3. **T-034** — reminders; the manifest already references the receiver classes.
4. **T-031** Health Connect, **T-033** encrypted backup/restore, **T-042** report page viewer/edit/export.
5. **T-038/T-039/T-040/T-041** — instrumented tests, docs, CI, store readiness.

### Resume commands
```bash
tools/bootstrap-env.sh        # only if .toolchain/ is absent
source tools/env.sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
In this sandbox a helper already wraps env + Gradle: `/workspace/.toolchain/build.sh :app:assembleDebug`.

---

## 2026-11-20 — cline (session 1) — T-001

### What changed
- Repo scaffolding: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `app/build.gradle.kts` (no KGP — AGP 9 built-in Kotlin),
  `.gitignore`, `app/src/main/AndroidManifest.xml`.
- Design tokens started: `design/theme/{Color,Type,Shape,Theme}.kt` (single-hue palette, spacing,
  chart colours, light + dark schemes).
- Governance: `AGENTS.md`, `TASKS.md` (full chore chart), `CLAUDE.md`, `GEMINI.md`,
  `.skills/project-manager/SKILL.md`.
- **Toolchain proven**: a minimal Compose app with the exact plugin set of this repo builds a debug
  APK with AGP 9.5.1 / Gradle 9.9.2 / JDK 21 / compileSdk 37 / Compose BOM 2026.11.01, and every
  runtime dependency in `libs.versions.toml` (ML Kit OCR, PDFBox-Android, SQLCipher, Health Connect,
  Credential Manager, biometric, WorkManager, Coil 3, kotlinx-serialization) resolves.

### Gotchas for the next agent (important)
1. **Do not add `org.jetbrains.kotlin.android`.** AGP 9 has built-in Kotlin; adding KGP fails the
   build. Keep only `android.application` + `kotlin.plugin.compose` + `kotlin.plugin.serialization`.
2. `android.defaults.buildfeatures.buildconfig` no longer exists in AGP 9; `buildConfig = true` is
   set per-module instead.
3. `compileSdk = 37` resolves to SDK platform `android-37.0` (Android 17 minor-version scheme).
   AGP can auto-install SDK packages after licences are accepted.
4. In a fresh sandbox: install JDK 21 + Gradle 9.9.2 + Android SDK, then export
   `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`. Behind the sandbox TLS proxy, JVM downloads also
   need `/etc/mitm/ca.crt` imported into a truststore and
   `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=… -Djavax.net.ssl.trustStorePassword=changeit`.
   Details: `docs/BUILD_AND_RELEASE.md` (T-039).
5. No KVM/emulator here: verify with `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`,
   `lintDebug`. Do not claim instrumented tests were *run*.
6. Long builds must be detached (`setsid nohup gradle … &`) — the tool kills foreground commands
   after ~30 s.
7. Persistence is intentionally hand-written SQL over SQLCipher (no Room/KSP). Keep it that way.

### What is left
Everything in `TASKS.md` except T-000/T-001. Recommended order for the next session:
T-003 (models) → T-004 (persistence) → T-008 (catalog) → T-010 (lab parser) + tests → T-011 →
T-014/T-015/T-016 → T-002 (components) → T-018 (shell) → T-020…T-029 (screens) → T-035/T-036
(strings en/hi/gu + branding) → T-032/T-031/T-034 → T-037/T-039/T-040.

### Current state of the tree
Compiles: not yet (UI/domain code is being written; `app/src/main` currently holds the design tokens
plus the app shell being added in this session). Nothing has been pushed as broken-on-arrival: every
push so far is expected to keep `assembleDebug` green — verify before pushing.
