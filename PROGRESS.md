# Progress log

Environment: Linux container, JDK 21, Android SDK cmdline-tools 23 (platform 37.2, build-tools 37.0.0), no KVM.
Emulator and physical-device runs are **not available** here. Cold start is verified with Robolectric (SDK 35 runtime).

## Phase 1: toolchain, modules, design system, shell (done)
Commands (from repo root, `ANDROID_HOME=/opt/android-sdk`):
- `./gradlew testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL
  - PaletteTest 4/4, LocalizationParityTest 2/2, HardcodedCopyTest 1/1, ColdStartTest 1/1 (Robolectric)
  - lint: 0 errors (warnings: 24 UnusedResources for strings used in later phases, 1 ObsoleteSdkInt, 1 ConfigurationScreenWidthHeight)

Changed: Gradle wrapper and version catalog; `:domain` (pure JVM), `:data`, `:core:designsystem`, `:app`;
tokens (colour, type, spacing, radii, motion, elevation, stroke); components (buttons, card, badge, list row, metric tile,
banners, disclaimer, avatar, empty/error/offline/loading skeleton, progress, text field, chips, switch row, choice field,
dialog, top bar, bottom bar, nav rail, band chart, legend, score ring); hardened manifest (no backup, no internet,
data-extraction rules, locale config); app-private crash log; EN/HI/GU strings.

## Phase 2: domain models and parsers (done)
- `./gradlew assembleDebug testDebugUnitTest :domain:test lintDebug` → BUILD SUCCESSFUL, 43 tests, 0 failures; lint 0 errors
- Domain: models (FamilyMember, Report, Attachment, LabResult, Vital, DailyActivity, Medicine, DoseLog, Treatment);
  analyte catalogue (≈60 analytes, aliases, canonical units, conversions, sex-aware ranges, critical limits,
  direction, monitoring intervals); unit normaliser; parsers (text normaliser, Indian number grouping, dates, lab lines,
  vitals, prescriptions, document type, patient and lab name); value formatter.
- Tests: units/catalogue 4, dates 4, lab lines 16, vitals/medicines 3, full reports 6, formatter 2.

## Phase 3: encrypted persistence (done)
- `./gradlew assembleDebug testDebugUnitTest :domain:test lintDebug` → BUILD SUCCESSFUL, 64 tests, 0 failures; lint 0 errors
- `aapt2 dump permissions app-debug.apk` → no INTERNET / network permissions; `libsqlcipher.so` packaged for 4 ABIs
- New tests: SecurityTest 6 (GCM, PBKDF2, key manager fail-closed paths, vault AAD binding/wipe), DatabaseTest 10
  (FKs, cascade, upsert-no-cascade, uniqueness, atomic import with vault cleanup, duplicates, flows, reopen,
  lost key, erase), MigrationTest 3 (v1→v2 with data, newer schema refused and kept, snapshots),
  ColdStartTest 3 (clean install, keystore failure → retry recovers, invalidated key → erase → fresh start).
- Not run here: real AndroidKeyStore and SQLCipher native code (no device or emulator). JVM tests use a
  software key wrapper and framework SQLite through the same SupportSQLite code path.

## Phase 4: ingestion and import review (done)
- `./gradlew assembleDebug testDebugUnitTest :domain:test lintDebug` → BUILD SUCCESSFUL, 74 tests, 0 failures; lint 0 errors
- End-to-end (Robolectric, `ImportFlowTest`): a real PDF generated with PdfBox goes through the real text-layer
  extractor → parser → review UI (value edited via `performTextReplacement`) → save → stored values, printed range, lab
  name, encrypted attachment byte-identical → the same file is flagged as a duplicate on re-import; second test:
  saved report visible in the Records list.
- `IngestTest`: magic-byte sniffing; OCR path through the pipeline with a fake OCR engine (ML Kit cannot run on the
  JVM). `ImportDraftTest` (domain): draft ↔ bundle conversion, unit edits, validation, low-confidence exclusion,
  prescription → medicine course.
- Onboarding (welcome + own profile) and the member editor are in place. Cold start now reaches onboarding.
- Not run here: ML Kit OCR on a real image and the camera capture (needs a device); PdfBox rendering for the OCR
  fallback (slow under Robolectric).
