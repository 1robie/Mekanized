import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Builds the CraftEngine paper plugin jar from a git ref (the `dev` branch by default) so dev
 * servers run the same CraftEngine snapshot MachineEngine compiles against (D7).
 *
 * Skipped when [outputJar] already exists unless [refresh] is set (`-PrefreshCraftEngine`).
 */
abstract class BuildCraftEngineDev : DefaultTask() {
    @get:Input
    abstract val repositoryUrl: Property<String>

    @get:Input
    abstract val ref: Property<String>

    @get:Input
    abstract val refresh: Property<Boolean>

    /** Max heap for CraftEngine's own Gradle build (its gradle.properties asks for 16G). */
    @get:Input
    abstract val buildHeap: Property<String>

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        outputs.upToDateWhen { !refresh.get() && outputJar.get().asFile.exists() }
    }

    @TaskAction
    fun build() {
        val source = sourceDirectory.get().asFile
        if (source.resolve(".git").isDirectory) {
            git(source, "fetch", "--depth", "1", "origin", ref.get())
            git(source, "checkout", "--force", "FETCH_HEAD")
        } else {
            source.parentFile.mkdirs()
            git(null, "clone", "--depth", "1", "--branch", ref.get(), repositoryUrl.get(), source.absolutePath)
        }

        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val wrapper = source.resolve(if (windows) "gradlew.bat" else "gradlew")
        if (!windows) wrapper.setExecutable(true)
        execOperations.exec {
            workingDir = source
            commandLine(wrapper.absolutePath, ":bukkit:paper-loader:shadowJar", "--no-configuration-cache",
                "-Dorg.gradle.jvmargs=-Xmx${buildHeap.get()}")
        }

        val built = source.resolve("target").listFiles { file: File ->
            file.name.startsWith("craft-engine-paper-plugin-") && file.name.endsWith(".jar")
        }?.maxByOrNull { it.lastModified() } ?: error("CraftEngine build produced no paper plugin jar in ${source.resolve("target")}")
        built.copyTo(outputJar.get().asFile, overwrite = true)
        logger.lifecycle("CraftEngine ${ref.get()} built: ${built.name}")
    }

    private fun git(workingDirectory: File?, vararg args: String) {
        execOperations.exec {
            workingDirectory?.let { workingDir = it }
            commandLine(listOf("git", "-c", "core.longpaths=true") + args)
        }
    }
}
