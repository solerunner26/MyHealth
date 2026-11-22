# Solid UI (non-glass)

Learned from: forest-green wallet app, apricot rupee budgeting app, lime/black digital bank, violet transfer app,
orange learning companion.

## Surfaces
- Canvas: warm off-white tinted toward the hue; top 30 % of the screen carries a soft **header wash** (radial or
  vertical gradient of accentSoft → canvas). Animated: wash slowly drifts and breathes.
- Cards: opaque surface (white in light, l 0.14 in dark), radius 24, **soft diffuse shadow** (y 6–12 dp, blur 24,
  alpha 0.06–0.10, tinted by hue), no borders. Group lists inside one card with hairline dividers inset 64 dp.
- **Hero card**: accentDeep fill (forest/violet/ink-black for lime), light text, a subtle diagonal sheen or sparkle
  shape, a small pill chip for the delta ("▲ 12.6 % vs last month") in accentBright on translucent white.
- Full-accent screens: onboarding/splash and "premium"/celebration moments use the accent as the whole background
  with a big two-tone headline and a white pill CTA with →.

## Layout patterns
- Greeting header: "Good morning" title + subtitle line, round icon buttons (search, bell) on the right in
  surface-coloured circles, avatar at left or right.
- Hero → **quick actions row** (4 rounded-square or circular buttons, icon centred, label below, neutral fill) →
  section header with "See all →" → list card → insight callout (accentSoft card with an illustration/leaf/icon
  and a chevron).
- Segmented pill tabs: track = neutral sunk, selected = accentDeep filled pill with onAccent text; indicator
  slides.
- Underline tabs (apricot style) for secondary views: 2 dp accent underline that slides.
- Bottom bar: surface bar with 4 icons + **centre circular FAB** in accent (raised 8 dp, shadow), or a floating
  ink pill bar (lime style) with the active item as an accent circle.
- Lists: 44 dp circular icon well (accentSoft) + two-line text + right-aligned value and date.
- Progress cards: icon inside a ring, "₹15,000 / ₹25,000", thin bar, "Current … Target" captions.
- Settings: grouped cards with section titles, accent toggles.
- Keypad entry: huge centred amount, airy digits, method chips above, category chips below, full-width CTA.

## Illustration & delight
Line illustrations or soft 3D objects in the accent hue only; mascot moments for empty states and success.
Celebrations: confetti in hue tints, 1.2 s, once.
