import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Converts a Mekanism checkout into Mekanized content (MEKANIZED_PLAN.md, Phase 0, K4):
 *
 * - `pack/resourcepack/assets/mekanized/`: textures, models and animations, with the `mekanism` and
 *   `mekanismgenerators` namespaces renamed to `mekanized`;
 * - `pack/configuration/recipes_<group>.yml`: crafting, smelting and blasting recipes for CraftEngine;
 * - `definitions/<name>.yml`: machine recipes (one file per recipe type) and tags as MachineEngine
 *   definitions (ARCHITECTURE.md §29);
 * - `manifest.yml` (the generated files the plugin installs), `LICENSE-MEKANISM.txt` and `import-report.txt`.
 *
 * Recipes for other mods (`neoforge:conditions`) and content outside the plan are skipped and
 * listed in the report. Generated files are build output; overlays live in the module's sources.
 *
 * The output depends on the pinned Mekanism [commit] and on the folders the import reads ([sources]),
 * not on the checkout's `.git` folder, so the task is cacheable.
 */
@CacheableTask
abstract class ImportMekanism : DefaultTask() {
    /** The pinned Mekanism commit: imported content never changes unless this does. */
    @get:Input
    abstract val commit: Property<String>

    /** The checkout (see [sources] for what of it is an input). */
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    /** Only the files of the checkout the import reads (the sparse folders of `fetchMekanism`). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun import() {
        val source = MekanismSource(sourceDirectory.get().asFile)
        val out = outputDirectory.get().asFile
        out.deleteRecursively()
        val report = Report()
        Assets(source, out.resolve("pack/resourcepack/assets/mekanized"), report).copy()
        GearModels(source, out.resolve("pack/resourcepack/assets/mekanized"), report).write()
        val itemTags = Tags(source, out.resolve("definitions"), report).convert()
        val content = MekanismContent(source, out, itemTags, report).also { it.generate() }
        val recipes = Recipes(source, out, report, content.ids.toList(), itemTags).also { it.convert() }
        writeAliases(out.resolve("definitions/vanilla_aliases.yml"), recipes.referencedTags + itemTags.keys, itemTags, report)
        source.resolve("LICENSE").copyTo(out.resolve("LICENSE-MEKANISM.txt"), overwrite = true)
        writeManifest(out)
        out.resolve("import-report.txt").writeText(report.format())
        logger.lifecycle(report.summary())
    }
}
