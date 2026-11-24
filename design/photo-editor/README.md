# 留白 · Photo editor design prototype

Mobile interaction prototype for the approved `docs/research/photo-editor-fusion-plan-20261124.md` scope. Uses Astryx Core/CLI 0.7.17, the imported neutral theme in dark mode, React 19, and Vite. No Android source changes or external image services.

## Run

```sh
npm ci
npm run dev -- --port 5178
npm run build
npx astryx doctor --built dist
npx tsc --noEmit --jsx react-jsx --moduleResolution bundler --module esnext --target es2022 --skipLibCheck src/main.tsx
```

Open `photo-editor.html` directly for the standalone interactive deliverable. It embeds its code and styles and needs no external image resources. `photo-editor-build.zip` contains the Vite `dist/` build for static hosting. To regenerate the standalone file: `npx astryx experimental export src/main.tsx --out photo-editor.html --title '留白 · 图片编辑器'`.

The workspace browser launch configuration is `photo-editor-design`. `npm run build` creates `dist/`; dependencies and build output are ignored. `screenshots/` contains captured preview evidence. `vite.config.ts` compiles app StyleX declarations using `@stylexjs/unplugin`; component `xstyle` handles the chrome and raw canvas elements use `stylex.props`. Numeric coordinates, authored colors and font pixel sizes inside the canvas compositor are image content, separate from UI theme tokens.

## Interaction

- Canvas-first layout with collapseable inspector and horizontally scrollable tool dock. All nine tools retain a text label on 360dp-class screens. Crop hides the tool dock; history opens a full-frame overlay.
- Eleven categorized filter previews, live intensity, and pointer/keyboard hold-to-compare. Brightness, contrast and saturation are interactive.
- Sticker and emoji palettes add canvas layers. Drag a selected layer; use size/rotation sliders or delete it. Text supports editable content, outline and background.
- Centered crop ratios (original, square, 4:3, 16:9, 9:16), crop rotation, cancel and apply. Export crops the composited PNG.
- Brush and illustrative mosaic strokes, image rotation and mirror.
- Snapshot undo/redo, selectable history rows and original-image restoration. Editing a restored history entry discards the later redo branch. Cmd/Ctrl-Z and Shift-Cmd/Ctrl-Z work outside text inputs.
- Export generates a local PNG download without selection guides. The coast image is original artwork drawn procedurally in the browser; it has no network dependencies.

## Native Android port

For a source-backed migration plan aligned with the current `:mediaview` implementation, see [`docs/design/photo-editor-native-port-20261124.md`](../../docs/design/photo-editor-native-port-20261124.md). The short mapping below is an overview; the browser prototype itself is not the Android implementation.

## Compose mapping

| Prototype | Proposed Compose mapping |
| --- | --- |
| Astryx `Layout` header/content/footer | `Scaffold`, compact top actions, weighted `Box` canvas, bottom tool region |
| Tool dock / inspector | `LazyRow` labeled tools; `AnimatedVisibility` or modal bottom sheet for tool controls |
| Canvas `Layer` | `EditOp.Sticker` / enhanced `EditOp.Text`; normalized x/y, relative size and rotation |
| `timeline` snapshots and cursor | Existing immutable `PhotoEdits` operation-list undo/redo snapshots |
| Filter category / intensity | `FilterPresets.ids` plus new LUT assets and existing intensity preview path |
| Outline / background toggles | Optional `outlineColor` and `backgroundArgb` fields in text rendering |
| Ratio crop / full-frame history | Fullscreen Compose crop route and history snapshot selection surface |
| Live slider `draft`, commit on release | Existing `withOpsUnrecorded` / live base preview, then record one snapshot |
| PNG compositor | Existing `PhotoRenderer.renderBase` / `drawOps`, export and draft tray |

The starting point was Astryx's scaffolded `canvas-editor` template. The mobile adaptation keeps its fixed-coordinate artboard model, three-region frame, toolbar `gap={2}` and inspector `padding={3}`, and moves the desktop inspector below the canvas. UI colors and spacing use imported theme tokens/component props. Artwork has its own authored palette.

## Prototype boundaries

This is a browser design prototype, not a Kotlin implementation or APK. Filters use Canvas/CSS approximations rather than production 33³ LUTs. The sample is a local illustration; photo import, draft persistence, large-image/OOM behavior and device performance are not implemented or verified. Sticker resize/rotation use sliders rather than two-finger gestures; the crop is centered with fixed ratios rather than draggable corner handles. Text uses the system font and does not yet include letter spacing or font selection. Mosaic uses illustrative colored blocks and must not be used to redact real private images. Export is PNG at the sample's 900×1200 base resolution (or centered crop), not a full-resolution Android export. Browser download initiation does not prove filesystem persistence.

The nine-tool scroll treatment is a design proposal; production Compose should preserve the existing eight-slot narrow-screen behavior until the ninth-tool placement is accepted. No new Gradle dependencies are proposed.
