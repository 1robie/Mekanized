# Mekanized: server owner's guide

Mekanized brings Mekanism's machines to Paper and Folia servers, with no client mod: CraftEngine sends the
blocks and items in a resource pack, and zMenu draws the machine menus. Textures, models, recipes and
names come from Mekanism (MIT, by Aidan C. Brady; `LICENSE-MEKANISM.txt` ships with the plugin).
Mekanized is not affiliated with Mekanism.

## Installing

1. Use Paper or Folia, Minecraft 1.20.6 to 26.x.
2. Put **CraftEngine**, **MachineEngine** and **Mekanized** in `plugins/`, and **zMenu**. zMenu is optional
   for MachineEngine, but without it machines have no menus.
3. Start the server once, then run `/ce reload all`. CraftEngine builds the resource pack with the
   Mekanized blocks and items and sends it to players.

Mekanized writes its files on the first start:

| Where | What |
|---|---|
| `plugins/CraftEngine/resources/mekanized/` | Blocks, items, crafting recipes, names in every language Mekanism has |
| `plugins/MachineEngine/definitions/mekanized/` | Machine recipes and tags |
| `plugins/MachineEngine/menus/mekanized_*.yml` | Machine menus (zMenu) |
| `plugins/Mekanized/config.yml` | Gameplay rules: radiation, teleporters, chunk loading and miner limits, QIO array size |

**Updates:** updating Mekanized replaces these files only if you have not edited them. An edited file is
kept, and the log says so; to get the new default, delete it.

**Energy and fluids are MachineEngine's:** Mekanized uses `machineengine:energy` and MachineEngine's
water and lava (`machineengine:fluid/water`, `…/lava`); its other fluids (`mekanized:fluid/brine`, …)
join MachineEngine's fluid category, so any fluid pipe or port moves them. Units and the bucket size
are set in `plugins/MachineEngine/config.yml` (`resources:`). The old keys `mekanized:energy`,
`mekanized:fluid/water` and `mekanized:fluid/lava` still work in edited definition files and saved
worlds.

## Playing

### Energy and machines
- **Getting energy:**
  - generators (heat, bio, solar) make energy;
  - energy cubes store it;
  - universal cables carry it.
- **Powering a machine by hand:** redstone in its energy slot gives 10 kJ per dust, and a charged
  Energy Tablet works too (energy cubes charge tablets in their charge slot).
- **Upgrades:**
  - Speed upgrades make a machine faster and costlier; machines whose recipes take one tick run one
    more operation per tick instead.
  - Energy upgrades lower the energy cost, and enlarge the energy buffer by its base size.

### Ore processing
| Per ore | Chain |
|---|---|
| 2 ingots | Enrichment Chamber → smelt the dust |
| 3 ingots | Purification Chamber (oxygen) → Crusher → Enrichment Chamber |
| 4 ingots | Chemical Injection Chamber (hydrogen chloride) → Purification Chamber → … |
| 5 ingots | Chemical Dissolution Chamber (sulfuric acid) → Chemical Washer (water) → Chemical Crystallizer → … |

Chemicals and fluids are made by the chemical machines: Electrolytic Separator, Chemical Oxidizer,
Chemical Infuser, Rotary Condensentrator. They are stored in tanks and carried by pressurized tubes
(chemicals) and mechanical pipes (fluids). Water and lava buckets work in bucket slots. The Electric Pump
takes water and lava source blocks from below it.

### The Configurator
- **Right-click a machine:** its side configuration. Each side is input, output, both or none, for
  items, energy, chemicals and fluids, with a tab for each; auto-eject is there too.
- **Sneak + right-click a machine:** turns it.
- **Right-click a transport, or a chest next to one:** cycles how that side connects:
  - **default:** machines in and out, chests only filled;
  - **pull:** takes from the block (funnel arm);
  - **push:** only fills it (nozzle arm);
  - **none:** disconnected, also from another transport.
- **Right-click an induction port:** switches it between input and output.

### Logistics
- **Logistical transporters** carry items in batches every half second. To move items out of a chest,
  set the transporter side touching it to pull, or use a Logistical Sorter (with filters).
