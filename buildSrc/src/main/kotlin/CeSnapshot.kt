import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.TreeMap

/*
 * ceSnapshot (docs/CE_TEMPLATES_PLAN.md, Stage 0): what CraftEngine makes of every Mekanized block and
 * item, read from a dev server. Unit tests cannot load CraftEngine, so this is how moving the content to
 * templates and config factories is checked to change nothing.
 *
 * ceSnapshotPrepare writes the content files into the dev server's Mekanized pack with `debug: true` and
 * `ce_snapshot_id: <id>` added to every block and item (CraftEngine then logs each one fully expanded);
 * the server starts and stops by itself (StopWhenDone); ceSnapshotCollect puts the installed files back,
 * reads the log and compares the result with the reference.
 */

/** Adds the snapshot markers to every block and item entry of a CraftEngine configuration file. */
object SnapshotMarkers {
    const val ID_KEY = "ce_snapshot_id"
    private val ENTRY_SECTIONS = setOf("items", "item", "blocks", "block")
    private val BLUEPRINTS = setOf("blueprint", "prototype", "schema")
    /** A key line, once its comment is cut; keys may hold `:` (ids) and `#` (`config_factory#name`). */
    private val KEY = Regex("""^( *)('[^']*'|"[^"]*"|[^\s'"-].*?):(\s.*)?$""")
    private val COMMENT = Regex("""\s+#.*$""")

    fun mark(file: String, lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size * 2)
        // (indent, key) of the keys enclosing the current line.
        val stack = ArrayDeque<Pair<Int, String>>()
        for ((index, line) in lines.withIndex()) {
            out += line
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) continue
            val match = KEY.find(line.replace(COMMENT, "")) ?: continue
            val indent = match.groupValues[1].length
            val key = match.groupValues[2].trim().removeSurrounding("'").removeSurrounding("\"")
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
            val parent = stack.lastOrNull()
            val grandParent = stack.getOrNull(stack.size - 2)
            stack.addLast(indent to key)
            val isEntry = parent != null && parent.second in ENTRY_SECTIONS &&
                    (grandParent == null && parent.first == 0 || grandParent != null && grandParent.second in BLUEPRINTS)
            if (!isEntry) continue
            if (match.groupValues[3].isNotBlank()) {
                throw GradleException("$file:${index + 1}: write '$key' as a block mapping, not on one line")
            }
            val child = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }
            val childIndent = child?.let { it.length - it.trimStart().length } ?: -1
            if (childIndent <= indent) throw GradleException("$file:${index + 1}: '$key' has no content")
            val pad = " ".repeat(childIndent)
            out += "${pad}debug: true"
            // An item and its block share their id: the section tells them apart.
            val section = if (parent!!.second.endsWith("s")) parent.second else parent.second + "s"
            out += "${pad}$ID_KEY: '$section ${key.replace("'", "''")}'"
        }
        return out
    }
}

/** Whether the dev server in [runDirectory] is running (its world is locked). */
private fun serverRunning(runDirectory: File): Boolean {
    val lock = runDirectory.resolve("world/session.lock")
    if (!lock.exists()) return false
    return try {
        RandomAccessFile(lock, "rw").use { file ->
            val held = file.channel.tryLock() ?: return true
            held.release()
            false
        }
    } catch (e: Exception) {
        true
    }
}

/** Puts back the installed content files saved by [CeSnapshotPrepare], if any. */
private fun restoreInstalled(backup: File, installed: File) {
    if (!backup.isDirectory) return
    installed.listFiles { f -> f.name.endsWith(".yml") }?.forEach { it.delete() }
    backup.copyRecursively(installed, overwrite = true)
    backup.deleteRecursively()
}

abstract class CeSnapshotPrepare : DefaultTask() {
    /** Mekanized's content files (pack/configuration in its resources). */
    @get:Internal
    abstract val sourceConfiguration: DirectoryProperty

    @get:Internal
    abstract val runDirectory: DirectoryProperty

    /** The dev server's installed copy of the content files. */
    @get:Internal
    abstract val installedConfiguration: DirectoryProperty

    @get:Internal
    abstract val backupDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        if (serverRunning(runDirectory.get().asFile)) {
            throw GradleException("The dev server in ${runDirectory.get().asFile} is running: stop it first")
        }
        val installed = installedConfiguration.get().asFile
        val backup = backupDirectory.get().asFile
        restoreInstalled(backup, installed) // left over by an interrupted run
        backup.mkdirs()
        installed.mkdirs()
        installed.listFiles { f -> f.name.endsWith(".yml") }?.forEach { it.copyTo(backup.resolve(it.name)) }
        var marked = 0
        sourceConfiguration.get().asFile.listFiles { f -> f.name.endsWith(".yml") }!!.sortedBy { it.name }.forEach { file ->
            val lines = file.readLines(Charsets.UTF_8)
            val result = SnapshotMarkers.mark(file.name, lines)
            marked += (result.size - lines.size) / 2
            installed.resolve(file.name).writeText(result.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        }
        logger.lifecycle("ceSnapshot: marked $marked entries in ${installed}")
    }
}

abstract class CeSnapshotCollect : DefaultTask() {
    @get:Internal
    abstract val runDirectory: DirectoryProperty

    @get:Internal
    abstract val installedConfiguration: DirectoryProperty

    @get:Internal
    abstract val backupDirectory: DirectoryProperty

    /** The snapshot of this run. */
    @get:Internal
    abstract val output: RegularFileProperty

    /** The snapshot every run must match (written by the first run, or with -PacceptCeSnapshot). */
    @get:Internal
    abstract val reference: RegularFileProperty

    @get:Input
    abstract val accept: Property<Boolean>

