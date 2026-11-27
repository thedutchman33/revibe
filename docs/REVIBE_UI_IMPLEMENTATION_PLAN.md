# Revibe UI implementation plan — flexible three-container shell

Status: **stage 1 implemented and manually verified**. Playback, catalog and
recommendation work is tracked separately. Evidence: `docs/revibe-layout/`.

Reference: `docs/spotify-reference/SPOTIFY_LAYOUT_MEASUREMENTS.md`,
`SPOTIFY_COMPONENT_REFERENCE.md`, `SPOTIFY_ANIMATION_AUDIT.md`.

## Goal

Replace Revibe's rigid Row with a persistent three-container desktop shell that:

- resizes and collapses the left library;
- resizes and closes the right rail;
- keeps the centre responsive and above a usable minimum;
- keeps the player bar permanently at the bottom;
- persists widths and collapse states in SQLite;
- preserves every current route, action and provider.

This is a UI-shell change only. It must not change queue semantics, playback
engine selection, data sources or SQLite schema beyond settings values.

## Current implementation

`shared/src/commonMain/kotlin/com/revibe/ui/RevibeApp.kt`:

```
Column
  topBar()
  Row(weight = 1)
    Sidebar(vm)                 // fixed 288 dp / 76 dp when compact
    Box(weight = 1) { route }   // no width constraint
    if (rightPanelVisible) RightPanel(vm)  // fixed 344 dp
  PlayerBar(vm)                 // fixed 94 dp
```

Relevant current files:

- `shared/src/commonMain/kotlin/com/revibe/ui/components/Chrome.kt` — `Sidebar`
  width is hard-coded `288.dp` / `76.dp`; `PlayerBar` is fixed 94 dp.
- `shared/src/commonMain/kotlin/com/revibe/ui/components/RightPanel.kt` —
  `.width(344.dp)` hard-coded; no close control in the rail header.
- `shared/src/commonMain/kotlin/com/revibe/ui/AppState.kt` —
  `compactSidebar`, `rightPanelVisible`, toggles and settings persistence.
- `shared/src/commonMain/kotlin/com/revibe/model/Models.kt` — `AppSettings`.
- `shared/src/commonMain/kotlin/com/revibe/settings/SettingsRepository.kt` —
  SQLite-backed `settings` key/value store.
- `shared/src/commonMain/kotlin/com/revibe/data/JdbcLibraryStore.kt` — already
  supports `getSetting` / `setSetting`; no schema change needed for layout.

## Layout contract

### Widths

| State | Left | Centre | Right | Gutters |
| --- | ---: | ---: | ---: | ---: |
| Default expanded | 280 dp (persisted) | flexible, min 480 dp | 340 dp (persisted) | 8 dp |
| Left collapsed | 72 dp rail | flexible | persisted | 8 dp |
| Right closed | persisted | flexible | 0 | 8 dp |
| Both closed at 1280 dp logical | 72 | 1200 – 16 | 0 | 8 |

- Left expanded range: **220–420 dp**.
- Right range: **280–420 dp**.
- Hard centre minimum: **480 dp** at default window size; below that the right
  rail auto-closes before the centre is squeezed.
- Minimum window width remains the existing desktop window minimum; this plan
  does not add a new window-size dependency.
- All spacing uses `dp`; no physical pixels in Compose code.

### Drag handles

- An 8 dp-wide transparent hit area on each side-panel edge.
- Hover: a 1–2 dp vertical highlight line.
- Drag: updates the persisted width immediately (no animation lag); cursor
  stays an east-west resize cursor (platform cursor where available).
- Double-click reset is planned for the motion/polish stage; it is not in stage 1.
- Collapsed left: dragging its edge expands it first, then resizes.
- Closed right: no resize handle until reopened.
- Keyboard: not required for stage 1; an accessible resize alternative is a
  later polish item.

### Collapse / close

- Left: existing chevron toggles `compactSidebar`; width target 72 dp; animated.
- Right: add an `x` button in the rail header and keep the existing
  `toggleRightPanel`; selecting Now playing / Lyrics / Queue reopens it.
- Persist `leftPanelWidth`, `rightPanelWidth`, `compactSidebar`,
  `showRightPanel`.
- On app start, clamp persisted widths to the current window so a layout saved
  on a wide monitor cannot create an unusable window.

### Player

- Player stays outside the draggable workspace and spans the full window width.
- Do not make it part of any horizontal resize.
- No change to player layout in this stage beyond accepting the shell's height.

## Files to modify

