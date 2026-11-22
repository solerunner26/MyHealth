# Component recipes (Compose)

- **PrimaryButton**: pill, 52 dp, accent (solid) or accent→accentBright gradient with glow (glass), label semibold
  16, trailing → icon for forward actions. Press: scale 0.97 + darken 6 %.
- **IconActionButton**: 56 dp rounded-square (r 18) or circle, neutral fill (surfaceSunken), icon 24, label 12
  below; selected = accentSoft.
- **Segmented control**: track r full, 44 dp; selected pill animates its x/width (animateDpAsState / spring
  dampingRatio 0.85).
- **Bottom navigation**: 4–5 items; active = filled icon + label + capsule indicator sliding; optional centre FAB.
- **Metric tile** (vital/test): overline label + status icon top row; gauge centre; big number (auto-size, units
  55 %); status pill; one-line delta footer. Equal heights per row (IntrinsicSize.Max).
- **Alert tile** (out of range): hero material, warning badge, breathing glow, "▲ High · 165 mg/dL", CTA chip
  "What to do".
- **Insight callout**: accentSoft card, leading illustration/icon in a 48 dp well, title + one line, chevron.
- **Splash**: full-accent (solid) or deep glass canvas, logo mark centred, app name, tagline, "Enter" pill button at
  the bottom (animated in after 400 ms), page indicator if onboarding follows. For health apps the mark is a
  **beating heart**: a double-thump scale (1 → 1.14 → 1 → 1.09 → 1 over 1.1 s, ~55 bpm) synced with a halo glow, and
  an ECG pulse line inside the heart that draws and erases along its path each beat (stroke-dash offset), with a
  soft blurred copy as glow in Glass themes. Static mark when animations are off.
- **Empty state**: illustration in hue, one sentence, one primary action.
- **Dialog / sheet**: r 28, scrim 40 % ink, content padding 24.
