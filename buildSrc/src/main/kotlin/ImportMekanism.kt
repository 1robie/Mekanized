import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Converts a Mekanism checkout into Mekanized content (MEKANIZED_PLAN.md, Phase 0, K4):
 *
 * - `pack/resourcepack/assets/mekanized/`: textures, models and animations, with the `mekanism` and
 *   `mekanismgenerators` namespaces renamed to `mekanized`;
 * - `pack/configuration/recipes_<group>.yml`: crafting, smelting and blasting recipes for CraftEngine;
 * - `definitions/<name>.yml`: machine recipes (one file per recipe type) and tags as MachineEngine
 *   definitions (ARCHITECTURE.md §29);
 * - `LICENSE-MEKANISM.txt` and `import-report.txt`.
 *
 * Recipes for other mods (`neoforge:conditions`) and content outside the plan are skipped and
 * listed in the report. Generated files are build output; overlays live in the module's sources.
 */
abstract class ImportMekanism : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun import() {
        val source = sourceDirectory.get().asFile
        val out = outputDirectory.get().asFile
        out.deleteRecursively()
        val report = Report()
        Assets(source, out.resolve("pack/resourcepack/assets/mekanized"), report).copy()
        val itemTags = Tags(source, out.resolve("definitions"), report).convert()
        val content = MekanismContent(source, out, itemTags, report).also { it.generate() }
        val recipes = Recipes(source, out, report, content.ids, content.machineIds, content.chemistryIds, content.logisticsIds,
            content.multiblockIds, itemTags)
            .also { it.convert() }
        writeAliases(out.resolve("definitions/vanilla_aliases.yml"), recipes.referencedTags + itemTags.keys, itemTags, report)
        source.resolve("LICENSE").copyTo(out.resolve("LICENSE-MEKANISM.txt"), overwrite = true)
        out.resolve("import-report.txt").writeText(report.format())
        logger.lifecycle(report.summary())
    }
}

/** What the import did and skipped, written next to the output for review. */
internal class Report {
    val counts = sortedMapOf<String, Int>()
    val skipped = sortedMapOf<String, MutableList<String>>()

    fun count(what: String) {
        counts[what] = (counts[what] ?: 0) + 1
    }

    fun skip(reason: String, what: String) {
        skipped.getOrPut(reason) { mutableListOf() }.add(what)
    }

    fun summary() = "Mekanism import: " + counts.entries.joinToString { "${it.value} ${it.key}" } +
            "; skipped " + skipped.values.sumOf { it.size } + " (see import-report.txt)"

    fun format() = buildString {
        appendLine("# Mekanism import report")
        counts.forEach { (what, count) -> appendLine("$count $what") }
        skipped.forEach { (reason, items) ->
            appendLine()
            appendLine("## Skipped: $reason (${items.size})")
            items.sorted().forEach { appendLine("- $it") }
        }
    }
}

/**
 * `c:` tags with their vanilla members (E10): Mekanized item tags that make NeoForge convention tags
 * work on Paper. Tags with neither vanilla nor Mekanized members are listed in the report.
 */
internal fun writeAliases(file: File, tags: Set<String>, itemTags: Map<String, List<String>>, report: Report) {
    val aliases = sortedMapOf<String, Any?>()
    for (tag in tags.filter { it.startsWith("c:") }.sorted()) {
        val vanilla = VanillaAliases.of(tag)
        if (vanilla.isNotEmpty()) {
            aliases[tag] = vanilla
            report.count("vanilla tag aliases")
        } else if (itemTags[tag].isNullOrEmpty()) {
            report.skip("c: tag with no known vanilla or Mekanized members (add them to an item-tags overlay)", tag)
        }
    }
    writeYaml(file, "Vanilla members of NeoForge's c: convention tags, which do not exist on Paper.\n" +
            "Generated for Mekanized; add your own items to these tags in another definitions file.", linkedMapOf("item-tags" to aliases))
}

internal object Ids {
    /** `mekanism:x` and `mekanismgenerators:x` become `mekanized:x`; other namespaces stay. */
    fun rename(id: String): String {
        val tag = id.startsWith("#")
        val bare = id.removePrefix("#")
        val renamed = when {
            bare.startsWith("mekanismgenerators:") -> "mekanized:" + bare.removePrefix("mekanismgenerators:")
            bare.startsWith("mekanism:") -> "mekanized:" + bare.removePrefix("mekanism:")
            else -> bare
        }
        return if (tag) "#$renamed" else renamed
    }

    /** Renames every namespaced reference inside a JSON text (models, animations). */
    fun renameInText(text: String) = text
        .replace("\"mekanismgenerators:", "\"mekanized:")
        .replace("\"mekanism:", "\"mekanized:")
        .replace("\"#mekanismgenerators:", "\"#mekanized:")
        .replace("\"#mekanism:", "\"#mekanized:")
}

