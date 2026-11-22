# TASKS.md — chore chart

Status legend: `[ ]` pending · `[~]` in progress (claimer + ISO time required) · `[x]` done · `[!]` blocked

**Rule:** claim exactly one task by moving it to *In progress* with your name and the current time,
commit that edit **before** you start coding. Never touch another agent's claim (older than 24 h =
abandoned, re-claim with a note in `HANDOFF.md`).

---

## In progress

- [~] T-039 Docs: `BUILD_AND_RELEASE.md` and `DESIGN_SYSTEM.md` are complete; **ARCHITECTURE, DATA_MODEL,
  PRIVACY, SECURITY, TESTING remain** (claimed by: cline, 2026-11-22T08:00Z)
- [~] T-021 Records UI: list, detail, delete and filters are done in the new design; **page viewer, edit
  details and per-report export remain** (T-042)
- [~] T-029 Settings: appearance, language, security, data and about are done; **encrypted backup/restore
  remains** (T-033)

## Pending

### Next up (resume here)
1. **T-031** Health Connect sync.
2. **T-033** encrypted backup/restore.
3. **T-042** report page viewer, edit metadata, per-report export.
4. **T-038** instrumented/Compose tests, then a real-device pass (no emulator in the sandbox).
5. **T-039** remaining docs, **T-040** CI, **T-041** store readiness.

### Connectivity & output
- [ ] T-031 Health Connect: consent flow, permission set, incremental sync worker, daily aggregation, device-dashboard screen — **medium**
- [ ] T-033 Encrypted backup/restore (kotlinx-serialization payload, AES-GCM, SAF file) + secure wipe — **medium**

### Resources & quality
- [ ] T-038 Instrumented + Compose UI tests (auth, dashboard, import review, trends table) — **medium**
- [ ] T-040 CI: GitHub Actions (assembleDebug, unit tests, lint, release build on tag) — **low**
- [ ] T-041 Play Store readiness: R8 rules audit, baseline profile, accessibility pass, store listing copy — **low**
- [ ] T-042 Report detail extras: encrypted page viewer, edit report metadata, per-report CSV/PDF export — **medium**

## Done

- [x] T-000 Toolchain spike: AGP 9.5.1 + built-in Kotlin + Compose BOM 2026.11.01 + compileSdk 37.2 on Gradle 9.9.2/JDK 21 builds a debug APK (by: cline)
- [x] T-001 Foundation: repo scaffolding, Gradle/toolchain baseline, design tokens (by: cline)
- [x] T-002 Design system: Rare UI language reimplemented in Compose (tokens, 30+ primitives, charts, PIN slots, nav chrome) + `docs/DESIGN_SYSTEM.md` (by: cline)
- [x] T-003 Domain model layer (by: cline)
- [x] T-004 Encrypted persistence: SQL engine abstraction + SQLCipher, schema v1, DAOs, mappers, repositories (by: cline)
- [x] T-005 Security core: keystore vault key, AES-GCM vault, PIN (PBKDF2), biometric app lock, secure prefs, audit log (by: cline)
- [x] T-006 Settings repository (theme, language, security flags, auto-lock, Health Connect consent) (by: cline)
- [x] T-007 Locale controller en/hi/gu + activity re-creation (by: cline)
- [x] T-008 Biomarker catalog (~100 analytes) + tests (by: cline)
- [x] T-009 Document pipeline: SAF/camera → PDFBox text → PdfRenderer raster → ML Kit OCR fallback (by: cline)
- [x] T-010 Lab report parser + review model + tests (by: cline)
- [x] T-011 Pharmacy-bill parser + tests (by: cline)
- [x] T-012 Vitals extraction from report text + tests (by: cline)
- [x] T-013 Duplicate/repeat detection: SHA-256 import guard + same-date series merge (by: cline)
- [x] T-014 Trend analyser: latest/previous/delta/%/rate, 3-year window, status + tests (by: cline)
- [x] T-015 Focus engine with rationale/severity + tests (by: cline)
- [x] T-016 Health score with explainable domains + tests (by: cline)
- [x] T-017 Doctor-visit summary model + builder + offline PDF (by: cline)
- [x] T-018 App shell: navigation graph, Rare bottom bar + rail, theming, splash, locale, app-lock gate (by: cline)
- [x] T-019 Onboarding + auth (optional Google sign-in, OTP-style PIN create/confirm, biometric unlock) (by: cline)
- [x] T-020 Dashboard (animated score, focus cards, latest vitals, medicines, recent reports, quick actions) (by: cline)
- [x] T-022 Import flow: pick/capture → progress → review & edit → save (+ duplicate warning) (by: cline)
- [x] T-023 Trends: picker + 3-year table + chart with reference band + stats + CSV export (by: cline)
- [x] T-024 Vitals: manual entry, history, 7-day average, chart (by: cline)
- [x] T-025 Medicines: active/past, add/edit/delete, course dates, reminder toggle + scheduling (by: cline)
- [x] T-026 Treatments: timeline + add/edit/delete + follow-up dates (by: cline)
- [x] T-027 Insights: focus list with evidence + score breakdown + disclaimer (by: cline)
- [x] T-028 Profile: identity, body basics, allergies/conditions, emergency contact (by: cline)
- [x] T-030 Charts: line + reference band + tap tooltip, ring gauge, sparkline, bar chart (by: cline)
- [x] T-032 Doctor summary PDF + CSV export + share intents (by: cline)
- [x] T-034 Medicine reminders: AlarmManager scheduling, notification channel, reminder + boot receivers,
  POST_NOTIFICATIONS request, SCHEDULE_EXACT_ALARM (by: cline)
- [x] T-035 Localization: full `values-hi` + `values-gu` (439 keys) with parity test (by: cline)
- [x] T-036 Branding: adaptive icon, monochrome themed icon, brand mark, splash asset (by: cline)
- [x] T-037 Unit tests: 50 tests across parsers, catalog, trends/focus/score, CSV, PIN hashing, vitals, i18n parity, database gating, activity launch (by: cline)
- [x] T-045 Startup crash hardening: database observers gated on open, StrongBox fallback fixed, secure
  writes fail closed, passphrase never overwritten, crash logger + share action, retry UI (by: cline)
- [x] T-046 Database open on device: `PRAGMA journal_mode` moved from `execSQL` to `rawQuery`, WAL made
  optional, never-initialised database quarantined once, regression test (by: cline)

---

## Blocked

_(nothing blocked; the device-only steps need hardware, see T-038)_
