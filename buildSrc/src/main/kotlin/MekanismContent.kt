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
        val chemicals = chemicals()
        nameKeys += chemicals.map { "chemical.mekanism.$it" }
        out.resolve("chemicals.txt").writeText(chemicals.joinToString("\n", postfix = "\n"))
        writeYaml(out.resolve("pack/configuration/lang.yml"),
            "Names of Mekanized items, blocks and chemicals in every language Mekanism ships (Mekanism, MIT).\n" +
                    "Shown in each player's language.",
            linkedMapOf("lang" to languages(nameKeys)))
    }

    private fun MutableMap<String, Any?>.withTags(tags: Set<String>?): MutableMap<String, Any?> {
        if (!tags.isNullOrEmpty()) this["settings"] = mapOf("tags" to tags.sorted())
        return this
    }

    /** The model of a plain item definition (`minecraft:model`), else null (special items come later). */
    private fun itemModel(file: File): String? {
        val model = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("model") ?: return null
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

    /**
     * Chemical names (gases, infuse types, slurries; Mekanism 1.21 unifies them) from the lang keys.
     * Pigments (colors) are left out: the painting machine is not in the plan.
     */
    private fun chemicals(): List<String> {
        val pigments = setOf("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "purple", "orange", "gray", "dark_gray",
            "indigo", "bright_green", "aqua", "red", "pink", "yellow", "white", "brown", "bright_pink", "blue", "cyan", "green",
            "light_blue", "lime", "magenta", "light_gray")
        val names = sortedSetOf<String>()
        for (module in listOf("mekanism", "mekanismgenerators")) {
            val file = source.resolve("src/datagen/generated/$module/assets/$module/lang/en_us.json")
            if (!file.isFile) continue
            JsonParser.parseString(file.readText()).asJsonObject.keySet()
                .filter { it.startsWith("chemical.$module.") }
                .map { it.removePrefix("chemical.$module.") }
                .filter { "." !in it && it != "empty" && it !in pigments }
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
