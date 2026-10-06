# Mekanized: plan

A Mekanism-style content plugin for MachineEngine. It is a separate plugin that depends only on
MachineEngine's API, its YAML definitions and a CraftEngine pack (D27; it still uses some engine
internals until RESTRUCTURE_PLAN.md R7). It is also the reference addon since the example was removed.

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
- **Module:** `mekanized` (Paper plugin, dev servers install it next to MachineEngine).
- **`importMekanism` task:** downloads the pinned commit as an archive and caches it under `.mekanism/`.
  - It copies the textures, models and `.mcmeta` animations that Mekanized uses into the CraftEngine pack, renaming the namespace to `mekanized`.
  - It converts Mekanism recipes into Mekanized YAML: recipe types map to ours (enriching, crushing, …), and crafting and smelting recipes become CraftEngine recipes.
  - `#c:` and `#mekanism:` tags become Mekanized tag files, which include the matching vanilla tags.
  - Imported data is generated into `build/` and packaged. A small hand-written overlay holds what cannot be converted.

**Status: done (2026-10-02).**
- **Pin:** `gradle.properties` pins `mekanism_commit` (branch 26.1, `4718860`).
- **`:mekanized:fetchMekanism`:** a shallow, sparse, blob-filtered clone into `.mekanism/source` (about 25 MB).
- **`importMekanism` (buildSrc `ImportMekanism`):**
  - Assets: 1,038 textures and 680 models.
  - Recipes: 950 machine recipes in 18 recipe types (`definitions/recipes_<type>.yml`, with durations and Joules per tick from Mekanism's machine defaults), and 353 crafting, smelting and blasting recipes (CraftEngine `configuration/recipes_<group>.yml`).
  - Tags: 191 item tags and 17 chemical tags (`definitions/tags.yml`).
  - Skipped, and listed in `import-report.txt`: 637 recipes for other mods, 370 painting and pigment recipes, endgame and multiblock recipes, and 90 models that need NeoForge loaders (83 composite, which can be flattened later, 6 OBJ and 1 energy cube).
- **Plugin:** installs the CraftEngine pack with the imported assets, refreshed on every start. The import writes `manifest.yml`, the list of generated files the plugin installs (one declarative phase table, `ContentPhases.kt`, decides which exist). `LICENSE-MEKANISM.txt` ships in the jar and in the pack.
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

**Status: done (2026-10-02), not yet tried in game.**
- **Engine:**
  - Chemical and fluid networks (E9) needed nothing new: `NetworkBehavior.distributing()` and a transport filter per resource category.
  - Port directions a behavior can switch at runtime (`MachineContext.setPortDirection` / `portDirection`), not saved: the behavior sets them again from its own data on load. A first step toward per-machine side configuration (E2).
  - World fluids (E5): `MachineContext.worldFluidAt` returns a `WorldFluid` (the platform's fluid id, and whether the block is a source), and `takeWorldFluid` removes a source block. Only on the owning thread: Folia regions are not crossed. The CraftEngine host reads water and lava; `FakeWorld.setFluid` sets them in tests.
  - The `machineengine_data` zMenu button changes a setting the machine type declares (switches a boolean, or cycles `values:`), shown by `%zmenu_machineengine_data_<id>%`.
- **Mekanized, in Java:**
  - Fluids: a `mekanized:fluid` category with `mekanized:fluid/<name>` for water, lava and Mekanism's 22 fluids, separate from the chemicals of the same name.
  - Machines: Chemical Oxidizer, Chemical Infuser (left and right tanks on their sides, output at the front), Chemical Injection Chamber, Chemical Dissolution Chamber, Chemical Washer, Chemical Crystallizer, Electrolytic Separator (two output tanks) and Rotary Condensentrator (a mode switch, chemical on the left, fluid on the right).
  - Electric Pump: 2 kJ for one water or lava source block (1000 mB) a second, below it or reached through the fluid below, up to 32 blocks away.
  - Tanks: chemical tanks (64k to 8.192M mB, output through the front) and fluid tanks (32k to 256k mB, output at the bottom), four tiers plus creative.
  - Pressurized tubes and mechanical pipes, four tiers each, at Mekanism's pull rates.
  - Bucket slots (`bucket_in`, `bucket_out`) on fluid tanks and fluid machines, from `bucket_filling` / `bucket_draining` recipes: vanilla water and lava buckets only, because Mekanism's buckets need a mod-side model.
  - Tank filters from recipes: a machine's tanks take only the chemicals or fluids its recipes use, as inputs or per-tick costs.
- **Generated:**
  - CraftEngine blocks for 19 machines and tanks, 8 tubes and pipes (`chemistry.yml`), and the electrolytic core and energy tablet items.
  - The rotary recipes are split into `condensing` and `decondensing`. Fluid tags become fluid types. Per-tick chemical tags become their first chemical, for example water vapor.
  - 31 crafting recipes (`recipes_chemistry_shaped.yml`). The chemical oxidizer's recipe is adapted: Mekanism's needs a dynamic tank and personal storage, which are outside the plan, so it uses a basic fluid tank and a chest. The import report lists the change.
  - Every tier of a transport family shares one CraftEngine sapling state, which has only 8 slots, through `auto_state: {type: sapling, id: ...}`. Display-rendered machines keep one state each, so their breaking particles stay right.
  - zMenu menus for every Phase 3 block.
  - `display_rotation` on display-rendered blocks: the model's own turn when facing north (separator 90°, pump and bio generator 180°), which the display entity adds to its yaw.
- **Tests:** in `MekanizedChainTest`:
  - one osmium ore gives 1000 mB of dirty slurry, then 1000 mB of clean slurry (5 mB of water per mB), then 5 crystals;
  - the separator splits water (2 mB give 2 mB of hydrogen and 1 mB of oxygen);
  - the rotary works both ways and its ports swap with the mode;
  - buckets fill and empty a fluid tank;
  - the pump reaches a source block through flowing water;
  - a pressurized tube carries brine from a chemical tank to a crystallizer.
- **Server check:** `/mekanized test-chemistry`: a separator, a tube and a chemical tank, with 1000 mB of water.
- **Known limits:** speed upgrades do not speed up the one-tick chemical recipes (washer, infuser, separator, rotary). Mekanism runs several operations per tick instead; that comes with factories (E7, Phase 4). Chemical and fluid names show as resource ids in menus.

### Phase 4: logistics and the Mekanism feel
- **Logistics:** logistical transporters (item pipes, tiers) and filters.
- **Machine setup:** side configuration per machine, with the input and output colors and auto-eject.
- **Factories and tiers:** factories in basic, advanced, elite and ultimate, and tier installers that upgrade a placed machine and keep its state.
- **Tools:** Configurator, and an Energy Tablet that charges items.

**Status: done (2026-10-02), not yet tried in game.**
- **Engine:**
  - Side configuration (E2): `MachineContext.setPortSides` / `portSides`, saved with the machine (`sides` in its data, only for ports moved from the type's sides). Ports have a `group` (Mekanized: items, energy, chemical, fluid); each side shows at most one port of a group. Networks and transport arms follow the configuration.
  - Container blocks (`MachineHost.worldHandlerAt`): vanilla chests, barrels, shulker boxes, hoppers, dispensers and droppers are item handlers, changed in transactions (undone on abort). Machines push into them and read them as neighbors; networks only fill them, so two chests cannot pass items back and forth. Transports next to a changed block re-read their network's endpoints.
  - Item batches: `NetworkBehavior.distributing(everyTicks)`.
  - Changing a placed machine's type while keeping its state (E6): `CoreMachines.restoreAs`, and `MachineLocator.replaceBlock` on the CraftEngine host. It places the new block with the same (or a given) facing, and the new machine takes the contents over; nothing drops. The running process is cancelled first (its inputs go back).
  - zMenu: `machineengine_side` (one side of a port group), `machineengine_menu` (another menu for the same machine, e.g. side configuration and back), `%zmenu_machineengine_side_<group>_<side>%`. A `machineengine_data` button hides on machines without that setting.
  - `MachineLocator.current()` and `MachineMenuService.current()` let addons reach the host and the menus.
- **Mekanized, in Java:**
  - Auto-eject (`EjectBehavior`, the `eject` setting): outputs are pushed out of the sides their ports are on, into machines or containers.
  - Logistical transporters, four tiers (8, 16, 32, 64 items every 10 ticks), on an item network.
  - Logistical sorter: every 10 ticks it pulls up to a stack from the block behind it (a container or a machine's output), and pushes out of its front, into a machine, a container or a transporter. Nine filter slots: only matching items pass; with no filter, everything does.
  - Factories (E7, `FactoryBehavior`): 9 kinds × 4 tiers (3, 5, 7 and 9 lanes). As in Mekanism, a lane counts progress, paying its energy (and chemical) each tick, and takes its inputs only when the operation completes. Items spread over empty lanes. Speed and energy upgrades apply. Lane 1 is the machine's own `input`, so upgrading keeps it.
  - Tier installers: a machine becomes its basic factory, a factory its next tier, one tier at a time.
  - Configurator: right-click opens the side configuration; sneak + right-click turns the machine (it keeps its contents; a running operation restarts).
  - Energy Tablet (E4, `EnergyItems`): holds 1 MJ in the item's persistent data, shown in its lore. In a machine's energy slot it gives 10 kJ per tick; energy cubes have a charge slot that fills it at the cube's output rate.
- **Generated:**
  - `logistics.yml`: the sorter, 36 factories (composite models flattened, children's parent models inlined), transporters, tier installers and the configurator (a `select` item model: its fallback). 46 crafting recipes in `recipes_logistics_shaped.yml`.
  - The four tiers of a factory kind share one leaves state (9 instead of 36), and every Mekanized transport shares the universal cable's sapling state (CraftEngine has only 8).
  - A side configuration menu (`mekanized_side_config`): rows of items, energy, chemicals and fluids, one button per side, plus the auto-eject switch. Every machine menu opens it. Sorter and factory menus are generated too.
- **Tests:** side configuration (moved ports, saved), auto-eject into a tank, a factory's 3 lanes at once, a tier upgrade keeping energy and input, a sorter feeding a chest through transporters with a filter, and a cube charging a tablet that powers a machine.
- **Fixes after in-game testing (2026-10-02 and 03):**
  - **Transport side modes** (Mekanism's connection types, `TransportSideMode`), set per transport side and saved with the block:
    - default: machines in and out, containers only filled;
    - pull: takes from the block;
    - push: only fills it;
    - none: disconnected, also from another transport (the network splits).
  - **Configurator on transports:** it cycles the clicked side, or, on a container, the side of every transport touching it. Pull sides draw Mekanism's funnel arm and push sides its nozzle.
  - **Side configuration by roles:** each side is input, output, both or none for a whole port group (every factory lane at once). There is one menu per group, with tabs, laid out as an unfolded cube.
  - **Factories:** a lane uses whichever recipe input matches its item (Mekanism lists the infuse type first).
  - **Furnace recipes:** ingredients of any kind are read, including 26.x item-type choices. The importer logs how many it registered.
  - **Transporters:** drawn with Mekanism's opaque textures (it draws a glass layer that vanilla cannot).
- **Known limits:**
  - Transporters move items instantly, with no items drawn travelling along them. Restrictive and diversion transporters are not made yet.
  - Turning a machine with the configurator restarts its current operation.

### Polish after Phase 4 (2026-10-03)
- **Names in menus:** `%zmenu_machineengine_storage_<id>_name%` (and `_slot_<n>_name`) shows a resource's name. Addons name their resources through `ResourceNames`. Mekanized's are translated on each player's client: chemicals (`chemical.mekanized.*`), fluids (their liquid blocks, `block.mekanized.*`; vanilla water and lava), energy.
- **Speed upgrades on one-tick recipes:** a `processes_per_tick` modifier. `ProcessingBehavior` completes that many one-tick processes per tick, each paying its own cost. Each speed upgrade adds one (washer, infuser, separator, rotary, oxidizer...).
- **Energy cube core:** a `level_display` block option, a gauge inside the block (`<item>/1..<steps>` by how full a storage is, nothing when empty). Energy cubes show Mekanism's core, tinted with the tier's color, in 4 sizes.
- **Energy cube parts:** the frame always shows, and each side's port and LEDs only while that side has a port (`side_display`). Ports are redrawn on every side configuration change (`MachineHost.portsChanged`). Mekanism's left and right, seen from the front, are swapped to MachineEngine's, seen from behind.
- **Energy upgrades:** as in Mekanism, each one also enlarges the energy buffer by its base size (`StorageDefinition.capacityModifier`, a storage capacity that follows upgrades; upgrades are decoded before storages, so energy above the base size survives a reload).
- **Machines facing up or down:** their sides follow the turned block. The front is the facing, the top faces south (up) or north (down), and left and right stay west and east.
- **CraftEngine reloads:** block properties (active, facing) are read by name from the current state; property objects from before a reload no longer match.
- **Left out:**
  - restrictive and diversion transporters: they change Mekanism's per-item routing, and our networks move without paths;
  - items drawn travelling along transporters: display entities moved every tick, too costly for busy servers.

### Phase 5a: multiblocks (started 2026-10-03, the owner's choice)
- **Order:**
  1. Dynamic Tank (casings, valves, structural glass; one fluid or one chemical);
  2. Induction Matrix (casings, ports, cells, providers);
  3. Thermal Evaporation Plant (Mekanism's evaporating recipes).
  The boiler and the fission and fusion reactors need Mekanism's heat system, and come later.
- **Engine (E12):**
  - **Reading blocks:** `MachineHost.blockAt` gives the block at a position (a CraftEngine id or a vanilla one), on the owner thread.
  - **Runtime capacities:** `MachineContext.setStorageCapacity` sizes a storage at runtime. It is saved with the machine and read before the storages.
  - **Passing through:** `MachineContext.exposeHandler` makes a machine show another handler on all its sides (valves and ports pass through to the structure's storage).
  - **Another machine's storage:** `MachineContext.storageAt` gives full access to a storage of another machine that is loaded and owned by the same thread.
- **Mekanized:**
  - **Members:** every frame block (casing, valve, port, glass) is a machine; the interior blocks of the matrix (cells, providers) are plain blocks.
  - **Detection:** a structure is a hollow box whose edges are casings, whose faces are casings, valves, ports or glass, and whose interior follows the type's rule; between 3 and 18 blocks on each side.
    - It runs when a member loads, when one is removed, and on a configurator click.
    - It forms only if every block is loaded and in the same Folia region.
  - **Contents:** the lowest corner casing (the anchor) holds the structure's storage, sized at formation.
    - Removing another block unforms the structure; the anchor keeps the contents until it forms again.
    - Breaking the anchor keeps them in its item (keep-contents, like every Mekanized machine).
    - Menus of every member open the anchor's.
- **Status (2026-10-03):** the Dynamic Tank and the Induction Matrix are done, not yet tried in game.
  - Members re-check their structure when they load. The anchor re-checks every 5 seconds, which notices blocks placed inside or cells added.
  - The configurator switches an induction port between input and output; an output port pushes energy to its neighbors at the providers' rate.
  - Tests: `MultiblockTest` covers formation, the valve passing through, one fluid or chemical at a time, unforming and forming again, matrix capacity from cells, and an output port feeding a machine.
  - **Thermal Evaporation Plant** (built 2026-10-03, not yet tried in game):
    - a 4x4 tower, 3 to 18 high, open at the top, with exactly one controller (the detector gained a fixed footprint, an open top and one required block);
    - the anchor evaporates once a second by Mekanism's recipes (water to brine, brine to liquid lithium): 10 mB of input per tick per level, halved at night and again in rain (simpler than Mekanism's temperatures);
    - valves fill the input and give the output; bucket slots for both.

### Phase 6: the rest of Mekanism (2026-10-03 to 2026-10-04, the owner's choice: "implement all")
- **Wave 1:** the remaining single-block machines and generators.
- **Wave 2:** bins, personal storage, chargepad, plenisher, assemblicator, lasers, alarm.
- **Wave 3:** heat; boiler, turbine, fission and fusion reactors, SPS.
- **Wave 4:** Digital Miner, seismic vibrator, teleporter, QIO, cardboard box.
- **Wave 5:** tools, armour and modules; gear models converted from Mekanism's Java models.
- **Wave 6 (2026-10-04, not yet tried in game):**
  - **Missing items:** the intermediates the imported recipes used without declaring them (pellets,
    yellow cake, reprocessed fissile fragment, dirty netherite scrap, solar panel).
  - **Upgrades:** chemical, filter (heavy water), anchor (chunk loading) and stone generator.
  - **Machines:** dimensional stabilizer; quantum entangloporter (a shared buffer per frequency, locked
    and changed after each machine's commit, so it works across Folia regions; saved in
    `entangloporters.txt`).
  - **Radiation:** sources from fission breaches; player dose with hazmat or module shielding; waste
    barrel, dosimeter, Geiger counter.
  - **Food:** nutritional liquifier and canteen.
  - **Tools:** configuration card, dictionary, gauge dropper, crafting formula, portable teleporter and
    QIO dashboard.
  - **Gear:** the remaining modules, and the modification station that removes them.
  - **Tests:** `ExtrasTest`.
- **Left out:**
  - the security desk;
  - the muffling upgrade (machines make no sound);
  - the QIO redstone adapter (no usable blockstate);
  - restrictive and diversion transporters;
  - the color modulation and laser dissipation units.

## Settings (cleanup stage 5, 2026-10-06, the owner's choice)
- **`config.yml`:** gameplay numbers moved from code into sections: `gear`, `radiation`, `teleporter`, `chunk-loading`, `qio` and `digital-miner`, next to the earlier `transport`, `dynamic-tank` and `thermal-evaporation`. Server owners tune them; read when the server starts. The defaults equal the numbers that were in the code, so behaviour is unchanged out of the box.
- **Code:** each family has a plain Java holder in `machine/` (`GearSettings`, `RadiationSettings`, `TeleporterSettings`, `ChunkLoadingSettings`, `QioSettings`, `DigitalMinerSettings`) with static fields, a `DEFAULT_*` constant for each, and `reset()` for tests. Machine and multiblock code read the holders, so they stay free of Bukkit. `MekanizedSettings.load` (and `GearSettingsLoader`) reads the file in `onLoad`, before machines are registered, and validates every value: a missing key keeps the default, a value that is not a number is replaced by the default, an out-of-range number is limited to its nearest bound, each with a warning in the log.
- **Tests:** `SettingsTest` checks that the defaults equal the old numbers, that the `config.yml` shipped in the jar loads without a warning and gives them, that overridden values are read, and that bad values are limited. Behaviour tests check an overridden teleport cost, stabilizer radius and energy, miner energy and radius, drive sizes and importer rate, radiation decay and barrel decay.
- **Left in code:** numbers that are not gameplay constants (animation and potion durations, step height, mining speeds, chemical radioactivity per chemical, buffer sizes of machines, menu layouts), and the seismic vibrator's energy.

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
| E13 | Process costs per resource category (`StandardModifiers.processCost`, `MachineContext.modifiedCost`) and `placeWorldBlock` | Energy and chemical upgrades, stone generator |

## Verification per phase
- **Headless tests:** each processing chain gives exactly the expected yield. For example, 1 ore gives 2 ingots through the Enrichment Chamber and 5 through the full chemical chain. Resources are always conserved.
- **Server checks:** commands in the style of `/mexample` that build a line of machines and count the results, on Paper 1.20.6 and Folia 26.2.
- **Handoff:** a stop at the end of each phase so the owner can try it in game.
