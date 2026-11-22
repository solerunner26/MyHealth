# HANDOFF.md — session ledger

Newest entry first. Every agent appends `## <ISO date> — <agent> — <task id>` with: what changed,
what is left, gotchas. Keep it short and factual; the code is the source of truth.

---

## 2026-11-22 — cline (session 4) — on-device crash fix

### Symptom
On a Google Pixel 10 Pro the debug APK crashed continuously before onboarding. No device was available
here, so the launch path was exercised with a real Robolectric activity launch plus targeted regression
tests.

### Root causes found and fixed
1. **Observers queried the encrypted database before it opened.** `MyHealthApp` collected the profile
   flow regardless of `ready`, and `MyHealthDatabase.observe` mapped the initial change tick straight to a
   query, which threw `IllegalStateException("Database is not open yet")` inside a `LaunchedEffect` and
   killed the process. The initial composition always races the background open.
   Fix: `MyHealthDatabase` now exposes an `openState` and `observe` only queries after open (emitting as
   soon as it opens); `MyHealthApp` gates the profile collection on `ready`. Regression tests:
   `DatabaseGateTest` (no query before open, emits after open, silent after close).
2. **Keystore failures crashed the main thread.** Two problems: the StrongBox fallback reused the same
   `KeyGenParameterSpec.Builder`, so the "fallback" still requested StrongBox and threw outside the guard;
   and every `SecureStore` write was unguarded. `ON_STOP` (backgrounding) and PIN creation therefore
   crashed on devices where StrongBox is advertised but unavailable, producing a crash loop at onboarding.
   Fix: a fresh TEE spec for the fallback, all secure writes fail closed (return false, never throw),
   `AppLockManager.setPin` reports failure and onboarding shows a localized error, lifecycle callbacks are
   wrapped defensively.
3. **A transient Keystore failure could brick an existing database.** `databasePassphrase()` generated a
   new passphrase when the stored one could not be decrypted, overwriting the only copy. It now throws
   `VaultUnavailableException` and the UI offers a retry instead of destroying access.
4. `DocumentReader.close()` initialised ML Kit just to close it; it now closes only an existing engine.
   App shutdown is guarded.
5. **Diagnostics for the next round:** a crash logger writes `filesDir/diagnostics/last-crash.txt`
   (device, app version, stack trace) before the process dies; Settings → About offers "Share last crash
   log", and the database error screen shows the cause with a Retry button. Nothing is uploaded.

### Follow-up: the real device error (versionCode 3)
The device then reported: **"unknown error (code 0): Queries can be performed using SQLiteDatabase query
or rawQuery methods only."** That is Android rejecting `execSQL("PRAGMA journal_mode = WAL")`: the
statement returns a row (the resulting mode), so it must go through `rawQuery`. This was the next failure
behind the launch crash.

* `SqlcipherDbFactory` now configures a new database through an internal `DbConfigurator` split: plain
  statements use `execSQL`, row-returning PRAGMAs use `rawQuery`. `DatabaseConfigurationTest` pins the
  split so it cannot regress.
* WAL is treated as optional: if a device refuses it, the app logs and continues (slower, still correct).
* If an older build left a half-initialised database, `AppContainer` quarantines it once
  (`myhealth.db.unusable-<ts>`) and starts clean. A database that opened successfully before is never
  touched, because a `db_initialized` marker is stored after the first successful open.
* Evidence: **51 unit tests, 0 failures**, `assembleDebug` successful, APK **versionCode 3** uploaded.

### Evidence
- `AppLaunchTest` launches the real `MainActivity` with the real `Application` under Robolectric; it failed
  on the unguarded Keystore write before the fix and passes now.
- `DatabaseGateTest` proves observers cannot query before open.
- `:app:testDebugUnitTest` → **50 tests, 0 failures**; `:app:assembleDebug` → **BUILD SUCCESSFUL**
  (versionCode 2; APK re-uploaded to `preview-rare-ui-2026-11-22`).
- Still no physical device: the APK must be re-tested on the Pixel. If it crashes again, Settings → About →
  Share last crash log will contain the exact stack trace.

---

## 2026-11-22 — cline (session 3) — CHECKPOINT: Rare UI redesign + reminders + hi/gu

### Verified state (evidence)
- `:app:assembleDebug` → **BUILD SUCCESSFUL**; `app/build/outputs/apk/debug/app-debug.apk` (79.8 MB).
- `:app:testDebugUnitTest` → **BUILD SUCCESSFUL, 42 tests, 0 failures** (adds `VitalExtractorTest`,
  `PinHasherTest`, `I18nParityTest` to the earlier six classes).
- Still **no device/emulator**: the redesign is verified by compilation and unit tests only.

### Design system: Rare UI, reimplemented natively
- Source inspected: `https://github.com/swamimalode07/rare-ui` (Next.js/shadcn/Tailwind). License is
  **MIT + Commons Clause + Attribution**. No code was copied, because it is a web library; the visual and
  interaction rules were reimplemented in Compose. Attribution is now visible in **Settings → About**
  (link to rareui.com), in `README.md`, and in `docs/DESIGN_SYSTEM.md`.
