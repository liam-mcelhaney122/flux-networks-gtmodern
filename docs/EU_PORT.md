# GregTech CEu (EU) Port

This document describes the architecture, known caveats, and test procedures
for the FE/EU dual-unit energy system. This port added this system to Flux
Networks.

## Architecture

**Per-network unit.** Each `FluxNetwork` stores an `EnergyType` (`FE` or
`EU`, `common/connection/FluxNetwork.java:84`). It derives an
`IEnergySystem` from this type (`mEnergySystem = IEnergySystem.of(mEnergyType)`,
`FluxNetwork.java:85-210`). The network denominates all internal values —
buffers, limits, statistics — in that energy type. The code injects
`IEnergySystem` (`api/energy/IEnergySystem.java`) into every
`TransferHandler#onCycleStart`/`onCycleEnd` call, so handlers never hardcode
an energy type. Conversion is a pure bit shift: 1 EU equals 4 FE
(`EnergyType.getFEShift()`, FE=0, EU=2). So, when a system and a connector
share the same type, conversion becomes identity everywhere
(`IEnergySystem.convert`, shift-0 short-circuit).

**Connector-declared native units.** Each `IBlockEnergyConnector` and
`IItemEnergyConnector` declares `getNativeType()`. `ForgeEnergyConnector`
returns FE (`common/integration/energy/ForgeEnergyConnector.java:24-26`).
`GTCEUEnergyConnector` returns EU
(`common/integration/energy/GTCEUEnergyConnector.java:37-39`). Boundary code
normalizes the amount with the network's `IEnergySystem`:
- `SideTransfer.send` (`common/device/SideTransfer.java:37-51`) converts the
  network amount to the connector's energy type through `toConnector`. It
  then converts the connector's returned amount back through
  `fromConnectorCeil`, which rounds up so a partial-unit send can never
  create energy.
- `FluxControllerHandler.WirelessHandler.chargeItems`
  (`common/device/FluxControllerHandler.java:181-197`) does the same
  conversion for item-based (curios/inventory) charging.

**GT capability exposure.** `TileFluxPlug` and `TileFluxPoint` expose GT's
`IEnergyContainer` capability through `IGTEnergyBridge`
(`common/integration/energy/IGTEnergyBridge.java`). Its only implementation
is `GTCEUCapabilityBridge`
(`common/integration/energy/GTCEUCapabilityBridge.java`). `IGTEnergyBridge`
imports no `com.gregtechceu` types, so flux tiles can hold a reference to it
unconditionally. Only two classes may import GT types:
`GTCEUCapabilityBridge` and `GTCEUEnergyConnector`. `EnergyUtils.register()`
(`common/util/EnergyUtils.java:52-63`) gates both behind
`FluxConfig.enableGTCEU` **and** `ModList.get().isLoaded("gtceu")`. When
either is false, `sGTEnergyBridge` stays `null`, the game never registers
`GTCEUEnergyConnector`, and it never loads a GT class.
`PlugEnergyContainer.acceptEnergyFromNetwork` credits whole amps only. It
uses `EnergyMath.clampAmps`/`wholeAmps` (`api/energy/EnergyMath.java`)
around a simulate-then-execute call into `FluxPlugHandler#receive`.
`PointEnergyContainer` is a connection-only stub: it refuses all input
(`getEnergyCanBeInserted() == 0`), purely so GT cables can attach and
discover an endpoint. Actual point-to-neighbor delivery stays on the flux
side, through `SideTransfer.send` → `GTCEUEnergyConnector.sendTo`.

**Per-device unit reconciliation.** Each `TransferHandler` saves the
`EnergyType` that its buffer and limit currently use (`mUnit`, default FE,
`common/connection/TransferHandler.java:56-60`). When a network's type
switches (`ServerFluxNetwork.setEnergyType`,
`common/connection/ServerFluxNetwork.java:252-279`), the code walks every
loaded device, queued device, and unloaded phantom, and re-denominates each
one through `onEnergyTypeChanged` (overflow-clamped `IEnergySystem.convert`).
This walk cannot reach a device that sits in an unloaded chunk, because its
own chunk NBT still holds the old, tagged energy type. So,
`TileFluxDevice#connect` calls `TransferHandler#reconcileEnergyUnit` on
every (re)connect (`common/device/TileFluxDevice.java:191-213`), to catch
up the device once its chunk loads.

## Known caveats

- **FE-denominated storage capacity.** `FluxStorageHandler#getMaxEnergyStorage`
  reads a config value in FE and never converts it. So, on an EU network,
  the same raw number represents 4 times the physical energy. See the TODO
  javadoc at `common/device/FluxStorageHandler.java:63-74`. The handler has
  no reference to the network. The code also reads this value independent
  of the network (client GUI/renderers, and `setLimit` during NBT load
  before a network is bound). So, re-denominating this value locally is not
  safe.
