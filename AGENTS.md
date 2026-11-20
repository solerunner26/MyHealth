# AGENTS.md — working agreement for the My Health Android app

> **Read this file first.** It is the single source of truth for every AI tool (Codex, Claude Code,
> Gemini CLI, Cursor, Cline…) and for human contributors. `CLAUDE.md` and `GEMINI.md` only point here.

---

## 1. What we are building

**My Health** — a private, offline-first Android health-record app.

* It ingests medical documents (lab reports, prescriptions, pharmacy bills, scans) as **PDF, JPG,
  JPEG or PNG**, extracts the values **on-device**, and files them chronologically.
* It keeps a **longitudinal record over the last 3 years**: haemoglobin from a report on 20.02.2026
  is one table row, the 30.05.2026 report appends the next row for the same analyte.
* It tracks **vitals** (BP, weight, pulse, SpO2, glucose, temperature…), **medicines** (read from
  bill images or typed in) and **treatments/procedures**.
* It optionally mirrors **wearable data** (Fitbit / Google Health) through **Health Connect** —
  sleep, steps, heart rate, HRV, SpO2, weight — giving daily data between lab visits.
* It answers **"what should I focus on?"** with rule-based, explainable focus cards and a health
  score, phrased as "discuss with your doctor", never as a diagnosis.
* It can produce a **doctor summary PDF/CSV** so the patient never carries paper again.
* It is **standalone and offline**. No account is required; Google sign-in is only a label.

**Non-negotiable product rules**

1. **Offline first.** No feature may require the network. The only network use allowed is the
   optional Google sign-in handshake.
2. **Data stays on the device.** No analytics SDK, no uploading crash reporter, no cloud sync, no
   ads. `allowBackup=false`, attachments encrypted at rest.
3. **One hue.** Single deep-pine accent + warm neutrals. Never add a second hue; convey severity
   with icon, label, fill weight and wording (colour-blind safe).
4. **Every screen is localized** in **English (default), Hindi (hi) and Gujarati (gu)**.
5. **Medical safety.** Extraction output is reviewed/edited by the user before saving; every insight
   carries its rationale and a disclaimer.

---

## 2. Session protocol (house rules — mandatory)

1. **Start of session:** read `TASKS.md` and `HANDOFF.md`; then `git status` and `git log -5`.
2. **Claim before working:** move exactly one task in `TASKS.md` to *In progress* as
   `- [~] T-0xx Title (claimed by: <agent>, <ISO timestamp>)` **and commit that change immediately**.
   Never touch a task someone else has claimed. A claim older than 24 h is abandoned — re-claim it
   and say so in `HANDOFF.md`.
3. **One task at a time.** Small diffs. Commit with the task id: `T-012: parse mmHg pairs`.
4. **Definition of done:** `./gradlew :app:assembleDebug` **and** `./gradlew :app:testDebugUnitTest`
   pass, new behaviour has at least one unit test, and any new string exists in **all three**
   language files.
