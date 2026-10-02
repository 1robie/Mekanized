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

**Status: done (2026-10-02).**
- **Pin:** `gradle.properties` pins `mekanism_commit` (branch 26.1, `4718860`).
- **`:machineengine-mekanized:fetchMekanism`:** a shallow, sparse, blob-filtered clone into `.mekanism/source` (about 25 MB).
- **`importMekanism` (buildSrc `ImportMekanism`):**
  - Assets: 1,038 textures and 680 models.
  - Recipes: 950 machine recipes in 18 recipe types (`definitions/recipes_<type>.yml`, with durations and Joules per tick from Mekanism's machine defaults), and 353 crafting, smelting and blasting recipes (CraftEngine `configuration/recipes_<group>.yml`).
  - Tags: 191 item tags and 17 chemical tags (`definitions/tags.yml`).
  - Skipped, and listed in `import-report.txt`: 637 recipes for other mods, 370 painting and pigment recipes, endgame and multiblock recipes, and 90 models that need NeoForge loaders (83 composite, which can be flattened later, 6 OBJ and 1 energy cube).
- **Plugin:** installs the CraftEngine pack with the imported assets, refreshed on every start. Generated configuration files are only installed once their phase defines the items they reference (`ENABLED_CONFIGURATION`). `LICENSE-MEKANISM.txt` ships in the jar and in the pack.
- **Verified:** the pack loads on Paper 1.20.6.

### Phase 1: materials and energy
- **Resources:** energy in Joules (`mekanized:energy`), items, and infuse types (carbon, redstone, diamond, refined obsidian, tin, gold, bio, fungi).
- **Items:**
  - ingots, nuggets, dusts, dirty dusts, clumps, shards and crystals for each metal;
  - alloys, circuits (four tiers), steel casing, HDPE, salt, sulfur, fluorite gems.
- **Ore blocks:** osmium, tin, lead, uranium and fluorite, plus their deepslate variants, with fortune drops (no world generation, K3). Raw and storage blocks too.
- **Crafting and smelting recipes:** through CraftEngine.

**Status: done (2026-10-02), not yet tried in game.**
- **Engine E10 (item tags):** the reloadable `machineengine:item_tag` registry (`ItemTagContribution`: items and included tags), `Items.inTag`, the `item-tag:` filter, and the `item-tags:` and `resource-tags:` definitions sections. `TagSnapshot` resolves includes and guards against cycles, then asks the platform: on Paper, `CraftEngineItemBridge` reports vanilla tags and CraftEngine item tags. Machine slots now read tags live, so reloads reach existing machines. Tested in `ItemTagTest`.
- **Engine E11 (custom item ids):**
  - `PaperItems.idOf` gives CraftEngine items their custom id.
  - Definition item ids resolve CraftEngine items.
  - Recipes wait silently until CraftEngine has loaded its items (`ItemResolver.ready()`).
  - Recipes, resource tags and item tags reload on every CraftEngine (re)load.
  - Definition files are read from sub-folders too.
- **Content:**
  - Items and blocks: 93 items and 27 blocks, generated into the CraftEngine pack (`materials.yml`). Ores use CraftEngine's `ore` loot template (silk touch, fortune, `2~4` for fluorite); storage blocks drop themselves.
  - Names: in all 46 of Mekanism's languages (`lang.yml`).
  - Tags: `c:` tags are declared on the items, parent tags included.
  - Recipes: 93 crafting, smelting and blasting recipes whose items all exist (`recipes_materials_*.yml`).
  - Vanilla aliases: 86 `c:` tags (`vanilla_aliases.yml`). Crafting ingredients that use those tags become lists that include the vanilla members.
  - Registered in Java: energy (`mekanized:energy`, J) and 49 chemicals (`mekanized:chemical`, mB). Pigments are left out.
- **Verified:** on Paper 1.20.6 and Folia 26.2, CraftEngine loads the pack, recipes and resource pack with no issues, and MachineEngine reports no problems.

### Phase 2: first machines (2x and 3x ore processing)
- **Processing machines:** Enrichment Chamber, Crusher, Energized Smelter, Osmium Compressor, Combiner, Metallurgic Infuser, Purification Chamber, Precision Sawmill.
- **Generators:** Heat Generator, Solar Generator, Bio-Generator.
- **Energy storage and cables:** Energy Cubes (basic, advanced, elite, ultimate, creative) and Universal Cables (the same tiers).
- **Upgrades:** speed and energy.
- **Menus:** zMenu menus with energy and progress gauges and upgrade slots. They use Mekanism-style backgrounds if CraftEngine image titles work in zMenu menus (to verify).

**Status: done (2026-10-02), not yet tried in game.**
- **Engine:**
  - Process bindings take several cost storages; each cost is paid from the first storage that holds it. Old saves, with one cost storage, still load.
  - Chance outputs (E1): rolled at process start with the machine's random, then kept with the process. The `chance-outputs` definitions key reads them.
  - A visible active state: `MachineContext.setActive` is saved, and the CraftEngine `active` property follows it.
  - World info (E3): `MachineContext.environment()` gives sky, daytime, rain and sky light.
  - The `machineengine_upgrades` zMenu button: an item whose id is an upgrade type's key is that upgrade.
  - `PaperItems.itemOf`.
  - Tested in `ProcessFeaturesTest`.
- **Mekanized, in Java:**
  - Machines: Enrichment Chamber, Crusher, Energized Smelter (every furnace recipe on the server, 93 on a vanilla server), Precision Sawmill, Combiner, Osmium Compressor, Purification Chamber, Metallurgic Infuser.
  - Item slots that feed the energy buffer or the chemical tank (`ConvertingBehavior`). They have no port; side configuration comes in Phase 4.
  - Generators: Heat (furnace fuels, 200 J/t), Bio (bio fuel, 350 J/t) and Solar (50 J/t in daylight, a fifth in rain).
  - Energy cubes: four tiers plus creative. Input on five sides, output through the front.
  - Universal cables: four tiers on an energy network.
  - Upgrades: speed (duration ×0.75, cost ×1.33 each) and energy (cost ×0.8 each), up to 8 of each.
- **Generated:**
  - CraftEngine blocks for the 20 machine, generator, cube and cable blocks, from Mekanism's blockstates (`facing` plus `active` appearances); energy cubes use their item model, because their block model needs a loader.
  - 16 machine crafting recipes.
  - 16 zMenu menus with slots, energy, progress and chemical gauges and upgrades. Titles are translated (`<lang:…>`).
- **Converter:** `#mekanized:` and `#c:` crafting ingredients are expanded to their vanilla members, for example basic alloy = redstone.
- **Tests:** headless, `MekanizedChainTest` loads every imported recipe and tag without a warning and checks:
  - enrichment (1 ore gives 2 dust);
  - speed upgrades;
  - purification (3 clumps, 1 mB of oxygen per tick);
  - redstone to energy;
  - the heat and solar generators.
- **Verified:** `/mekanized test` on Folia 26.2 gives 2 osmium dust for 10 kJ, and CraftEngine reports no issues.
- **Fixes after in-game testing (2026-10-02):**
  - **Rendering of non-full blocks:** they are drawn by an item display entity (`display_item` on the machine behavior), turned to the facing and switched to the active model while working, over one empty non-occluding `tintable_leaves` state. Non-occluding states are scarce: CraftEngine's leaves group has 117 slots, shared with its own content. One state per facing overflowed the group, made energy cubes and cables invisible, and broke blocks after a restart.
  - **Cables:** drawn entirely by display entities, a center piece plus an arm per connected side, over one sapling-type state (small hitbox, no collision). Connections come from `CoreNetworks.connections`: a transport of the same network, or a machine port on the facing side that carries what the transport does.
  - **Converted models:** the importer flattens `mekanism:energy_cube` and `neoforge:composite` models into vanilla ones (84 models). Only OBJ transmitters remain, and cables do not use them.
  - **Texture metadata:** sanitized. Empty animation `frames` lists made vanilla clients reject the whole pack; CTM sections are dropped.
  - **Menus:**
    - slots rebuild from the machine (`isUpdatedMasterButton`); gauges keep zMenu's periodic placeholder update;
    - menus are refreshed in place after machine changes (at most every 5 ticks), never reopened;
    - the top inventory is reached reflectively, because `InventoryView` is a class on 1.20.6 and an interface on 26.x.
  - **Slot filters from recipes:** the input slot takes what the machine's recipes use, the chemical slot what converts into its chemical, the energy slot what converts into energy.
  - **Updates to generated files:** generated files are updated unless a server owner edited them (`EditableFiles`, checksums in `plugins/Mekanized/installed.properties`).
- **Still to do:** the energy cube's inner core (drawn in code by Mekanism) as a display entity.

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
| E10 | Item tags: an `item-tag:` filter, a provider backed by CraftEngine (`itemIdsByTag`: vanilla and custom items), and aliases such as `c:ores/iron` → `#minecraft:iron_ores` | Every imported recipe (Mekanism uses `#c:` tags) |
| E11 | Definition item ids resolve CraftEngine custom items, not only vanilla materials | Recipes and filters naming `mekanized:` items |

## Verification per phase
- **Headless tests:** each processing chain gives exactly the expected yield. For example, 1 ore gives 2 ingots through the Enrichment Chamber and 5 through the full chemical chain. Resources are always conserved.
- **Server checks:** commands in the style of `/mexample` that build a line of machines and count the results, on Paper 1.20.6 and Folia 26.2.
- **Handoff:** a stop at the end of each phase so the owner can try it in game.
