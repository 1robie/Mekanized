# Mekanized: plan

A Mekanism-style content plugin for MachineEngine. Like the example addon, it is a separate plugin
that depends only on MachineEngine's API, its YAML definitions and a CraftEngine pack.

## Decisions (project owner, 2026-10-02)

| # | Decision |
|---|----------|
| K1 | **Name:** the plugin is `Mekanized`, with the `mekanized:` namespace for every item, block, resource, machine and recipe. It is never called "Mekanism" and never presented as official. Its description reads "inspired by Mekanism; textures, models and recipes from Mekanism (MIT)". |
| K2 | **Scope of the first release:** Phases 0–4 below, everything except multiblocks and endgame (Phase 5). |
| K3 | **No ore world generation:** Mekanized defines the ore blocks and their drops only. Worlds get ores from a separate worldgen plugin or datapack. |
| K4 | **Recipes are imported from Mekanism:** a Gradle task converts Mekanism's recipe JSON from a pinned commit into Mekanized's editable YAML definitions. Server owners can then change them. |

## License

Mekanism is MIT, copyright 2017-2025 Aidan C. Brady. The license covers the whole repository,
textures and models included. Every Mekanized jar and CraftEngine pack ships
`LICENSE-MEKANISM.txt` (the full MIT text) and a credits line. Imported files are not changed in
ways that hide where they came from. Source: `https://github.com/mekanism/Mekanism`. The branch
`26.1` (Minecraft 26.1) is pinned to one commit hash in `gradle.properties`.

## Phases

### Phase 0: setup and import tooling
- **Module:** `machineengine-mekanized` (Paper plugin, dev servers install it like the example).
- **`importMekanism` task:** downloads the pinned commit as an archive and caches it under `.mekanism/`.
  - It copies the textures, models and `.mcmeta` animations that Mekanized uses into the CraftEngine pack, renaming the namespace to `mekanized`.
  - It converts Mekanism recipes into Mekanized YAML: recipe types map to ours (enriching, crushing, …), and crafting and smelting recipes become CraftEngine recipes.
  - `#c:` and `#mekanism:` tags become Mekanized tag files, which include the matching vanilla tags.
  - Imported data is generated into `build/` and packaged. A small hand-written overlay holds what cannot be converted.

### Phase 1: materials and energy
- **Resources:** energy in Joules (`mekanized:energy`), items, and infuse types (carbon, redstone, diamond, refined obsidian, tin, gold, bio, fungi).
- **Items:**
  - ingots, nuggets, dusts, dirty dusts, clumps, shards and crystals for each metal;
  - alloys, circuits (four tiers), steel casing, HDPE, salt, sulfur, fluorite gems.
- **Ore blocks:** osmium, tin, lead, uranium and fluorite, plus their deepslate variants, with fortune drops (no world generation, K3). Raw and storage blocks too.
- **Crafting and smelting recipes:** through CraftEngine.

### Phase 2: first machines (2x and 3x ore processing)
- **Processing machines:** Enrichment Chamber, Crusher, Energized Smelter, Osmium Compressor, Combiner, Metallurgic Infuser, Purification Chamber, Precision Sawmill.
- **Generators:** Heat Generator, Solar Generator, Bio-Generator.
- **Energy storage and cables:** Energy Cubes (basic, advanced, elite, ultimate, creative) and Universal Cables (the same tiers).
- **Upgrades:** speed and energy.
- **Menus:** zMenu menus with energy and progress gauges and upgrade slots. They use Mekanism-style backgrounds if CraftEngine image titles work in zMenu menus (to verify).

### Phase 3: chemicals and fluids (4x and 5x ore processing)
- **Chemicals:** chemical tanks (tiers) and pressurized tubes.
- **Chemical machines:** Electrolytic Separator, Chemical Oxidizer, Chemical Infuser, Chemical Injection Chamber, Chemical Dissolution Chamber, Chemical Washer, Chemical Crystallizer, Rotary Condensentrator.
- **Fluids:** fluid tanks, mechanical pipes and an Electric Pump.

### Phase 4: logistics and the Mekanism feel
- **Logistics:** logistical transporters (item pipes, tiers) and filters.
- **Machine setup:** side configuration per machine, with the input and output colors and auto-eject.
- **Factories and tiers:** factories in basic, advanced, elite and ultimate, and tier installers that upgrade a placed machine and keep its state.
- **Tools:** Configurator, and an Energy Tablet that charges items.

### Phase 5: later, not in the first release
Multiblocks (Dynamic Tank, Induction Matrix, Thermal Evaporation, Boiler, Fission, Fusion), Digital
Miner, Teleporter, QIO, MekaSuit.

## MachineEngine features needed (built in the engine, tested headless)

| # | Feature | For |
|---|---------|-----|
| E1 | Chance outputs on processing recipes | Sawmill secondary outputs |
| E2 | Side configuration per machine instance (ports reassigned per side) and auto-eject | Phase 4 |
| E3 | World info for behaviors: sky light, daylight, weather, biome, height | Solar, wind |
| E4 | Items that store energy or chemicals, and machine charge slots | Energy Tablet, batteries |
| E5 | Bucket and fluid interop, and placing or picking up world fluids | Fluid tanks, pump |
| E6 | Changing a placed machine's type while keeping its state | Tier installers |
| E7 | Several parallel processes per machine | Factories |
| E8 | zMenu buttons: upgrade slot, energy, chemical and fluid gauges, side configuration | All menus |
| E9 | Chemical and fluid networks (energy and items already work) | Tubes and pipes |

## Verification per phase
- **Headless tests:** each processing chain gives exactly the expected yield. For example, 1 ore gives 2 ingots through the Enrichment Chamber and 5 through the full chemical chain. Resources are always conserved.
- **Server checks:** commands in the style of `/mexample` that build a line of machines and count the results, on Paper 1.20.6 and Folia 26.2.
- **Handoff:** a stop at the end of each phase so the owner can try it in game.
