# Foundations

## Colour recipe (one hue)
Inputs: hue `h`, family, mode (light/dark).
- **accent**: s 0.55–0.85, l tuned so text-on-accent passes AA (solve lightness, don't guess).
- **accentDeep** (hero card / solid fill): same h, l 0.18–0.26 light mode, 0.30–0.40 dark mode.
- **accentSoft** (chips, selected track, icon wells): l 0.90–0.94 light, 0.20–0.26 dark, s × 0.5.
- **accentBright** (neon step, glows only): l 0.62–0.70, s 0.95.
- **neutrals**: same h at s 0.04–0.12. Solid light canvas: warm off-white l 0.95–0.97 (e.g. #F3F1EC for forest,
  #FBF4EC apricot, #F1F6E6 lime, #F4F2FB iris). Dark canvas l 0.07–0.10.
- **ink**: text primary l 0.10–0.14 tinted by h (never pure black on solid light), secondary 0.38, tertiary 0.52.
- **chart series**: 4–6 lightness steps of h from accentDeep to accentSoft; the "current"/highlighted bar is the
  deepest step with a tooltip bubble.

Reference hue families observed on the boards (use as starting points, still single-hue):
| Theme idea | Hue | Canvas | Hero | Mood |
|---|---|---|---|---|
| Evergreen | 150° forest | warm cream | deep green card, white text | wealth, calm |
| Apricot | 28° peach | cream→white wash | white cards, peach FAB | friendly, Indian |
| Citrus | 80° lime | pale lime | near-black card with lime | bold, digital |
| Iris | 255° violet | white + lavender wash | violet gradient card | trustworthy |
| Ember | 24° orange | white with orange glows | full-orange premium card | playful, energetic |

## Type
Geometric sans (Manrope/Plus Jakarta/Sora class). Scale: display 34/40, headline 24/30, title 18/24, body 15/22,
label 13/18 (+0.2 tracking), overline 11/14 caps +1.2 tracking. **Numbers**: tabular, light or semibold at 34–44 sp
for heroes; decimals and units drop to 55 % size and secondary colour ("12,480.50" → ".50" small). Two-tone
headlines (primary + accent tint) on onboarding/splash.

## Shape & space
Radii: 12 (chips), 16 (inputs), 24 (cards), 28–32 (hero/sheets), full (buttons, FAB, avatars). Spacing 4-pt grid:
4, 8, 12, 16, 20, 24, 32. Screen gutter 20 dp. Card padding 16–20 dp.

## Motion tokens
| Use | Duration | Easing |
|---|---|---|
| press feedback | 90 ms | standard |
| tab indicator slide, chip select | 280 ms | emphasized decel |
| page enter (push) | 360 ms fade + 6 % slide-up / shared axis X | emphasized |
| page exit | 220 ms fade | accelerate |
| list stagger | 40 ms per item, max 12 | emphasized |
| gauge/number reveal | 900 ms | emphasized decel; numbers count up |
| ambient background | 14–24 s loop, reverse | linear |
| alert breathe (out-of-range) | 2.4 s | standard |
Respect system animator scale 0 → no ambient, instant reveals.

## Data viz
Monochrome tonal series of the accent. Donut: thick (14–18 dp) rounded segments with 4 dp gaps, centre total.
Bars: rounded tops (or stacked rounded blocks), the selected bar deepest + bubble tooltip. Progress: thin 6 dp
rounded bars on accentSoft track, or rings around an icon. Always print the number; colour is never the only cue.

## Status (health/finance values)
| State | Treatment |
|---|---|
| In range | normal card (glass or solid), small check icon, quiet label |
| Slightly out | card gets accentSoft fill + accent border 1.5 dp + "▲ Slightly high" pill |
| Out of range | card switches to **hero material**: accentDeep fill (solid) / bright glow rim + inner gradient (glass), onAccent text, warning icon badge, slow breathe glow, arrow showing direction |
| Unknown | dashed outline, tertiary text |
