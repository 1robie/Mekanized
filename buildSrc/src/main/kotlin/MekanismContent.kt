import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Mekanized items and blocks as CraftEngine content (MEKANIZED_PLAN.md, Phase 1): materials, ores and
 * storage blocks, with Mekanism's models, translated names (CraftEngine client-side `lang`, every
 * language Mekanism ships) and `c:` item tags (transitively, so `c:ingots` contains what
 * `c:ingots/osmium` contains).
 *
 * @param itemTags tag -> members ("#tag" includes), already renamed to `mekanized:`
 */
internal class MekanismContent(
    private val source: File,
    private val out: File,
    private val itemTags: Map<String, List<String>>,
    private val report: Report,
) {
    /** Phase 1 materials, by Mekanism item name. */
    private val materialPattern = Regex(
        "^(ingot|nugget|dust|dirty_dust|clump|shard|crystal|raw|alloy|enriched)_.+$|^.+_control_circuit$|" +
                "^(hdpe_pellet|hdpe_rod|hdpe_sheet|hdpe_stick|substrate|salt|fluorite_gem|bio_fuel|sawdust|dye_base)$"
    )
    private val blockPattern = Regex("^(deepslate_)?(osmium|tin|lead|uranium|fluorite)_ore$|^block_.+$|^steel_casing$")

    /** Ids (`mekanized:x`) of the content generated here, for the recipe split. */
    val ids = sortedSetOf<String>()

    fun generate() {
        val generated = source.resolve("src/datagen/generated/mekanism/assets/mekanism")
        val itemDefinitions = generated.resolve("items").listFiles { file -> file.extension == "json" }?.sortedBy { it.name } ?: listOf()
        val items = linkedMapOf<String, Any?>()
        val blocks = linkedMapOf<String, Any?>()
        val membership = membership()
        val nameKeys = mutableSetOf<String>()
        for (file in itemDefinitions) {
            val name = file.nameWithoutExtension
            val model = itemModel(file) ?: continue
            val id = "mekanized:$name"
            val isBlock = blockPattern.matches(name)
            if (!isBlock && !materialPattern.matches(name)) continue
            if (isBlock) {
                val block = block(name) ?: continue
                blocks[id] = block
                items[id] = linkedMapOf<String, Any?>(
                    "data" to mapOf("item_name" to "<!i><lang:block.mekanized.$name>"),
                    "model" to Ids.rename(model),
                    "behavior" to mapOf("type" to "block_item", "block" to id),
                ).withTags(membership[id])
                nameKeys += "block.mekanism.$name"
                report.count("blocks")
            } else {
                items[id] = linkedMapOf<String, Any?>(
                    "data" to mapOf("item_name" to "<!i><lang:item.mekanized.$name>"),
                    "model" to Ids.rename(model),
                ).withTags(membership[id])
                nameKeys += "item.mekanism.$name"
                report.count("items")
            }
            ids += id
        }
        writeYaml(out.resolve("pack/configuration/materials.yml"),
            "Mekanized materials, ores and storage blocks (Phase 1), generated from Mekanism (MIT, see LICENSE-MEKANISM.txt).",
            linkedMapOf("items" to items, "blocks" to blocks))
        generateBlocks("machines.yml", "Mekanized machines, generators, energy cubes, cables and upgrades (Phase 2), generated from Mekanism (MIT).",
            phase2Machines, phase2Transports, phase2Items, machineIds, nameKeys, membership)
        generateBlocks("chemistry.yml", "Mekanized chemical machines, tanks, tubes, pipes and the pump (Phase 3), generated from Mekanism (MIT).",
            phase3Machines, phase3Transports, phase3Items, chemistryIds, nameKeys, membership)
        generateBlocks("logistics.yml", "Mekanized transporters, the logistical sorter, factories, tier installers and the configurator (Phase 4), generated from Mekanism (MIT).",
            phase4Machines, phase4Transports, phase4Items, logisticsIds, nameKeys, membership)
        generateBlocks("multiblocks.yml", "Mekanized multiblocks: Dynamic Tank, Induction Matrix, Thermal Evaporation Plant (Phase 5a), generated from Mekanism (MIT).",
            phase5Machines, mapOf(), listOf(), multiblockIds, nameKeys, membership, phase5Plain)
        generateBlocks("endgame.yml", "Mekanized late-game machines and generators (Phase 6), generated from Mekanism (MIT).",
            phase6Machines, mapOf(), listOf(), endgameIds, nameKeys, membership)
        generateBlocks("utility.yml", "Mekanized bins, personal storage, chargepad, plenisher, assemblicator, lasers (Phase 6), generated from Mekanism (MIT).",
            phase6Utility, mapOf(), listOf(), endgameIds, nameKeys, membership)
        val chemicals = chemicals()
        nameKeys += chemicals.map { "chemical.mekanism.$it" }
        // Fluids are named by their liquid blocks (block.mekanism.brine).
        nameKeys += source.resolve("src/datagen/generated").listFiles().orEmpty()
            .flatMap { it.resolve("data/c/tags/fluid").listFiles().orEmpty().toList() }
            .map { it.nameWithoutExtension }.filter { it != "gaseous" }.map { "block.mekanism.$it" }
        out.resolve("chemicals.txt").writeText(chemicals.joinToString("\n", postfix = "\n"))
        writeYaml(out.resolve("pack/configuration/lang.yml"),
            "Names of Mekanized items, blocks and chemicals in every language Mekanism ships (Mekanism, MIT).\n" +
                    "Shown in each player's language.",
            linkedMapOf("lang" to languages(nameKeys)))
    }

    // -- Phase 2: machines, generators, energy cubes, cables, upgrades ----------------------------

    /** Machine blocks of Phase 2: block name -> the MachineEngine machine (or transport) behind it. */
    private val phase2Machines = listOf(
        "enrichment_chamber", "crusher", "energized_smelter", "osmium_compressor", "combiner", "metallurgic_infuser",
        "purification_chamber", "precision_sawmill", "heat_generator", "solar_generator", "bio_generator",
        "basic_energy_cube", "advanced_energy_cube", "elite_energy_cube", "ultimate_energy_cube", "creative_energy_cube",
    )
    private val tiers = listOf("basic", "advanced", "elite", "ultimate")

    /** Transports -> the low edge of their square section (small 5..11, large 4..12). */
    private val phase2Transports = tiers.associate { "${it}_universal_cable" to 5 }
    private val phase2Items = listOf("upgrade_speed", "upgrade_energy")

    // -- Phase 3: chemical machines, tanks, tubes, pipes, pump ------------------------------------
    private val phase3Machines = listOf(
        "chemical_oxidizer", "chemical_infuser", "chemical_injection_chamber", "chemical_dissolution_chamber",
        "chemical_washer", "chemical_crystallizer", "electrolytic_separator", "rotary_condensentrator", "electric_pump",
    ) + (tiers + "creative").flatMap { listOf("${it}_chemical_tank", "${it}_fluid_tank") }
    /** Crafting ingredients of Phase 3 machines (the tablet charges items in Phase 4). */
    private val phase3Items = listOf("electrolytic_core", "energy_tablet")
    private val phase3Transports = tiers.associate { "${it}_pressurized_tube" to 5 } + tiers.associate { "${it}_mechanical_pipe" to 4 }

    /** Ids of Phase 2 content, for the recipe split. */
    val machineIds = sortedSetOf<String>()

    /** Ids of Phase 3 content, for the recipe split. */
    val chemistryIds = sortedSetOf<String>()

    // -- Phase 4: logistics, factories, tier installers, tools ------------------------------------
    /** Mekanism's factory kinds: each runs several processes of one machine at once. */
    val factoryKinds = listOf("smelting", "enriching", "crushing", "compressing", "combining", "purifying", "injecting", "infusing", "sawing")
    private val phase4Machines = listOf("logistical_sorter") + tiers.flatMap { tier -> factoryKinds.map { "${tier}_${it}_factory" } }
    private val phase4Transports = tiers.associate { "${it}_logistical_transporter" to 4 }
    private val phase4Items = tiers.map { "${it}_tier_installer" } + "configurator"

    /** Ids of Phase 4 content, for the recipe split. */
    val logisticsIds = sortedSetOf<String>()

    // -- Phase 5a: multiblocks ---------------------------------------------------------------------
    /** Frame blocks: machines (they find their structure and pass through to its storage). */
    private val phase5Machines = listOf("dynamic_tank", "dynamic_valve", "structural_glass", "induction_casing", "induction_port",
        "thermal_evaporation_block", "thermal_evaporation_valve", "thermal_evaporation_controller")
    /** Interior blocks of the induction matrix: plain blocks the structure counts. */
    private val phase5Plain = tiers.flatMap { listOf("${it}_induction_cell", "${it}_induction_provider") }

    /** Ids of Phase 5a content, for the recipe split. */
    val multiblockIds = sortedSetOf<String>()

    // -- Phase 6: the remaining single-block machines and generators ---------------------------
    private val phase6Machines = listOf("pressurized_reaction_chamber", "isotopic_centrifuge", "solar_neutron_activator",
        "antiprotonic_nucleosynthesizer", "painting_machine", "pigment_extractor", "pigment_mixer",
        "gas_burning_generator", "wind_generator", "advanced_solar_generator")

    /** Ids of Phase 6 content, for the recipe split. */
    val endgameIds = sortedSetOf<String>()

    // -- Phase 6 wave 2: storage and utility blocks ------------------------------------------------
    private val phase6Utility = (tiers + "creative").map { "${it}_bin" } + listOf("personal_chest", "personal_barrel", "chargepad",
        "fluidic_plenisher", "formulaic_assemblicator", "laser", "laser_amplifier", "laser_tractor_beam", "oredictionificator",
        "industrial_alarm")

    private fun generateBlocks(fileName: String, header: String, machineNames: List<String>, transports: Map<String, Int>,
                               extraItems: List<String>, phaseIds: MutableSet<String>, nameKeys: MutableSet<String>,
                               membership: Map<String, Set<String>>, plainBlocks: List<String> = listOf()) {
        val items = linkedMapOf<String, Any?>()
        val blocks = linkedMapOf<String, Any?>()
        val modelsRoot = out.resolve("pack/resourcepack/assets/mekanized/models")
        for (name in machineNames + transports.keys) {
            val id = "mekanized:$name"
            val cable = name in transports
            var itemModel: String? = null
            val behavior: Map<String, Any?>
            val states: Map<String, Any?>
            if (cable) {
                // OBJ models upstream: drawn entirely by display entities (center and arms); the block is
                // an empty sapling-type state: small hitbox, no collision, one state per cable.
                // Transporters: Mekanism draws a glass layer over a see-through frame; the opaque textures
                // (glass filled in) give the same look with one model.
                val texture = if (name.endsWith("_logistical_transporter")) "opaque/$name" else name
                val models = CableModels.write(modelsRoot, name, texture, transports.getValue(name))
                for ((part, model) in models) {
                    items["mekanized:cable_part/$name/$part"] = linkedMapOf<String, Any?>("model" to model)
                }
                // Every transport shares one state (CraftEngine has only 8 sapling states): the same
                // empty model, with the universal cable's breaking particles.
                states = mapOf("state" to mapOf("auto_state" to mapOf("type" to "sapling", "id" to "mekanized_universal_cable"),
                    "model" to mapOf("path" to emptyModel(modelsRoot, "universal_cable", "mekanized:block/cable/basic_universal_cable/center"))))
                behavior = mapOf("type" to "machineengine:transport", "transport" to id, "connection_items" to "mekanized:cable_part/$name")
                itemModel = models.getValue("center")
            } else {
                states = machineStates(name) ?: run {
                    report.skip("machine block without a usable blockstate", name)
                    continue
                }
                behavior = mapOf("type" to "machineengine:machine", "machine" to id, "keep_contents" to true)
            }
            // Not a full cube: drawn by a display entity (turned to the facing, switching to the active
            // model), over one empty non-occluding state, however many facings it has. Non-occluding
            // states are scarce (about 117 slots), and the blocks around stay drawn (no seeing through walls).
            // Glass is a full cube that must not hide what is behind it: a non-occluding state.
            val glass = name == "structural_glass"
            val full = !cable && !glass && allModels(states).all { CableModels.isFullCube(modelsRoot, it) }
            val finalStates: Map<String, Any?>
            var finalBehavior = behavior
            if (glass) {
                finalStates = withAutoState(states, "tintable_leaves")
            } else if (full || cable) {
                finalStates = states
            } else {
                val (inactive, active) = displayModels(states)
                items["mekanized:display/$name"] = linkedMapOf<String, Any?>("model" to inactive)
                if (active != null) items["mekanized:display/$name/active"] = linkedMapOf<String, Any?>("model" to active)
                // Factories: the four tiers of a kind share one state (and the basic tier's particles).
                val factoryKind = factoryKinds.firstOrNull { name.endsWith("_${it}_factory") }
                finalStates = if (factoryKind == null) singleAppearance(states, emptyModel(modelsRoot, name, inactive))
                else singleAppearance(states, emptyModel(modelsRoot, "${factoryKind}_factory", "mekanized:block/factory/$factoryKind/basic"),
                    "mekanized_${factoryKind}_factory")
                finalBehavior = behavior + ("display_item" to "mekanized:display/$name")
                // Some blockstates turn the model even when facing north (separator 90, pump 180).
                northRotation(states).let { if (it != 0) finalBehavior = finalBehavior + ("display_rotation" to it) }
                // Every facing's turn, x included (lasers point up in their model): "north=90/180,south=...".
                facingRotations(states)?.let { finalBehavior = finalBehavior + ("display_rotations" to it) }
                // Energy cubes: Mekanism's glowing core, drawn in code, as a second display growing with the charge.
                energyCoreTier(name)?.let { tier ->
                    energyCore(modelsRoot, tier, items)
                    finalBehavior = finalBehavior + mapOf("level_display" to "mekanized:display/energy_core/$tier",
                        "level_storage" to "energy", "level_steps" to CORE_LEVELS)
                    // Like Mekanism: the frame always, a side's port only while that side is configured.
                    if (energyCubeParts(modelsRoot, tier, name, items)) {
                        finalBehavior = finalBehavior + ("side_display" to "mekanized:display/$name/port")
                    }
                }
                report.count("display-rendered blocks")
            }
            blocks[id] = linkedMapOf<String, Any?>(
                "behavior" to finalBehavior,
                "loot" to mapOf("template" to "default:loot_table/self"),
                "settings" to mapOf("template" to if (full) "default:settings/solid_1x1x1" else "default:settings/transparent_1x1x1",
                    "overrides" to mapOf("hardness" to 3.5, "resistance" to 8.0, "push_reaction" to "block",
                        "tags" to listOf("minecraft:mineable/pickaxe"), "correct_tools" to listOf("#minecraft:pickaxes"), "required_break_power" to 1)),
            ).apply { putAll(finalStates) }
            items[id] = linkedMapOf<String, Any?>(
                "data" to mapOf("item_name" to "<!i><lang:block.mekanized.$name>"),
                "model" to (itemModel ?: blockItemModel(name) ?: firstModel(states)),
                "behavior" to mapOf("type" to "block_item", "block" to id),
            ).withTags(membership[id])
            nameKeys += "block.mekanism.$name"
            phaseIds += id
            report.count("machine blocks")
        }
        for (name in plainBlocks) {
            val id = "mekanized:$name"
            val block = block(name) ?: continue
            blocks[id] = block
            items[id] = linkedMapOf<String, Any?>(
                "data" to mapOf("item_name" to "<!i><lang:block.mekanized.$name>"),
                "model" to (blockItemModel(name) ?: (block["state"] as Map<*, *>).let { (it["model"] as Map<*, *>)["path"] }),
                "behavior" to mapOf("type" to "block_item", "block" to id),
            ).withTags(membership[id])
            nameKeys += "block.mekanism.$name"
            phaseIds += id
            report.count("blocks")
        }
        for (name in extraItems) {
            val model = itemDefinition(name)?.let { itemModel(it) } ?: continue
            items["mekanized:$name"] = linkedMapOf<String, Any?>(
                "data" to mapOf("item_name" to "<!i><lang:item.mekanized.$name>"),
                "model" to Ids.rename(model),
            ).withTags(membership["mekanized:$name"])
            nameKeys += "item.mekanism.$name"
            phaseIds += "mekanized:$name"
        }
        writeYaml(out.resolve("pack/configuration/$fileName"), header, linkedMapOf("items" to items, "blocks" to blocks))
    }

    private fun itemDefinition(name: String): File? = listOf(
        "src/datagen/generated/mekanism/assets/mekanism/items/$name.json",
        "src/datagen/generated/mekanismgenerators/assets/mekanismgenerators/items/$name.json",
    ).map { source.resolve(it) }.firstOrNull { it.isFile }

    /** The item model: a plain model, or the base of a special one (energy cubes). */
    private fun blockItemModel(name: String): String? {
        val file = itemDefinition(name) ?: return null
        val model = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("model") ?: return null
        val ref = when (model.get("type")?.asString) {
            "minecraft:model" -> model.get("model")?.asString
            "minecraft:special" -> model.get("base")?.asString
            else -> null
        }
        val renamed = ref?.let { Ids.rename(it) } ?: return null
        // Mekanism draws some items in code (energy cubes): an empty model is no use, show the block.
        val file2 = out.resolve("pack/resourcepack/assets/mekanized/models/" + renamed.substringAfter(":") + ".json")
        if (file2.isFile) {
            val model = JsonParser.parseString(file2.readText()).asJsonObject
            if (!model.has("parent") && !model.has("elements")) return null
        }
        return renamed
    }

    /**
     * CraftEngine states from a Mekanism blockstate: `facing` (4 or 6 directions) and `active`
     * properties, one appearance per variant with its rotation. A model that needs a NeoForge loader
     * is replaced by the block's item model (a plain cube for energy cubes).
     */
    private fun machineStates(name: String): Map<String, Any?>? {
        val file = listOf("src/main/resources/assets/mekanism", "src/generators/resources/assets/mekanismgenerators",
            "src/datagen/generated/mekanism/assets/mekanism", "src/datagen/generated/mekanismgenerators/assets/mekanismgenerators")
            .map { source.resolve("$it/blockstates/$name.json") }.firstOrNull { it.isFile } ?: return null
        val variants = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("variants") ?: return null
        val values = linkedMapOf<String, MutableSet<String>>()
        val appearances = linkedMapOf<String, Any?>()
        val mapping = linkedMapOf<String, Any?>()
        for ((key, value) in variants.entrySet()) {
            val raw = (if (value.isJsonArray) value.asJsonArray[0] else value).asJsonObject
            // Energy cubes: a Mekanism variant type keeping the vanilla part under "tier_model".
            val variant = raw.getAsJsonObject("tier_model") ?: raw
            val modelRef = variant.get("model")?.asString ?: return null
            val model = if (needsLoader(modelRef)) (blockItemModel(name) ?: return null) else Ids.rename(modelRef)
            val appearance = linkedMapOf<String, Any?>("path" to model)
            variant.get("x")?.asInt?.let { if (it != 0) appearance["x"] = Math.floorMod(it, 360) }
            variant.get("y")?.asInt?.let { if (it != 0) appearance["y"] = Math.floorMod(it, 360) }
            val appearanceName = if (key.isEmpty()) "default" else key.replace("=", "_").replace(",", "_")
            appearances[appearanceName] = mapOf("auto_state" to "solid", "model" to appearance)
            if (key.isNotEmpty()) {
                mapping[key] = mapOf("appearance" to appearanceName)
                key.split(",").forEach { pair -> values.getOrPut(pair.substringBefore("=")) { linkedSetOf() } += pair.substringAfter("=") }
            }
        }
        if (values.isEmpty()) return mapOf("state" to appearances["default"])
        val properties = linkedMapOf<String, Any?>()
        for ((property, options) in values) {
            properties[property] = when (property) {
                "facing" -> mapOf("type" to if (options.size == 4) "4-direction" else "6-direction", "default" to "north")
                "active", "open" -> mapOf("type" to "boolean", "default" to false)
                else -> return null
            }
        }
        return mapOf("states" to linkedMapOf("properties" to properties, "appearances" to appearances, "variants" to mapping))
    }

    private fun needsLoader(modelRef: String): Boolean {
        val path = modelRef.substringAfter(":")
        val module = if (modelRef.startsWith("mekanismgenerators:")) "mekanismgenerators" else "mekanism"
        return listOf("src/main/resources/assets/$module/models", "src/generators/resources/assets/$module/models",
            "src/datagen/generated/$module/assets/$module/models")
            .map { source.resolve("$it/$path.json") }.firstOrNull { it.isFile }?.readText()
            // energy_cube and composite models are flattened into vanilla ones by the import.
            ?.let { "\"loader\"" in flattenLoaderModel(Ids.renameInText(it), mekanismModels(source)) } ?: true
    }

    /** The models of the "north, inactive" and "north, active" appearances (active is null if none). */
    @Suppress("UNCHECKED_CAST")
    private fun displayModels(states: Map<String, Any?>): Pair<String, String?> {
        val appearances = (states["states"] as Map<String, Any?>?)?.get("appearances") as Map<String, Map<String, Any?>>?
            ?: return allModels(states).first() to null
        fun model(name: String) = appearances[name]?.let { (it["model"] as Map<String, Any?>)["path"] as String }
        val inactive = model("facing_north_active_false") ?: model("facing_north") ?: allModels(states).first()
        return inactive to model("facing_north_active_true")
    }

    // -- energy cube core -------------------------------------------------------------------------

    private val CORE_LEVELS = 4
    /** Mekanism's tier colors (BaseTier), which tint the core. */
    private val TIER_COLORS = mapOf("basic" to 0x55FF55, "advanced" to 0xAA0000, "elite" to 0x0000AA, "ultimate" to 0xAA00AA, "creative" to 0x202020)

    /**
     * The energy cube split as Mekanism's loader draws it: `<tier>/frame` (the display item of the block)
     * and `<tier>/port_<side>` (connector, port and LEDs of a side, model sides as seen facing north).
     */
    private fun energyCubeParts(modelsRoot: File, tier: String, name: String, items: MutableMap<String, Any?>): Boolean {
        val root = source.resolve("src/main/resources/assets/mekanism/models/block/energy_cube")
        val base = root.resolve("base.json").takeIf { it.isFile } ?: return false
        val json = JsonParser.parseString(Ids.renameInText(base.readText())).asJsonObject
        val textures = json.getAsJsonObject("textures")?.deepCopy() ?: JsonObject()
        root.resolve("$tier.json").takeIf { it.isFile }?.let { file ->
            JsonParser.parseString(Ids.renameInText(file.readText())).asJsonObject.getAsJsonObject("textures")
                ?.entrySet()?.forEach { (key, value) -> textures.add(key, value) }
        }
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        fun write(part: String, elements: com.google.gson.JsonArray): String {
            val model = JsonObject()
            model.addProperty("parent", "minecraft:block/block")
            model.add("textures", textures)
            model.add("elements", elements)
            val file = modelsRoot.resolve("block/energy_cube/$tier/$part.json")
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(model))
            return "mekanized:block/energy_cube/$tier/$part"
        }
        items["mekanized:display/$name"] = linkedMapOf<String, Any?>("model" to write("frame", json.getAsJsonArray("frame") ?: return false))
        val ports = json.getAsJsonObject("ports") ?: return false
        val leds = json.getAsJsonObject("leds")
        for (side in listOf("front", "back", "left", "right", "top", "bottom")) {
            // Mekanism's left and right are as seen facing the front; MachineEngine's from behind.
            val mekanism = when (side) { "left" -> "right"; "right" -> "left"; else -> side }
            val elements = com.google.gson.JsonArray()
            ports.getAsJsonArray(mekanism)?.forEach { elements.add(it) }
            leds?.getAsJsonArray(mekanism)?.forEach { elements.add(it) }
            items["mekanized:display/$name/port/$side"] = linkedMapOf<String, Any?>("model" to write("port_$side", elements))
        }
        return true
    }

    private fun energyCoreTier(name: String): String? =
        TIER_COLORS.keys.firstOrNull { name == "${it}_energy_cube" }

    /**
     * The core of a tier: Mekanism's grey `render/energy_core.png` tinted with the tier's color, as a
     * cube turned 45 degrees, in [CORE_LEVELS] sizes (items `display/energy_core/<tier>/<level>`).
     */
    private fun energyCore(modelsRoot: File, tier: String, items: MutableMap<String, Any?>) {
        val texture = out.resolve("pack/resourcepack/assets/mekanized/textures/block/energy_core/$tier.png")
        if (!texture.isFile) {
            val grey = javax.imageio.ImageIO.read(source.resolve("src/main/resources/assets/mekanism/render/energy_core.png")) ?: return
            val color = TIER_COLORS.getValue(tier)
            val tinted = java.awt.image.BufferedImage(grey.width, grey.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until grey.width) for (y in 0 until grey.height) {
                val argb = grey.getRGB(x, y)
                val level = (argb shr 16) and 0xFF
                fun channel(shift: Int) = (((color shr shift) and 0xFF) * level / 255) shl shift
                tinted.setRGB(x, y, (argb and 0xFF000000.toInt()) or channel(16) or channel(8) or channel(0))
            }
            texture.parentFile.mkdirs()
            javax.imageio.ImageIO.write(tinted, "png", texture)
        }
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        for (level in 1..CORE_LEVELS) {
            val half = 1.0 + level * 1.0 // 4 to 10 pixels wide
            val face = mapOf("uv" to listOf(0, 0, 16, 16), "texture" to "#core")
            val model = linkedMapOf<String, Any>(
                "parent" to "minecraft:block/block",
                "textures" to mapOf("core" to "mekanized:block/energy_core/$tier", "particle" to "mekanized:block/energy_core/$tier"),
                "elements" to listOf(linkedMapOf(
                    "from" to listOf(8 - half, 8 - half, 8 - half), "to" to listOf(8 + half, 8 + half, 8 + half),
                    "rotation" to mapOf("angle" to 45, "axis" to "y", "origin" to listOf(8, 8, 8)),
                    "shade" to false,
                    "faces" to listOf("north", "south", "east", "west", "up", "down").associateWith { face })))
            val file = modelsRoot.resolve("block/energy_core/$tier/$level.json")
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(model))
            items["mekanized:display/energy_core/$tier/$level"] = linkedMapOf<String, Any?>("model" to "mekanized:block/energy_core/$tier/$level")
        }
    }

    /** Each facing's blockstate rotation as "facing=x/y,..." (inactive appearances); null without a facing property. */
    @Suppress("UNCHECKED_CAST")
    private fun facingRotations(states: Map<String, Any?>): String? {
        val appearances = (states["states"] as Map<String, Any?>?)?.get("appearances") as Map<String, Map<String, Any?>>? ?: return null
        val rotations = mutableListOf<String>()
        for (facing in listOf("north", "south", "east", "west", "up", "down")) {
            val appearance = appearances["facing_${facing}_active_false"] ?: appearances["facing_${facing}_open_false"]
                ?: appearances["facing_$facing"] ?: continue
            val model = appearance["model"] as Map<String, Any?>
            rotations += "$facing=${model["x"] ?: 0}/${model["y"] ?: 0}"
        }
        return if (rotations.isEmpty()) null else rotations.joinToString(",")
    }

    /** The y rotation of the "north, inactive" appearance, in degrees (0 to 270). */
    @Suppress("UNCHECKED_CAST")
    private fun northRotation(states: Map<String, Any?>): Int {
        val appearances = (states["states"] as Map<String, Any?>?)?.get("appearances") as Map<String, Map<String, Any?>>? ?: return 0
        val north = appearances["facing_north_active_false"] ?: appearances["facing_north"] ?: return 0
        return (north["model"] as Map<String, Any?>)["y"] as Int? ?: 0
    }

    /** The same properties, every variant shown by one empty, non-occluding appearance. */
    @Suppress("UNCHECKED_CAST")
    private fun singleAppearance(states: Map<String, Any?>, emptyModel: String, sharedId: String? = null): Map<String, Any?> {
        // The model is empty, so leaf tint does not matter: the larger tintable group keeps the scarce
        // non-tintable leaves for content that needs them. Blocks with the same sharedId share one state.
        val autoState: Any = if (sharedId == null) "tintable_leaves" else mapOf("type" to "tintable_leaves", "id" to sharedId)
        val appearance = mapOf("auto_state" to autoState, "model" to mapOf("path" to emptyModel))
        val nested = states["states"] as Map<String, Any?>? ?: return mapOf("state" to appearance)
        val variants = (nested["variants"] as Map<String, Any?>).mapValues { mapOf("appearance" to "default") }
        return mapOf("states" to linkedMapOf("properties" to nested["properties"], "appearances" to mapOf("default" to appearance),
            "variants" to variants))
    }

    /** A model with no faces (the display entity draws the block) but the real model's breaking particles. */
    private fun emptyModel(modelsRoot: File, name: String, realModel: String): String {
        val file = modelsRoot.resolve("block/display_empty/$name.json")
        file.parentFile.mkdirs()
        file.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "textures" to mapOf("particle" to (particle(modelsRoot, realModel) ?: "minecraft:block/iron_block")),
            "elements" to listOf<Any>())))
        return "mekanized:block/display_empty/$name"
    }

    private fun particle(modelsRoot: File, model: String, depth: Int = 0): String? {
        if (depth > 8 || !model.startsWith("mekanized:")) return null
        val file = modelsRoot.resolve(model.substringAfter(":") + ".json")
        if (!file.isFile) return null
        val json = JsonParser.parseString(file.readText()).asJsonObject
        val textures = json.getAsJsonObject("textures")
        val direct = textures?.get("particle")?.asString
        if (direct != null && !direct.startsWith("#")) return direct
        textures?.entrySet()?.firstOrNull { !it.value.asString.startsWith("#") }?.let { return it.value.asString }
        return json.get("parent")?.asString?.let { particle(modelsRoot, it, depth + 1) }
    }

    /** Every model path in a `state` / `states` entry. */
    @Suppress("UNCHECKED_CAST")
    private fun allModels(states: Map<String, Any?>): List<String> {
        val appearances = (states["states"] as Map<String, Any?>?)?.let { (it["appearances"] as Map<String, Map<String, Any?>>).values.toList() }
            ?: listOf(states["state"] as Map<String, Any?>)
        return appearances.map { (it["model"] as Map<String, Any?>)["path"] as String }
    }

    /** The same states with every appearance's `auto_state` replaced. */
    @Suppress("UNCHECKED_CAST")
    private fun withAutoState(states: Map<String, Any?>, autoState: String): Map<String, Any?> {
        fun replace(appearance: Map<String, Any?>) = appearance.toMutableMap().apply { this["auto_state"] = autoState }
        states["state"]?.let { return mapOf("state" to replace(it as Map<String, Any?>)) }
        val nested = (states["states"] as Map<String, Any?>).toMutableMap()
        nested["appearances"] = (nested["appearances"] as Map<String, Map<String, Any?>>).mapValues { replace(it.value) }
        return mapOf("states" to nested)
    }

    /** The model of the first appearance, from the `state` or `states` entry [machineStates] made. */
    @Suppress("UNCHECKED_CAST")
    private fun firstModel(states: Map<String, Any?>): String {
        val appearance = (states["states"] as Map<String, Any?>?)?.let { (it["appearances"] as Map<String, Map<String, Any?>>).values.first() }
            ?: states["state"] as Map<String, Any?>
        return (appearance["model"] as Map<String, Any?>)["path"] as String
    }

    private fun MutableMap<String, Any?>.withTags(tags: Set<String>?): MutableMap<String, Any?> {
        if (!tags.isNullOrEmpty()) this["settings"] = mapOf("tags" to tags.sorted())
        return this
    }

    /** The model of a plain item definition (`minecraft:model`), else null (special items come later). */
    private fun itemModel(file: File): String? {
        var model = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("model") ?: return null
        // A model chosen by a mod component (the configurator's modes): its fallback.
        if (model.get("type")?.asString == "minecraft:select") model = model.getAsJsonObject("fallback") ?: return null
        if (model.get("type")?.asString != "minecraft:model") return null
        return model.get("model")?.asString
    }

    /** A block with one model (ores, storage blocks), its drops and its settings. */
    private fun block(name: String): Map<String, Any?>? {
        val state = blockState(name) ?: run {
            report.skip("block without a single-model blockstate (Phase 1 handles simple blocks only)", name)
            return null
        }
        val ore = name.endsWith("_ore")
        val loot = lootTable(name)
        val settings: Map<String, Any?> = when {
            ore -> mapOf("template" to if (name.startsWith("deepslate_")) "default:settings/deepslate_ore" else "default:settings/ore",
                "arguments" to mapOf("break_power" to 1))
            else -> mapOf("template" to "default:settings/solid_1x1x1", "overrides" to mapOf(
                "hardness" to 5.0, "resistance" to 6.0, "tags" to listOf("minecraft:mineable/pickaxe"),
                "correct_tools" to listOf("#minecraft:pickaxes"), "required_break_power" to 1))
        }
        return linkedMapOf("loot" to loot, "settings" to settings,
            "state" to mapOf("auto_state" to "solid", "model" to mapOf("path" to Ids.rename(state))))
    }

    private fun blockState(name: String): String? {
        for (root in listOf("src/main/resources/assets/mekanism", "src/datagen/generated/mekanism/assets/mekanism")) {
            val file = source.resolve("$root/blockstates/$name.json")
            if (!file.isFile) continue
            val variants = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("variants") ?: return null
            if (variants.size() != 1) return null
            val only = variants.entrySet().first().value
            return (if (only.isJsonArray) only.asJsonArray[0].asJsonObject else only.asJsonObject).get("model")?.asString
        }
        return null
    }

    /** Ores: silk touch drops the block, else the raw item with Fortune; other blocks drop themselves. */
    private fun lootTable(name: String): Map<String, Any?> {
        val self = mapOf("template" to "default:loot_table/self")
        val file = source.resolve("src/datagen/generated/mekanism/data/mekanism/loot_table/blocks/$name.json")
        if (!file.isFile) return self
        val text = file.readText()
        if ("minecraft:apply_bonus" !in text) return self
        val drop = Regex("\"name\": \"(mekanism:[a-z0-9_]+)\"").findAll(text).map { it.groupValues[1] }
            .firstOrNull { it != "mekanism:$name" } ?: return self
        val arguments = linkedMapOf<String, Any?>("ore_drop" to Ids.rename(drop), "ore_block" to "mekanized:$name")
        Regex("\"max\": ([0-9.]+),\\s*\"min\": ([0-9.]+)").find(text)?.let { range ->
            arguments["ore_drop_count"] = "${range.groupValues[2].toDouble().toInt()}~${range.groupValues[1].toDouble().toInt()}"
        }
        return mapOf("template" to "default:loot_table/ore", "arguments" to arguments)
    }

    /** Chemical names (gases, infuse types, slurries, pigments; Mekanism 1.21 unifies them) from the lang keys. */
    private fun chemicals(): List<String> {
        val names = sortedSetOf<String>()
        for (module in listOf("mekanism", "mekanismgenerators")) {
            val file = source.resolve("src/datagen/generated/$module/assets/$module/lang/en_us.json")
            if (!file.isFile) continue
            JsonParser.parseString(file.readText()).asJsonObject.keySet()
                .filter { it.startsWith("chemical.$module.") }
                .map { it.removePrefix("chemical.$module.") }
                .filter { "." !in it && it != "empty" }
                .forEach { names += it }
        }
        names.forEach { _ -> report.count("chemicals") }
        return names.toList()
    }

    /** item id -> every tag that contains it, following "#tag" includes. */
    private fun membership(): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        fun members(tag: String, visited: MutableSet<String>): Set<String> {
            if (!visited.add(tag)) return setOf()
            return itemTags[tag].orEmpty().flatMap { if (it.startsWith("#")) members(it.removePrefix("#"), visited) else listOf(it) }.toSet()
        }
        for (tag in itemTags.keys) {
            for (item in members(tag, mutableSetOf())) {
                if (item.startsWith("mekanized:")) result.getOrPut(item) { sortedSetOf() } += tag
            }
        }
        return result
    }

    /** lang -> key -> name, for [keys] (Mekanism `item.mekanism.x` keys, renamed). */
    private fun languages(keys: Set<String>): Map<String, Any?> {
        val languages = sortedMapOf<String, Any?>()
        // Generators' names are merged into the same language (one namespace, K1).
        val files = listOf("src/main/resources/assets/mekanism/lang", "src/datagen/generated/mekanism/assets/mekanism/lang",
            "src/generators/resources/assets/mekanismgenerators/lang", "src/datagen/generated/mekanismgenerators/assets/mekanismgenerators/lang")
            .flatMap { source.resolve(it).listFiles().orEmpty().toList() }
            .filter { it.extension == "json" }
        for (file in files) {
            val json: JsonObject = JsonParser.parseString(file.readText()).asJsonObject
            @Suppress("UNCHECKED_CAST")
            val entries = languages.getOrPut(file.nameWithoutExtension) { sortedMapOf<String, String>() } as MutableMap<String, String>
            for (key in keys) {
                val value = (json.get(key) ?: json.get(key.replace(".mekanism.", ".mekanismgenerators.")))?.asString ?: continue
                // Mekanism names are plain text; CraftEngine reads lang values as MiniMessage.
                entries[key.replace(".mekanism.", ".mekanized.")] = value.replace("<", "\\<")
            }
        }
        languages.entries.removeIf { (it.value as Map<*, *>).isEmpty() }
        report.count("languages")
        return languages
    }
}