| File | Change |
| --- | --- |
| `shared/src/commonMain/kotlin/com/revibe/model/Models.kt` | Add `leftPanelWidth: Float = 280f`, `rightPanelWidth: Float = 340f` to `AppSettings` |
| `shared/src/commonMain/kotlin/com/revibe/settings/SettingsRepository.kt` | Read/write the two widths; clamp to ranges; keep old defaults when absent |
| `shared/src/commonMain/kotlin/com/revibe/ui/AppState.kt` | Expose widths; `setLeftPanelWidth`, `setRightPanelWidth`, reset helpers; debounce SQLite writes; clamp against window-independent limits |
| `shared/src/commonMain/kotlin/com/revibe/ui/RevibeApp.kt` | Compose the resizable Row, handles and animation; keep route Crossfade and modals |
| `shared/src/commonMain/kotlin/com/revibe/ui/components/Chrome.kt` | `Sidebar(vm, width: Dp)`; collapsed width 72 dp; preserve row/nav rendering |
| `shared/src/commonMain/kotlin/com/revibe/ui/components/RightPanel.kt` | `RightPanel(vm, width: Dp, onClose: () -> Unit)`; add close control |
| `shared/src/commonMain/kotlin/com/revibe/ui/components/ResizeHandle.kt` | **new** small reusable handle; drag callback + hover line |
| `shared/src/commonMain/kotlin/com/revibe/model/LayoutSpec.kt` | **new** width constants and pure clamp function |
| `shared/src/desktopTest/kotlin/com/revibe/LayoutSettingsTest.kt` | **new** clamp and SQLite persistence tests |
| `shared/src/commonMain/kotlin/com/revibe/ui/components/PlatformCursor.kt` + desktop actual | **new** east-west resize cursor |

Do **not** modify `PlayerController`, `CatalogSource`, `JdbcLibraryStore` schema,
or route screens for this stage.

## Milestones

### Stage 1 — Shell foundation (implemented)

1. Add settings fields and `SettingsRepository` load/save. **Done**
2. Add pure `clampLeftWidth`, `clampRightWidth` and tests. **Done**
3. Add `ResizeHandle` with drag + platform resize cursor. **Done**
4. Wire left/right widths in `RevibeApp`. **Done**
5. Show a close button in `RightPanel`. **Done**
6. Compile, run the suite and launch the app. **Done — 28 tests, 0 failures**
   - left drag, right drag, collapse/expand left, close/reopen right, restart
     persistence and fixed player were verified manually (`docs/revibe-layout/`);
   - 1120 dp minimum and non-100% DPI are not yet verified; a forced resize was
     inconclusive and was not counted.

Acceptance: no route regressions, no audio change, widths persist across
restart, centre never collapses below its minimum at the default window size.
Remaining before calling stage 1 fully complete: a 1120 dp visual pass and a
150%/200% DPI pass.

### Stage 2 — Chrome alignment

- Make `TopBar` search width responsive instead of max 520 dp fixed.
- Make player left/right clusters shrink gracefully before the centre controls.
- Add hover/focus states for handles and rail close.
- Ensure scroll areas clip to the new container widths.

### Stage 3 — Right rail and queue presentation

- Right rail accepts the queue engine's manual/auto sections.
- Keep tabs visible at 280 dp; truncate labels, never wrap controls.
- Add empty/loading/error states for queue autoplay.
- Do not add a fake "Recently played" tab until real history is wired.

### Stage 4 — Motion and keyboard polish

- 220 ms collapse; 180 ms right-panel open/close; 120 ms hover.
- Escape closes the context menu; not the rail.
- Add documented keyboard shortcuts only after checking current handlers.
- Reduced-motion fallback where the toolkit supports it.

### Stage 5 — Visual verification

- Capture Revibe at 1920x1200 / 125% and at the minimum window width.
- Compare proportions, not pixels, against the reference tables.
- Verify no clipped controls at 220 dp left / 280 dp right.
- Record any deliberate deviations in `docs/PROJECT_STATUS.md`.

## Testing strategy

- Pure layout tests for clamps and defaults (fast, no Compose).
- Existing `./gradlew :shared:desktopTest` must stay green.
- Compose UI coverage currently covers the Account import-history card and its guarded undo.
  Pointer interactions such as drag reordering remain manual; do not claim drag behaviour is
  unit-tested.
- Persistence check: change widths, close app, reopen, verify SQLite values via
  the settings UI or a focused repository test.
- No screenshots are treated as proof of interaction; hover/drag need manual
  observation.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Compose pointer drag steals clicks from headers | Handle sits in the 8 dp gutter, not over controls |
| Animated width fights drag updates | Direct width while dragging; animate collapsed/expanded only |
| Persisted width too large for a smaller monitor | Clamp on load and on window size changes |
| Right rail closes unexpectedly when narrowing | Only auto-close at a documented breakpoint; keep a visible reopen control |
| Sidebar content overflows at 220 dp | Use ellipsis and existing compact layout; test 220 dp |
| Existing player tests depend on 94 dp | Do not change PlayerBar height in this stage |

## Out of scope for this plan

Queue generation, autoplay, recommendation scoring, playback DSP, search
suggestions, Spotify import, cloud sync and installer work. Those have separate
plans or roadmap phases.
