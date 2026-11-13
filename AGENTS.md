# Repository Guidelines

## Project Overview
Ancient Alpha is a NextGen Fabric client mod targeting Minecraft 26.3. It implements high-performance combat automation, anticheat compensation (specifically targeting GrimAC and Vulcan checks), a Material 3 inspired ClickGUI with MSDF font and SDF shader pipelines, and an offline-trained neural network rotation engine. Mod metadata disguises the binary as `lithium-fabric` (`meow.ancient`).

---

## Architecture & Data Flow

### Core Architectural Layers
```
[Minecraft Game Loop & Network Netty Stack]
                   │
                   ▼ (Mixins at priority 1337)
        [Java Mixins Bridge] ──> invokes ──> [Kotlin Singletons (.INSTANCE)]
                   │
                   ▼
       [Flora EventManager (post / register / unregister)]
                   │
         ┌─────────┴───────────────┐
         ▼                         ▼
   [Core Managers]         [Active Modules]
(Rotation, Anticheat,     (HitAura, Speed, Chams,
 SilentSwap, Slowness)     ClickGui, Hud, etc.)
         │                         │
         └─────────┬───────────────┘
                   ▼
    [Client State & Outbound Packets]
```

### Event Bus & Lifecycle
- **Event Bus**: Powered by Flora (`sweetie.evaware.flora.Flora`). `EventManager` exposes `post(any)`, `register(any)`, and `unregister(any)`. Handlers use `@Commando` annotations from `sweetie.evaware.flora.api.Commando` with optional priority levels (`@Commando(priority = -128)`).
- **Client Lifecycle**: `Ancient.kt` implements `ClientModInitializer`. Initializes audio, themes, Vulkan/MSDF pipelines, core event hooks (`SlownessManager`, `ServerCritState`), managers, and active configuration. It hooks shutdown via `ClientLifecycleEvents.CLIENT_STOPPING` and a JVM shutdown hook to flush configs and terminate active dataset recording.

### Subsystem Interactivity & Data Flow
- **Rotation System**: `RotationManager` maintains decoupled client and server rotations (`currentRotation`, `previousRotation`).
  - Injected into `MixinLocalPlayer.sendChanges`: writes spoofed yaw/pitch to movement packets, then restores local camera angles.
  - Movement vector alignment: `MoveFixProcessor` converts user WASD inputs into server-facing motion under `MoveFixMode.SILENT` or `FREE`.
  - Third-person rendering: `MixinLivingEntityRenderer` reads `RotationManager.getServerRotation()` to ensure head pitch matches server packets.
- **Combat Pipeline (`HitAuraModule`)**:
  - Modular pipeline: `TargetSelector` -> `AttackExecutor` -> `AuraRotationProcessor` -> `SprintResetProcessor` -> `CriticalProcessor` -> `ShieldBreakProcessor`.
  - Rotations are interchangeable strategies (`Grim1204Rotation`, `SnapRotation`, `NeuroRotation`, `FuntimeRotation`, `UniversalRotation`, `AdvancedRotation`).
- **Anticheat Evasion**:
  - `AnticheatManager`: Reads active configuration from `AntiCheatModule` (sprint reset types, slot bypass modes, health sources).
  - `SilentSwapUtil`: Executes server hotbar swaps (`withSlot(slot) { ... }`) and coalesces slot restorations to evade Grim desync checks.
  - `SlownessManager`: Throttles packet bursts and movement impulses (`MixinKeyboardInput`) after container interactions.
  - `SilentPacketQueue`: Sends network packets bypassing event handlers to avoid recursive loops.
- **Neuro ML Subsystem**:
  - `NeuroRecorder`: Logs combat tick telemetry to CSV (`run/ancient/neuro/data/`).
  - Offline training (`train_aura.py`, `common.py`): Trains a GRU + Gaussian Mixture Model in PyTorch and exports weights to JSON.
  - In-game inference (`NeuroModel.kt`): Zero-dependency pure Kotlin CPU matrix multiplication and Gaussian mixture sampling.
  - `NeuroRotationTracker`: Computes mouse delta quantization (GCD) and tracks rotation trajectories.

---

## Key Directories