- **Tokens** (`design/theme`): new warm-neutral `Palette.Ink*` scale, pine accent kept (the product's
  single-hue rule outranks Rare's orange), radii 6/8/10/14/20, hairline 1dp, motion 120/200/320ms,
  Inter bundled (`res/font/inter.ttf`, OFL text in `docs/licenses/INTER-OFL.txt`), mono digits.
- **Primitives** (`design/rare`): `RareCard`, `RareInset`, `RareSectionLabel`, `RareButton` (4 styles),
  `RareIconButton`, `RareTextField`, `RareSearchField`, `RareSwitch`, `RareCheckbox`, `RareChip`,
  `RareSegmented` (sliding pill), `RareBadge`, `RareStat`, `RareListRow`, `RareTaskRow`, `RareKeyValue`,
  `RareAvatar`, `RareEmptyState`, `RareProgressRing`, `AnimatedCounter`, `RareSnackbarHost`,
  `RareLinearProgress`, `RareScrollProgress`, `RareSkeleton`, `RareSpinner`, `RareSheet`,
  `RareConfirmDialog`, `RareChoiceDialog`, `RareBanner`, `RarePinEntry`, `RareTopBar`, `RarePageHeader`,
  `RareBottomBar`, `RareNavRail`, `Modifier.pressScale`.
- **Screens redesigned**: dashboard (bento hero score + animated counter + focus + vitals + meds + reports),
  records (sticky search, chips, grouped hairline card), trends list + detail, vitals, medicines, insights
  (score domain bars), settings, profile, import picker + review, report detail, trend detail, doctor
  summary, onboarding, unlock (OTP-style PIN slots).
- Old `design/components` names are thin aliases over the Rare primitives, so the whole product inherited
  the new language in one step; `PinPad.kt` was deleted.

### Polish after the first pass
- Bottom bar now applies navigation-bar insets; onboarding shows the offline note again.
- Treatments editor moved from an AlertDialog to the Rare sheet; import progress and error states use the
  Rare spinner and empty state; Settings shows a real "Documents" label for storage.

### Quality pass after lint
- Lint flagged three real errors, all fixed: `LocalDate.ofInstant` needed API 34 (now `Instant.atZone`), StrongBox
  calls needed an API 28 guard, and three composables read `Locale.getDefault()` instead of
  `LocalConfiguration`. `CredentialManagerMisuse` is handled by catching `NoCredentialException` and
  `GetCredentialException` and falling back to the offline path.
- Reminder cancellation now persists the armed time labels, so editing a reminder time cannot leave an
  orphaned alarm behind.
- **Lint is now clean: `:app:lintDebug` reports 0 errors** (remaining warnings are unused resources, UseKtx and
  PluralsCandidate suggestions).

### Medicine reminders (T-034) — manifest debt cleared
- `MedicineReminders` uses **AlarmManager** (exact when the platform allows it, inexact otherwise),
  one alarm per reminder time, re-armed after firing and skipped past the course end date.
- `MedicineReminderReceiver` posts the notification and reschedules; `BootCompletedReceiver` re-arms all
  reminders after reboot or app update. `POST_NOTIFICATIONS` is requested from the medicines screen;
  `SCHEDULE_EXACT_ALARM` added to the manifest; channel created at startup.
- Deviation from the task text: WorkManager was not used. A dose is a precise single moment, and alarms
  survive Doze without keeping the process alive. The dependency remains for future sync work.

### Localisation (T-035) — complete
- `values-hi` and `values-gu` now hold all 439 translatable keys; `I18nParityTest` enforces key and
  format-placeholder parity, so no screen can fall back silently.

### Installable preview and screenshots
- Debug APK attached to the GitHub release
  `preview-rare-ui-2026-11-22` (package `com.solerunner.myhealth.debug`, Android 8+).
- Screenshots are **rendered from the real Compose UI** by `ScreenshotGalleryTest`, a Robolectric test with
  native graphics that draws the activity decor view into a bitmap. No mockups, no screenshot service.
  Images live in `docs/screenshots/` and are attached to the release as well.
- The screenshot test both proves the UI composes without a device and regenerates the images:
  `./gradlew :app:testDebugUnitTest --tests "*ScreenshotGalleryTest"`.

### Gotchas
- `compileSdkMinor = 2` is still required (Compose 1.13 metadata needs 37.1+).
- Never add `org.jetbrains.kotlin.android`; AGP 9 has built-in Kotlin.
- `RareSegmented` and `RareChoiceDialog` take **@Composable** label lambdas. `RarePageHeader` takes
  `onBack`; screens with a back arrow use it instead of hand-rolling a header row.
- Keep new UI on the `design/rare` names; the aliases in `design/components` exist only for older screens.
- Background builds only (`setsid nohup ... &`), one at a time.

### Resume here
1. **T-031** Health Connect sync, **T-033** encrypted backup/restore, **T-042** report page viewer/edit.
2. **T-038** instrumented/Compose tests, then a real-device pass (no emulator here).
3. **T-039** remaining docs (ARCHITECTURE, DATA_MODEL, PRIVACY, SECURITY, TESTING), **T-040** CI,
   **T-041** store readiness.

### Resume commands
```bash
source tools/env.sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

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
