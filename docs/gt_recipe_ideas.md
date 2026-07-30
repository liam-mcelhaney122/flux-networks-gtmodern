# GregTech: Modern Recipe Ideas for Flux Networks

This is an ideation document for a second pass. Its goal is to give every
Flux Networks item an optional, lore-appropriate GTCEu (GregTech: Modern)
recipe path. This way, pack makers who run both mods can "greggify"
progression, instead of relying on the vanilla-only crafting table recipes
that ship today. This document implements nothing: it does not touch any
Java or JSON code. It marks GT item ids with `(?)` wherever it could not
verify the exact registry name against the GT-Modern `1.20.1` branch
source.

## How Flux Networks could ship these

**Datapack JSON (`data/fluxnetworks/recipes/*.json`, `"type": "gtceu:assembler"`).**
This option needs no compile-time dependency. But GT-Modern's real recipe
codec is *not* a flat `inputs`/`EUt` object. It deserializes through a
capability-keyed structure (`inputs`, `outputs`, `tickInputs`,
`tickOutputs`, `recipeConditions`, keyed by capability type:
item/fluid/energy) that GT's own datagen generates. Hand-authoring that
structure by copying examples is fragile, and the source code is its only
documentation. Wrap the recipe in a `neoforge:conditions`/Forge
conditional-recipe block, so it stays inert without `gtceu` loaded. This
option is possible, but not pleasant.

**KubeJS (`kubejs/server_scripts`, via the KubeJS-GTCEu addon).** This
option is most ergonomic for *pack makers*, not for Flux Networks itself.
It needs the user's pack to include KubeJS and the GTCEu bridge, so the mod
cannot bundle it in the jar. It works well as "here's a starter script"
documentation, not as shipped content.

**GT addon-style Java datagen, soft-dependency gated.** This option adds a
small `sonar.fluxnetworks.compat.gtceu` package. Its `GTRecipeProvider`
runs only when `ModList.get().isLoaded("gtceu")`, and uses GT's real
builder API (`ASSEMBLER_RECIPES.recipeBuilder(...)`), the same way
first-party GT addons do. It produces correct nested JSON automatically,
and fails closed without GT.

**Recommendation:** Use Java datagen behind a soft dependency. This is the
only option that produces guaranteed-correct recipe JSON, and degrades
safely without GT installed. Ship it as an optional Gradle source-set or
compat jar. Do not bundle GT as a real compile dependency of the base mod.

## Tier ladder rationale

The mod's own three-tier storage line (Basic → Herculean → Gargantuan) maps
cleanly onto HV → EV → IV. So, the ladder below stretches the whole item
set across these same three tiers, instead of clustering everything at one
voltage:

| Tier | Items |
|---|---|
| HV (480 EU/t) | Flux Dust, Flux Core, Flux Block, Flux Plug, Basic Flux Storage, Flux Configurator |
| EV (1920 EU/t) | Flux Point, Flux Controller, Herculean Flux Storage |
| IV (7680 EU/t) | Gargantuan Flux Storage (wireless-charging capable, see tooltip) |
| Creative only | Admin Configurator (no recipe today; stays that way) |

Dust, Core, and Block stay at HV, so the mod remains reachable mid-game.
This matches the spirit of today's obsidian-and-ender-eye recipes. Plug
comes before Point, because Plug only receives energy passively (an
input-hatch analog). Point actively pushes energy out, so it moves up a
tier for the extra flow-control parts. Basic storage stays at HV, as a
"starter battery bank." Herculean and Gargantuan storage climb with the
machine tier that crafts them. This mirrors the vanilla recipes' own
escalating cost (`fluxnetworks:flux_storage_recipe`).

## Per-item recipes

### Flux Dust (`fluxnetworks:flux_dust`)
Current: no crafting-table recipe exists. A world-interaction ritual makes
it: left-click an obsidian block that sits two blocks above bedrock (or a
Flux Block), with redstone item entities piled below it. This ritual
converts up to 512 redstone into an equal stack of flux dust
(`EventHandler#onPlayerInteract`).
Proposed: **keep the ritual** (it is a nice signature mechanic, and needs
no GT dependency), **and add** a Mixer alternative, for players who never
touch bedrock.
- Machine: Mixer | Tier: HV | EUt: 480 | Duration: 100 | Cleanroom: No
- Inputs: 4× `minecraft:redstone`, 1× `gtceu:obsidian_dust` (?), 1×
  `gtceu:ender_pearl_dust` (?), 250 mB water