- **Transient statistics mix after a type switch.** When a network's type
  changes, the code does not convert historical statistics (produced/used
  totals) that built up under the old energy type. It re-denominates only
  the live buffers and limits. Expect a discontinuity in the stats graphs
  at the switch point.
- **Point-to-cable-to-plug circulation.** Both `TileFluxPoint` and
  `TileFluxPlug` can expose a GT `IEnergyContainer` on the same GT cable
  network. If a point and a plug from the *same* flux network both attach
  to that cable network, energy can route point → cable → plug → back into
  the same network's buffer. This port does not detect or prevent that
  loop. Treat it as a topology to avoid, just as you would avoid any
  third-party cable that bridges two devices on the same network.
- **GT `EUToFEProvider` interplay.** GTCEU, not Flux Networks, owns the
  EU↔FE conversion for platform-native energy (`FluxConfig.java:141-145`:
  "EU-to-FE conversion is provided by GTCEU, instead of Flux Networks...
  Ensure you have enabled GTCEU's `nativeEUToPlatformNative` and
  `euToPlatformRatio = 4`"). This port never touches that provider. It only
  speaks GT's native `EU`, through `IEnergyContainer`. This is also why
  `ForgeEnergyConnector` must stay registered *before* `GTCEUEnergyConnector`,
  in `EnergyUtils`'s static block/`register()`
  (`common/util/EnergyUtils.java:41-63`). Connector lookup uses the first
  match, and GT machines at the supported tag expose no
  `ForgeCapabilities.ENERGY`. So, registering Forge first guarantees that
  neither connector double-converts the other's traffic. Do not reorder
  them: this is a correctness bug, not a style choice. See the warning
  docblocks on `GTCEUEnergyConnector` (lines 20-25) and the `EnergyUtils`
  static initializer.
- **Everything here depends on `enableGTCEU`.** The entire GT bridge —
  capability exposure, `GTCEUEnergyConnector`, and EU as a selectable
  network type in the GUI — stays inert unless `FluxConfig.enableGTCEU` is
  true (default) *and* GTCEU is installed. If you disable it, or run
  without GTCEU, the game exercises only the FE-identity path.

## In-game test checklist

Run this checklist after `./gradlew compileJava` succeeds (the full
MC-coupled build gate):

- [ ] `./gradlew compileJava` compiles clean.
- [ ] A GT cable attaches to both a flux plug and a flux point (capability
      discovery succeeds on both tile types).
- [ ] A GT generator pushes power into a plug on an **EU** network. The
      buffer fills, and the game raises no exceptions.
- [ ] A GT generator pushes power into a plug on an **FE** network. The
      same happens, but the boundary applies the `<<2` conversion (4 FE
      credited per EU).
- [ ] A GT generator idles (it stops pushing, or reports no acceptance)
      when the network has zero demand. `getEnergyCanBeInserted()` must
      honestly report 0, not a stale positive value.
- [ ] An LuV-tier GT cable feeds a plug on an **FE** network without
      exploding. This confirms that the adapters never trigger GT's
      over-voltage checks.
- [ ] Switch a live network between FE and EU. Verify that the buffers and
      limits re-denominate correctly, **including a device in a
      currently-unloaded chunk**. Load that chunk afterward, and confirm
      that `TileFluxDevice#connect` catches it up, instead of silently
      keeping stale-unit values.

## Unit tests

Run with:

```
./gradlew test
```

The tests live under `src/test/java/sonar/fluxnetworks/api/energy/`. They
import only `sonar.fluxnetworks.api.energy` and JUnit 5, so they run
without MC bootstrapping. `EnergyTypeTest` checks wire-format stability and
the `fromId` round-trip. `IEnergySystemTest` checks FE-identity, EU↔FE
exactness, and the ceil-overflow edge. `EnergyMathTest` checks the
`clampAmps`/`wholeAmps` whole-amp floor and the overflow clamp.

The porting environment also ran these tests directly. That environment had
no Gradle or MC available. So, the port compiled the MC-free `api/energy`
sources with plain `javac` (JDK 21), and ran them under the standalone
JUnit Platform Console (1.10.2), fetched from Maven Central through the
session proxy. This was the first real execution of this port's conversion
math. Here is the verbatim summary:

```
Test run finished after 125 ms
[         6 containers found      ]
[         0 containers skipped    ]
[         6 containers started    ]
[         0 containers aborted    ]
[         6 containers successful ]
[         0 containers failed     ]
[        33 tests found           ]
[         0 tests skipped         ]
[        33 tests started         ]
[         0 tests aborted         ]
[        33 tests successful      ]
[         0 tests failed          ]
```

On a machine with the full toolchain, `./gradlew test` should reproduce
this green result. `./gradlew compileJava` remains the authoritative gate
for everything MC-coupled (tiles, GUIs, and the GT bridge itself) that this
environment could not compile or run.