```
.
├── .github/workflows/          # CI release build workflow
├── assets/ancient/             # Shaders, sounds, fonts, and Python ML trainer scripts
├── gradle/wrapper/             # Gradle wrapper distribution (9.8.2)
├── run/                        # Local runtime directory used by runClient (configs, logs, datasets)
└── src/main/
    ├── java/meow/ancient/
    │   └── mixin/              # Java Mixins injecting into Minecraft 26.3 classes
    ├── kotlin/meow/ancient/
    │   ├── Ancient.kt          # Client mod entry point
    │   ├── client/
    │   │   ├── command/        # CommandManager and command implementations
    │   │   ├── config/         # JSON config loader, serializer, and auto-save manager
    │   │   ├── extensions/     # SafeClientContext and Minecraft extension methods
    │   │   ├── gui/
    │   │   │   ├── hud/        # HUD rendering, dragging, and settings popups
    │   │   │   └── material/   # Material 3 ClickGUI, components, and animations
    │   │   ├── listener/       # Input and Render listeners
    │   │   └── util/
    │   │       ├── anticheat/  # Anticheat bypass policies and Grim integration
    │   │       ├── combat/     # Falling player, crit timings, server crit tracker
    │   │       ├── inventory/  # Silent hotbar swaps, slowness manager, equipment helpers
    │   │       ├── neuro/      # In-game neural network inference and trainer discovery
    │   │       ├── render/     # Chams renderer, Vulkan shape/font pipelines
    │   │       └── rotation/   # RotationManager, MoveFixProcessor, rotation trajectory math
    │   ├── event/              # EventManager, Event/EventCancellable, and concrete event types
    │   ├── module/             # Module base class, ModuleManager, and Setting delegates
    │   │   └── modules/        # Module implementations (combat, movement, player, render, other)
    │   └── network/            # SilentPacketQueue
    └── resources/              # fabric.mod.json, ancient.mixins.json, ancient.accesswidener, assets
```

---

## Development Commands

### Build & Package
```bash
# Build production mod jar and sources jar (outputs to build/libs/)
./gradlew build

# Compile Kotlin sources only (JVM 21 target)
./gradlew compileKotlin

# Compile Java sources only (Java 21 target)
./gradlew compileJava

# Process and expand version into fabric.mod.json
./gradlew processResources
```

### Run Client
```bash
# Launch Minecraft 26.3 with mod loaded in Fabric Loom dev environment
./gradlew runClient
```

*Note: No automated test task (`./gradlew test`) or test source set is configured in the repository.*

---

## Code Conventions & Common Patterns

### Singletons vs Classes
- Use `object` for managers, modules, utility facades, and single-instance renderers:
  ```kotlin
  object ModuleManager : ILoadable { ... }
  object HitAuraModule : Module("HitAura", Category.COMBAT, "Attacks entities") { ... }
  ```
- Use `class` for events, settings, UI components, calculation sub-processors, and isolated state instances.

### Module Declaration & Settings DSL
Modules extend `Module` and register configuration properties via built-in delegate methods:
```kotlin
object ExampleModule : Module("Example", Category.COMBAT, "Module description") {
    private val speed by number("Speed", 1.0, 0.1, 5.0, 0.1)
    private val bypass by boolean("Grim Bypass", true)
    private val mode by mode("Mode", "Normal", listOf("Normal", "Fast"))

    override fun onEnable() {
        // Automatically registered to EventManager upon enable
    }

    override fun onDisable() {
        // Automatically unregistered from EventManager upon disable
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        safeRun {
            if (!inGame) return@safeRun
            // Safe player and level access
        }
    }
}
```

### Event Handling
- Subscribe methods with `@Commando` from `sweetie.evaware.flora.api.Commando`.
- For priority handling, use integer priority values (e.g. `@Commando(priority = -128)`).
- Cancellable events extend `EventCancellable`: call `event.cancel()`.
- Netty packet listening uses `PacketSendEvent` and `PacketReceiveEvent`.

### Safe In-Game Context
- Direct global getters `mc.player` and `mc.level` can be null.
- Always check `if (!inGame) return` or use `safeRun`:
  ```kotlin
  safeRun {
      val eyePos = player.eyePosition
      val currentItem = player.mainHandItem
  }
  ```

### Mixin Conventions
- Mixins are written in Java under `src/main/java/meow/ancient/mixin/`.
- Configured with priority `1337`, `compatibilityLevel: "JAVA_21"`, and `defaultRequire: 1`.
- Mojang official mappings are used directly.
- Mixins bridge to Kotlin singletons via `.INSTANCE`:
  ```java
  @Inject(method = "sendChanges", at = @At("HEAD"))
  private void onPreSendChanges(CallbackInfo ci) {
      RotationManager.INSTANCE.onPreSendChanges();
  }
  ```