- **Factories** run 3 to 9 operations at once. A basic tier installer turns a machine into its basic
  factory; higher installers raise the tier one step. The machine keeps its contents.

### Multiblocks
Hollow boxes; right-click any part to open the structure's menu. If the action bar says "Not formed",
check the walls and the inside.

| Multiblock | Walls | Inside | Size |
|---|---|---|---|
| Dynamic Tank | Dynamic Tank casings (every edge), Dynamic Valves, Structural Glass | Air | 3 to 18 blocks a side |
| Induction Matrix | Induction Casings (every edge), Induction Ports, Structural Glass | Induction Cells, Induction Providers, air | 3 to 18 blocks a side |
| Thermal Evaporation Plant | Thermal Evaporation Blocks (every edge and the floor), valves, exactly one controller | Air, no roof | 4x4, 3 to 18 high |

Each structure works as follows:
- **Dynamic Tank:** holds one fluid or one chemical; pipes or tubes on its valves fill and empty it.
- **Induction Matrix:** cells store energy and providers set the output rate.
- **Thermal Evaporation Plant:** turns water into brine and brine into liquid lithium. It is faster when
  taller, by day, and when it is not raining.

The lowest corner block keeps the structure's contents. Breaking another block unforms the structure
until it is rebuilt; breaking the corner block keeps the contents in its item.

### Upgrades
Click a machine's upgrade button with the upgrade in hand to install it.

| Upgrade | Effect | Fits |
|---|---|---|
| Speed | Faster, uses more energy per tick | Machines with upgrade slots |
| Energy | Less energy per operation, bigger energy buffer | Machines with upgrade slots |
| Chemical | Less chemical per tick | Osmium Compressor, Purification and Injection Chambers, their factories |
| Filter | Pumps heavy water (10 mB a block) out of water | Electric Pump |
| Anchor | Keeps the machine's chunk loaded, even after a restart | Machines with upgrade slots |
| Stone Generator | Fills each mined block with stone | Digital Miner |

### Frequencies, chunk loading and the Quantum Entangloporter
- **Frequencies:** look at a teleporter, QIO block or Quantum Entangloporter and run
  `/mekanized frequency <name>`. Holding a portable teleporter or portable QIO dashboard instead sets
  the item's frequency.
- **Dimensional Stabilizer:** keeps its own chunk loaded, or the 3x3 or 5x5 chunks around it, while it
  has energy (500 J/t per chunk).
- **Quantum Entangloporter:** every entangloporter of a frequency shares one buffer of energy, items,
  fluid and chemical, across the whole server. Set each one to send (fed by cables, pipes, tubes and
  hoppers) or receive (pushes out of every side) in its menu.

### Radiation
- **Sources:** a fission reactor reaching 100 % damage SCRAMs and lets its nuclear waste out as
  radiation. Sources fade by half every few minutes.
- **Dose:** players take in the radiation around them. A high dose makes them hungry, then sick, then hurts.
- **Protection:** each hazmat piece, or MekaSuit piece with a radiation shielding unit, stops a quarter.
- **Tools:** the Geiger counter reads the radiation where you stand, and the dosimeter your dose. A
  MekaSuit helmet with the matching unit shows them on the action bar.
- **Radioactive Waste Barrel:** stores up to 512,000 mB of radioactive chemicals while they decay.