internal fun yaml(): Yaml = Yaml(DumperOptions().apply {
    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    indent = 2
    width = 160
    splitLines = false
})

internal fun writeYaml(file: File, header: String, content: Map<String, Any?>) {
    file.parentFile.mkdirs()
    file.writeText(header.lines().joinToString("\n") { "# $it".trimEnd() } + "\n" + yaml().dump(content))
}

// -- assets ---------------------------------------------------------------------------------------

internal class Assets(private val source: File, private val target: File, private val report: Report) {
    private val roots = listOf(
        "src/main/resources/assets/mekanism",
        "src/datagen/generated/mekanism/assets/mekanism",
        "src/generators/resources/assets/mekanismgenerators",
        "src/datagen/generated/mekanismgenerators/assets/mekanismgenerators",
    )

    fun copy() {
        val origin = mutableMapOf<String, String>()
        for (root in roots) {
            for (folder in listOf("textures", "models")) {
                val base = source.resolve(root).resolve(folder)
                if (!base.isDirectory) continue
                base.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = folder + "/" + file.relativeTo(base).invariantSeparatorsPath
                    val previous = origin.putIfAbsent(relative, root)
                    if (previous != null) {
                        if (file.readBytes().contentEquals(target.resolve(relative).readBytes())) return@forEach
                        report.skip("asset path used by both Mekanism and Mekanism Generators (first kept)", "$relative ($root)")
                        return@forEach
                    }
                    copyFile(file, target.resolve(relative), relative)
                }
            }
        }
    }

    private fun copyFile(file: File, destination: File, relative: String) {
        destination.parentFile.mkdirs()
        if (file.extension == "mcmeta") {
            val cleaned = vanillaMetadata(Ids.renameInText(file.readText()), relative) ?: return
            destination.writeText(cleaned)
            report.count("texture files")
            return
        }
        if (file.extension == "json") {
            val text = flattenLoaderModel(Ids.renameInText(file.readText()), mekanismModels(source))
            if (file.extension == "json" && "\"loader\"" in text) {
                report.skip("model needs a mod-side loader (NeoForge composite/obj); not usable by vanilla clients yet", relative)
            }
            destination.writeText(text)
        } else {
            file.copyTo(destination, overwrite = true)
        }
        report.count(if (relative.startsWith("textures/")) "texture files" else "model files")
    }
}

/**
 * Texture metadata a vanilla client accepts: mod-only sections (CTM, connected textures) are dropped,
 * and an empty `frames` list (NeoForge reads it as "every frame", vanilla fails to stitch the atlas)
 * is removed. Null when nothing is left to write.
 */
/**
 * Two of Mekanism's model loaders only group plain elements, so they flatten into vanilla models:
 * `mekanism:energy_cube` (frame, ports and leds lists) and `neoforge:composite` (child models with
 * their own textures, prefixed to avoid clashes). OBJ models and other loaders are left as they are.
 */
/**
 * Reads a renamed model reference (`mekanized:block/x`) from Mekanism's sources, renamed too; null if
 * it is not one of Mekanism's models.
 */
internal fun mekanismModels(source: File): (String) -> JsonObject? = { ref ->
    if (!ref.startsWith("mekanized:")) null
    else listOf("src/main/resources/assets/mekanism", "src/generators/resources/assets/mekanismgenerators",
        "src/datagen/generated/mekanism/assets/mekanism", "src/datagen/generated/mekanismgenerators/assets/mekanismgenerators")
        .map { source.resolve("$it/models/${ref.removePrefix("mekanized:")}.json") }.firstOrNull { it.isFile }
        ?.let { JsonParser.parseString(Ids.renameInText(it.readText())).asJsonObject }
}

/** A child model's elements and textures, following `parent` references to the first model with elements. */
private fun inlineParents(child: JsonObject, resolve: (String) -> JsonObject?, depth: Int = 0): JsonObject {
    if (child.has("elements") || depth > 8) return child
    val parent = child.get("parent")?.asString?.let(resolve) ?: return child
    val inlined = inlineParents(parent, resolve, depth + 1).deepCopy()
    val textures = inlined.getAsJsonObject("textures") ?: JsonObject().also { inlined.add("textures", it) }
    child.getAsJsonObject("textures")?.entrySet()?.forEach { (key, value) -> textures.add(key, value) } // the child wins
    inlined.remove("parent")
    return inlined
}

