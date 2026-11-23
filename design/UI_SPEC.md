# TVunboxed — UI spec for the Android app

**Source of truth: `design/demo2.html`.** Open it in a browser (or Android WebView) and compare against it. If this document and the HTML ever disagree, the HTML wins. Do not redesign, restyle, "modernise", or substitute Material defaults. The goal is a pixel-faithful native port.

## 0. Rules for the implementing agent

1. Read `design/demo2.html` fully before writing any code. All colours, sizes and copy below come from it.
2. Do **not** use stock Material 3 components with their default look (rounded corners, elevation shadows, ripple-only feedback, tonal surfaces). This design is flat, square-cornered and uses **hard offset shadows**.
3. Build the design tokens (section 1) first, as a theme object. Never hard-code a colour or size in a screen.
4. Build the shared components (section 3) once, then the screens (section 4).
5. Keep all copy exactly as written in the HTML (sentence case, same wording).
6. After each screen, render it on a ~390×844 dp device/emulator and compare with the HTML opened at the same width. Fix differences before moving on.
7. Light and dark themes are both required.
8. Nothing is wired to a real TV yet. Keep the TV layer behind one interface (`TvController`) with a fake implementation that matches the demo's behaviour (toasts, delays).

## 1. Design tokens

### Colours — dark (default)
| Token | Hex |
|---|---|
| bg | #0E0F1A |
| card | #16182B |
| muted | #1F2238 |
| recess | #0B0C16 |
| line | #2C3048 |
| border (bord) | #636889 |
| text | #EDEFFC |
| dim | #A6ACD6 |
| sub | #8E94BD |
| accent | #D5CDFF |
| accentInk | #2A1159 |
| ok | #C5E17A |
| info | #ADDBFF |
| warn | #FFE08A |
| danger | #FFC3C3 |
| pink | #FFBFD9 |
| okText / infoText / dangerText / warnText | same as ok / info / danger / warn |
| scrim | #B30B0C16 (about 70 percent) |

### Colours — light
bg #CCCFFA, card #FFFFFF, muted #F1ECE6, recess #E9E4DA, line #DCD3C6, border #2D241B, text #2D241B, dim #5A5146, sub #6B6154, accent #2D241B, accentInk #CCCFFA. Fill colours (ok, info, warn, danger, pink) stay the same pastels. Text-use variants in light: okText #476000, infoText #0B5A8F, dangerText #A3002F, warnText #7A5A00.

Fixed ink colours on pastel buttons (both themes): blue button text #003454, green #3F4E00, pink #82004F, danger #880023, file-type and app icon glyphs #1C1C1C.

