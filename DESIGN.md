# TVunboxed — Design System ("Mixtape")

**Cassette-forward Y2K.** Analog soul, digital brain. Vanilla HTML/CSS — no frameworks, no build step. Implemented in `web/index.html`; everything below is the contract for future UI work.

## Principles

1. **Radius is always 0.** No rounded corners anywhere except the status dot (a light is a light).
2. **Borders are 2px ink.** Emphasized = `--bord`; quiet = `--line`. Buttons, inputs, cards all wear them.
3. **Shadows are flat offsets** (`4px 4px 0` / `3px 3px 0`), never blur.
4. **High-chroma blocks, low-chroma canvas.** Neon green/electric blue/salmon appear only on controls that do something specific.
5. **Dark and light are first-class.** Every color is a CSS variable scoped by `html[data-theme]`. No hard-coded palette values outside the two theme blocks (logo art is exempt).

## Logo

`assets/logo.svg` (also inline in the app bar and favicon). A purple cassette (`#D5CDFF`) on ink (`#16182B`), label window in neon green (`#C5E17A`) with `TV-UNBOXED` in JetBrains Mono bold, two reels.

- Use on both modes as-is (the logo is mode-agnostic).
- Minimum 32px; below that drop the label text (favicon variant keeps shell + reels only).
- Don't recolor, don't add effects, don't put it on busy backgrounds.

## Color tokens

### Dark (default)
| Token | Value | Role |
|---|---|---|
| `--bg` | `#0E0F1A` | body canvas |
| `--card` | `#16182B` | cards |
| `--muted` / `--recess` | `#1F2238` / `#0B0C16` | rows, inputs, sinks |
| `--line` / `--bord` | `#2C3048` / `#636889` | quiet / emphasized borders |
| `--text` / `--dim` / `--disabled` | `#EDEDFC`→`#EDEFFC` / `#A6ACD6` / `#4A4F6B` | text ladder |
| `--accent` / `--accent-ink` | `#D5CDFF` / `#2A1159` | **primary actions** (purple shell) |
| `--accent2` | `#CCCFFA` | legacy periwinkle highlights |
| `--ok` `--info` `--warn` `--danger` `--pink` `--cyan` | `#C5E17A` `#ADDBFF` `#FFE08A` `#FFC3C3` `#FFBFD9` `#B2F0ED` | semantic fills (ink variants `…-ink`) |

### Light
Body is the classic Y2K periwinkle `#CCCFFA`; cards white (`#FFF`); primary action = charcoal `#2D241B` with periwinkle text (`#CCCFFA` on charcoal — classic 2001 Y2K); borders `#2D241B`; shadows warm `rgba(45,36,27,.30)`. Everything else maps 1:1 by role.

## Type

- **Poppins** 400/500/600/700 — body & controls
- **Crimson Text italic 700** — display wordmark, hero numbers, "TVunboxed" in app bar
- **JetBrains Mono** — labels, data, counters, badges, status (`// TRK 02 · SIDE A`)

Scale: base 14.5px; card titles 0.64rem mono caps with `.2em` letter-spacing; hero 1.6–1.45rem display italic.

## Components

- **Card** — 1px `--line` border, `--hard` shadow, 14px padding; title row = mono caps + dashed underline; optional Right-side `.hint` (mono, `--ok`).
- **Button** — 2px `--bord` border, `--hard-sm` shadow; variants: `.btn-amber` (accent=primary), `.btn-green` (success), `.btn-blue` (info/nav), `.btn-pink` (cast/open), `.btn-green` play, `.danger`. Active: translate 2px, shadow collapses.
- **Segmented (Side A/Side B)** — mono caps, reel glyph `◉ ◉`, `.active` = accent fill. Used for d-pad/air-mouse.
- **D-pad** — 3×64px grid; OK = `--info` block; spacers `visibility:hidden`.
- **Inputs** — `--card`/`--recess` fill, 1px `--bord`, `--shadow-focus` ring on focus; ≥16px font on coarse pointers (no iOS zoom).
- **Range slider** — 10px square track, fill = `--info`, thumb = `--accent` block with `--hard-sm`.
- **Tiles (dashboard)** — `--muted`, 2/4-col responsive; `.good/.warn/.bad/.info` tint values.
- **Nav (5 slots)** — fixed bottom; active item = accent fill + ink text + `--hard-sm`; Remote is the physical center slot.
- **Toast** — card + 2px accent border, mono.
- **Apps grid** — 4–5 col icons; `--hard-sm` borders on icons.
- **Theme toggle** — square button in app bar; sun/moon swap by `data-theme`; persisted in `localStorage` (`tvunboxed-theme`), defaults to `prefers-color-scheme`. No flash of wrong theme (inline head script sets `data-theme` pre-paint).

## Motion

120–250ms, ease-out, transform+opacity only. Buttons press-translate; tabs fade-up 10px; foil divider loops 9s. No springs, no bounce — the tape doesn't bounce.

## Voice

Short, loud, literal. Name the fix: "TV offline — is it powered and on Wi‑Fi?" Cassette metaphors ≤1 per screen (Side A/B, eject). **No exclamation marks.**

## Do / Don't

- ✅ New UI = variables only; ✅ light & dark checked before merge; ✅ focus-visible ring on every interactive element; ✅ test on a real phone width (~360px).
- ❌ No radius; ❌ no blurred shadows; ❌ no hard-coded hex outside the two theme blocks (logo exempt); ❌ no gradients except the foil divider; ❌ no emoji in UI chrome (use the SVG set).

## File map

`web/index.html` (markup+CSS+JS, single file) · `assets/logo.svg` · `DESIGN.md` · `README.md`· `METHODOLOGY.md`
