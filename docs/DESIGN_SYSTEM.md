# DESIGN_SYSTEM

My Health has **one hue**: a deep pine accent over warm porcelain neutrals. Severity is carried by
icon, wording, fill weight and position, never by introducing a second colour. That rule is the single
non-negotiable of this design system and it outranks any imported palette.

## Where the visual language comes from

The layout, surface treatment, motion and component grammar follow **[Rare UI](https://rareui.com)**
(MIT + Commons Clause + Attribution, © Swami Malode), a shadcn registry of small, precise, animated
components. Rare UI is a web library, so nothing was copied as code; this Android Compose implementation
reinterprets its rules:

| Rare UI rule | How it lands in My Health |
| --- | --- |
| Near-black / porcelain neutrals, one accent | `Palette.Ink*` scale + the existing Pine accent family |
| 1px hairline borders, small radii | `RareCard` at 14dp, controls at 10dp, sheets at 20dp, 1dp strokes |
| Tight display type, tabular numbers | Inter (bundled, OFL) for text, platform mono for values and scores |
| Small-caps section labels | `RareSectionLabel`, `RareLabelStyle` |
| Sliding indicators, count-up numbers | `RareSegmented` pill, `RareBottomBar` dot, `AnimatedCounter` |
| OTP-style input slots | `RarePinEntry` (4–12 digit PIN, rows of four, masked dots) |
| Toast / sheet overlays | `RareSnackbarHost`, `RareSheet`, `RareConfirmDialog` |
| Press feedback on physical controls | `Modifier.pressScale` (0.97 spring) on buttons, keys, chips |
| Respect reduced motion | Looping animations are confined to the PIN caret and loading skeletons |

Attribution is shown in the app under **Settings → About** with a link to rareui.com. The license
and a written notice live in `docs/licenses/RARE-UI-LICENSE.txt` and `docs/licenses/RARE-UI-NOTICE.txt`,
next to the Inter OFL text (`docs/licenses/INTER-OFL.txt`).

## Tokens

`design/theme/Theme.kt` exposes `MyHealthTokens` with `spacing`, `chart`, `dims` and `motion`.

* **Spacing** 2/4/8/12/16/24/32/48, screen padding 20, card padding 16, row height 56.
* **Radii** 6 (chip/checkbox), 8 (small control), 10 (control), 14 (card), 20 (sheet).
* **Dims** 1dp hairline, 1.5dp focus stroke, 46dp control height, 40dp icon button, 48dp touch target.
* **Motion** 120/200/320 ms with cubic-bezier(0.22, 1, 0.36, 1) out and (0.65, 0, 0.35, 1) in-out.
* **Chart** pine line on a 10–14% pine reference band, dashed neutral grid, 2.5dp stroke.

## Component inventory (`design/rare`)

Surfaces: `RareCard`, `RareInset`, `RareSectionLabel`, `RareDivider`, `RareList`.
Controls: `RareButton` (primary, secondary, ghost, destructive), `RareIconButton`, `RareTextField`,
`RareSearchField`, `RareSwitch`, `RareCheckbox`, `RareChip`, `RareSegmented`, `RareUnderlineTabs`,
`RarePinEntry`.
Data display: `RareStat`, `RareListRow`, `RareTaskRow`, `RareKeyValue`, `RareBadge`, `RareAvatar`,
`RareEmptyState`, `RareProgressRing`, `AnimatedCounter`.
Feedback and overlays: `RareSnackbarHost`, `RareLinearProgress`, `RareScrollProgress`, `RareSkeleton`,
`RareSpinner`, `RareSheet`, `RareConfirmDialog`, `RareChoiceDialog`, `RareBanner`.
Chrome: `RareTopBar`, `RarePageHeader`, `RareBottomBar`, `RareNavRail`.

`design/components` keeps the older screen-level names (`HealthCard`, `SectionHeader`, `StatusPill`,
`MetricTile`, `ListRow`, …) as thin aliases onto these primitives so existing screens inherited the new
language in one step. New work should use the `rare` names directly.

## Accessibility

* Every interactive element is at least 46–48dp tall; icon buttons carry `contentDescription`.
* Status is announced as text: `RareBadge` always renders a word, never a bare colour dot.
* Charts expose their data through `semantics` and print the selected point as text.
* The PIN is never echoed; slots show dots and the caret only marks position.
* Text scales with the system font size; layouts use wrap or ellipsis instead of fixed heights for text.

## Do / do not

* Do use `RareCard` for any new grouped content; one card per idea.
* Do keep page headers to a small-caps eyebrow, one large title and at most two actions.
* Do not add a second accent hue, gradients or decorative shadows.
* Do not hardcode text; every string lives in `values/`, `values-hi/`, `values-gu/`.