- Output: 4× `fluxnetworks:flux_dust`
- Flavor: this mixer does chemically what the ritual does physically. It
  grinds obsidian and ender pearl into the redstone lattice.

### Flux Core (`fluxnetworks:flux_core`)
Current: shaped, obsidian + flux dust + ender eye (3×3, `fluxcore.json`).
Proposed: Assembler, HV, EUt 480, duration 200, no cleanroom.
- Inputs: 4× `fluxnetworks:flux_dust`, 2× `minecraft:ender_eye`, 1×
  `gtceu:hv_sensor`, 1× `#gtceu:circuits/hv`
- Output: 4× `fluxnetworks:flux_core`
- Flavor: the sensor is what lets the core "see" network state. It also
  keeps the ender-eye teleportation flavor intact.

### Flux Block (`fluxnetworks:flux_block`)
Current: shaped, flux core + flux dust (3×3, `fluxblock.json`).
Proposed: Assembler, HV, EUt 480, duration 300, no cleanroom.
- Inputs: 4× `fluxnetworks:flux_core`, 4× `fluxnetworks:flux_dust`, 2×
  `gtceu:stainless_steel_plate`, 1× `gtceu:hv_machine_hull`
- Output: 1× `fluxnetworks:flux_block`
- Flavor: the hull item stands in for "this is now a proper storage medium
  block," not just loose crystal.

### Flux Plug (`fluxnetworks:flux_plug`)
Current: shaped, flux core + flux block (`fluxplug.json`).
Proposed: Assembler, HV, EUt 480, duration 200, no cleanroom.
- Inputs: 2× `fluxnetworks:flux_core`, 1× `fluxnetworks:flux_block`, 2×
  `gtceu:gold_single_cable` (?), 1× `#gtceu:circuits/hv`
- Output: 1× `fluxnetworks:flux_plug`
- Flavor: GT's own energy-input-hatch recipe leans on a single cable plus
  a circuit. Plug is the network's input hatch, so this recipe reuses that
  shape directly.

### Flux Point (`fluxnetworks:flux_point`)
Current: shaped, flux core + redstone block (`fluxpoint.json`).
Proposed: Assembler, EV, EUt 1920, duration 250, no cleanroom.
- Inputs: 2× `fluxnetworks:flux_core`, 1× `minecraft:redstone_block`, 2×
  `gtceu:aluminium_single_cable` (?), 1× `gtceu:ev_sensor`, 1×
  `#gtceu:circuits/ev`
- Output: 1× `fluxnetworks:flux_point`
- Flavor: this is an output-hatch analog. The extra sensor represents
  load-balanced active output, instead of Plug's passive intake.

### Flux Controller (`fluxnetworks:flux_controller`)
Current: shaped, flux block + flux core + flux dust (`fluxcontroller.json`).
Proposed: Assembly Line, EV, EUt 1920, duration 400, no cleanroom.
- Inputs: 3× `fluxnetworks:flux_block`, 1× `fluxnetworks:flux_core`, 4×
  `fluxnetworks:flux_dust`, 1× `gtceu:ev_field_generator`, 2×
  `#gtceu:circuits/ev`, 1× `gtceu:ev_electric_motor`
- Output: 1× `fluxnetworks:flux_controller`
- Flavor: a Flux Controller built on an Assembly Line is exactly the kind
  of on-the-nose joke that GT players enjoy. The field generator justifies
  the wireless-charging tooltip that the item already ships with.

### Basic Flux Storage (`fluxnetworks:basic_flux_storage`)
Current: shaped, flux block ×6 + glass pane ×2 (`basicfluxstorage.json`).
Proposed: Assembler, HV, EUt 480, duration 300, no cleanroom.
- Inputs: 6× `fluxnetworks:flux_block`, 2× `minecraft:glass_pane`, 1×
  Cadmium/Lithium/Sodium Battery (HV) (?), 1× `gtceu:hv_machine_hull`
- Output: 1× `fluxnetworks:basic_flux_storage`
- Flavor: GT already ships a tiered battery item line (`BATTERY_HV_*`).
  Using one battery as a literal charge-buffer ingredient sells "this item
  stores power" far better than glass panes alone.

### Herculean Flux Storage (`fluxnetworks:herculean_flux_storage`)
Current: custom `fluxnetworks:flux_storage_recipe`, basic storage ×6 + glass
pane ×2 (`herculeanfluxstorage.json`).
Proposed: Assembly Line, EV, EUt 1920, duration 500, no cleanroom.
- Inputs: 6× `fluxnetworks:basic_flux_storage`, 2× `minecraft:glass_pane`, 2×
  Vanadium Redox Battery (EV) (?), 1× `gtceu:ev_electric_piston`
