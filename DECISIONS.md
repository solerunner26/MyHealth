# Decisions log

Ambiguities are resolved in favour of preserving user data and privacy.

## D-001 Branch and baseline
Development starts from the README-only initial commit on `claude/elegant-sagan-tkw90w`. No code from other branches or tags
of this repository is used.

## D-002 Toolchain (pinned in `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`)
| Tool | Version | Why |
|---|---|---|
| Gradle | 9.9.2 | Current stable; required by AGP 9.5 |
| Android Gradle Plugin | 9.5.2 | Current stable; built-in Kotlin support (no separate kotlin-android plugin) |
| Kotlin | 2.4.21 | Current stable; Compose compiler plugin ships with Kotlin |
| JDK | 21 (bytecode target 17) | Installed JDK; target 17 keeps compatibility with AGP desugaring |
| compileSdk | 37.2 (Android 17, minor API 2) | Compose 1.13 requires compileSdk >= 37.1 |
| targetSdk | 37 | Android 17 runtime behaviour |
| minSdk | 26 | Product requirement |
| Build tools | 37.0.0 | Matches the platform |

## D-003 Dependencies (each addition justified)
| Dependency | Module | Justification |
|---|---|---|
| kotlinx-coroutines | domain, data, app | Async I/O and Flow-based observable state. Pure Kotlin, so the domain stays JVM-testable |
| Compose BOM 2026.11.01 (ui, foundation, material3) | designsystem | UI toolkit. Material3 is wrapped by `MyHealth*` components and fed only our tokens |
| material-icons-extended | designsystem | Medical glyphs (science, medication, monitor-heart). R8 strips unused icons in release |
| activity-compose, lifecycle-runtime/viewmodel-compose | app | Compose host, lifecycle-aware state collection, ViewModels |
| core-ktx | app, data | Compat helpers (notifications, FileProvider) |
| Robolectric, androidx.test, compose ui-test | tests only | JVM cold-start and UI tests. The container has no KVM, so no emulator |

## D-004 Single-hue palette
One teal hue (HSL 174°) plus true greys. `PaletteTest` enforces it on every colour role, light and dark, together with
WCAG AA contrast. Material's `error` role is mapped to neutral text colours, so a red can never appear. Status uses
icon + label + fill weight (Subtle/Medium/Strong) + stroke weight; charts use filled vs hollow markers.