### Tools
| Tool | Use |
|---|---|
| Configuration Card | Sneak-right-click a machine to copy its sides, auto-eject and redstone setting; right-click another of the same kind to paste |
| Dictionary | Right-click a block (or the air, for the item in your off hand) to list its tags |
| Gauge Dropper | Right-click a machine to take up to 16,000 mB (its item config's `capacity`) of a fluid or chemical; sneak-right-click to put it back; sneak-right-click the air to empty it |
| Crafting Formula | Sneak-right-click a Formulaic Assemblicator to record its pattern; right-click one with an empty pattern to set it |
| Portable Teleporter | Right-click to go to the teleporter of its frequency (energy as a teleporter) |
| Portable QIO Dashboard | Right-click to open the QIO of its frequency |
| Canteen | Fill it at a Nutritional Liquifier (food into paste); right-click to eat |

### Gear and modules
- **Installing:** sneak-right-click with a module while wearing the piece it fits. For the Meka-Tool,
  hold the tool in your off hand.
- **Removing:** right-click a Modification Station holding the gear; its last module comes back out.
- **Not supported:** the color modulation and laser dissipation units (lasers do not hurt in Mekanized).

## Configuring
- **`plugins/Mekanized/config.yml`:** the gameplay numbers. Restart to apply. The defaults are Mekanism's
  (or what Mekanized shipped with), so a server that never edits the file plays as before. A key you leave out
  keeps its default; a wrong value is replaced or limited to its nearest allowed value, with a warning in the log
  (the file keeps what you wrote, for you to fix). Settings added by an update are written into your file, with
  their comments, at the next start; your values and comments are kept.
  - `radiation`: background, how fast sources and doses fade, the doses that make players sick, hurt them and kill them.
  - `teleporter`: Joules per teleport (base, per block, to another world) and the cooldown between teleports.
    The Meka-Tool's teleportation unit uses the same costs.
  - `chunk-loading`: the dimensional stabilizer's widest radius.
  - `qio`: kinds per drive array (it sets the array's slots).
  - `digital-miner`: positions scanned per tick, widest radius.
- **Recipes** (durations, energy per tick, inputs and outputs): edit
  `plugins/MachineEngine/definitions/mekanized/recipes_*.yml`. They reload with the server's recipes.
  The Energized Smelter uses every furnace recipe of the server, datapacks included.
- **Crafting recipes:** in CraftEngine's `recipes_*.yml` under `plugins/CraftEngine/resources/mekanized/`.
- **Menus:** the zMenu files `plugins/MachineEngine/menus/mekanized_*.yml`. Placeholders are
  `%zmenu_machineengine_<key>%`; see MachineEngine's README.

  Gear has no `config.yml` settings any more: every number is in the item's CraftEngine config
  (`plugins/CraftEngine/resources/mekanized/configuration/gear.yml`, `chemistry.yml`), under `behaviors`.
  Edit it there and run `/ce reload`. Old `gear.*` keys left in `config.yml` are ignored.

  Blocks keep their own numbers in their CraftEngine block config the same way (D34), under `behavior`:
  - `rate:` on every universal cable, pressurized tube, mechanical pipe, logistical transporter and
    thermodynamic conductor (per tick; transporters: items every 10 ticks);
  - `storages: { items: { capacity: ... } }` on the bins;
  - `options: { charge-per-tick: ... }` on the chargepad (Joules per player per tick);
  - `options:` on the nutritional liquifier (`ticks`, `energy-per-tick`), electric pump (`energy`, `ticks`,
    `range`, `heavy-water-per-block`), fuelwood heater (`heat-per-tick`), seismic vibrator (`energy-per-tick`),
    lasers (`energy-per-tick`, `range`, `energy-per-hardness`), digital miner (`energy-per-block`,
    `ticks-per-block`) and dimensional stabilizer (`energy-per-chunk`);
  - the radioactive waste barrel: `storages: { waste: { capacity: ... } }` and `options: { decay-per-second: ... }`;
  - `options: { items-per-second: ... }` on the QIO importer and exporter;
  - multiblocks: `options:` on the casing that anchors each structure (`multiblocks.yml`, `reactors.yml`): the
    dynamic tank, the induction casing (`cell-capacity` per tier), the thermal evaporation block, the boiler,
    fission and SPS casings, the fusion reactor frame and the turbine casing: their capacities per block and their
    physics (heat per mB, ignition energy, steam energy, vent flow...). The induction port has `provider-output`
    per tier.

  A QIO drive's size is in its item config: `- type: mekanized:qio_drive` with `items` and `kinds`
  (`automation.yml`). Any item given that behavior goes into a drive array; `/ce reload` applies it.

  Restart the server after editing a block's options: a machine keeps the ones it read when it loaded (whether
  `/ce reload` reaches machines already loaded is not checked yet). Old `config.yml` keys
  for these (`transport.*`, `radiation.barrel-*`, `chunk-loading.energy-per-chunk`,
  `digital-miner.energy-per-block`, `digital-miner.ticks-per-block`, `qio.drive-items`, `qio.drive-kinds`,
  `qio.importer-exporter-rate`, `dynamic-tank.*`, `thermal-evaporation.*`) are ignored.

  | Behavior | Items | Options |
  |---|---|---|
  | `machineengine:energy_item` | energy tablet, tools, MekaSuit, free runners | `max-energy` |
  | `machineengine:resource_item` | jetpacks, flamethrower, scuba tank, canteen, MekaSuit body armor | `resource`, `capacity`, `fill-from-machines` |
  | `mekanized:mining_tool` | atomic disassembler, Meka-Tool | `energy-per-block`, `vein-limit`, `vein-limit-with-unit`, `hit-energy`, `damage`, `amplified-damage`, `charged-speed`, `amplified-speed` |
  | `mekanized:meka_tool` | Meka-Tool | `teleport-range`, `shear-energy` |
  | `mekanized:electric_bow` | electric bow | `energy-per-shot` |
  | `mekanized:flamethrower` | flamethrower | `hydrogen-per-use`, `damage` |
  | `mekanized:jetpack` | jetpacks, MekaSuit body armor | `hydrogen-per-tick`, `thrust`, `max-speed`, `walk-speed`, `sprint-speed`, `requires-module` |
  | `mekanized:scuba_tank` | scuba tank | `oxygen-per-refill`, `mask` |
  | `mekanized:fall_absorber` | free runners, MekaSuit boots | `energy-per-damage` |
  | `mekanized:radiation_shielding` | hazmat suit, MekaSuit | `protection` (0.25 a piece), `requires-module` |
  | `machineengine:wrench` | configurator | `click`, `sneak-click`: `rotate`, `dismantle` or `none` |
  | `mekanized:module` | `module_*` items | `module`, `energy` (per second, per use or made), `range`, `capacity-factor`, `enchantment`, `enchantment-level`, `glider`, `fits` (the items it goes into) |

## Commands (permission `machineengine.admin`)
| Command | What it does |
|---|---|
| `/mekanized build <structure> [x y z]` | Builds the smallest working multiblock (dynamic_tank, induction_matrix, thermal_evaporation, boiler, turbine, fission_reactor, fusion_reactor, sps, teleporter) towards +x, +y, +z |
| `/mekanized radiation <Sv/h>` / `clear` | Adds a radiation source where you stand / removes every source |
| `/mekanized test` | Places an Enrichment Chamber, feeds it, and logs the result (2 osmium dust) |
| `/mekanized test-cable` | A creative energy cube powering a chamber through a cable |
| `/mekanized test-chemistry` | An Electrolytic Separator feeding a chemical tank through a tube |
| `/machineengine ...` | MachineEngine's own: machine info, debug view, placeholders |

## Differences from Mekanism
- **Transporters:** items move between batches instantly, with no items drawn on them. There are no
  restrictive or diversion transporters.
- **Thermal Evaporation Plant:** its heat is simpler (height, daylight, rain).
- **Fission meltdown:** a SCRAM plus radiation, no explosion.
- **Radiation:** only fission breaches release it; dumping chemicals does not.
- **Not made:** the security desk, the muffling upgrade (machines make no sound), the QIO redstone
  adapter, and the color modulation and laser dissipation units.
- **No ore generation:** use your world's ores, or a world generator plugin.

## Troubleshooting
- **Players see missing textures or nothing at all:** run `/ce reload all` after an update.
- **CraftEngine reports no free block states:** run `/ce clean-cache visual_block_states`, then
  `/ce reload all`. Old entries hold states that new blocks need.
- **A multiblock does not form:**
  - every block must be loaded;
  - edges must be casings;
  - nothing but the allowed blocks may be inside;
  - on Folia, the whole structure must be in one region.
