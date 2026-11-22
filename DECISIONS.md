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