internal fun flattenLoaderModel(text: String, resolve: (String) -> JsonObject? = { null }): String {
    val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return text
    val loader = json.get("loader")?.asString ?: return text
    val elements = com.google.gson.JsonArray()
    fun addAll(value: JsonElement?) {
        when {
            value == null -> {}
            value.isJsonArray -> value.asJsonArray.forEach { if (it.isJsonObject && it.asJsonObject.has("from")) elements.add(it) else addAll(it) }
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { addAll(it.value) }
        }
    }
    when (loader) {
        "mekanized:energy_cube" -> {
            addAll(json.remove("frame"))
            addAll(json.remove("ports"))
            addAll(json.remove("leds"))
        }
        "neoforge:composite" -> {
            val textures = json.getAsJsonObject("textures") ?: JsonObject().also { json.add("textures", it) }
            val children = json.remove("children")?.asJsonObject ?: return text
            for ((childName, childValue) in children.entrySet()) {
                // Factories: a child is just a parent reference (the machine base, the tier's LED).
                val child = inlineParents(childValue.asJsonObject, resolve)
                val prefix = childName.replace(Regex("[^a-z0-9_]"), "_") + "_"
                child.getAsJsonObject("textures")?.entrySet()?.forEach { (key, texture) -> textures.add(prefix + key, texture) }
                child.getAsJsonArray("elements")?.forEach { element ->
                    element.asJsonObject.getAsJsonObject("faces")?.entrySet()?.forEach { (_, face) ->
                        val ref = face.asJsonObject.get("texture")?.asString
                        if (ref != null && ref.startsWith("#") && child.getAsJsonObject("textures")?.has(ref.removePrefix("#")) == true) {
                            face.asJsonObject.addProperty("texture", "#" + prefix + ref.removePrefix("#"))
                        }
                    }
                    elements.add(element)
                }
            }
            if (!json.has("parent")) json.addProperty("parent", "minecraft:block/block")
        }
        else -> return text
    }
    json.remove("loader")
    json.add("elements", elements)
    return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json)
}

internal fun Assets.vanillaMetadata(text: String, relative: String): String? {
    val json = JsonParser.parseString(text).asJsonObject
    for (modSection in listOf("ctm", "extra")) json.remove(modSection)
    json.getAsJsonObject("animation")?.let { animation ->
        val frames = animation.get("frames")
        if (frames != null && frames.isJsonArray && frames.asJsonArray.isEmpty) animation.remove("frames")
    }
    if (json.size() == 0) return null
    return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json)
}

// -- recipes --------------------------------------------------------------------------------------

/**
 * Machine recipe conversion. Durations and energy per tick are Mekanism's defaults for the machine
 * running that recipe type; they are written into every recipe so server owners can tune them.
 */
