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
| `plugins/Mekanized/config.yml` | Transport rates and multiblock numbers |

**Updates:** updating Mekanized replaces these files only if you have not edited them. An edited file is
kept, and the log says so; to get the new default, delete it.

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

## Configuring
- **`plugins/Mekanized/config.yml`:** network rates per tier, Dynamic Tank capacity, Thermal Evaporation
  rate. Restart to apply.
- **Recipes** (durations, energy per tick, inputs and outputs): edit
  `plugins/MachineEngine/definitions/mekanized/recipes_*.yml`. They reload with the server's recipes.
  The Energized Smelter uses every furnace recipe of the server, datapacks included.
- **Crafting recipes:** in CraftEngine's `recipes_*.yml` under `plugins/CraftEngine/resources/mekanized/`.
- **Menus:** the zMenu files `plugins/MachineEngine/menus/mekanized_*.yml`. Placeholders are
  `%zmenu_machineengine_<key>%`; see MachineEngine's README.

## Commands (permission `machineengine.admin`)
| Command | What it does |
|---|---|
| `/mekanized test` | Places an Enrichment Chamber, feeds it, and logs the result (2 osmium dust) |
| `/mekanized test-cable` | A creative energy cube powering a chamber through a cable |
| `/mekanized test-chemistry` | An Electrolytic Separator feeding a chemical tank through a tube |
| `/machineengine ...` | MachineEngine's own: machine info, debug view, placeholders |

## Differences from Mekanism
- **Transporters:** items move between batches instantly, with no items drawn on them. There are no
  restrictive or diversion transporters.
- **Thermal Evaporation Plant:** its heat is simpler (height, daylight, rain).
- **Not made yet:** the boiler, fission and fusion reactors, Digital Miner, Teleporter and QIO.
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
