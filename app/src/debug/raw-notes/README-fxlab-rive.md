# fx-lab Rive fixtures — provenance and licensing

Files: `app/src/debug/res/raw/fxlab_loopy.riv` (211 B), `app/src/debug/res/raw/fxlab_circle_move.riv` (269 B)

- Source: `rive-app/rive-android` tag `11.16.3`,
  `app/src/main/res/raw/loopy.riv` and `app/src/main/res/raw/circle_move.riv`
  (fetched 2026-11-24 from `raw.githubusercontent.com`; byte sizes verified 211 / 269).
- `rive-app/rive-android` is MIT-licensed, **but that license text covers "the Software"**.
  The repo carries **no asset-level grant for the `.riv` files**. Redistribution rights for
  these two sample files are therefore **not established**.
- Consequence (user decision 2026-11-24, recorded in the design doc §13): the two files are
  used **only as debug comparison fixtures**, live in `app/src/debug` (they are physically
  absent from release builds — verified by `assembleChatRelease` + APK grep), and no other
  sample media from that repo is copied (explicitly not `walle.riv`, which references
  external images).
- Replace with self-exported assets: open Rive's free editor, export a `.riv` under the same
  resource names (or add new ones and extend `riveRawResFor`), then delete these fixtures.
  Nothing else in the Lab needs to change.

Do not move these files out of `app/src/debug` while the licensing question is unresolved.