    @TaskAction
    fun collect() {
        val run = runDirectory.get().asFile
        restoreInstalled(backupDirectory.get().asFile, installedConfiguration.get().asFile)

        val entries = TreeMap<String, TreeMap<String, Any?>>()
        val warnings = sortedSetOf<String>()
        val prefix = Regex("""^\[\d\d:\d\d:\d\d] \[[^\]]+/(INFO|WARN|ERROR)]: """)
        run.resolve("logs/latest.log").readLines(Charsets.UTF_8).forEach { line ->
            val level = prefix.find(line)?.groupValues?.get(1)
            if (line.contains("\"${SnapshotMarkers.ID_KEY}\"")) {
                @Suppress("UNCHECKED_CAST")
                val json = JsonSlurper().parseText(line.substring(line.indexOf('{'))) as MutableMap<String, Any?>
                val (section, id) = (json.remove(SnapshotMarkers.ID_KEY) as String).split(' ', limit = 2)
                json.remove("debug")
                if (entries.getOrPut(section) { TreeMap() }.put(id, json) != null) {
                    throw GradleException("ceSnapshot: $section $id was logged twice")
                }
            } else if (level == "WARN" || level == "ERROR") {
                val text = line.replace(prefix, "")
                // "… was edited: kept as is" is Mekanized seeing the marked files this run installed.
                if (text.contains("mekanized", true) && !text.contains("was edited: kept as is")) warnings += text
            }
        }
        if (entries.isEmpty()) throw GradleException("ceSnapshot: no entry in ${run.resolve("logs/latest.log")}; did the server start?")

        val snapshot = TreeMap<String, Any?>()
        entries.forEach { (section, values) -> snapshot[section] = values }
        snapshot["block_states"] = cache(run, "custom_block_states.json")
        snapshot["visual_states"] = cache(run, "visual_block_states.json")
        snapshot["warnings"] = warnings.toList()
        val text = JsonOutput.prettyPrint(JsonOutput.toJson(sorted(snapshot))) + "\n"
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(text, Charsets.UTF_8)
        logger.lifecycle("ceSnapshot: ${entries.map { "${it.value.size} ${it.key}" }.joinToString()}, ${warnings.size} warnings -> ${output.get().asFile}")

        val referenceFile = reference.get().asFile
        if (!referenceFile.exists() || accept.get()) {
            referenceFile.writeText(text, Charsets.UTF_8)
            logger.lifecycle("ceSnapshot: reference written to $referenceFile")
            return
        }
        @Suppress("UNCHECKED_CAST")
        val expected = JsonSlurper().parse(referenceFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val actual = JsonSlurper().parseText(text) as Map<String, Any?>
        val differences = ArrayList<String>()
        diff("", expected, actual, differences)
        if (differences.isNotEmpty()) {
            differences.take(40).forEach { logger.error("  $it") }
            if (differences.size > 40) logger.error("  … ${differences.size - 40} more")
            throw GradleException("ceSnapshot: ${differences.size} differences with $referenceFile (see above)")
        }
        logger.lifecycle("ceSnapshot: matches the reference")
    }

    /** The Mekanized entries of one of CraftEngine's state caches. */
    private fun cache(run: File, name: String): Map<String, Any?> {
        val file = run.resolve("plugins/CraftEngine/cache/$name")
        if (!file.exists()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        val all = JsonSlurper().parse(file) as Map<String, Any?>
        return all.filterKeys { it.contains("mekanized") }
    }

    private fun sorted(value: Any?): Any? = when (value) {
        is Map<*, *> -> TreeMap<String, Any?>().apply { value.forEach { (k, v) -> put(k.toString(), sorted(v)) } }
        is List<*> -> value.map { sorted(it) }
        else -> value
    }

    private fun diff(path: String, expected: Any?, actual: Any?, out: MutableList<String>) {
        if (expected is Map<*, *> && actual is Map<*, *>) {
            for (key in (expected.keys + actual.keys).map { it.toString() }.toSortedSet()) {
                val at = if (path.isEmpty()) key else "$path > $key"
                when {
                    !actual.containsKey(key) -> out += "missing: $at"
                    !expected.containsKey(key) -> out += "new: $at = ${short(actual[key])}"
                    else -> diff(at, expected[key], actual[key], out)
                }
            }
        } else if (expected != actual) {
            out += "changed: $path: ${short(expected)} -> ${short(actual)}"
        }
    }

    private fun short(value: Any?): String = JsonOutput.toJson(value).let { if (it.length > 160) it.take(160) + "…" else it }
}

/**
 * The console of a dev server that stops by itself: it types `stop` once the server has started
 * ("Done (" in latest.log) and the log has been quiet for a few seconds (CraftEngine loads its
 * resources after the start), or after [timeoutMillis].
 */
class StopWhenDone(private val log: File, private val timeoutMillis: Long = 10 * 60 * 1000) : InputStream() {
    private val started = System.currentTimeMillis()
    private var command: ByteArray? = null
    private var position = 0

    override fun read(): Int {
        val bytes = command ?: waitForStart().also { command = it }
        return if (position < bytes.size) bytes[position++].toInt() else -1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val first = read()
        if (first < 0) return -1
        buffer[offset] = first.toByte()
        return 1
    }

    private fun waitForStart(): ByteArray {
        var done = false
        var lastSize = -1L
        var quietSince = 0L
        while (System.currentTimeMillis() - started < timeoutMillis) {
            Thread.sleep(1000)
            if (!log.exists() || log.lastModified() < started) continue
            if (!done) done = log.readText(Charsets.UTF_8).contains("Done (")
            if (!done) continue
            val size = log.length()
            if (size != lastSize) {
                lastSize = size
                quietSince = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - quietSince >= 8000) {
                break
            }
        }
        return "stop\n".toByteArray()
    }
}
