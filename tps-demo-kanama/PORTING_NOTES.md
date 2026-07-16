# TPS Kanama Port Notes

## Scope

This is a public Kanama/Kotlin port of Godot's TPS demo snapshot:

- Source repo: https://github.com/godotengine/tps-demo
- Source commit: `e3bfd239fd53479eb6b7ea565f6f0732937c1c1f`
- Import method: clean tracked-file snapshot; no upstream or private Git
  history is included

Ported gameplay `.gd` files were removed after their scene references were
rewired to Kotlin scripts in `kotlin-src/`. The remaining `.gd` files are the
Kanama Tools editor plugin scripts under `addons/kanama_tools/`.

## Validation

Useful checks from the `kanama-demos` repository root:

```sh
./gradlew tpsBuildScripts
./gradlew tpsBuildAndSmokeGodot
./gradlew tpsBuildAndBulletSmokeGodot
./gradlew tpsBuildAndReloadSmokeGodot
```

`tpsBuildAndRenderSmokeGodot` opens a small window and exercises the render
path used by robot death parts.

## Intentional Differences From Upstream

- `TpsSettings.applyGraphicsSettings()` skips window-mode changes while running
  headless so validation does not exercise display mode transitions.
- The online menu is a touch-sized LAN lobby on every platform. Clients wait
  for `connected_to_server` before loading, report connection errors in-place,
  and can cancel an in-flight join; hosts display a usable local IPv4 address.
  The host starts the match explicitly, then an RPC readiness barrier holds the
  scene switch until every peer has loaded, preventing early spawner packets
  from racing slower mobile clients.
- Gameplay and authority setup remain unchanged from the existing Kotlin port.
  The lobby consumes the standard integer peer IDs emitted by Godot; the iOS
  callable bridge defect that previously replaced those IDs with `null` is
  fixed in Kanama rather than worked around here.
- The menu UI root is reduced to the mobile display safe area so its lobby
  fields and actions stay clear of notches, cutouts, and home indicators.
- The Kotlin settings path keeps SSAO disabled when the UI/config says it is
  disabled.
- Robot death parts no longer spawn the short `PartDisappear` puff or
  queue-free each individual part after fading. Under Godot 4.7 preview builds
  on Metal, that faithful visual cleanup path produced native render/physics
  instability during repeated robot death checks. The port keeps the gameplay
  behavior and uses a more conservative cleanup path.

## Review Notes

This port is useful coverage for larger Godot projects because it exercises
threaded loading, multiplayer/RPC metadata, `MultiplayerSynchronizer`
properties, generated RPC helpers, typed script calls, and scene reload
cleanup. The smoke tasks cover startup, robot death, real bullet collision,
and level reload paths; manual gameplay is still the best check for feel and
full multiplayer behavior.
