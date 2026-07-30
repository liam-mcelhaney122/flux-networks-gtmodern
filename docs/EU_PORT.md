# GregTech CEu (EU) Port

Architecture summary, known caveats, and test procedures for the FE/EU dual-unit
energy system added to Flux Networks.

## Architecture

**Per-network unit.** Each `FluxNetwork` stores an `EnergyType` (`FE` or `EU`,
`common/connection/FluxNetwork.java:84`) and derives an `IEnergySystem` from it
(`mEnergySystem = IEnergySystem.of(mEnergyType)`, `FluxNetwork.java:85-210`). All
network-internal values — buffers, limits, statistics — are denominated in that
unit. `IEnergySystem` (`api/energy/IEnergySystem.java`) is dependency-injected into
every `TransferHandler#onCycleStart`/`onCycleEnd` call so handlers never hardcode a
unit. Conversion is a pure bit shift, 1 EU = 4 FE (`EnergyType.getFEShift()`, FE=0,
EU=2), so a system paired with a connector of the same type reduces to identity
everywhere (`IEnergySystem.convert`, shift-0 short-circuit).

**Connector-declared native units.** Each `IBlockEnergyConnector` /
`IItemEnergyConnector` declares `getNativeType()` — `ForgeEnergyConnector` is FE
(`common/integration/energy/ForgeEnergyConnector.java:24-26`), `GTCEUEnergyConnector`
is EU (`common/integration/energy/GTCEUEnergyConnector.java:37-39`). Boundary code
normalizes with the network's `IEnergySystem`:
- `SideTransfer.send` (`common/device/SideTransfer.java:37-51`) converts the network
  amount to the connector's unit via `toConnector`, then the connector's returned
  amount back via `fromConnectorCeil` (rounds up, so a partial-unit send can never
  mint energy).
- `FluxControllerHandler.WirelessHandler.chargeItems`
  (`common/device/FluxControllerHandler.java:181-197`) does the same for
  item-based (curios/inventory) charging.

**GT capability exposure.** `TileFluxPlug`/`TileFluxPoint` expose GT's
`IEnergyContainer` capability through `IGTEnergyBridge`
(`common/integration/energy/IGTEnergyBridge.java`), whose only implementation is
`GTCEUCapabilityBridge` (`common/integration/energy/GTCEUCapabilityBridge.java`).
`IGTEnergyBridge` imports no `com.gregtechceu` types, so flux tiles can hold a
reference to it unconditionally; `GTCEUCapabilityBridge` and `GTCEUEnergyConnector`
are the only two classes allowed to import GT types. Both are gated behind
`FluxConfig.enableGTCEU` **and** `ModList.get().isLoaded("gtceu")` in
`EnergyUtils.register()` (`common/util/EnergyUtils.java:52-63`) — when either is
false, `sGTEnergyBridge` stays `null`, `GTCEUEnergyConnector` is never registered,
and no GT class is ever loaded. `PlugEnergyContainer.acceptEnergyFromNetwork`
credits whole amps only, via `EnergyMath.clampAmps`/`wholeAmps`
(`api/energy/EnergyMath.java`) around a simulate-then-execute call into
`FluxPlugHandler#receive`. `PointEnergyContainer` is a connection-only stub
(refuses all input, `getEnergyCanBeInserted() == 0`) purely so GT cables attach and
discover an endpoint; actual point→neighbor delivery stays flux-side through
`SideTransfer.send` → `GTCEUEnergyConnector.sendTo`.

**Per-device unit reconciliation.** Each `TransferHandler` persists the `EnergyType`
its buffer/limit are currently denominated in (`mUnit`, default FE,
`common/connection/TransferHandler.java:56-60`). When a network's type is switched
(`ServerFluxNetwork.setEnergyType`, `common/connection/ServerFluxNetwork.java:252-279`),
every loaded/queued device and unloaded phantom is walked and re-denominated via
`onEnergyTypeChanged` (overflow-clamped `IEnergySystem.convert`). A device sitting in
an unloaded chunk can't be reached that way — its own chunk NBT still holds the old,
tagged unit — so `TileFluxDevice#connect` calls `TransferHandler#reconcileEnergyUnit`
on every (re)connect (`common/device/TileFluxDevice.java:191-213`) to catch it up
once its chunk loads.

## Known caveats

