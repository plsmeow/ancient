# Scaffold: LiquidBounce Normal adaptation

Source: CCBlueX/LiquidBounce, commit `0eccefdb56d5afaca6443ec79016be2c3529dbce`, retrieved 2026-11-15.
Copyright (c) 2015–2026 CCBlueX. Upstream GPL-3.0-or-later headers are preserved in the adapted helpers; license and source attribution are packaged in META-INF. Earlier Meteor attribution and GPL-3.0-only license remain packaged. Distribution must satisfy the applicable GPL terms and provide corresponding source.

## Ported behavior

- LiquidBounce Normal targets the block below current feet. SameY Off follows `floor(feetY) - 1` while airborne, ascending or descending. Keep Y uses the last grounded row. The upstream normal stationary-jump exception also follows feet, even with Keep Y enabled.
- The former Ancient DDA path was removed. The Normal offsets are the upstream 18 cells: x/z in -1..1 and y in 0,-1 relative to the requested position, sorted by length/y/x/z. Already solid support at the requested position immediately stops target finding. Keep Y restricts offsets to its row.
- Movement planning uses stable support selection (surface height, overlap, distance and hysteresis), a four-placement history, eight-direction hysteresis and a line following recent placements. Horizontal stair history is flattened to avoid introducing vertical movement into that line.
- Prediction uses the upstream expanded collision-box edge traversal over three blocks, a 0.2-block bootstrap backoff, 0.05 cutoff, four placement offsets and two-placement warmup. Prediction only changes horizontal aim; placement height always comes from current feet.
- Target finding resolves neighbor/self clicks through native BlockItem.updatePlacementContext and BlockPlaceContext. Stabilized targeting trims faces by 15%, crops to the movement line and finds the nearest point to the rotation line. Face selection prioritizes direct facing toward the predicted eye. Execution traces from the last sent eye/rotation and checks block, face, minimum shape-box Y and actual placement context.
- Block selection uses upstream walkable-block validation, rejects falling blocks/TNT/web/portal, and prefers favorable full blocks and larger stacks when choosing a hotbar slot. Main-hand/offhand blocks retain execution priority.

## Ancient integration

The module uses Ancient settings, Flora events, RotationManager, SilentSwapUtil, ActionCoordinator, inventory pause, PacketMine/placement reservations and native gameMode.useItemOn. Slots and look prepare before physics and refresh after aiStep. Native use occurs with an already matching sent look before physics or directly after matching sendChanges. Additional rotation packets, upstream timer modes, click simulation, alternate techniques and synthetic tower modes are not imported.
Existing one-attempt-per-tick tracking, one outstanding server confirmation, timeout/backoff/retry caps and server-confirmed Safe Walk remain. These are Ancient integration choices and affect pacing relative to upstream. Safe Walk preserves the physical jump input and does not clip upward jump movement; the makeJump guard only suppresses automatic jumping when the physical jump key is not held.

## Validation

Required build: `./gradlew build`. Standalone JVM checks cover changing height during ascent/descent, Keep Y and stationary jumping, exact upstream offsets, candidate priority, direction hysteresis, stabilized face geometry, connected-support edge traversal, rejection/confirmation timing and edge clipping. No test suite was added to the repository.
Live client/server QA remains required for jumping/stair placement, walking/sprinting, diagonals/turns, partial blocks, main/offhand, latency/rejections and exact Grim/server versions. Compilation and pure geometry checks do not demonstrate live anticheat compatibility.

## Exact upstream sources

| File | SHA-256 |
|---|---|
| [ModuleScaffold.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/ModuleScaffold.kt) | `a0cc6eb38d7c49a80e648491f2c4c4cd8fb0017d8281601a8630bdae0ba0c0c7` |
| [ScaffoldNormalTechnique.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/techniques/ScaffoldNormalTechnique.kt) | `f27ac8aa8e3dfa018ace1fc5a92c5c9cc2e904f15d4920cf1c0eb133248c3452` |
| [ScaffoldTechnique.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/techniques/ScaffoldTechnique.kt) | `c1a1c38fad72f95f9f35bab5c2608fb07a3010ad64aef2faaecffb3a6cb8f4ee` |
| [ScaffoldMovementPlanner.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/ScaffoldMovementPlanner.kt) | `2c4de58ccbff70573fcc9c8e97ba22442132b424bf2e5583bb3d95f903a87ab5` |
| [ScaffoldMovementPrediction.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/features/ScaffoldMovementPrediction.kt) | `c310be7d0893331c5a1f44e21eff8e87d294dbe7ac4312301d7332b2101e9faa` |
| [ScaffoldBlockItemSelection.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/ScaffoldBlockItemSelection.kt) | `1280a89e8ea9dbeaa37f7f7bb710e5e56d9c7167d60acf51d3c33a35c71559b6` |
| [BlockPosOffsets.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/utils/block/targetfinding/BlockPosOffsets.kt) | `fef30d7c32317a28c8fba28976e94920c1ce40c365f4be53e24eab15f3aef9f0` |
| [TargetFinding.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/utils/block/targetfinding/TargetFinding.kt) | `ccebc0b40113c4aaa8ab4558319af6530a66976855806716c6522f6affcde509` |
| [FaceTargetPositionFactory.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/utils/block/targetfinding/FaceTargetPositionFactory.kt) | `d627448cd58c30f621ae2f68f74f873e5f6164a80669f03275a1107076ab4115` |
| [PlacementVerification.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/utils/block/targetfinding/PlacementVerification.kt) | `99186d3089ecacb7163e4242f74b587d2f4c184cf8fb2dc0922d9a144361b0d3` |
| [MovementUtils.kt](https://github.com/CCBlueX/LiquidBounce/blob/0eccefdb56d5afaca6443ec79016be2c3529dbce/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/MovementUtils.kt) | `93dd05d26fb1d39a9852c4fc035d37b0931a0b34fd859736db8e3f8b34307825` |
