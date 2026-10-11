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
4. Players need that resource pack (make CraftEngine send it as required): it also draws the machine
   menus. Without it, menu titles show boxes and the gauges are invisible.

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
- **Installing:** right-click the machine with an upgrade (sneak: as many as fit), or click the upgrade tab
  (top right of the machine's menu) with upgrades on the cursor (right-click: one).
- **The upgrades menu:** click the upgrade tab with an empty hand. It shows the upgrades the machine takes,
  each with `installed/max`: click one to take one out, shift-click to take them all out, click with upgrades
  to install them. Put upgrades in the slot below, or shift-click them from your inventory.
- A machine has 2 upgrade slots: two kinds at once, each up to its max (8 speed, 8 energy, 8 chemical, 1 of the others).
  A refused upgrade says why on the action bar.

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
  MekaSuit helmet with the matching unit shows them on the action bar; players hide it with
  `/machinesettings radiation_hud off`.
- **Saved dose:** the dose is player data `mekanized:radiation_dose` (reset on death). Menus show it
  with `%zmenu_machineengine_player_mekanized_radiation_dose%`; admins read or clear it with
  `/machineengine player <player> get|reset mekanized:radiation_dose`. Doses saved by older versions
  move over the first time each player joins.
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
- **The Modification Station:** put a MekaSuit piece or the Meka-Tool in its gear slot (bottom left) and a
  module in the slot above it. With energy it installs one level every 2 seconds (400 J a tick); the line
  under the slots says why it waits (no gear, does not fit, at its max level, needs energy...). The middle
  shows the gear's modules: click to take a level out, shift-click to take them all out, right-click to
  switch one on or off. Taking out an energy unit that would lose stored energy asks first: click again
  within 5 seconds.
- **Shortcut:** sneak-right-click with a module while wearing the piece it fits (the Meka-Tool: in your off
  hand) to install one level.
- **Levels:** some modules take several, as in Mekanism; the item's tooltip shows `level/max`, and a module
  switched off shows gray and does nothing.

  | Module | Max | Each level |
  |---|---|---|
  | Energy unit | 8 | doubles the piece's energy capacity (cannot be switched off) |
  | Solar recharging, geothermal generator | 8 | more energy made |
  | Fortune unit | 3 | Fortune I more |
  | Magnetic attraction | 4 | 4 blocks more range |
  | Locomotive boosting, hydraulic propulsion | 4 | stronger speed or jump, more energy |
  | Vein mining, excavation escalation, attack amplification | 4 | a quarter of the way to the full boost |
  | Every other module | 1 | |

- **Enchantments** from modules (fortune, silk touch) are taken back when the module goes or is switched off;
  an enchantment the item had before comes back.
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
  `%zmenu_machineengine_<key>%`; see MachineEngine's README and "Textured menus" below.

  Gear has no `config.yml` settings any more: every number is in the item's CraftEngine config
  (`plugins/CraftEngine/resources/mekanized/configuration/gear.yml`, `chemistry.yml`), under `behaviors`.
  Edit it there and run `/ce reload`. Old `gear.*` keys left in `config.yml` are ignored.

  Blocks keep their own numbers in their CraftEngine block config the same way (D34), under `behavior`
  (or, for a block made by a config factory, in its row: see "Reading the block and item files" below):
  - `rate:` on every universal cable, pressurized tube, mechanical pipe, logistical transporter and
    thermodynamic conductor (per tick; transporters: items every 10 ticks), in the rows of the
    `config_factory#..._transports` at the end of `machines.yml`, `chemistry.yml`, `logistics.yml` and `reactors.yml`;
  - `storages: { items: { capacity: ... } }` on the bins (rows of `config_factory#bins`, `utility.yml`);
  - `options: { charge-per-tick: ... }` on the chargepad (Joules per player per tick);
  - `options:` on the nutritional liquifier (`ticks`, `energy-per-tick`), electric pump (`energy`, `ticks`,
    `range`, `heavy-water-per-block`), fuelwood heater (`heat-per-tick`), seismic vibrator (`energy-per-tick`),
    lasers (`energy-per-tick`, `range`, `energy-per-hardness`), digital miner (`energy-per-block`,
    `ticks-per-block`), dimensional stabilizer (`energy-per-chunk`) and modification station (`ticks` per
    module level, `energy-per-tick`, `confirm-seconds` for the second click before losing stored energy);
  - the radioactive waste barrel: `storages: { waste: { capacity: ... } }` and `options: { decay-per-second: ... }`;
  - energy cubes, chemical tanks and fluid tanks: `storages:` (their capacity) and `ports: { out: { rate: ... } }`
    (what they give out per tick, and how fast they charge an item), in the rows of `config_factory#energy_cubes`
    (`machines.yml`), `#fluid_tanks` and `#chemical_tanks` (`chemistry.yml`);
  - `options:` on the heat, bio, solar and advanced solar generators (`energy-per-tick`), the wind generator
    (`max-energy-per-tick`), the logistical sorter (`every-ticks`, `stack-size`), the fluidic plenisher (`energy`,
    `ticks`, `range`) and the formulaic assemblicator (`energy`, `ticks`);
  - `options: { items-per-second: ... }` on the QIO importer and exporter;
  - multiblocks: `options:` on the casing that anchors each structure (`multiblocks.yml`, `reactors.yml`): the
    dynamic tank, the induction casing (`cell-capacity` per tier), the thermal evaporation block, the boiler,
    fission and SPS casings, the fusion reactor frame and the turbine casing: their capacities per block and their
    physics (heat per mB, ignition energy, steam energy, vent flow...). The induction port has `provider-output`
    per tier.

  **Reading the block and item files.** Most entries are short: they name a CraftEngine template and add
  what is their own.
  - `template: mekanized:block/display_machine_facing` (or `machine`, `machine_facing_active`, …) gives the
    block's shape: its behavior type, drops, hardness and states. The keys written next to it (`behavior:
    { options: ... }`, `arguments: { model: ... }`) are the block's own and are merged in. To change one block,
    edit or add keys there: `settings: { hardness: 10.0 }` next to the template changes only that block.
  - `config_factory#<name>:` makes a whole family: each row of `instances:` is one block (with its item and
    display items), and `blueprint:` is written once for all of them. A row holds the block's numbers, e.g.
    `- transport: basic_universal_cable` / `rate: 8000`, or `- tier: basic` / `behavior: { storages: ... }`.
    Edit the row to change one block; edit the blueprint to change the whole family.
  - The templates are in `configuration/templates.yml` next to these files, and MachineEngine's in
    `plugins/CraftEngine/resources/machineengine/configuration/templates.yml`. Both are rewritten at every
    start: to change a template, copy it into your own pack under another name and point blocks at the copy.

  A QIO drive's size is in its item config: `- type: mekanized:qio_drive` with `items` and `kinds`
  (`automation.yml`). Any item given that behavior goes into a drive array; `/ce reload` applies it.

  Any machine block also takes `storages: { <id>: { capacity: ... } }` and `ports: { <id>: { rate: ... } }` for its
  own storages and ports, shipped or not (`/machineengine inspect` lists a machine's storages).

  After editing a block's options, run `/ce reload`: placed machines and cables read them again too. Old
  `config.yml` keys
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
  | `mekanized:module` | `module_*` items | `module`, `max-level`, `energy` (per second, per use or made) and `energy-per-level`, `effect-amplifier` (per level) and `effect-duration` (potion modules), `range` (per level) and `pull-speed` (magnet), `step-height` (servos), `removes-effects` (inhalation purification), `capacity-factor` (per level), `enchantment`, `enchantment-level` (per level), `glider`, `fits` (the items it goes into) |
  | `machineengine:upgrade` | `upgrade_*` items | `upgrade` (the upgrade type; the item's own id by default) |

## Textured menus

The menus look like Mekanism's windows. Each menu file draws its window in two parts:

- **The title draws everything that does not move:** the panel (player inventory included), the slot
  frames and the bar frames. They are CraftEngine images, placed with `<shift:n>` (n pixels right or left):
  ```yaml
  name: "<white><shift:-8><image:mekanized:gui/panel_5><shift:-152><image:mekanized:gui/slot_input_r2>…<shift:-162></white><lang:block.mekanized.crusher>"
  ```
  The title starts at x 8 of the window; `<shift:-8>` brings it to 0. An image moves the next one by its
  width + 1 (a slot frame 18 + 1, the panel 176 + 1). Chest slot (row, column), counted from 0, has its
  frame at x `7 + 18 × column`, so to move an image by one slot, change the shifts around it by 18.
- **Slots draw everything that moves:** fill bars, empty slots and icons are CraftEngine items in zMenu
  buttons.

**Images** (`plugins/CraftEngine/resources/mekanized/configuration/gui.yml`):

| Image | What |
|---|---|
| `mekanized:gui/panel_<rows>` | the window for a chest of 3 to 6 rows |
| `mekanized:gui/slot_<kind>_r<row>` | a slot frame on chest row `row`: `normal`, `input` (red), `input_2`, `output` (blue), `output_2`, `extra` (yellow), `power` (green) |
| `mekanized:gui/overlay_<icon>_r<row>` | an icon over a slot frame: `power`, `input`, `output`, `upgrade` |
| `mekanized:gui/frame_<bar>_r<row>` | a bar's frame starting on row `row`: `tall_gauge` and `power_vertical` (3 rows tall), `power_horizontal` and `progress` (3 columns wide), `lane` (one slot) |

**Items** (`craftengine:<id>` as a button's `material`):

| Item | What |
|---|---|
| `mekanized:gui/empty` | nothing: an empty machine slot shows its frame, the button's name is the tooltip |
| `mekanized:gui/ghost_module`, `gui/ghost_upgrade` | a faded gray module or upgrade: an empty row of the station or upgrades menu (`empty:` of a `machineengine_action` button) |
| `mekanized:gui/bar/<shape>/<cell>/<level>` | one slot of a fill bar: shapes `chemical`, `fluid`, `energy`, `energy_horizontal`, `heat`, `progress`, `lane` |
| `mekanized:gui/icon/<name>` | Mekanism's tab and button icons: `upgrade`, `configuration`, `redstone_ignored`, `redstone_high`, `redstone_low`, `items`, `energy`, `chemicals`, `fluids`, `heat`, `auto_eject`, `back`, `clear_sides`, `transporter_config` |
| `mekanized:gui/side/<mode>` | a side configuration square: `none`, `input`, `output`, `both` |

**A fill bar** covers 3 slots (bottom to top, or left to right), one button per slot, cell 0 first. Each
button names its item from placeholders, which MachineEngine fills in for the machine the player looks at:
```yaml
  water-0:
    slot: 27
    item:
      material: "craftengine:mekanized:gui/bar/fluid/0/%zmenu_machineengine_storage_water_cell_0%"
      components:
        dyed-color: "%zmenu_machineengine_storage_water_color%"
      flags: [HIDE_DYE, HIDE_ATTRIBUTES]
      name: "<!i><aqua>Water"
      lore:
        - "<!i><gray>%zmenu_machineengine_storage_water_amount% / %zmenu_machineengine_storage_water_capacity% mB"
```
- `storage_<id>_cell_<k>` is cell k's level (0 to 18; 16 for cell 2); `storage_<id>_slot_<n>_cell_<k>` for
  one slot of a storage; `process_cell_<k>` for the progress arrow; `data_<id>_cell` (0 to 16) for a
  one-slot bar following a percent setting, like a factory lane.
- `storage_<id>_color` is the color of what the storage holds: the menu color of its resource type (Mekanism's,
  from `chemicals.txt` and `fluids.txt`, or the `colors` setting in Mekanized's config.yml to change them; water
  and lava from MachineEngine's config.yml). Only the `chemical`, `fluid` and `heat` shapes are tinted, and their
  labels take the same color.
- MachineEngine redraws every button whose `material` holds a machine placeholder by itself, all at once,
  at the menu's `update-interval` and whenever the machine changes. Do not add `update: true` to them.

**Icons that follow a setting** name their item from it the same way:
`craftengine:mekanized:gui/icon/redstone_%zmenu_machineengine_data_redstone%`,
`craftengine:mekanized:gui/side/%zmenu_machineengine_side_items_front_mode%`.

The textures are Mekanism's, kept in the plugin. The panels, bar frames and bar models are made by
`mekanized/tools/compose_menu_textures.py` in the source tree (run by hand; not needed to edit menus).

## Commands (permission `machineengine.admin`)
| Command | What it does |
|---|---|
| `/mekanized build <structure> [x y z]` | Builds the smallest working multiblock (dynamic_tank, induction_matrix, thermal_evaporation, boiler, turbine, fission_reactor, fusion_reactor, sps, teleporter) towards +x, +y, +z |
| `/mekanized radiation add <Sv/h>` (or `radiation <Sv/h>`) / `clear` | Adds a radiation source where you stand / removes every source |
| `/mekanized radiation sources` | Lists the radiation sources, strongest first |
| `/mekanized radiation level` | The radiation where you stand (everyone: `mekanized.radiation.dose`) |
| `/mekanized radiation dose` | Your own dose (everyone: `mekanized.radiation.dose`) |
| `/mekanized radiation dose <player> [set <Sv> \| add <Sv> \| clear]` | Shows or changes a player's dose (admins) |
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
- **Menu titles show boxes or `<image:…>` text:** the player has not loaded the resource pack, or
  CraftEngine's `network.intercept-packets.container` is off (it must be on).
- **A menu still looks plain or old after an update:** you edited that file, so the update kept it (the
  log says so). Delete it to get the new menu, then make your changes again.
- **CraftEngine reports no free block states:** run `/ce clean-cache visual_block_states`, then
  `/ce reload all`. Old entries hold states that new blocks need.
- **A multiblock does not form:**
  - every block must be loaded;
  - edges must be casings;
  - nothing but the allowed blocks may be inside;
  - on Folia, the whole structure must be in one region.
