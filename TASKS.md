# TASKS.md — chore chart

Status legend: `[ ]` pending · `[~]` in progress (claimer + ISO time required) · `[x]` done · `[!]` blocked

**Rule:** claim exactly one task by moving it to *In progress* with your name and the current time,
commit that edit **before** you start coding. Never touch another agent's claim (older than 24 h =
abandoned, re-claim with a note in `HANDOFF.md`).

---

## In progress

- [~] T-001 Foundation: repo scaffolding, Gradle/toolchain baseline, design **tokens** (claimed by: cline, 2026-11-20T09:20Z)

## Pending

### Foundation
- [ ] T-002 Design system component library (cards, section headers, status pills, metric tiles, list rows, dialogs, empty/loading states, segmented controls, date picker field) — **priority: high**
- [ ] T-003 Domain model layer (Report, LabResult, Analyte, Measurement, Medicine, Treatment, DailyMetric, Profile, Insight, AuditEvent) — **high**
- [ ] T-004 Encrypted persistence: SQL engine abstraction + SQLCipher impl, schema v1, DAOs, mappers, repositories — **high**
- [ ] T-005 Security core: keystore vault key, AES-GCM attachment vault, PIN (PBKDF2) + biometric app lock, secure prefs, audit log — **high**
- [ ] T-006 Settings repository (theme mode, language, security flags, auto-lock, Health Connect consent) — **high**
- [ ] T-007 Locale controller: en (default) / hi / gu switching on API 26–37 — **high**

### Document intelligence
- [ ] T-008 Biomarker catalog (~120 analytes: aliases, canonical unit, unit conversions, reference ranges, category, direction of improvement) + tests — **high**
- [ ] T-009 Document pipeline: SAF picker/camera → type sniff → PDFBox text extraction → PdfRenderer raster → ML Kit OCR fallback → normalized text + page images — **high**
- [ ] T-010 Lab report parser (analytes, units, ranges, flags, report date in many formats, lab/doctor) + review model + tests — **high**
- [ ] T-011 Pharmacy-bill / prescription parser for medicines (brand + molecule + dose + frequency + duration) + tests — **high**
- [ ] T-012 Vitals extraction from report text (BP pairs, pulse, SpO2, weight, height, BMI, temperature) + tests — **medium**
- [ ] T-013 Duplicate/repeat-report detection (same analyte+date merge, re-import guard) — **medium**

### Intelligence
- [ ] T-014 Trend analyser: latest/previous/delta/%/rate, 3-year windowing, per-analyte status vs reference range + tests — **high**
- [ ] T-015 Focus engine ("what should I focus on?"): out-of-range, rapid change, overdue monitoring, data gaps, medicine review, wearable trends — each with rationale + severity + tests — **high**
- [ ] T-016 Health score (weighted domains, explainable breakdown) + tests — **high**
- [ ] T-017 Doctor-visit summary model (what changed since last visit, red flags, med list) — **medium**

### UI
- [ ] T-018 App shell: navigation graph, bottom bar + navigation rail, theming, splash, locale, app-lock gate — **high**
- [ ] T-019 Onboarding + auth screens (Google sign-in via Credential Manager, offline profile, PIN create/confirm, biometric unlock) — **high**
- [ ] T-020 Dashboard (greeting, health score ring, focus cards, latest vitals, quick actions, recent reports, meds due) — **high**
- [ ] T-021 Records: report list (search/filter by year+type) + report detail (analyte table, pages viewer, edit, delete, export) — **high**
- [ ] T-022 Import flow: pick/capture → progress with OCR status → review & edit extracted rows → save (incl. batch) — **high**
- [ ] T-023 Trends: analyte picker (search + category) → 3-year table (date rows, one row per report date) + chart with reference band + stats + CSV export — **high**
- [ ] T-024 Vitals: manual entry (BP, weight, pulse, SpO2, glucose, temperature, custom) + history table + chart — **high**
- [ ] T-025 Medicines: list (active/past), add/edit, course dates, reminder toggle, import-from-bill, timeline — **high**
- [ ] T-026 Treatments/procedures: timeline + add/edit + follow-up dates — **medium**
- [ ] T-027 Insights screen: focus list with evidence, health-score breakdown, disclaimers — **high**
- [ ] T-028 Profile screen (name, DOB, sex, height, blood group, allergies, conditions, emergency contact) — **medium**
- [ ] T-029 Settings: appearance (Light/Dark/System), language (English/Hindi/Gujarati), security (app lock, biometric, auto-lock, screenshot block), data (backup/restore/wipe), about/disclaimer — **high**
- [ ] T-030 Charts library: line/area with reference band + tap tooltip, bar chart (sleep), ring gauge, sparkline — **high**

### Connectivity & output
- [ ] T-031 Health Connect: consent flow, permission set, incremental sync worker, daily aggregation, device-dashboard screen — **medium**
- [ ] T-032 Doctor summary PDF (offline `PdfDocument`) + CSV export + share intents — **high**
- [ ] T-033 Encrypted backup/restore (kotlinx-serialization payload, AES-GCM, SAF file) + secure wipe — **medium**
- [ ] T-034 Medicine reminders: WorkManager + notifications + reboot rescheduling + POST_NOTIFICATIONS permission — **medium**

### Resources & quality
- [ ] T-035 Localization resources: `values/`, `values-hi/`, `values-gu/` for every key (parity test) — **high**
- [ ] T-036 Branding: adaptive app icon + monochrome themed icon, brand mark, splash screen, logo export (SVG/PNG) — **high**
- [ ] T-037 Unit tests: parsers, catalog, trends, insights, score, security, formatters, i18n parity — **high**
- [ ] T-038 Instrumented + Compose UI tests (auth, dashboard, import review, trends table) — **medium**
- [ ] T-039 Docs: README, ARCHITECTURE, DATA_MODEL, DESIGN_SYSTEM, PRIVACY, SECURITY, TESTING, BUILD_AND_RELEASE — **high**
- [ ] T-040 CI: GitHub Actions (assembleDebug, unit tests, lint, release build on tag) — **low**
- [ ] T-041 Play Store readiness: R8 rules audit, baseline profile, accessibility pass, store listing copy — **low**

## Done

- [x] T-000 Toolchain spike: AGP 9.5.1 + built-in Kotlin + Compose BOM 2026.11.01 + compileSdk 37 on Gradle 9.9.2/JDK 21 builds a debug APK (by: cline)

---

## Blocked

_(nothing yet — add `[!] T-0xx … (blocked by: …)` here with the reason)_
