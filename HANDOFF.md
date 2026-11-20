# HANDOFF.md — session ledger

Newest entry first. Every agent appends `## <ISO date> — <agent> — <task id>` with: what changed,
what is left, gotchas. Keep it short and factual; the code is the source of truth.

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
