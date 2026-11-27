# Revibe flexible shell — verification captures

Date: 2026-11-26. Windows 11, 1920 x 1200 at 125% scaling. Revibe window
1440 x 900 dp (1800 x 1125 physical), launched with `:desktopApp:run`.

These are captures of the **Revibe** application after the first shell stage. They
are not Spotify evidence and live outside `docs/spotify-reference/`.

## Captures

| File | State |
| --- | --- |
| `01_expanded.png` | Default: library 280 dp, centre flexible, right rail 340 dp, player fixed |
| `02_library_resized.png` | Left gutter dragged; widths update live |
| `03_library_collapsed.png` | Library collapsed to the 72 dp icon rail; centre expands |
| `04_right_closed.png` | Right rail closed; centre expands |
| `05_queue_reopened.png` | Player Queue control reopens the rail on the Queue tab |
| `06_final.png` | Library expanded again (width persisted from the drag), queue open |
| `07_right_resized.png` | Right gutter dragged wider; centre narrows, player fixed |
| `08_restarted.png` | App closed and relaunched: widths and open rail restored from SQLite |

All images are full-resolution 1800 x 1125 PNGs. The chat-oriented previews were
deleted after review; the full PNG is the authority.

## Manually verified

1. The left gutter drag changed the library width without moving the player.
2. The library collapse chevron animated the container to the icon rail and the
   centre reflowed into the released width.
3. The right rail's `x` closed the rail and the centre expanded.
4. The player's Queue control reopened the rail with `Queue` selected.
5. The right gutter widened the rail to ~373 dp and narrowed the centre without
   moving the player or overflowing.
6. Closing and relaunching restored the dragged widths and the open right rail.
7. The player stayed at the bottom in every state.
8. At 1440 dp there was no overlap or horizontal overflow.

## Not verified manually

- 1120 dp minimum-window behaviour and 125%/150%/200% DPI switching. A forced
  Win32 resize produced an inconclusive/stale frame and was discarded.
- Hover line colour on the gutter (cursor moved too quickly for a still).
- Reduced-motion behaviour.

## Measured final state (`06_final.png`)

Physical pixels, 1800 x 1125 capture:

| Region | x range | Width |
| --- | ---: | ---: |
| Left library (resized) | 0 – 409 | 410 px (328 dp) |
| Gutter | 410 – 419 | 10 px (8 dp) |
| Centre | 420 – 1373 | 954 px (763 dp) |
| Gutter | 1374 – 1383 | 10 px (8 dp) |
| Right rail | 1384 – 1799 | 416 px (333 dp) |
| Top bar | y 0 – 86 | 87 px |
| Workspace | y 87 – 1005 | 919 px |
| Player | y 1007 – 1125 | 118 px (94 dp) |

The default capture (`01_expanded.png`) uses 350 px (280 dp) left. The right
rail measures 416 px (333 dp) at the capture edge; the configured 340 dp value
remains the persisted width used by the layout.

## Contract covered by tests

`shared/src/desktopTest/kotlin/com/revibe/LayoutSettingsTest.kt`:

- left clamps to 220–420 dp;
- right clamps to 280–420 dp;
- both widths survive a reload through `SettingsRepository` / SQLite.

**28 tests, 0 failures** in `./gradlew :shared:desktopTest` after this change.
