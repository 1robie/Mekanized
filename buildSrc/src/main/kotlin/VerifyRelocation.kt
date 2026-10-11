import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * D35: bundled libraries are relocated. Checks that the shaded [jar] has no entry left under one of the
 * original [packages] (internal names such as `com/tcoded/folialib/`): two plugins that bundle the same
 * unrelocated library can load each other's classes.
 */
@CacheableTask
abstract class VerifyRelocation : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:Input
    abstract val packages: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val prefixes = packages.get()
        val left = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { name -> prefixes.any { name.startsWith(it) } }.toList()
        }
        val output = report.get().asFile
        if (left.isEmpty()) {
            output.writeText("No entries under ${prefixes.joinToString()}.\n")
            return
        }
        output.writeText(left.joinToString("\n", postfix = "\n"))
        throw GradleException(
            "${left.size} entr${if (left.size == 1) "y is" else "ies are"} not relocated in ${jar.get().asFile.name} (D35), e.g. ${left.first()}. " +
                    "Add a relocate(...) to the shadowJar task."
        )
    }
}