internal class Recipes(
    private val source: File,
    private val out: File,
    private val report: Report,
    /** Content generated so far: CraftEngine recipes making these go into the `materials_` files. */
    private val contentIds: Set<String>,
    /** Phase 2 content: CraftEngine recipes needing it (and Phase 1) go into the `machines_` files. */
    private val machineIds: Set<String>,
    /** Phase 3 content: CraftEngine recipes needing it (and earlier phases) go into the `chemistry_` files. */
    private val chemistryIds: Set<String>,
    /** Phase 4 content: CraftEngine recipes needing it (and earlier phases) go into the `logistics_` files. */
    private val logisticsIds: Set<String>,
    /** Phase 5a content: CraftEngine recipes needing it (and earlier phases) go into the `multiblocks_` files. */
    private val multiblockIds: Set<String>,
    private val itemTags: Map<String, List<String>>,
) {
    /**
     * Mekanism's fluids (its `c:` fluid tags, one per fluid), plus water and lava: resources
     * `mekanized:fluid/<name>`, apart from the chemicals of the same name.
     */
    val fluids: List<String> = source.resolve("src/datagen/generated").listFiles().orEmpty()
        .flatMap { it.resolve("data/c/tags/fluid").listFiles().orEmpty().toList() }
        .map { it.nameWithoutExtension }.filter { it != "gaseous" }.plus(listOf("water", "lava")).distinct().sorted()

    /** A fluid id or `c:`/`minecraft:` fluid tag as a fluid resource type. */
    private fun fluidType(ref: String): String {
        val id = Ids.rename(ref.removePrefix("#"))
        return when {
            id == "minecraft:water" || id == "minecraft:lava" -> "mekanized:fluid/" + id.removePrefix("minecraft:")
            id.startsWith("c:") && id.removePrefix("c:") in fluids -> "mekanized:fluid/" + id.removePrefix("c:")
            id.startsWith("mekanized:") && id.removePrefix("mekanized:") in fluids -> "mekanized:fluid/" + id.removePrefix("mekanized:")
            else -> throw Unsupported("unknown fluid $ref")
        }
    }

    /**
     * The first chemical of a chemical tag: per-tick costs are exact resources (e.g. water vapor
     * for `#mekanism:water_vapor`, which also lists steam).
     */
    private fun firstChemical(tag: String): String {
        val namespace = tag.substringBefore(":")
        val path = tag.substringAfter(":")
        // Already renamed: Mekanism's own tags live under its original namespaces.
        val namespaces = if (namespace == "mekanized") listOf("mekanism", "mekanismgenerators") else listOf(namespace)
        for (module in source.resolve("src/datagen/generated").listFiles().orEmpty()) for (namespace in namespaces) {
            for (folder in listOf("tags/mekanism/chemical", "tags/chemical")) {
                val file = module.resolve("data/$namespace/$folder/$path.json")
                if (!file.isFile) continue
                val first = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("values")
                    .map { if (it.isJsonPrimitive) it.asString else it.asJsonObject.get("id").asString }
                    .firstOrNull { !it.startsWith("#") } ?: continue
                return Ids.rename(first)
            }
        }
        throw Unsupported("chemical tag $tag has no chemical")
    }
    /** Every item tag the recipes reference, for the vanilla aliases. */
    val referencedTags = sortedSetOf<String>()
    private data class Machine(val duration: Int, val energyPerTick: Long, val phase: Int)

    private val machines = mapOf(
        "enriching" to Machine(200, 50, 2),
        "crushing" to Machine(200, 50, 2),
        "combining" to Machine(200, 50, 2),
        "sawing" to Machine(200, 50, 2),
        "metallurgic_infusing" to Machine(200, 50, 2),
        "compressing" to Machine(200, 100, 2),
        "purifying" to Machine(200, 200, 2),
        "injecting" to Machine(200, 400, 3),
        "oxidizing" to Machine(100, 200, 3),
        "dissolution" to Machine(100, 400, 3),
        "washing" to Machine(1, 200, 3),
        "crystallizing" to Machine(200, 160, 3),
        "chemical_infusing" to Machine(1, 200, 3),
        "separating" to Machine(1, 400, 3),
        // Rotary recipes go both ways: split into one recipe type per direction (the machine's mode).
        // The Thermal Evaporation Plant: no energy, its rate comes from its height and the sun (Phase 5a).
        "evaporating" to Machine(1, 0, 5),
        "condensing" to Machine(1, 100, 3),
        "decondensing" to Machine(1, 100, 3),
        "reaction" to Machine(100, 0, 3),
        // Not machines: items turned into a chemical or energy when put into a machine's slot.
        "chemical_conversion" to Machine(0, 0, 2),
        "energy_conversion" to Machine(0, 0, 2),
    )

    private val outOfPlan = mapOf(
        "painting" to "painting machine and pigments are not in the plan",
        "pigment_extracting" to "painting machine and pigments are not in the plan",
        "pigment_mixing" to "painting machine and pigments are not in the plan",
        "nucleosynthesizing" to "endgame (Phase 5)",
        "activating" to "endgame (Phase 5)",
        "centrifuging" to "endgame (Phase 5)",
        "bin_insert" to "special recipe (bins handle it in code)",
        "bin_extract" to "special recipe (bins handle it in code)",
        "clear_configuration" to "special recipe (handled in code)",
    )

    private val machineRecipes = sortedMapOf<String, MutableMap<String, Any?>>()
    private val craftEngineRecipes = sortedMapOf<String, MutableMap<String, Any?>>()

    fun convert() {
        for (module in listOf("mekanism", "mekanismgenerators")) {
            val base = source.resolve("src/datagen/generated/$module/data/$module/recipe")
            if (!base.isDirectory) continue
            base.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.path }.forEach { file ->
                val path = file.relativeTo(base).invariantSeparatorsPath.removeSuffix(".json")
                val id = "mekanized:$path"
                val json = JsonParser.parseString(file.readText()).asJsonObject
                try {
                    convert(id, json)
                } catch (e: Unsupported) {
                    report.skip(e.message ?: "unsupported", id)
                }
            }
        }
        writeOutputs()
    }

    private class Unsupported(message: String) : RuntimeException(message)

    /**
     * Ingredients replaced where Mekanism's recipe needs content outside the plan, e.g. the chemical
     * oxidizer's dynamic tank (a Phase 5 multiblock) and personal storage. Listed in the report.
     */
    private val adaptedIngredients = mapOf(
        "mekanized:chemical_oxidizer" to mapOf("mekanism:dynamic_tank" to "mekanism:basic_fluid_tank",
            "#mekanism:personal_storage" to "minecraft:chest"),
    )

    private fun convert(id: String, json: JsonObject) {
        if (json.has("neoforge:conditions")) throw Unsupported("needs another mod (neoforge:conditions)")
        adaptedIngredients[id]?.let { replacements ->
            val key = json.getAsJsonObject("key")
            for ((symbol, value) in key.entrySet().toList()) {
                if (value.isJsonPrimitive) replacements[value.asString]?.let { key.addProperty(symbol, it) }
            }
            report.skip("adapted: ingredients outside the plan replaced (" + replacements.entries.joinToString { "${it.key} -> ${it.value}" } + ")", id)
        }
        val type = json.get("type")?.asString ?: throw Unsupported("no type")
        when (type) {
            "minecraft:crafting_shaped", "mekanism:mek_data" -> craftEngine(id, "shaped", shaped(json))
            "minecraft:crafting_shapeless" -> craftEngine(id, "shapeless", shapeless(json))
            "minecraft:smelting", "minecraft:blasting" -> craftEngine(id, "cooking", cooking(type.removePrefix("minecraft:"), json))
            else -> {
                val kind = type.removePrefix("mekanism:")
                outOfPlan[kind]?.let { throw Unsupported(it) }
                if (kind == "rotary") {
                    rotary(id, json)
                    return
                }
                val machine = machines[kind] ?: throw Unsupported("recipe type $type has no converter")
                evaporating = kind == "evaporating"
                machineRecipes[kind + "|" + id] = try {
                    machineRecipe(kind, machine, json)
                } finally {
                    evaporating = false
                }
                report.count("machine recipes")
            }
        }
    }

    // -- machine recipes

    /** Chemical to fluid (`condensing`) and fluid to chemical (`decondensing`). */
    private fun rotary(id: String, json: JsonObject) {
        for ((kind, fields) in listOf("condensing" to listOf("chemical_input", "fluid_output"),
            "decondensing" to listOf("fluid_input", "chemical_output"))) {
            val half = JsonObject()
            for (field in fields) half.add(field, json.get(field) ?: throw Unsupported("rotary recipe without $field"))
            machineRecipes[kind + "|" + id.replace("mekanized:rotary/", "mekanized:$kind/")] = machineRecipe(kind, machines.getValue(kind), half)
            report.count("machine recipes")
        }
    }

    private fun machineRecipe(kind: String, machine: Machine, json: JsonObject): MutableMap<String, Any?> {
        val recipe = linkedMapOf<String, Any?>("type" to "mekanized:$kind")
        val inputs = mutableListOf<Any?>()
        val outputs = mutableListOf<Any?>()
        val perTick = mutableListOf<Any?>()
        val chanceOutputs = mutableListOf<Any?>()
        val perTickChemical = json.get("per_tick_usage")?.asBoolean ?: false

        for ((field, value) in json.entrySet()) {
            when (field) {
                "type", "per_tick_usage", "duration", "secondary_chance", "energy_required", "energy_multiplier" -> {}
                "input", "item_input", "main_input", "extra_input" ->
                    if (value.isJsonObject && value.asJsonObject.has("chemical") || value.isJsonObject && value.asJsonObject.has("tag")
                        && value.asJsonObject.has("amount")) {
                        if (perTickChemical) perTick += chemicalCost(value.asJsonObject) else inputs += chemicalInput(value.asJsonObject)
                    } else if (value.isJsonObject && value.asJsonObject.has("amount") && !value.asJsonObject.has("count")) {
                        inputs += fluidInput(value.asJsonObject)
                    } else {
                        inputs += itemInput(value.asJsonObject)
                    }
                "chemical_input", "left_input", "right_input" ->
                    if (perTickChemical) perTick += chemicalCost(value.asJsonObject) else inputs += chemicalInput(value.asJsonObject)
                "fluid_input" -> inputs += fluidInput(value.asJsonObject)
                "output", "main_output", "item_output", "chemical_output", "fluid_output",
                "left_chemical_output", "right_chemical_output" ->
                    if (value.isJsonPrimitive) outputs += mapOf("type" to "mekanized:energy", "amount" to value.asLong)
                    else outputs += output(field, value.asJsonObject)
                "secondary_output" -> chanceOutputs += output(field, value.asJsonObject).toMutableMap().apply {
                    put("chance", json.get("secondary_chance")?.asDouble ?: 1.0)
                }
                else -> throw Unsupported("recipe field '$field' has no converter")
            }
        }
        val duration = json.get("duration")?.asInt ?: machine.duration
        if (duration > 0) recipe["duration"] = duration
        if (inputs.isNotEmpty()) recipe["inputs"] = inputs
        if (outputs.isNotEmpty()) recipe["outputs"] = outputs
        if (chanceOutputs.isNotEmpty()) recipe["chance-outputs"] = chanceOutputs
        // energy_required: the reaction's own energy per tick; energy_multiplier: times the machine's base usage.
        val energy = json.get("energy_required")?.asLong
            ?: (machine.energyPerTick * (json.get("energy_multiplier")?.asDouble ?: 1.0)).toLong()
        if (energy > 0) perTick.add(0, mapOf("type" to "mekanized:energy", "amount" to energy))
        if (perTick.isNotEmpty()) recipe["cost-per-tick"] = perTick
        return recipe
    }

    /** An item ingredient: `{item: id}`, `{item-tag: id}` or `{any-of: [...]}`, with its count. */
    private fun itemInput(json: JsonObject): Map<String, Any?> {
        val filter = itemFilter(json.get("ingredient") ?: throw Unsupported("item input without ingredient")).toMutableMap()
        filter["amount"] = json.get("count")?.asInt ?: 1
        return filter
    }

    private fun itemFilter(ingredient: JsonElement): Map<String, Any?> = when {
        ingredient.isJsonPrimitive -> itemRef(ingredient.asString)
        ingredient.isJsonArray -> mapOf("any-of" to ingredient.asJsonArray.map { itemFilter(it) })
        ingredient.isJsonObject && ingredient.asJsonObject.get("type")?.asString == "neoforge:or" ->
            mapOf("any-of" to ingredient.asJsonObject.getAsJsonArray("values").map { itemFilter(it) })
        ingredient.isJsonObject && ingredient.asJsonObject.has("tag") -> itemRef("#" + ingredient.asJsonObject.get("tag").asString)
        ingredient.isJsonObject && ingredient.asJsonObject.has("item") -> mapOf("item" to Ids.rename(ingredient.asJsonObject.get("item").asString))
        else -> throw Unsupported("item ingredient form ${ingredient.toString().take(60)}")
    }

    private fun itemRef(value: String): Map<String, Any?> {
        if (!value.startsWith("#")) return mapOf("item" to Ids.rename(value))
        val tag = Ids.rename(value.removePrefix("#"))
        referencedTags += tag
        return mapOf("item-tag" to tag)
    }

    private fun chemicalInput(json: JsonObject): Map<String, Any?> {
        val amount = json.get("amount")?.asLong ?: 1
        return when {
            json.has("chemical") -> mapOf("type" to Ids.rename(json.get("chemical").asString), "amount" to amount)
            json.has("tag") -> mapOf("tag" to Ids.rename(json.get("tag").asString), "amount" to amount)
            else -> throw Unsupported("chemical input form ${json.toString().take(60)}")
        }
    }

    /** A per-tick chemical cost: an exact chemical (costs are resources, not tags). */
    private fun chemicalCost(json: JsonObject): Map<String, Any?> {
        val input = chemicalInput(json)
        val tag = input["tag"] as String? ?: return input
        return mapOf("type" to firstChemical(tag), "amount" to input["amount"])
    }

    /** Fluids are resources of the Mekanized fluid category (Phase 3): a fluid tag names one fluid. */
    private fun fluidInput(json: JsonObject): Map<String, Any?> {
        val ingredient = json.get("ingredient") ?: throw Unsupported("fluid input without ingredient")
        val ref = if (ingredient.isJsonPrimitive) ingredient.asString else ingredient.asJsonObject.get("tag")?.asString?.let { "#$it" }
            ?: ingredient.asJsonObject.get("fluid")?.asString ?: throw Unsupported("fluid ingredient form ${ingredient.toString().take(60)}")
        return mapOf("type" to fluidType(ref), "amount" to (json.get("amount")?.asLong ?: 1))
    }

    /** Set while converting an evaporating recipe: its outputs are fluids. */
    private var evaporating = false

    private fun output(field: String, json: JsonObject): Map<String, Any?> {
        val id = Ids.rename(json.get("id")?.asString ?: throw Unsupported("output without id"))
        return when {
            json.has("count") || field in setOf("main_output", "item_output", "secondary_output") || !json.has("amount") ->
                mapOf("item" to id, "amount" to (json.get("count")?.asInt ?: 1))
            field == "fluid_output" -> mapOf("type" to fluidType(id), "amount" to json.get("amount").asLong)
            // Evaporating makes fluids (brine, liquid lithium), under an "output" field like chemicals'.
            evaporating -> mapOf("type" to fluidType(id), "amount" to json.get("amount").asLong)
            else -> mapOf("type" to id, "amount" to json.get("amount").asLong)
        }
    }

    // -- crafting (CraftEngine)

    private fun craftEngine(id: String, group: String, recipe: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val result = (recipe["result"] as Map<String, Any?>)["id"] as String
        // Each plan phase enables the files whose items all exist: the result (vanilla or already
        // generated) and every Mekanized ingredient.
        val ingredients = mekanizedIds(recipe) + listOf(result).filter { it.startsWith("mekanized:") }
        val file = when {
            ingredients.all { it in contentIds } -> "materials_$group"
            ingredients.all { it in contentIds || it in machineIds } -> "machines_$group"
            ingredients.all { it in contentIds || it in machineIds || it in chemistryIds } -> "chemistry_$group"
            ingredients.all { it in contentIds || it in machineIds || it in chemistryIds || it in logisticsIds } -> "logistics_$group"
            ingredients.all { it in contentIds || it in machineIds || it in chemistryIds || it in logisticsIds || it in multiblockIds } ->
                "multiblocks_$group"
            else -> group
        }
        craftEngineRecipes["$file|$id"] = recipe.toMutableMap()
        report.count("crafting/smelting recipes")
    }

    /** Every `mekanized:` item id in a recipe's ingredients (tags excluded). */
    private fun mekanizedIds(value: Any?): List<String> = when (value) {
        is String -> if (value.startsWith("mekanized:")) listOf(value) else listOf()
        is Map<*, *> -> value.filterKeys { it != "result" }.values.flatMap { mekanizedIds(it) }
        is List<*> -> value.flatMap { mekanizedIds(it) }
        else -> listOf()
    }

    private fun shaped(json: JsonObject): Map<String, Any?> = linkedMapOf(
        "type" to "shaped",
        "pattern" to json.getAsJsonArray("pattern").map { it.asString },
        "ingredients" to json.getAsJsonObject("key").entrySet().associate { (key, value) -> key to craftIngredient(value) },
        "result" to result(json.getAsJsonObject("result")),
    )

    private fun shapeless(json: JsonObject): Map<String, Any?> = linkedMapOf(
        "type" to "shapeless",
        "ingredients" to json.getAsJsonArray("ingredients").map { craftIngredient(it) },
        "result" to result(json.getAsJsonObject("result")),
    )

    private fun cooking(type: String, json: JsonObject): Map<String, Any?> = linkedMapOf(
        "type" to type,
        "experience" to (json.get("experience")?.asDouble ?: 0.0),
        "time" to (json.get("cookingtime")?.asInt ?: if (type == "blasting") 100 else 200),
        "ingredient" to craftIngredient(json.get("ingredient")),
        "result" to result(json.getAsJsonObject("result")),
    )

    /**
     * A `#c:` tag becomes a list of its vanilla members plus the tag itself while Mekanized items are
     * in it (CraftEngine knows those from the items' tags); vanilla items are not in `c:` tags on Paper.
     */
    private fun craftIngredient(value: JsonElement): Any {
        val ingredient = craftIngredientRaw(value)
        if (ingredient !is String || !ingredient.startsWith("#") || ingredient.startsWith("#minecraft:")) return ingredient
        val tag = ingredient.removePrefix("#")
        if (tag.startsWith("c:")) referencedTags += tag
        // CraftEngine knows a tag through the Mekanized items declaring it; vanilla members are listed.
        val expanded = vanillaMembers(tag, mutableSetOf()).distinct() + (if (hasMekanizedItems(tag, mutableSetOf())) listOf(ingredient) else listOf())
        return when (expanded.size) {
            0 -> ingredient
            1 -> expanded.first()
            else -> expanded
        }
    }

    /** Vanilla items and `#minecraft:` tags in [tag], following `c:` / `mekanized:` includes. */
    private fun vanillaMembers(tag: String, visited: MutableSet<String>): List<String> {
        if (!visited.add(tag)) return listOf()
        val direct = itemTags[tag].orEmpty().flatMap { member ->
            when {
                member.startsWith("#minecraft:") -> listOf(member)
                member.startsWith("#") -> vanillaMembers(member.removePrefix("#"), visited)
                member.startsWith("minecraft:") -> listOf(member)
                else -> listOf()
            }
        }
        return VanillaAliases.of(tag) + direct
    }

    private fun hasMekanizedItems(tag: String, visited: MutableSet<String>): Boolean {
        if (!visited.add(tag)) return false
        return itemTags[tag].orEmpty().any { member ->
            if (member.startsWith("#")) hasMekanizedItems(member.removePrefix("#"), visited) else member.startsWith("mekanized:")
        }
    }

    private fun craftIngredientRaw(value: JsonElement): Any = when {
        value.isJsonPrimitive -> Ids.rename(value.asString)
        value.isJsonArray -> value.asJsonArray.map { craftIngredient(it) }
        value.isJsonObject && value.asJsonObject.get("type")?.asString == "neoforge:or" ->
            value.asJsonObject.getAsJsonArray("values").map { craftIngredient(it) }
        value.isJsonObject && value.asJsonObject.has("tag") -> "#" + Ids.rename(value.asJsonObject.get("tag").asString)
        value.isJsonObject && value.asJsonObject.has("item") -> Ids.rename(value.asJsonObject.get("item").asString)
        else -> throw Unsupported("crafting ingredient form ${value.toString().take(60)}")
    }

    private fun result(json: JsonObject): Map<String, Any?> {
        if (json.has("components")) throw Unsupported("crafting result with item components")
        return linkedMapOf("id" to Ids.rename(json.get("id").asString), "count" to (json.get("count")?.asInt ?: 1))
    }

    // -- output

    private fun writeOutputs() {
        out.resolve("fluids.txt").writeText(fluids.joinToString("\n", postfix = "\n"))
        // Bucket slots of fluid machines and tanks (E5): only vanilla buckets, Mekanism's need a mod-side model.
        val buckets = linkedMapOf<String, Any?>()
        for (fluid in listOf("water", "lava")) {
            buckets["mekanized:bucket_filling/$fluid"] = linkedMapOf("type" to "mekanized:bucket_filling",
                "inputs" to listOf(mapOf("item" to "minecraft:${fluid}_bucket", "amount" to 1)),
                "outputs" to listOf(mapOf("type" to "mekanized:fluid/$fluid", "amount" to 1000), mapOf("item" to "minecraft:bucket", "amount" to 1)))
            buckets["mekanized:bucket_draining/$fluid"] = linkedMapOf("type" to "mekanized:bucket_draining",
                "inputs" to listOf(mapOf("item" to "minecraft:bucket", "amount" to 1), mapOf("type" to "mekanized:fluid/$fluid", "amount" to 1000)),
                "outputs" to listOf(mapOf("item" to "minecraft:${fluid}_bucket", "amount" to 1)))
        }
        writeYaml(out.resolve("definitions/recipes_buckets.yml"),
            "Mekanized bucket slots: a full bucket empties into the tank, an empty one is filled from it (1000 mB).\n" +
                    "Edit freely: updates replace this file only while it is unedited.", linkedMapOf("recipes" to buckets))
        machineRecipes.entries.groupBy { it.key.substringBefore("|") }.forEach { (kind, entries) ->
            val machine = machines.getValue(kind)
            writeYaml(out.resolve("definitions/recipes_$kind.yml"),
                "Mekanized $kind recipes, generated from Mekanism (MIT, see LICENSE-MEKANISM.txt).\n" +
                        "Edit freely: updates replace this file only while it is unedited; delete it to get the defaults back.\n" +
                        "Durations are ticks; energy is Joules per tick. Phase ${machine.phase} of MEKANIZED_PLAN.md.",
                linkedMapOf("recipes" to entries.associate { it.key.substringAfter("|") to it.value }))
        }
        craftEngineRecipes.entries.groupBy { it.key.substringBefore("|") }.forEach { (group, entries) ->
            writeYaml(out.resolve("pack/configuration/recipes_$group.yml"),
                "Mekanized $group recipes for CraftEngine, generated from Mekanism (MIT, see LICENSE-MEKANISM.txt).",
                linkedMapOf("recipes" to entries.associate { it.key.substringAfter("|") to it.value }))
        }
    }
}

