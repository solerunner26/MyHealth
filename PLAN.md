# MyHealth: delivery plan

Each phase ends at a Definition-of-Done gate: `assembleDebug`, `testDebugUnitTest`, Robolectric cold-start test,
end-to-end flow test (from Phase 4), `lintDebug` with 0 errors, and a PROGRESS.md entry with exact command outcomes.

| Phase | Scope | Status |
|---|---|---|
| 1 | Toolchain pinning, modules (:app :domain :data :core:designsystem), design tokens, component kit, EN/HI/GU scaffolding + parity test, launching shell | done |
| 2 | Domain models, analyte catalogue, unit converter, report text/table parsers, tests | done |
| 3 | SQLCipher database, Keystore key management, encrypted file vault, DAOs, repositories, migrations, tests | done |
| 4 | Ingestion: PDF text layer + ML Kit OCR fallback, page handling, SHA-256 duplicate detection, import review/edit/save | done |
| 5 | Trend engine, focus engine rules, explainable health score, tests | done |
| 6 | Adaptive navigation shell, member switcher, dashboard, records, report detail + attachment viewer, trend charts | done |
| 7 | Vitals, medicines, treatments, local dose reminders + reboot rescheduling | todo |
| 8 | Insights screen, member profile editor, settings (theme, language, app lock, FLAG_SECURE), doctor summary PDF/CSV | todo |
| 9 | Health Connect (opt-in), password-encrypted backup/restore via SAF, secure erase | todo |
| 10 | Localisation audit, accessibility/contrast pass, failure-mode hardening, CI, signed release build | todo |