- **FE-denominated storage capacity.** `FluxStorageHandler#getMaxEnergyStorage`
  reads a config value in FE and never converts it, so the same raw number
  represents 4x the physical energy on an EU network. See the TODO javadoc at
  `common/device/FluxStorageHandler.java:63-74` — the handler has no network
  reference, and the value is also read network-independently (client GUI/renderers,
  and `setLimit` during NBT load before a network is bound), so re-denominating it
  isn't safe to do locally.
- **Transient statistics mix after a type switch.** Historical statistics
  (produced/used totals) accumulated under the old unit are not retroactively
  converted when a network's type changes; only live buffers/limits are
  re-denominated. Expect a unit discontinuity in stats graphs across the switch.
- **Point→cable→plug circulation.** Both `TileFluxPoint` and `TileFluxPlug` can
  expose a GT `IEnergyContainer` on the same GT cable network. If a point and a
  plug of the *same* flux network end up attached to that cable network, energy
  can route point → cable → plug → back into the same network's buffer. Nothing
  in this port detects or prevents that loop; it's a topology to avoid, same as
  for any two same-network devices bridged by a third party's cable.
- **GT `EUToFEProvider` interplay.** GTCEU, not Flux Networks, owns EU↔FE
  conversion for platform-native energy (`FluxConfig.java:141-145`: "EU-to-FE
  conversion is provided by GTCEU, instead of Flux Networks... Ensure you have
  enabled GTCEU's `nativeEUToPlatformNative` and `euToPlatformRatio = 4`"). This
  port never touches that provider; it only speaks GT's native `EU` via
  `IEnergyContainer`. That's also why `ForgeEnergyConnector` must stay registered
  *before* `GTCEUEnergyConnector` in `EnergyUtils`'s static block/`register()`
  (`common/util/EnergyUtils.java:41-63`): connector lookup is first-match, and GT
  machines at the supported tag expose no `ForgeCapabilities.ENERGY`, so Forge
  first guarantees neither connector double-converts the other's traffic.
  Reordering is a correctness bug, not a style choice — see the warning docblocks
  on `GTCEUEnergyConnector` (lines 20-25) and the `EnergyUtils` static initializer.
- **Everything here rides `enableGTCEU`.** The entire GT bridge — capability
  exposure, `GTCEUEnergyConnector`, EU as a selectable network type in the GUI — is
  inert unless `FluxConfig.enableGTCEU` is true (default) *and* GTCEU is installed.
  Disabling it, or running without GTCEU, exercises only the FE-identity path.

## In-game test checklist

Run after `./gradlew compileJava` succeeds (the full MC-coupled build gate):

- [ ] `./gradlew compileJava` compiles clean.
- [ ] A GT cable attaches to both a flux plug and a flux point (capability
      discovery succeeds on both tile types).
- [ ] A GT generator pushes power into a plug on an **EU** network — buffer fills,
      no exceptions.
- [ ] A GT generator pushes power into a plug on an **FE** network — same, with
      the `<<2` boundary conversion applied (4 FE credited per EU).
- [ ] A GT generator idles (stops pushing / reports no acceptance) when the
      network has zero demand — `getEnergyCanBeInserted()` must honestly report
      0, not a stale positive.
- [ ] An LuV-tier GT cable feeding a plug on an **FE** network does not explode —
      confirms the adapters never trigger GT's over-voltage checks.
- [ ] Switch a live network FE↔EU and verify buffers/limits re-denominate
      correctly, **including a device in a currently-unloaded chunk** (load its
      chunk afterward and confirm `TileFluxDevice#connect` catches it up instead
      of silently keeping stale-unit values).

## Unit tests

Run with:

```
./gradlew test
```

Tests live under `src/test/java/sonar/fluxnetworks/api/energy/` and import only
`sonar.fluxnetworks.api.energy` + JUnit 5, so they run without MC bootstrapping:
`EnergyTypeTest` (wire-format stability, `fromId` round-trip), `IEnergySystemTest`
(FE-identity, EU↔FE exactness, ceil-overflow edge), `EnergyMathTest`
(`clampAmps`/`wholeAmps` whole-amp floor and overflow clamp).

They were also executed directly in the porting environment (no Gradle/MC
available there) by compiling the MC-free `api/energy` sources with plain
`javac` (JDK 21) and running them under the standalone JUnit Platform Console
(1.10.2) fetched from Maven Central through the session proxy — the first real
execution of this port's conversion math. Verbatim summary:

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

`./gradlew test` on a machine with the full toolchain should reproduce this
green; `./gradlew compileJava` remains the authoritative gate for everything
MC-coupled (tiles, GUIs, the GT bridge itself) that this environment could not
compile or run.
