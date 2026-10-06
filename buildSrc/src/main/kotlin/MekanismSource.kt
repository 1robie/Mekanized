import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/** The one pretty-printing Gson of the import: every JSON file it writes is formatted the same way. */
internal val prettyGson: Gson = GsonBuilder().setPrettyPrinting().create()

/** Mekanism's checkout and the places of it the import reads (the paths are written down once, here). */
internal class MekanismSource(val root: File) {
    /**
     * Asset folders (`textures`, `models`, `blockstates`, `lang`...): Mekanism's own, its datagen output
     * (items, generated models), then the same for Mekanism Generators.
     */
    val assetRoots = listOf(
        "src/main/resources/assets/mekanism",
        "src/datagen/generated/mekanism/assets/mekanism",
        "src/generators/resources/assets/mekanismgenerators",
        "src/datagen/generated/mekanismgenerators/assets/mekanismgenerators",
    )

    fun resolve(relative: String): File = root.resolve(relative)

    /** `<assets>/<relative>` in the first asset folder that has it (null if none does). */
    fun asset(relative: String): File? = assetRoots.map { root.resolve("$it/$relative") }.firstOrNull { it.isFile }

    /** One folder per Mekanism module under `src/datagen/generated`: `data/` (tags, recipes), `assets/`. */
    val datagenModules: List<File> by lazy { root.resolve("src/datagen/generated").listFiles().orEmpty().toList() }

    /** Fluid names of Mekanism's `c:` fluid tags (one tag per fluid, the `gaseous` tag aside). */
    val mekanismFluids: List<String> by lazy {
        datagenModules.flatMap { it.resolve("data/c/tags/fluid").listFiles().orEmpty().toList() }
            .map { it.nameWithoutExtension }.filter { it != "gaseous" }
    }

    /**
     * Mekanism's fluids plus water and lava. Water and lava are MachineEngine's (`machineengine:fluid/<name>`,
     * D29); the others are `mekanized:fluid/<name>`, apart from the chemicals of the same name.
     */
    val fluids: List<String> by lazy { mekanismFluids.plus(listOf("water", "lava")).distinct().sorted() }
}