- Output: 1× `fluxnetworks:herculean_flux_storage`
- Flavor: this recipe already matches the vanilla recipe's shape, because
  it consumes the previous tier's storage item as an ingredient. The GT
  version just adds a bigger battery inside it.

### Gargantuan Flux Storage (`fluxnetworks:gargantuan_flux_storage`)
Current: custom `fluxnetworks:flux_storage_recipe`, herculean storage ×6 +
glass pane ×2 (`gargantuanfluxstorage.json`).
Proposed: Assembly Line, IV, EUt 7680, duration 700, **Cleanroom: Yes**.
- Inputs: 6× `fluxnetworks:herculean_flux_storage`, 2× `minecraft:glass_pane`,
  1× Lapotron Crystal (?) or `ENERGY_LAPOTRONIC_ORB` (?), 1×
  `gtceu:iv_field_generator`, 2× `#gtceu:circuits/iv`
- Output: 1× `fluxnetworks:gargantuan_flux_storage`
- Flavor: this is the item whose tooltip enables wireless charging across
  the whole network. IV-tier GT parts (lapotron-class capacitors) are
  typically cleanroom-gated, so gate this recipe too, as the mod's genuine
  "endgame" item.

### Flux Configurator (`fluxnetworks:flux_configurator`)
Current: shaped, obsidian + flux dust + ender eye (`fluxconfigurator.json`).
Proposed: Assembler, HV, EUt 480, duration 150, no cleanroom.
- Inputs: 1× `minecraft:obsidian`, 2× `fluxnetworks:flux_dust`, 1×
  `minecraft:ender_eye`, 1× `gtceu:hv_electric_motor`
- Output: 1× `fluxnetworks:flux_configurator`
- Flavor: this is a hand tool, so it stays a lightweight, single-machine
  recipe, rather than an Assembly Line item. The motor just says "it has
  moving parts now."

### Admin Configurator (`fluxnetworks:admin_configurator`)
Current: no recipe of any kind exists (a creative/op-only item today).
Proposed: **no GT recipe either.** Keep it creative-tab/command-only.
Giving it a GTCEu recipe — even an absurdly expensive UHV+ one — would
imply that players can reach it in survival mode. That would contradict
its purpose as an admin debug tool.

## Custom GT parts sidebar

Two invented intermediate items would make the ladder read as more "Greg."
They would not add needless complexity to the existing item count:

1. **Flux-Infused Circuit Board** (?) — HV. Assembler: 1×
   `#gtceu:circuits/hv`, 2× `fluxnetworks:flux_dust`, 1×
   `gtceu:gold_single_cable` (?). EUt 480, duration 150, no cleanroom. This
   part could replace the raw `#gtceu:circuits/hv` input in the Flux Core
   recipe above, so Core gets a bespoke processed part instead of a
   generic GT circuit. This gives Flux Networks one "signature" mid-tier
   component, worth a screenshot in JEI/REI.
2. **Resonant Flux Coil** (?) — EV. Assembler: 4× `gtceu:aluminium_fine_wire`
   (?), 1× `minecraft:ender_pearl`, 1× `gtceu:ev_voltage_coil` (?), 1×
   `gtceu:titanium_rod` (?). EUt 1920, duration 200, no cleanroom. This is
   a drop-in replacement for the field generator in Flux Controller, and
   for the battery in Herculean/Gargantuan Storage. It ties the "wireless
   resonance" fluff directly to a physical item, instead of leaving it
   implicit.

Both parts are optional. The ladder above works with stock GT items alone,
if the author would rather not add new registry entries just for this.

## Open questions for the second pass

- Should vanilla recipes stay active alongside GT ones (parallel paths), or
  should GT presence disable or replace them through recipe conditions?
  Leaning: keep vanilla always on, with GT as an *easier/flavorful
  alternative*, not a gate. This mirrors "keep the ritual" above.
- Is a compile-time soft dependency on GT-Modern acceptable for this
  project's build and CI? Or should this stay JSON/KubeJS-only, to avoid
  GT's frequently changing API?
- Do the two invented intermediate items (circuit board, coil) earn their
  place? Or is reusing stock GT parts everywhere better, for a smaller
  diff?
- Is a config toggle worth adding for the flux-dust Mixer alternative
  recipe, in the style of `enableFluxRecipe`, matching the existing
  ritual's toggle?
- Should the cleanroom requirement extend from Gargantuan Storage to Flux
  Controller too? Or is that too punishing for a networking mod's core
  block?
