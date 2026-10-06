import java.io.File

/**
 * Mekanized items and blocks as CraftEngine content (MEKANIZED_PLAN.md, Phase 1 and later): materials,
 * ores and storage blocks picked by name, then the machines, transports, items and gear of each phase
 * of [ContentPhases], with Mekanism's models, translated names (CraftEngine client-side `lang`, every
 * language Mekanism ships) and `c:` item tags (transitively, so `c:ingots` contains what
 * `c:ingots/osmium` contains).
 *
 * @param itemTags tag -> members ("#tag" includes), already renamed to `mekanized:`
 */
internal class MekanismContent(
    private val source: MekanismSource,
    private val out: File,
    private val itemTags: Map<String, List<String>>,
    private val report: Report,
) {
    /** Phase 1 materials, by Mekanism item name. */
    private val materialPattern = Regex(
        "^(ingot|nugget|dust|dirty_dust|clump|shard|crystal|raw|alloy|enriched)_.+$|^.+_control_circuit$|" +
                "^(hdpe_pellet|hdpe_rod|hdpe_sheet|hdpe_stick|substrate|salt|fluorite_gem|bio_fuel|sawdust|dye_base)$|" +
                // Nuclear and netherite intermediates of the imported recipes.
                "^(pellet_.+|yellow_cake_uranium|reprocessed_fissile_fragment|dirty_netherite_scrap)$"
    )
    private val blockPattern = Regex("^(deepslate_)?(osmium|tin|lead|uranium|fluorite)_ore$|^block_.+$|^steel_casing$")

    private val assets = out.resolve("pack/resourcepack/assets/mekanized")
    private val states = BlockStates(source, assets.resolve("models"), report)
    private val display = DisplayStates(assets.resolve("models"))
    private val energyCube = EnergyCube(source, assets)
    private val lang = Lang(source, report)

    /** Ids (`mekanized:x`) of the content generated per phase (by phase key), for the recipe split. */
    val ids: Map<String, MutableSet<String>> = ContentPhases.all.associate { it.key to sortedSetOf<String>() }

    /** What a file under generation collects: its items and blocks, and the translation keys they need. */
    private class Output(val nameKeys: MutableSet<String>, val membership: Map<String, Set<String>>) {
        val items = linkedMapOf<String, Any?>()
        val blocks = linkedMapOf<String, Any?>()
    }

    fun generate() {
        val nameKeys = mutableSetOf<String>()
        val membership = membership()
        generateMaterials(Output(nameKeys, membership), ids.getValue("materials"))
        for (phase in ContentPhases.all) {
            val phaseIds = ids.getValue(phase.key)
            phase.files.forEach { generateFile(it, Output(nameKeys, membership), phaseIds) }
            if (phase.gear.isNotEmpty()) generateGear(phase, Output(nameKeys, membership), phaseIds)
        }
        val chemicals = lang.chemicals()
        nameKeys += chemicals.map { "chemical.mekanism.$it" }
        // Fluids are named by their liquid blocks (block.mekanism.brine).
        nameKeys += source.mekanismFluids.map { "block.mekanism.$it" }
        out.resolve("chemicals.txt").writeText(chemicals.joinToString("\n", postfix = "\n"))
        writeYaml(out.resolve("pack/configuration/lang.yml"),
            "Names of Mekanized items, blocks and chemicals in every language Mekanism ships (Mekanism, MIT).\n" +
                    "Shown in each player's language.",
            linkedMapOf("lang" to lang.languages(nameKeys)))
    }

    // -- materials, ores and storage blocks ---------------------------------------------------------

    private fun generateMaterials(output: Output, phaseIds: MutableSet<String>) {
        val itemDefinitions = source.resolve("src/datagen/generated/mekanism/assets/mekanism/items")
            .listFiles { file -> file.extension == "json" }?.sortedBy { it.name } ?: listOf()
        for (file in itemDefinitions) {
            val name = file.nameWithoutExtension
            val model = states.itemModel(file) ?: continue
            val id = "mekanized:$name"
            val isBlock = blockPattern.matches(name)
            if (!isBlock && !materialPattern.matches(name)) continue
            if (isBlock) {
                val block = states.plainBlock(name) ?: continue
                output.blocks[id] = block.toYaml()
                output.items[id] = itemYaml("block.mekanized.$name", Ids.rename(model), blockItemBehavior(id), output.membership[id])
                output.nameKeys += "block.mekanism.$name"
                report.count("blocks")
            } else {
                output.items[id] = itemYaml("item.mekanized.$name", Ids.rename(model), tags = output.membership[id])
                output.nameKeys += "item.mekanism.$name"
                report.count("items")
            }
            phaseIds.add(id)
        }
        writeYaml(out.resolve("pack/configuration/materials.yml"),
            "Mekanized materials, ores and storage blocks (Phase 1), generated from Mekanism (MIT, see LICENSE-MEKANISM.txt).",
            linkedMapOf("items" to output.items, "blocks" to output.blocks))
    }

    // -- gear -------------------------------------------------------------------------------------------

    private fun generateGear(phase: Phase, output: Output, gearIds: MutableSet<String>) {
        for (gear in phase.gear) {
            val model = gear.model ?: states.itemDefinition(gear.name)?.let { states.itemModel(it) }?.let { Ids.rename(it) } ?: run {
                report.skip("gear item without a usable model", gear.name)
                continue
            }
            val data = linkedMapOf<String, Any?>("item_name" to "<!i><lang:item.mekanized.${gear.name}>", "unbreakable" to true,
                "components" to mapOf("minecraft:max_stack_size" to 1))
            // Worn without the vanilla armour's look: Mekanism draws its gear in code.
            if (gear.slot != null) data["equippable"] = mapOf("slot" to gear.slot)
            output.items["mekanized:${gear.name}"] = linkedMapOf<String, Any?>("material" to gear.material, "data" to data, "model" to model)
                .withTags(output.membership["mekanized:${gear.name}"])
            output.nameKeys += "item.mekanism.${gear.name}"
            gearIds += "mekanized:${gear.name}"
        }
        for (name in phase.modules) {
            val model = states.itemDefinition(name)?.let { states.itemModel(it) } ?: continue
            output.items["mekanized:$name"] = itemYaml("item.mekanized.$name", Ids.rename(model), tags = output.membership["mekanized:$name"])
            output.nameKeys += "item.mekanism.$name"
            gearIds += "mekanized:$name"
        }
        writeYaml(out.resolve("pack/configuration/gear.yml"),
            "Mekanized tools, armour and modules (Phase 6 wave 5), generated from Mekanism (MIT).", linkedMapOf("items" to output.items))
    }

    // -- machine blocks ---------------------------------------------------------------------------------

    /** A machine or transport block before its look is decided: what it does, its states, its item's model. */
    private class BlockParts(val behavior: Map<String, Any?>, val states: BlockState, val itemModel: String? = null)

    /** A block and what it adds to its file: [behavior] with the display entity's, and the states that show it. */
    private class Look(val states: BlockState, val behavior: Map<String, Any?>, val full: Boolean)

    private fun generateFile(file: ContentFile, output: Output, phaseIds: MutableSet<String>) {
        for (name in file.machines + file.transports.keys) {
            val edge = file.transports[name]
            val parts = if (edge != null) transport(name, edge, output) else machine(name)
            if (parts == null) {
                report.skip("machine block without a usable blockstate", name)
                continue
            }
            addMachineBlock(name, parts, edge != null, output)
            phaseIds += "mekanized:$name"
        }
        for (name in file.plain) {
            val id = "mekanized:$name"
            val block = states.plainBlock(name) ?: continue
            output.blocks[id] = block.toYaml()
            output.items[id] = itemYaml("block.mekanized.$name", states.blockItemModel(name) ?: block.model,
                blockItemBehavior(id), output.membership[id])
            output.nameKeys += "block.mekanism.$name"
            phaseIds += id
            report.count("blocks")
        }
        for (name in file.items) {
            val model = states.itemDefinition(name)?.let { states.itemModel(it) } ?: continue
            // Blocks used as items only here (the cardboard box) keep their block name.
            val kind = if (source.resolve("src/main/resources/assets/mekanism/blockstates/$name.json").isFile) "block" else "item"
            output.items["mekanized:$name"] = itemYaml("$kind.mekanized.$name", Ids.rename(model), tags = output.membership["mekanized:$name"])
            output.nameKeys += "$kind.mekanism.$name"
            phaseIds += "mekanized:$name"
        }
        // Items with only a model and a name (the full cardboard box): name -> (model, name key).
        for ((name, model) in file.extraModels) {
            output.items["mekanized:$name"] = itemYaml(model.second, model.first)
            phaseIds += "mekanized:$name"
        }
        writeYaml(out.resolve("pack/configuration/${file.fileName}"), file.header,
            linkedMapOf("items" to output.items, "blocks" to output.blocks))
    }

    /**
     * A cable, tube, pipe, transporter or conductor. OBJ models upstream: drawn entirely by display entities
     * (center and arms); the block is an empty sapling-type state: small hitbox, no collision, and one
     * state shared by every transport (CraftEngine has only 8 sapling states) with the universal
     * cable's breaking particles. Transporters: Mekanism draws a glass layer over a see-through frame;
     * the opaque textures (glass filled in) give the same look with one model.
     */
    private fun transport(name: String, edge: Int, output: Output): BlockParts {
        val texture = if (name.endsWith("_logistical_transporter")) "opaque/$name" else name
        val models = CableModels.write(assets.resolve("models"), name, texture, edge)
        for ((part, model) in models) {
            output.items["mekanized:cable_part/$name/$part"] = linkedMapOf<String, Any?>("model" to model)
        }
        val shared = Appearance(mapOf("type" to "sapling", "id" to "mekanized_universal_cable"),
            display.emptyModel("universal_cable", "mekanized:block/cable/basic_universal_cable/center"))
        return BlockParts(
            mapOf("type" to "machineengine:transport", "transport" to "mekanized:$name", "connection_items" to "mekanized:cable_part/$name"),
            SingleState(shared), models.getValue("center"))
    }

    private fun machine(name: String): BlockParts? {
        val blockStates = states.machineStates(name) ?: return null
        return BlockParts(mapOf("type" to "machineengine:machine", "machine" to "mekanized:$name", "keep_contents" to true), blockStates)
    }

    private fun addMachineBlock(name: String, parts: BlockParts, cable: Boolean, output: Output) {
        val id = "mekanized:$name"
        val modelsRoot = assets.resolve("models")
        // Glass is a full cube that must not hide what is behind it: a non-occluding state.
        val glass = name == "structural_glass" || name == "reactor_glass"
        val full = !cable && !glass && parts.states.allAppearances.all { CableModels.isFullCube(modelsRoot, it.path) }
        val look = when {
            glass -> Look(parts.states.withAutoState("tintable_leaves"), parts.behavior, full)
            full || cable -> Look(parts.states, parts.behavior, full)
            else -> displayLook(name, parts, output.items)
        }
        output.blocks[id] = linkedMapOf<String, Any?>(
            "behavior" to look.behavior,
            "loot" to mapOf("template" to "default:loot_table/self"),
            "settings" to mapOf("template" to if (look.full) "default:settings/solid_1x1x1" else "default:settings/transparent_1x1x1",
                "overrides" to mapOf("hardness" to 3.5, "resistance" to 8.0, "push_reaction" to "block",
                    "tags" to listOf("minecraft:mineable/pickaxe"), "correct_tools" to listOf("#minecraft:pickaxes"), "required_break_power" to 1)),
        ).apply { putAll(look.states.toYaml()) }
        output.items[id] = itemYaml("block.mekanized.$name", parts.itemModel ?: states.blockItemModel(name) ?: parts.states.firstModel(),
            blockItemBehavior(id), output.membership[id])
        output.nameKeys += "block.mekanism.$name"
        report.count("machine blocks")
    }

    /**
     * Not a full cube: drawn by a display entity (turned to the facing, switching to the active model),
     * over one empty non-occluding state, however many facings it has. Non-occluding states are scarce
     * (about 117 slots), and the blocks around stay drawn (no seeing through walls).
     */
    private fun displayLook(name: String, parts: BlockParts, items: MutableMap<String, Any?>): Look {
        val (inactive, active) = display.displayModels(parts.states)
        items["mekanized:display/$name"] = linkedMapOf<String, Any?>("model" to inactive)
        if (active != null) items["mekanized:display/$name/active"] = linkedMapOf<String, Any?>("model" to active)
        // Factories: the four tiers of a kind share one state (and the basic tier's particles).
        val factoryKind = ContentPhases.factoryKinds.firstOrNull { name.endsWith("_${it}_factory") }
        val shown = when {
            factoryKind != null -> display.factory(parts.states, factoryKind)
            name in THIN_STATES -> display.thin(parts.states, name, inactive, THIN_STATES.getValue(name))
            else -> display.shared(parts.states, name, inactive)
        }
        var behavior = parts.behavior + ("display_item" to "mekanized:display/$name")
        // Some blockstates turn the model even when facing north (separator 90, pump 180).
        display.northRotation(parts.states).let { if (it != 0) behavior = behavior + ("display_rotation" to it) }
        // Every facing's turn, x included (lasers point up in their model): "north=90/180,south=...".
        display.facingRotations(parts.states)?.let { behavior = behavior + ("display_rotations" to it) }
        // Energy cubes: Mekanism's glowing core, drawn in code, as a second display growing with the charge.
        energyCube.tier(name)?.let { tier ->
            energyCube.core(tier, items)
            behavior = behavior + mapOf("level_display" to "mekanized:display/energy_core/$tier",
                "level_storage" to "energy", "level_steps" to EnergyCube.CORE_LEVELS)
            // Like Mekanism: the frame always, a side's port only while that side is configured.
            if (energyCube.parts(tier, name, items)) behavior = behavior + ("side_display" to "mekanized:display/$name/port")
        }
        report.count("display-rendered blocks")
        return Look(shown, behavior, full = false)
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

    private companion object {
        /** Display-rendered blocks players walk on: a thin state with no collision instead of a full leaves block. */
        val THIN_STATES = mapOf("chargepad" to "pressure_plate")
    }
}
