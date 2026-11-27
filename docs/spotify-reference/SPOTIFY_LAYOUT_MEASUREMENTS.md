# Spotify for Windows — layout measurements

Measured from full-resolution 1920 x 1200 captures on a 125% scaled display.
The Spotify window occupies **1920 x 1125 physical pixels at (0, 6)**, leaving
the Windows taskbar below. Values are physical pixels unless marked `dp`.

At 125%, `dp = physical / 1.25`, so the app window is **1536 x 900 dp**.

## 1. Vertical bands

| Band | Physical y | Height (physical) | Height (dp) |
| --- | ---: | ---: | ---: |
| Windows border / caption area above client | 0 – 8 | 9 | — |
| Global top bar | 9 – 87 | 79 | 63.2 |
| Workspace (containers) | 88 – 1022 | 935 | 748.0 |
| Player bar | 1023 – 1131 | 109 | 87.2 |

The top bar and player bar are full-window bands. The workspace is the only
region that changes height when the window is resized (not verified below the
maximised size).

## 2. Horizontal containers — library expanded

| Region | Physical x | Width (physical) | Width (dp) | Share of 1920 |
| --- | ---: | ---: | ---: | ---: |
| Outer left margin | 0 – 9 | 10 | 8.0 | — |
| Left library | 10 – 463 | 454 | 363.2 | 23.65% |
| Gutter | 464 – 473 | 10 | 8.0 | 0.52% |
| Centre | 474 – 1458 | 985 | 788.0 | 51.30% |
| Gutter | 1459 – 1468 | 10 | 8.0 | 0.52% |
| Right rail | 1469 – 1909 | 441 | 352.8 | 22.97% |
| Outer right margin | 1910 – 1918 | 9 | 7.2 | — |

Ratios of usable width (subtract the 8 dp gutters and margins): left **24.1%**,
centre **52.2%**, right **23.4%** (approximately 1 : 2.17 : 0.97).

```
+--+----------------+ +---------------------------+ +-------------------+
|  | Your Library   | | Home / Search / detail     | | Queue / Now playing|
|  | 454 px         | | 985 px                     | | 441 px            |
+--+----------------+ +---------------------------+ +-------------------+
   10 px gutters at each side of every container
```

## 3. Horizontal containers — library collapsed

| Region | Physical x | Width (physical) | Width (dp) |
| --- | ---: | ---: | ---: |
| Left rail | 10 – 98 | 89 | 71.2 |
| Gutter | 99 – 109 | 11 | 8.8 |
| Centre | 110 – 1458 | 1349 | 1079.2 |
| Right rail | 1469 – 1909 | 441 | 352.8 |

Collapsing the library releases **364 physical px (291 dp)** to the centre. The
right rail stays fixed. The rail's width is close to two standard 40 px icon
buttons plus padding, not zero; it remains usable as a navigation strip.

## 4. Container geometry

- Corner radius: approximately **8 physical px (6.4 dp)** on the outer
  container corners; inner artwork/card corners are larger relative to size.
- Gutter between containers: **10 physical px = 8 dp**.
- Container fill: near-black `rgb(18, 18, 18)` / `#121212`; global background is
  pure black. The right rail uses the same fill.
- Divider line between global bands: a near-invisible hairline; the player band
  reads as a separate surface rather than a bordered card.
- Containers run the full workspace height and clip their own scrolling content.
- The player bar spans the full width and is *not* inset like the three
  containers.

## 5. Global top bar measurements

| Element | Approximate physical bounds | Notes |
| --- | --- | --- |
| Caption / drag region | y 0 – 87, full width | Integrated; no native title bar |
| Back / forward cluster | x 66 – 130, y 40 – 64 | 22 px icons, 32 px hit targets |
| Home circle | x 632 – 675, y 30 – 70 | 40 px diameter |
| Search pill | x 698 – 1290, y 24 – 72 | ~592 x 48 px (474 x 38 dp) |
| Notification / friends | x 1578 – 1648 | 20 px icons, 32 px targets |
| Avatar | x 1670 – 1696 | 32 px circle |
| Caption controls | x 1830 – 1910 | minimise/maximise/close |

The search pill is horizontally centred in the window, not in the centre
container. Its width is roughly **31% of the window** at this size.

## 6. Player bar measurements

| Element | Approximate physical bounds | Notes |
| --- | --- | --- |
| Player band | y 1023 – 1131 | 109 px = 87 dp |
| Artwork | x 20 – 74, y 1043 – 1097 | 54 px square |
| Track text block | x 88 – 260 | title + artist, two lines |
| Shuffle | x 1028 – 1056 | centred control group starts here |
| Previous | x 1076 – 1108 | |
| Play/pause circle | x 1128 – 1172 | 44 px circle |
| Next | x 1192 – 1224 | |
| Repeat | x 1244 – 1272 | |
| Seek row | y 1100 – 1112 | thin track, times at x 944 / 1272 |
| Right controls | x 1550 – 1900 | lyrics, queue, volume, now playing |

The centre control cluster is centred on the **window**, not on the centre
container. The left and right clusters are each roughly 280–300 px wide in the
capture, leaving the centre free.

## 7. Responsive observations and limits

- At 1920 physical width the three containers fit without horizontal scrolling.
- The right rail is fixed at ~441 px; closing it gives the centre the extra
  width (did not capture a clean post-close frame; the `x` affordance and its
  position were verified in the Queue header).
- The left container can be collapsed to ~89 px; no intermediate widths were
  captured.
- Narrow-window reflow, minimum widths, and drag-resizing were **not** verified.
  Revibe will therefore choose explicit minimums rather than claiming to match
  Spotify's unknown behaviour.

## 8. Revibe target mapping (proposal, not a Spotify measurement)

Use the measured proportions as a starting point, not as fixed pixels:

| Revibe state | Left | Centre min | Right | Gutters |
| --- | ---: | ---: | ---: | ---: |
| Expanded default | 280 dp | 520 dp | 340 dp | 8 dp |
| Left collapsed | 72 dp | 520 dp | 340 dp | 8 dp |
| Right closed | 280 dp | 720 dp | 0 | 8 dp |
| User-resized | 220–420 dp | >= 480 dp | 280–420 dp | 8 dp |

The implementation must survive a 1280 x 720 logical window: side panels are
clamped, then the right panel closes before the centre drops below its minimum.
