# Glass UI

## Surfaces
- Background: deep canvas (l 0.06–0.10) or pale mist in light mode, with 2–3 large blurred blobs of the accent at
  different lightness (accentDeep, accent, accentBright) drifting slowly; plus a **glare sweep**: a wide diagonal
  white gradient at 4–6 % alpha that passes across every ~18 s.
- Panels: translucent fill (top 10–14 % white → bottom 4–6 % in dark; 70 → 55 % white in light), 1 dp inner border
  (white 14–20 %), a 1 dp specular highlight line on the top edge, radius 24–28, optional backdrop blur where the
  platform supports it (API 31+ RenderEffect); never put text directly on the blobs.
- Hero: gradient glass (accent 30 % → accentDeep 20 %) with an outer glow of accentBright at 25 % and a moving
  sheen.

## Layout patterns
Same information architecture as Solid (hero → actions → list → insight) so themes are swappable. Navigation bar
is a floating glass pill with the active item as an accent capsule that slides between items.

## Colour use
Neon (accentBright) for glows, rings, active indicators, sparkline heads. Text stays neutral (white 92 % / 64 % /
44 %). One hue only.

## Pitfalls
- Glass on glass loses legibility: max two layers.
- Low-contrast borders vanish on light mode: increase border alpha to 0.6 white + 0.06 ink outline.
- Blur cost: blur only the background layer once, not each card.
