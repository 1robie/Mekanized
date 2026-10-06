import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Checks out the parts of Mekanism that Mekanized imports (MEKANIZED_PLAN.md, Phase 0): a shallow,
 * sparse, blob-filtered clone of one pinned commit, so only a few MB are downloaded. Skipped when
 * the checkout is already at [commit].
 */
abstract class FetchMekanism : DefaultTask() {
    @get:Input
    abstract val repositoryUrl: Property<String>

    /** Full commit hash; imported content never changes unless this does. */
    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val paths: ListProperty<String>

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun fetch() {
        val source = sourceDirectory.get().asFile
        // Gradle cannot tell this task is up to date (it has no declared outputs), so it checks itself:
        // the pinned commit already checked out with the same folders means no network at all.
        val sparse = source.resolve(".git/info/sparse-checkout")
        if (currentCommit() == commit.get() && sparse.isFile && sparse.readLines().filter { it.isNotBlank() } == paths.get()) {
            logger.info("Mekanism ${commit.get().take(10)} already checked out")
            return
        }
        if (!source.resolve(".git").isDirectory) {
            source.mkdirs()
            git(source, "init", "--quiet")
            git(source, "remote", "add", "origin", repositoryUrl.get())
            git(source, "config", "core.sparseCheckout", "true")
        }
        git(source, "sparse-checkout", "set", "--no-cone", *paths.get().toTypedArray())
        git(source, "fetch", "--quiet", "--depth", "1", "--filter=blob:none", "origin", commit.get())
        git(source, "checkout", "--quiet", "--force", "FETCH_HEAD")
        logger.lifecycle("Mekanism ${commit.get().take(10)} checked out in $source")
    }

    private fun currentCommit(): String? {
        val source = sourceDirectory.get().asFile
        if (!source.resolve(".git").isDirectory) return null
        val out = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir = source
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = out
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) out.toString().trim() else null
    }

    private fun git(workingDirectory: File, vararg args: String) {
        execOperations.exec {
            workingDir = workingDirectory
            // Sparse paths start with "/": keep Git for Windows' MSYS layer from rewriting them.
            environment("MSYS_NO_PATHCONV", "1")
            commandLine(listOf("git", "-c", "core.longpaths=true") + args)
        }
    }
}