### Shape and shadow (the signature look)
- **Corner radius: 0 everywhere**, except: the D-pad ring and its arrow buttons (circle), status dots, the mic ring, spinners, the finger ring and ripples (circle), and the mouse icon.
- Border widths: cards 1 dp `line`; buttons, inputs, tiles that need emphasis 2 dp `border`.
- **Hard shadow:** solid offset, no blur. Card: 3 dp right, 3 dp down, black 45 percent (light theme: #2D241B at 30 percent). Button: 2 dp / 2 dp, black 40 percent. Pressed button: translate by (2 dp, 2 dp) and remove the shadow.
- Implement hard shadows by drawing a second rect behind the element offset by (dx, dy). Do not use `elevation`.

### Typography
- Body/UI: **Trebuchet MS**, fallback Verdana, then sans-serif. If not bundled, use a humanist sans and tell me which.
- Headings and big numbers: **Georgia italic bold** (fallback serif).
- Data, paths, timestamps, key readouts: **Courier New** bold (fallback monospace).

| Use | Font | Size |
|---|---|---|
| Page title | Georgia italic bold | 24 |
| Card title | Georgia italic bold | 14 (Georgia, not caps) |
| Sheet title | Georgia italic bold | 22 |
| TV name on Home | Georgia italic bold | 24 |
| Big storage number | Georgia italic bold | 28 |
| Button label | Trebuchet bold | 14 |
| Body note | Trebuchet | 13, line height 1.5 |
| File name | Trebuchet bold | 14 |
| File meta, path, readouts | Courier New | 12 |
| Nav label | Trebuchet bold | 12 |
| Minimum text size anywhere | | 11 |

Do not use all-caps labels except the words `VOL`, `CH`, `LIVE`, and `NEW`.

### Spacing and sizes
- Screen padding 12. Gap between cards 12. Card padding 14. Row gap 8.
- **Minimum touch target 44 × 44 dp.** Buttons are 46 dp tall by default.
- Icon sizes 20 (default), 14 (small), 30 (large). Icons are 2 dp stroke, square caps, miter joins, 24-unit grid. Use the SVG paths in the `<symbol>` sprite in the HTML (convert to VectorDrawable or ImageVector exactly; do not swap in Material icons).

## 2. App frame

Top to bottom:
1. **App bar** (card background, 1 dp bottom line, animated 3 dp rainbow "foil" line along the bottom edge that scrolls horizontally over 9 s, colours #CCCFFA, #FFBFD9, #B2F0ED, #C5E17A, #ADDBFF, #D5CDFF). Contents left to right: logo 38 dp (the inline SVG in the HTML), wordmark ("TVunboxed" Georgia italic bold 20 over "mixtape edition" Courier 11 dim), then pushed right: **TV chip** (42 dp tall, 2 dp border, status dot + TV name + small chevron) and a **42 × 42 gear button**.
2. **Screen area**: scrolls vertically, one page at a time, 12 dp padding, 34 dp extra bottom padding.
3. **Bottom nav**: 64 dp (plus system inset), card background, 1 dp top line. Five items: **Home, Files, Remote, Watch, Tools**. The active item has an accent fill with accentInk content and a 2 dp hard shadow. The centre **Remote** item is raised 24 dp above the bar, filled `ok` with dark green ink, 2 dp border, hard shadow, and a pulsing outline ring (scales 0.94 to 1.14 and fades over 2.6 s, repeating).

Page transition: fade in plus 10 dp slide up, 280 ms.

## 3. Shared components

- **Card**: card colour, 1 dp line border, hard shadow, 14 dp padding. Card title row: Georgia italic 14 bold, dashed 1 dp bottom line (`line`), 10 dp below, then 12 dp gap. Optional right-aligned "hint" action in okText Courier 12 bold (for example "Refresh" with a refresh icon; the icon spins 650 ms when tapped).
- **Button**: 46 dp tall, 2 dp border, hard shadow, label Trebuchet bold 14, icon + label gap 7. Variants: default (muted), `blue` (info bg), `green` (ok bg), `pink`, `amber` (accent bg, accentInk text), `danger`. Disabled: 45 percent opacity. Press: shift (2, 2) and drop shadow, plus a brief brightness flash.
- **Icon button**: 42 × 42, muted, 2 dp border, hard shadow.
- **Segmented control**: 2 dp border, recess background, 3 dp inner padding and gap, segments 40 dp tall, active segment accent fill with accentInk text. A "Danger" segment uses danger fill when active.
- **Switch row**: 46 dp tall, muted, 2 dp border, label left, square track right (44 × 24, 2 dp border; on = ok fill with the thumb moving 20 dp right).
- **Slider**: 12 dp track (filled part `info`, remainder `recess`, 1 dp border), thumb 22 × 32, accent fill, 2 dp border, hard shadow. No round thumb.
- **Text field**: 46 dp tall, recess background, 2 dp border, 12 dp horizontal padding, text 16. Focus: `info` border plus a 3 dp soft info glow. Empty-submit: shake 6 dp left and right over 350 ms and show a warning toast.
- **File row / list row**: muted background, 1 dp line border, min height 58, 8 dp padding, 10 dp gap: 42 × 42 type tile (2 dp border; video = info, image = pink, audio = ok, doc = accent, live = warn; glyph #1C1C1C) + text column (name bold 14 ellipsis, meta Courier 12 dim) + 44 × 44 primary action (accent fill, 2 dp border, hard shadow) + 44 × 44 "more" button (transparent, line border).
- **Chip (filter)**: 38 dp tall, 14 dp horizontal padding, 2 dp border; active = accent fill with accentInk and a hard shadow. Chip rows scroll horizontally.
- **Tip banner**: muted, dashed `border`, info icon, dim 13 text, close button top-right.
- **Empty state**: centred 34 dp icon, Georgia italic 17 title, dim 13 sentence telling the user what to do.
- **Skeleton**: 13 dp bars with a left-to-right shimmer (1.2 s loop).
- **Toast**: drops in from the top, below the status bar, centred, card background, 2 dp accent border (`ok` or `warn` variants with matching icon), hard shadow, 13 bold text, shows 2.2 s. One at a time.
- **Bottom sheet**: slides up 300 ms (decelerate), card background, 2 dp border (no bottom), 5 × 46 dp drag handle centred with a 10 dp touch zone; drag down more than 80 dp or tap the scrim to dismiss. Title Georgia italic 22. Option rows: 60 dp min height, 40 × 40 accent icon tile, bold 15 title, Courier 12 subtitle, 2 dp border, hard shadow.
- **Confirm dialog**: scrim, centred card max width 310, 2 dp `danger` border (soft variant uses `accent` border and an amber confirm button), warning icon + Georgia italic 20 title, 14 dim body, two equal buttons (Cancel focused by default, Confirm). Scale in from 0.92 with a slight overshoot, 250 ms.
- **Pop-up (launching / listening)**: scrim, centred card with a 76 × 76 icon tile that pops in (scale 0.7 to 1.06 to 1), title, one dim line, then a spinner or a Cancel button. Auto-closes (app launch 1.3 s, voice 2.4 s).
- **Haptics**: 8 to 12 ms on normal presses, a short double pulse for right-click and warnings. A settings switch turns haptics off.

## 4. Screens

### 4.1 Home
Cards in this order:
1. **TV stage card**: 16:9 preview (2 dp border, hard shadow, gradient from #2A1159 to #0E0F1A to #1A3A44, faint horizontal scanlines, a glowing `ok` dot wandering across it over 7 s, caption chip bottom-left "▶ file name"). Below: TV name (Georgia 24) with "E65 · online · 12 ms" (Courier 12 dim) and a four-bar signal indicator on the right. Then two equal buttons: **Remote** (amber) and **Air mouse** (blue).
2. **Now playing**: title row with state hint ("Playing" or "Paused"); 48 dp info tile with a video icon, file name and "From phone share"; seek slider; elapsed and total time (Courier 12); four-button transport row (rewind, play/pause in green, forward, stop); volume row (speaker icon button that toggles mute, slider, number). Seek advances every second while playing.
3. **Quick actions**: four equal 68 dp buttons with icon above label: Input, Browser, Mirror, Identify.
4. **TV health**: 2 × 2 tiles (muted, 1 dp line): Link (latency, okText), Model (E65, infoText), Phone battery (93%, charging), Network (4G, 1.5 Mb/s). Refresh randomises latency to 8 to 18 ms.
5. **Dismissible tip**: "Brightness and equaliser aren't available on this TV. Volume, mute and media keys live in the Remote."

### 4.2 Files
Page head "Files" / "Send anything from your phone to the TV." Then one card: breadcrumb row (up button, path "Phone / Download" in a recess box, refresh button), search field with leading magnifier, filter chips (All, Video, Images, Audio, Docs, filtering by **file type**, not by name), then rows. Sample data: holiday.mp4 842 MB, trip-clip.mkv 1.4 GB, poster.jpg 2.1 MB, cover-art.png 940 KB, mix.mp3 7.4 MB, podcast-ep12.m4a 28 MB, notes.pdf 310 KB.
- Primary action: video/audio = play icon ("Play"), image = "Show", doc = "Open". Playing a video/audio file updates Home's Now playing title.
- Tapping the name or the more button opens the file sheet: **Play on TV**, **Copy to TV**, **Preview on phone**.
- Copy shows a **transfer card** pinned 74 dp above the bottom, info border, filename, percentage, 10 dp progress bar, cancel. One transfer at a time. Ends with a toast "<name> is on the TV".

### 4.3 Watch
Segmented control on top: **Apps | Channels | Playlist**.
- **Apps**: card with a Refresh hint; 4-column grid of 56 × 56 icon tiles (2 dp border, hard shadow, two-letter Georgia italic glyph, pastel fills cycling pink, info, ok, accent, warn) with a 12 sp label. Shows skeletons for 800 ms on first open and on refresh; tiles pop in with a 40 ms stagger. Tapping an app opens the "Opening <app> on <TV>" pop-up, then a toast.
- **Channels**: preview player card (ok border, LIVE tag, animated 5-bar equaliser, Pause and "Cast to TV" buttons) shown after tapping a channel; filter field; chips All / Favourites; rows (live tile, name + "News · tap to preview", star toggle, cast button). Starred channels sort first. Data: Al Jazeera English, DW English, CGTN.
- **Playlist**: dashed drop area "Choose an .m3u file / It is read on this phone and never uploaded.", plus "Try a sample playlist". Parsing `#EXTINF` names (max 60) appends rows tagged "Playlist" and jumps to Channels.

### 4.4 Tools
Segmented control: **Send | TV Care | Danger**.
- **Send**: cards "Open a link on TV" (field + pink Open), "Launch an app by package" (field + Launch), "Type on TV" (note + amber "Open keyboard" which opens the Remote on the Keys tab).
- **TV Care**:
  - Storage card: big number "7.7 GB used of 16 GB", stacked 22 dp bar (info = other 6.2 GB, ok = reclaimable, warn = backups, recess = free; segments animate in 800 ms), 2 × 2 legend, amber "Run audit" with a 6 dp progress bar and the rotating status text "Scanning apps…", "Scanning files…", "Checking backups…".
  - Reclaimable files: checkbox rows (26 dp square, ok fill when on) cache_old.apk 640 MB and test_clip.mp4 560 MB; danger button "Move N to backup · size". A soft confirm dialog first; rows slide out 250 ms; a backup row is added.
  - TV backups: "Nothing is freed until you purge a backup."; each row has a danger **Purge** button with a danger confirm dialog; purging shows "Freed …" and shrinks the backup segment.
- **Danger**: danger-bordered card "Uninstall an app" with the package field and a danger **Uninstall** button, with a danger confirm dialog.

## 5. Full-screen Remote (most important screen)

Opens from the raised Remote button (slides up 350 ms, decelerate, covering everything including the nav). Top bar: 42 × 42 close (chevron-down) + segmented control **Remote | Air mouse | Keys** (each with a 14 dp icon). Bottom footer (card background, 1 dp top line): pulsing dot, last command text (Courier 12 bold, ellipsised), TV name on the right. Switching tabs cross-fades and shifts 16 dp, 220 ms. The remote remembers the last tab.

### 5.1 Remote tab
Top to bottom, 12 dp gaps:
1. Four equal 62 dp column buttons (icon above label): **Power** (tap = standby toast; hold 600 ms = red fill rises from the bottom, then "TV is powering off" warning toast), **Input**, **Mute** (pink; when muted turns danger-filled with the muted icon), **Voice** (opens the listening pop-up).
2. Middle row, vertically centred in the leftover space: columns 70 dp | flexible | 70 dp, 10 dp gaps.
   - Left **volume rocker**: bordered muted column, plus button, centre readout ("VOL" and the number, 20 bold), minus button.
   - Centre **round D-pad**: circle, max 250 dp, muted, 2 dp border, hard shadow; four arrow buttons at the top, bottom, left and right edges; centre **OK** circle 38 percent of the diameter, accent fill, 2 dp border, hard shadow.
   - Right **channel rocker**: same structure, readout "CH".
3. Three equal buttons: Back, Home, Menu.
4. Four-button transport: rewind, play/pause (green), forward, stop.

Behaviour: D-pad arrows, volume and channel buttons **repeat while held** (first after 380 ms, then every 110 ms). Volume changes by 2 per step and stays in sync with Home's slider. Mute state and play/pause state are shared with Home.

### 5.2 Air mouse tab (give this the most care)
Layout: top row (segmented **Touch | Motion**, plus the **Pointer** switch, 48 dp tall); then the main area; then a bottom row.
- **Main area** fills all remaining height: a big **pad** (2 dp border, recess bg, hard shadow, 34 dp grid lines in `line` colour, accent corner brackets 20 dp, 3 dp stroke at 70 percent) plus a 56 dp wide **scroll strip** on its right (muted, 2 dp border; up and down 48 dp buttons, a middle ticks area).
- Pad overlays:
  - Top-left **TV map**: 112 dp wide, 16:9, card bg, 2 dp border, "TV" label, with a glowing `ok` dot showing the cursor position on the TV (1920 × 1080 normalised), and a readout under it "X 960 · Y 540" (Courier 12 bold, okText, card bg).
  - Top-right vertical tool stack of 46 × 46 buttons: speed ("1×", "2×", "3×", cycles), centre pointer (target icon), full screen (expand icon).
  - Finger feedback: 66 dp `ok` ring with 16 percent fill under the finger, and a trail of small `ok` dots that shrink and fade in 600 ms.
  - Bottom-centre hint: "Slide to move · tap to click" (Motion mode: "Hold the pad and tilt the phone to aim").
  - First-run coach overlay (fades out on the first touch): a looping animated finger swipe, headline "Slide to move the TV cursor", then "Tap to click", "Hold to right-click", "Two fingers to scroll". A Settings button "Show air mouse tips again" re-enables it.
  - When the Pointer switch is off: pad content dims to 25 percent and a centred overlay says "Pointer is off / Turn it on to see a cursor on the TV." with a green "Turn on pointer" button.
- **Gestures (Touch mode)**: one finger moves the cursor relative to movement (px × 1920 / pad width × gain; gains 1.1, 2.0, 3.4 for the 1×, 2×, 3× speeds); a tap under 300 ms and under 10 dp of movement is a left click (blue ring ripple at the touch point, ping on the TV map, 12 ms haptic, readout flashes "CLICK"); holding still for 600 ms is a right click (pink ripple, double haptic, "RIGHT CLICK"); two fingers scroll (strip ticks move, readout "SCROLL UP/DOWN", a 5 ms haptic every 26 dp).
- **Motion mode**: only while a finger is held on the pad, the phone's tilt moves the cursor (gamma maps to X, beta to Y, about 45 px per degree × gain; ignore jumps over 40 degrees). The pad gets a 3 dp `ok` outline while aiming. If no motion sensor or permission, show a warning toast and fall back to Touch.
- **Full screen**: the expand tool hides the top bar, the top row and the footer so the pad takes the whole screen (respect system insets); the same button (now a shrink icon) and the system Back button exit it.
- **Bottom row**: Back (54 × 56 icon button), Home (54 × 56), **Left click** (amber), **Right click** (pink), all 56 dp tall.

### 5.3 Keys tab
- Typed text box: min 70, max 104 dp, recess bg, 2 dp border, Courier 16 okText, blinking block cursor; placeholder "Type here. It appears on the TV." in `sub`.
- Three equal buttons: Paste, Clear, Voice.
- Keyboard fills the remaining height. Rows (max 62 dp tall, 5 dp gaps, keys 2 dp border, hard shadow, label 18):
  - abc layer: `1234567890` / `qwertyuiop` / `asdfghjkl` (5 percent side padding) / shift + `zxcvbnm` + backspace / `123`, `,`, space (flex 4.5), `.`, enter.
  - symbols layer: `!@#$%^&*()` / `-_=+[]{}/\` / `;:"'<>?~` / Tab, `.com`, `www.`, backspace / `abc`, `,`, space, `.`, enter.
  - Function keys use recess fill and 13 bold labels; shift is accent-filled when on (one-shot). Backspace repeats while held. Every key is sent live to the TV.
- Caption under the keyboard: "Every key is typed live into the TV's focused field."

## 6. Settings and TV picker sheets
- **TV picker**: title "Choose a TV", subtitle "Phone and TV must be on the same Wi-Fi.", option rows (TV icon, name + "E65 · 192.168.1.24", check on the selected one, offline rows at 55 percent with "· offline" and a shaking list plus warning toast when tapped), and a "Scan for TVs" button that shows a shimmer row then adds one "NEW" TV the first time. Selecting a TV makes the chip show "Connecting…" with a fast amber pulsing dot for 1.1 s, then "Connected to <name>".
- **Settings**: Theme segmented (Dark with moon, Light with sun), "Vibrate on key press" switch, "Show air mouse tips again" button.

## 7. Motion summary
Page fade-up 280 ms; sheets 300 ms decelerate; dialogs scale-in 250 ms with overshoot; remote overlay 350 ms; nav ring pulse 2.6 s loop; foil 9 s loop; status dot pulse 2 s; skeleton shimmer 1.2 s; app tiles pop with 40 ms stagger; storage bar 800 ms; transfer card slide 300 ms. Respect the system "remove animations" setting (collapse durations to about 0 and stop loops).

## 8. Accessibility
Every icon-only button has a content description (the HTML `aria-label`s are the wording). Minimum 44 dp targets. Visible focus (3 dp `info` outline) for keyboard/D-pad navigation. Toasts announce politely. Dialogs trap focus and focus Cancel first.

## 9. Definition of done
- Side-by-side screenshots of every screen in dark and light match `demo2.html` at 390 dp width (spacing within about 2 dp, identical colours, fonts and copy).
- The Air mouse tab fills the screen as described, with all gestures, full-screen mode, and the TV map working.
- No Material default styling is visible anywhere.
