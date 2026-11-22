# TASKS.md — chore chart

Status legend: `[ ]` pending · `[~]` in progress (claimer + ISO time required) · `[x]` done · `[!]` blocked

**Rule:** claim exactly one task by moving it to *In progress* with your name and the current time,
commit that edit **before** you start coding. Never touch another agent's claim (older than 24 h =
abandoned, re-claim with a note in `HANDOFF.md`).

---

## In progress

- [~] T-035 Localization resources: `values/`, `values-hi/`, `values-gu/` for every key (parity test) (claimed by: cline, 2026-11-22T00:00Z) — **English complete; hi/gu pending**
- [~] T-037 Unit tests: parsers, catalog, trends, insights, score, security, formatters, i18n parity (claimed by: cline, 2026-11-22T00:00Z) — 6 test classes written; run `:app:testDebugUnitTest` for current status
- [~] T-021 Records UI: list + detail + delete done; **page viewer, edit details and per-report export remain** (T-042)
- [~] T-029 Settings: appearance/language/security/data/about done; **encrypted backup/restore remains** (T-033)

## Pending

### Next up (resume here)
1. **T-037** — finish the unit-test pass (vitals, PIN hasher, i18n parity).
2. **T-035** — translate `values-hi` and `values-gu`; parity test enforces key equality.
3. **T-034** — medicine reminders **before any release**: `AndroidManifest.xml` already points at
   `.reminders.MedicineReminderReceiver` and `.reminders.BootCompletedReceiver`; those classes must exist.
4. **T-031** Health Connect sync, **T-033** encrypted backup/restore, **T-042** report page viewer/edit.
5. **T-039/T-040/T-041** docs, CI, Play readiness; then a real-device pass (`connectedDebugAndroidTest`).

### Connectivity & output
- [ ] T-031 Health Connect: consent flow, permission set, incremental sync worker, daily aggregation, device-dashboard screen — **medium**
- [ ] T-033 Encrypted backup/restore (kotlinx-serialization payload, AES-GCM, SAF file) + secure wipe — **medium**
- [ ] T-034 Medicine reminders: WorkManager + notifications + reboot rescheduling + POST_NOTIFICATIONS permission — **medium**
  - **blocker for release:** the manifest already declares the two receivers; implement them or remove the declarations.

### Resources & quality
- [ ] T-038 Instrumented + Compose UI tests (auth, dashboard, import review, trends table) — **medium**
- [ ] T-039 Docs: README, ARCHITECTURE, DATA_MODEL, DESIGN_SYSTEM, PRIVACY, SECURITY, TESTING, BUILD_AND_RELEASE (`BUILD_AND_RELEASE.md` done) — **high**
- [ ] T-040 CI: GitHub Actions (assembleDebug, unit tests, lint, release build on tag) — **low**
- [ ] T-041 Play Store readiness: R8 rules audit, baseline profile, accessibility pass, store listing copy — **low**
- [ ] T-042 Report detail extras: encrypted page viewer, edit report metadata, per-report CSV/PDF export — **medium**
- [ ] T-043 Remaining unit tests: `VitalExtractor`, `PinHasher`, i18n parity, `AppLockManager` — **high**

## Done

- [x] T-000 Toolchain spike: AGP 9.5.1 + built-in Kotlin + Compose BOM 2026.11.01 + compileSdk 37 on Gradle 9.9.2/JDK 21 builds a debug APK (by: cline)
- [x] T-001 Foundation: repo scaffolding, Gradle/toolchain baseline, design tokens (by: cline)
- [x] T-002 Design system component library: cards, section headers, status pills, metric tiles, list rows, dialogs, empty/loading, segmented controls, date picker, line/ring/spark/bar charts (by: cline)
- [x] T-003 Domain model layer (by: cline)
- [x] T-004 Encrypted persistence: SQL engine abstraction + SQLCipher, schema v1, DAOs, mappers, repositories (by: cline)
- [x] T-005 Security core: keystore vault key, AES-GCM vault, PIN (PBKDF2), biometric app lock, secure prefs, audit log (by: cline)
- [x] T-006 Settings repository (theme, language, security flags, auto-lock, Health Connect consent) (by: cline)
- [x] T-007 Locale controller en/hi/gu + activity re-creation (by: cline)
- [x] T-008 Biomarker catalog (~100 analytes) + tests (by: cline)
- [x] T-009 Document pipeline: SAF/camera → PDFBox text → PdfRenderer raster → ML Kit OCR fallback (by: cline)
- [x] T-010 Lab report parser + review model + tests (by: cline)
- [x] T-011 Pharmacy-bill parser + tests (by: cline)
- [x] T-012 Vitals extraction from report text (by: cline)
- [x] T-013 Duplicate/repeat detection: SHA-256 import guard + same-date series merge (by: cline)
- [x] T-014 Trend analyser: latest/previous/delta/%/rate, 3-year window, status + tests (by: cline)
- [x] T-015 Focus engine with rationale/severity + tests (by: cline)
- [x] T-016 Health score with explainable domains + tests (by: cline)
- [x] T-017 Doctor-visit summary model + builder + offline PDF (by: cline)
- [x] T-018 App shell: navigation graph, bottom bar + rail, theming, splash, locale, app-lock gate (by: cline)
- [x] T-019 Onboarding + auth (optional Google sign-in, PIN create/confirm, biometric unlock) (by: cline)
- [x] T-020 Dashboard (greeting, score ring, focus cards, latest vitals, medicines, recent reports, quick actions) (by: cline)
- [x] T-022 Import flow: pick/capture → progress → review & edit → save (+ duplicate warning) (by: cline)
- [x] T-023 Trends: picker + 3-year table + chart with reference band + stats + CSV export (by: cline)
- [x] T-024 Vitals: manual entry, history, 7-day average, chart (by: cline)
- [x] T-025 Medicines: active/past, add/edit/delete, course dates, reminder toggle (scheduling is T-034) (by: cline)
- [x] T-026 Treatments: timeline + add/edit/delete + follow-up dates (by: cline)
- [x] T-027 Insights: focus list with evidence + score breakdown + disclaimer (by: cline)
- [x] T-028 Profile: identity, body basics, allergies/conditions, emergency contact (by: cline)
- [x] T-030 Charts: line + reference band + tap tooltip, ring gauge, sparkline, bar chart (by: cline)
- [x] T-032 Doctor summary PDF + CSV export + share intents (by: cline)
- [x] T-036 Branding: adaptive icon, monochrome themed icon, brand mark, splash asset (by: cline)

---

## Blocked

_(nothing blocked — the device-only steps need hardware, see T-038)_