### Concurrency & Thread Safety
- Minecraft logic must execute on the client render thread: wrap deferred calls in `mc.execute { ... }`.
- Background asynchronous tasks must run on daemon threads:
  ```kotlin
  thread(name = "Ancient-Worker", isDaemon = true) {
      // background operation
  }
  ```
- Shared mutable state accessed across threads must use `@Volatile`, `@Synchronized`, or thread-safe structures (`Collections.synchronizedMap`).

---

## Important Files

| File Path | Description |
|---|---|
| `src/main/kotlin/meow/ancient/Ancient.kt` | Mod entry point implementing `ClientModInitializer`. |
| `src/main/kotlin/meow/ancient/module/Module.kt` | Base module class with settings delegate factory methods. |
| `src/main/kotlin/meow/ancient/module/ModuleManager.kt` | Registry holding all client modules. |
| `src/main/kotlin/meow/ancient/event/EventManager.kt` | Facade for Flora event dispatching and registration. |
| `src/main/kotlin/meow/ancient/client/util/rotation/RotationManager.kt` | Core rotation engine managing spoofed view angles and move fixes. |
| `src/main/kotlin/meow/ancient/client/util/inventory/SilentSwapUtil.kt` | Anticheat-safe hotbar swap utility. |
| `src/main/kotlin/meow/ancient/client/util/neuro/NeuroModel.kt` | Zero-dependency pure Kotlin GRU ML inference engine. |
| `src/main/resources/fabric.mod.json` | Fabric mod metadata, dependencies, and Kotlin entrypoint definition. |
| `src/main/resources/ancient.mixins.json` | Mixin configuration registering 33 client injectors at priority 1337. |
| `src/main/resources/ancient.accesswidener` | Official Mojang mapping access widener definitions. |
| `build.gradle` & `gradle.properties` | Loom 1.19.2, Kotlin 2.4.21, and JVM 21 build configuration. |

---

## Runtime/Tooling Preferences

- **Java Version**: Java 21 compilation (`options.release = 21`, `jvmTarget = JVM_21`). Java 25 is used in CI and recommended for client runtime.
- **Kotlin Version**: `2.4.21` using Fabric Language Kotlin `1.14.2+kotlin.2.4.21`.
- **Minecraft Version**: `26.3` with Fabric Loader `0.19.5+` and Fabric API `0.164.0+26.3`.
- **Build System**: Gradle 9.8.2 via `./gradlew` with Fabric Loom 1.19.2.
- **Obfuscation / Mappings**: `fabric.loom.disableObfuscation=true`. Uses official Mojang mappings (`Mojmap`) in both development and production.
- **Python / Machine Learning**:
  - Python 3.10+ with `torch` and `numpy` is needed **only** for training new neural rotation weights via `train_aura.py`.
  - The client dynamically locates Python via PATH, `.venv`, Conda, Mise, Pyenv, or `python.txt`.
  - In-game inference requires no Python or native C++ libraries; it runs directly on the JVM.

---

## Testing & QA

### Testing Setup
- There is **no automated unit testing suite** (no `src/test/`, JUnit, Kotest, or Fabric GameTest).
- CI (`.github/workflows/main.yml`) builds artifacts via `./gradlew build` on JDK 25 without running a test phase.
- AI assistants must not generate boilerplate mock tests or introduce JUnit suites unless requested.

### Manual In-Game QA Protocol
Testing is conducted manually in the Fabric Loom client environment (`./gradlew runClient`):
- **Combat & Rotations**: Use `FakePlayerModule` (`src/main/kotlin/meow/ancient/module/modules/player/FakePlayerModule.kt`) to spawn dummy client-side `RemotePlayer` targets to test reach distance, hitbox detection, rotation smoothing, and attack cooldowns.
- **UI Components**: Use `TestModule` (`src/main/kotlin/meow/ancient/module/modules/other/TestModule.kt`) to verify Material 3 shape rendering, curve editors, and color pickers.
- **Mixins**: Injection points must strictly match Minecraft 26.3 Mojang mappings; check client startup logs for mixin injector errors.
- **Build Cleanliness**: All modifications must pass `./gradlew build` cleanly with zero compilation failures.