// -- tags -----------------------------------------------------------------------------------------

/**
 * Item and chemical tags. Mekanism's datagen only lists its own items in `c:` tags (NeoForge adds
 * the vanilla members); the vanilla members are added by the Mekanized overlay (Phase 1, E10).
 */
internal class Tags(private val source: File, private val target: File, private val report: Report) {
    fun convert(): Map<String, List<String>> {
        val itemTags = sortedMapOf<String, Any?>()
        val chemicalTags = sortedMapOf<String, Any?>()
        val data = source.resolve("src/datagen/generated")
        data.listFiles()?.forEach { module ->
            val namespaces = module.resolve("data").listFiles() ?: return@forEach
            for (namespace in namespaces) {
                read(namespace.resolve("tags/item"), namespace.name, itemTags, "item tags")
                read(namespace.resolve("tags/mekanism/chemical"), namespace.name, chemicalTags, "chemical tags")
                read(namespace.resolve("tags/chemical"), namespace.name, chemicalTags, "chemical tags")
            }
        }
        writeYaml(target.resolve("tags.yml"),
            "Mekanized item and chemical tags, generated from Mekanism (MIT, see LICENSE-MEKANISM.txt).\n" +
                    "\"#name\" entries include another tag.",
            linkedMapOf("item-tags" to itemTags, "resource-tags" to chemicalTags))
        @Suppress("UNCHECKED_CAST")
        return itemTags as Map<String, List<String>>
    }

    private fun read(base: File, namespace: String, into: MutableMap<String, Any?>, what: String) {
        if (!base.isDirectory) return
        base.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { file ->
            val tag = Ids.rename("$namespace:" + file.relativeTo(base).invariantSeparatorsPath.removeSuffix(".json"))
            val values = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("values") ?: JsonArray()
            val members = values.mapNotNull { value ->
                when {
                    value.isJsonPrimitive -> Ids.rename(value.asString)
                    value.isJsonObject && value.asJsonObject.get("required")?.asBoolean == false -> null
                    value.isJsonObject -> Ids.rename(value.asJsonObject.get("id").asString)
                    else -> null
                }
            }.filter { !it.removePrefix("#").contains(":") || it.removePrefix("#").substringBefore(":") in setOf("mekanized", "minecraft", "c") }
            if (members.isEmpty()) return@forEach
            @Suppress("UNCHECKED_CAST")
            val existing = into[tag] as MutableList<String>? ?: mutableListOf<String>().also { into[tag] = it }
            members.filter { it !in existing }.forEach { existing += it }
            report.count(what)
        }
    }
}
