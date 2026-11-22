---
name: android-design-studio
description: Personal UI/UX design system for Android (Jetpack Compose) apps, in two families - GLASS UI (frosted translucent panels over living colour) and SOLID UI (warm neutral canvas, one bold hero card, soft shadows). Use whenever designing, theming, reviewing or restyling an app screen, theme, component, splash, onboarding, chart or icon, or when the user names this skill. Enforces ONE accent hue + neutrals, calm motion, no overlap, and screenshot review.
---

# Android Design Studio

A design method distilled from the owner's reference boards (finance, budgeting, banking, learning apps) and
their feedback on MyHealth. Use it for every visual decision. It has two families; every theme belongs to exactly one.

| Family | Feels like | Read |
|---|---|---|
| **Glass UI** | Night sky, depth, light passing through frosted panels | `references/glass.md` |
| **Solid UI** | Paper, calm, tactile cards on a warm canvas, one dark hero | `references/solid.md` |

Shared foundations (tokens, type, motion, data-viz, status) are in `references/foundations.md`; component recipes in
`references/components.md`; the review checklist in `references/review.md`. Read the family file plus foundations
before writing code.

## Non-negotiables
1. **One accent hue + neutrals.** A theme is ONE hue (e.g. forest 150°, apricot 28°, lime 80°, iris 255°) and
   neutrals that lean slightly toward it. Tints, shades, glows and chart series are lightness/chroma steps of that
   hue. No second brand colour. Status never relies on a new colour: it uses icon + label + fill weight + shape.
   (Neon is allowed only as the *brightest step* of the same hue, for glows and small highlights.)
2. **Nothing overlaps or truncates badly.** Numbers auto-size, units never wrap, every row reserves space for its
   longest translation (Hindi/Gujarati are ~30 % longer), touch targets ≥ 48 dp.
3. **Calm motion with purpose.** Every screen change, tab change and value reveal animates (see foundations), but
   nothing loops fast; ambient motion is ≥ 12 s per cycle and stops when system animations are off.
4. **Backgrounds are never flat.** A slow hue drift, a soft glare sweep or a header wash, always low contrast
   behind content.
5. **A hero per screen.** One element is the focus (balance card / score / most urgent value); everything else is
   quieter.
6. **Out-of-range is unmistakable**: the tile changes *material* (solid accent fill or deep ink, glow ring, badge),
   not just a colour.
7. **Verify by looking.** Render screenshots (light + dark, smallest phone width, 2x font) and inspect before calling
   a design done.

## Workflow
1. Pick family (Glass or Solid) and one hue. Name the theme after a feeling, not a colour code.
2. Generate tokens with the recipe in foundations (HSL steps + contrast solver). Check WCAG AA for every text pair.
3. Lay out with the screen patterns of the family (hero → quick actions → content list → insight callout).
4. Apply motion tokens; add the ambient background.
5. Screenshot, review against `references/review.md`, fix, repeat.
6. When mocking up before code, make a Design artifact (Claude Design) with both families side by side.