5. **End of session:** set the task done/blocked in `TASKS.md`, append a dated note to `HANDOFF.md`
   (what changed, what's left, gotchas), commit both.
6. **New work you discover** goes into *Pending* — do not silently grow your current task.
7. **Never commit to the default branch.** Use your own branch or a git worktree.
8. **Never run two agents/builds in the same checkout.** One at a time, or one worktree per agent (S7).

---

## 3. Toolchain (verified working in this repo — do not "modernize" blindly)

| Piece | Version / fact |
| --- | --- |
| Gradle | **9.9.2** (wrapper is committed) |
| JDK | **21** (Temurin); AGP 9 needs 17+ |
| Android Gradle plugin | **9.5.1** |
| Kotlin | **2.4.x**, supplied by **AGP built-in Kotlin** |
| Compose Material 3 | `compose-bom` **2026.11.01** |
| compileSdk / targetSdk | **37** (Android 17); runs on Android 14–17 |
| minSdk | **26** |
| namespace / applicationId | `com.solerunner.myhealth` |

### The one thing that bites: AGP 9 built-in Kotlin

AGP 9 ships Kotlin support itself. Adding the old plugin fails with:

> `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.`

So **never add `org.jetbrains.kotlin.android` / KGP.** Module plugins are exactly:

```kotlin
plugins {
    alias(libs.plugins.android.application)   // com.android.application
    alias(libs.plugins.kotlin.compose)        // compiler plugin: Compose
    alias(libs.plugins.kotlin.serialization)  // compiler plugin: kotlinx.serialization
}
```

Kotlin options still use the standard extension AGP installs:
`kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.

Other AGP 9 differences already handled: `buildConfig` defaults to **false** (we opt in),
`android.defaults.buildfeatures.buildconfig` was removed, `kotlinOptions { }` is gone, default Java
level is 11 (we pin 17).

**No KSP, no kapt, no Hilt, no Room.** Persistence is hand-written SQL over SQLCipher (S4) — zero
annotation-processor risk. kotlinx-serialization is used for backup/export payloads.

SDK packages: `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools` (AGP can auto-install
once licences are accepted in `$ANDROID_HOME/licenses`).

---

## 4. Architecture

```
app/src/main/java/com/solerunner/myhealth/
├── MyHealthApplication.kt   # manual DI (AppContainer) + startup work
├── MainActivity.kt          # single activity, edge-to-edge, locale + theme aware
├── core/util/               # date/decimal/text helpers
├── core/security/           # keystore vault key, PIN hashing, secure prefs, app lock, audit log
├── domain/model/            # pure Kotlin models (no android.* imports)
├── domain/lab/              # biomarker catalog, unit conversion, lab parser, report date parsing
├── domain/medicine/         # pharmacy-bill parser
├── domain/vitals/           # vitals extraction from report text
├── domain/analytics/        # trends, statistics, health score, focus/insight engine
├── domain/repository/       # repository interfaces (implemented in data/)
├── data/db/                 # SQL engine abstraction, schema, DAOs, mappers
├── data/import/             # PDF text -> PDF raster -> ML Kit OCR -> parser pipeline + vault
├── data/healthconnect/      # Health Connect client, permission set, daily aggregation
├── data/export/             # doctor-summary PDF, CSV, encrypted backup codec
├── data/prefs/              # settings repository (theme, language, security, sync)
├── design/                  # theme tokens + reusable M3 component library
└── ui/                      # one package per screen + ViewModels + navigation
```

Rules:

* `domain/` is **pure Kotlin** (JVM-unit-testable, no `android.*`). Parsers, trend analyser and
  insight engine live there and carry the heaviest tests.
* `data/db` never leaks cursors or SQL upward — DAOs return domain models.
* ViewModels expose immutable `UiState` through `StateFlow`; Compose screens are stateless and take
  state + callbacks.
* Repositories are interfaces in `domain`, implementations in `data`, wired in `AppContainer`.

---

## 5. Build, test, run

```bash
./gradlew :app:assembleDebug             # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest         # JVM tests: parsers, analytics, insights, i18n, security
./gradlew :app:assembleDebugAndroidTest  # compiles instrumented/Compose tests (no device needed)
./gradlew :app:lintDebug
./gradlew :app:assembleRelease           # minified; needs signing props (below)
```

Signing (never commit secrets):

```bash
./gradlew :app:assembleRelease -PMYHEALTH_KEYSTORE=/abs/keystore.jks \
  -PMYHEALTH_KEYSTORE_PASSWORD=… -PMYHEALTH_KEY_ALIAS=… -PMYHEALTH_KEY_PASSWORD=…
```

Optional Google sign-in: `-PMYHEALTH_GOOGLE_WEB_CLIENT_ID=<id>.apps.googleusercontent.com`. Without
it the app builds and runs fine and offers "continue without an account".

Headless verification loop (no KVM/emulator available in sandboxes): `assembleDebug` +
`testDebugUnitTest` + `assembleDebugAndroidTest` + `lintDebug`. Instrumented tests are written and
compiled here but executed on a machine with a device (`:app:connectedDebugAndroidTest`).

---

## 6. Conventions

* **Strings:** all user-visible text is a resource — `values/strings.xml` (English, default),
  `values-hi/strings.xml`, `values-gu/strings.xml`. Keys are `snake_case` with feature prefixes
  (`home_`, `records_`, `trends_`, `meds_`, `insight_`, `settings_`…). Never hardcode UI text; never
  leave a language file behind.
* **Numbers/dates:** store `Double` + canonical unit; format through `core/util/`. Dates stored as
  ISO-8601 `epochDay`/`epochSecond` (UTC), rendered in device locale as `dd MMM yyyy`.
* **Units:** canonical unit per analyte in `BiomarkerCatalog`; parser stores canonical value and
  keeps the printed value/unit for traceability.
* **Theme/language:** `isSystemInDarkTheme()` is read only in the theme entry point; the user's
  `ThemeMode.SYSTEM|LIGHT|DARK` setting drives it. Language switching goes through `LocaleController`.
* **Accessibility:** 48 dp touch targets, `contentDescription` for meaningful icons, charts expose
  their data via `semantics`, never information by colour alone.
* **Docs:** architecture, data model, design system, privacy, testing each have a file in `docs/`.
  Behaviour change ⇒ same-commit doc update.

---

## 7. Working in parallel

Two agents must never share one checkout. Either go sequentially (default) or use worktrees:

```bash
git worktree add ../myhealth-<agent> -b feat/<agent>-<task>
```

The claims in `TASKS.md` are the mutex; a claim older than 24 h can be taken over with a note in
`HANDOFF.md`. Rebase, satisfy S2's definition of done, then merge into the integration branch.

---

## 8. Project-manager skill

`.skills/project-manager/SKILL.md` documents the helper commands ("what's pending", "claim next

task", "hand off", "review last agent's work") that wrap the rules above. When a tool ignores
skills, this file still governs.
