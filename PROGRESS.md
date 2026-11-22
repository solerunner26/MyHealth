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