## D-005 No network permission at all
The spec allows an *optional* sign-in handshake. We ship without one, and the manifest strips `INTERNET` and
`ACCESS_NETWORK_STATE` (`tools:node="remove"`) even when a library (e.g. ML Kit's telemetry transport) merges them in.
This makes exfiltration impossible by construction. The app is fully usable without an account.

## D-006 Width classes without an extra dependency
Bottom bar when the window is < 600dp wide, navigation rail otherwise (same breakpoint as `WindowWidthSizeClass.Compact`).

## D-007 Hardcoded copy
Lint `HardcodedText` covers XML only. `HardcodedCopyTest` scans Compose sources for string literals passed to
Text/label/title parameters and fails the build if it finds any.

## D-008 Printed range beats catalogue range
Lab ranges depend on the method, so a range printed on the report always wins. The catalogue's adult range
(sex-aware where it matters) is used only when no range is printed, and the review screen labels it
`RangeFromCatalogue` so the user knows. Paediatric ranges are not in the catalogue; for children the printed range is
the only reliable source.

## D-009 Parser behaviour
- Deterministic, rule-based, no ML. Alias matching uses the longest alias first, then the earliest position. Words
  before the first number count as the name.
- A list of "ignored terms" (e.g. *Free PSA*, *urine glucose*, *mean platelet volume*) stops a different test from
  being filed under a similar-sounding catalogue analyte.
- A missing or unknown unit is inferred by plausibility against the catalogue range (e.g. WBC 7800 → /µL → 7.8
  10³/µL), always flagged as `UnitInferred` with Medium confidence.
- Numeric dates are read day-first (Indian convention) unless the first part is > 12. Collection date > report date >
  generic date; DOB/expiry dates are ignored; future dates are rejected.
- Unrecognised test lines are not offered as rows (too noisy). The user can add a row manually on the review screen.

## D-010 Latin digits for values
Medical values are always shown with Latin digits and a '.' decimal point in all three languages, as printed on Indian
lab reports. UI text is fully translated.

## D-011 SQLCipher without Room (no annotation processors)
The spec forbids codegen unless the toolchain demands it. Room would need KSP, so persistence uses
`net.zetetic:sqlcipher-android` (justification: encryption at rest) through the `androidx.sqlite` SupportSQLite API
(justification: a stable abstraction that lets JVM tests swap in the framework SQLite) with hand-written DAOs.
- The schema is explicit (`Schema.V1` plus `Migrations.steps`). Versions only move forward. Each version's DDL is
  snapshotted in `data/schemas/<n>.sql`, and `MigrationTest.schemaSnapshotsMatch` fails on drift.
- A migration test builds a real v1 database, migrates it, and checks that its DDL equals a fresh install's.
- `onDowngrade` refuses (`NewerSchema`) and `onCorruption` is a no-op. The framework default *deletes the database*;
  we never delete user data implicitly.
- Upserts are update-then-insert, never `INSERT OR REPLACE`, because REPLACE fires ON DELETE CASCADE.
- `lab_results` has `UNIQUE(member_id, analyte_id, date)`. A new value for the same key replaces the old one, and the
  review screen warns before that happens.

## D-012 Key hierarchy and fail-closed rules
AndroidKeyStore AES-256-GCM key (StrongBox when present, not auth-bound so reminders work after reboot) wraps two
random 256-bit data keys: the SQLCipher passphrase and the attachment-vault key. The wrapped keys live in
`noBackupFilesDir/secure/keys/keys.bin`. The app never regenerates keys over existing encrypted data, and never
falls back to plaintext. Failures map to `SecureFailure` and a dedicated screen offers *Try again* and, after explicit
confirmation, *Erase and start fresh* (not offered for `NewerSchema`).

## D-013 Attachment vault
Each file is AES-GCM sealed with AAD = its random id, so ciphertexts cannot be swapped between records. On-disk names
are random ids; the original file name exists only inside the encrypted DB. Max 30 MB per file. Import writes files
first, then commits all rows in one transaction, and removes the files again if the transaction fails.

## D-014 Preferences outside the database
Theme, language, lock settings and the active member id are needed before the database opens, so they live in
app-private SharedPreferences. They hold no health data and are excluded from backup. The PIN is stored only as a
salted PBKDF2 hash.

## D-015 Ingestion pipeline (dependencies: PdfBox-Android, ML Kit text recognition, navigation-compose)
- **PdfBox-Android 2.0.27.0**: PDF text layer, sorted by position so table rows stay on one line, plus in-memory page
  rendering for OCR. No plaintext temp files: `PdfRenderer` would need a file descriptor, so we don't use it.
- **ML Kit `text-recognition` 16.0.1 (bundled Latin model)**: on-device OCR with no model download. Its telemetry
  transport cannot send anything because the manifest strips INTERNET (D-005). Verified with `aapt2 dump permissions`.
- **navigation-compose 2.10.2**: back stack and argument routing for detail screens. The tab shell stays custom
  (D-006).
- A page counts as "scanned" when its text layer has fewer than 40 letters/digits. Its OCR text is used only when it
  is richer than the layer, so a failed OCR never loses a usable text layer. At most 40 pages, rendered at 200 dpi.
- The file type comes from magic bytes, never from the name or MIME type. The size limit is 30 MB, enforced while
  streaming.
- Camera capture uses `ACTION_IMAGE_CAPTURE` through a `FileProvider` cache path, so no CAMERA permission is needed.
  The capture is overwritten and deleted once it has been imported (encrypted) or abandoned.
- SHA-256 duplicates are flagged on the review screen. Saving again is allowed and leaves a second copy, preserving
  user intent.
- Low-confidence rows start unticked. Values are shown in the canonical unit, with the printed line alongside for
  traceability. The unit picker lists only units the analyte can convert from.
- Analyte names are localised (`strings_analytes.xml`, generated from one table to guarantee parity). Indian lab
  reports print English test names, so Hindi/Gujarati use common transliterations, often with the English
  abbreviation (e.g. "HbA1c (3 महीने की औसत शुगर)").
- If the title is left blank, the report gets the localised document-type label, so ViewModels never hold a Context.

## D-016 Trend semantics
- Latest vs previous: absolute change, % change, and rate per month (canonical units and %, using 30.4375-day months).
  A rate needs two readings on different days.
- Direction is judged against the analyte's direction of improvement. If either reading is out of range, the
  direction is decided by whether the value moves closer to or further from the band. If both are in range, only
  lower-/higher-is-better analytes get a direction, and only for moves ≥ 10%. Moves under 2% are Stable.
- The band shown is the latest printed range, else the catalogue range for the member's sex and age.
- The history window (default 5 years, configurable 1–30) filters views and analytics only. Older data is never
  deleted.
- CSV export is RFC 4180 with CRLF. Cells starting with = + - @ that aren't numbers get a leading apostrophe
  (CSV-injection guard).

## D-017 Focus engine (deterministic; emits typed items, never free text)
| Rule | Trigger | Severity |
|---|---|---|
| Out of range | latest value outside band | High if beyond critical limit; Moderate if ≥ 25% past the bound; else Low |
| Rapid change | worsening, readings ≥ 14 days apart, \|%/month\| ≥ analyte threshold (default 10, Hb/HbA1c 5, eGFR 5) | Moderate at 2× threshold, else Low |
| Overdue monitoring | last test older than the re-test interval (shorter if the last value was abnormal) | Moderate if last value abnormal, else Low |
| Vital alert (last 90 days) | BP ACC/AHA categories, SpO₂, pulse, BMI (derived from weight + height), temperature, home glucose | High/Moderate/Low by band |
| Data gap | no labs ever / none in 12 months; adults without BP in 12 months; no weight in 6 months | Info |
| Medicine issue | same medicine active twice; ≥ 3 missed reminder doses in 7 days; course end passed but not marked finished; active without reminders | Moderate / Low / Info |
| Follow-up | treatment follow-up overdue or due within 14 days | Moderate / Low |
| Wearable trend | 7-day steps < 70% of the prior 4-week baseline; resting HR +7 bpm; sleep < 6 h | Low / Info |
Items are sorted by severity, then date, then key. Every item carries its evidence (dates, values, units, ranges).

## D-018 Health score
Weights: lab key markers 40, vitals 30, monitoring up to date 15, activity 15. Missing components are excluded and
the remaining weights are renormalised, so missing data never counts as good or bad. If less than 40% of the
weight has data, no score is shown ("not enough data"), and the components still explain what is missing. Per-input
points are shown so the user can see why the score is what it is.

## D-019 UI structure
- Tabs: Home (dashboard), Records, Trends, Care, More. Detail screens (report, attachment, trend, insights, import,
  member editor) are navigation-compose destinations above the tab shell.
- The member switcher in the top bar scopes every screen: all ViewModels observe `Session.activeMember` via
  `flatMapLatest`, so switching re-queries everything for the new member and no data leaks across members.
- Search and filters run in the composable on localised text, so searching works in Hindi and Gujarati as well as
  English, aliases and printed names.
- Attachments are decrypted and rendered in memory (PdfBox, 110 dpi, first 30 pages). *Share file* decrypts into
  `cache/share`, warns that the copy is unencrypted, and the folder is wiped on the next start.
- Charts mark out-of-range points as hollow rings with a heavy stroke and in-range points as small filled dots. The
  band is drawn with dashed edges, a legend explains each encoding, and every chart has a text description for
  TalkBack.

## D-020 Dose reminders
- One AlarmManager alarm per medicine, set for its next dose (`DoseSchedule.next`, pure and DST-safe on local wall
  time). When it fires, the notification is shown and the following dose is scheduled. Every unlock, boot, app
  update, clock change and time-zone change rebuilds all alarms from the database (idempotent).
- **No `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`.** Android 12+ denies them by default and Play restricts them to
  alarm/calendar apps. API 26–30 use `setExactAndAllowWhileIdle`; 31+ use `setAndAllowWhileIdle`, which Doze may
  defer by a few minutes. That is acceptable for dose reminders, and documented here.
- Notification actions *Taken*, *In 15 min* and *Skip* are logged as `DoseLog` rows against the scheduled slot (one
  row per slot, latest action wins). Adherence ("taken X of Y in 7 days") and the missed-dose focus rule read them.
- Privacy: `VISIBILITY_PRIVATE` with a generic public version ("Medicine reminder"), so medicine and member names
  never appear on the lock screen.
- Receivers run inside `goAsync` with an 8 s timeout and a catch-all, so they can never crash the process. If
  storage cannot be opened (e.g. Keystore unavailable) they do nothing: fail closed, nothing shown.
- POST_NOTIFICATIONS is requested only when the user turns reminders on. If it is denied, the editor explains how to
  enable it; the setting is kept.

## D-021 Vitals
- Values are stored in canonical units. Entry accepts lb, °F, mmol/L and inches and converts them. Plausibility
  bounds reject typos only.
- BMI is never stored as a separate reading unless one comes from a document; it is derived from each weight and
  the profile height (or the latest height reading), so it updates when the height is corrected.
- Vital bands: ACC/AHA BP categories, SpO₂ ≥ 95, resting pulse 50–100, temperature 36.1–37.4 °C, breathing rate
  12–20, home glucose 70–140. BP charts show systolic solid and diastolic dashed, with square markers.
