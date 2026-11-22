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
