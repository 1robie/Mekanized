import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes `mekanized/manifest.yml`, the list of content files the Mekanized plugin installs: CraftEngine
 * configuration files (`pack:`, relative to the pack folder) and MachineEngine definitions
 * (`definitions:`), read from the `.yml` files of [contentDirectory]. A file added there is installed
 * without editing a list by hand. Textures, models and the license are not listed: they are always copied.
 */
@CacheableTask
abstract class ContentManifest : DefaultTask() {
    /** The plugin's content folder (`src/main/resources/mekanized`). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDirectory: DirectoryProperty

    /** Receives `mekanized/manifest.yml`, added to the jar's resources. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val content = contentDirectory.get().asFile
        fun yamlFiles(folder: String): List<String> = content.resolve(folder).listFiles { file -> file.isFile && file.extension == "yml" }
            .orEmpty().map { it.name }.sorted()
        val manifest = outputDirectory.get().asFile.resolve("mekanized/manifest.yml")
        manifest.parentFile.mkdirs()
        manifest.writeText(buildString {
            appendLine("# The content files the Mekanized plugin installs, listed by the build (do not edit).")
            appendLine("pack:")
            yamlFiles("pack/configuration").forEach { appendLine("- configuration/$it") }
            appendLine("definitions:")
            yamlFiles("definitions").forEach { appendLine("- $it") }
        })
    }
}
