import net.minecrell.pluginyml.paper.PaperPluginDescription
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("mekanized.platform")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml.paper)
    alias(libs.plugins.run.paper)
}

description = "Mekanized: Mekanism-style machines for MachineEngine (textures, models and recipes from Mekanism, MIT)."

dependencies {
    // A content addon: MachineEngine's public API only (MachineEngine's D27).
    compileOnly(libs.machineengine.api)
    // Its own relocated copy (D35): command classes are generic in the plugin, so not shared through api.
    bundledLibraries(libs.paper.dispatch)

    // Headless tests of the content (machines, recipes, tags), no server. The harness exposes
    // engine internals; that is allowed in tests (MachineEngine's D28).
    testImplementation(libs.machineengine.test)
}

// -- Content: src/main/resources/mekanized, kept by hand (D31). Based on Mekanism (MIT) at commit
// 4718860a0c98598cf1b34e9f631b24eaa7c7ff47 (branch 26.1), imported once and edited since.

val contentManifest = tasks.register<ContentManifest>("contentManifest") {
    group = "mekanized"
    description = "Lists the content files the plugin installs (mekanized/manifest.yml)."
    contentDirectory = layout.projectDirectory.dir("src/main/resources/mekanized")
    outputDirectory = layout.buildDirectory.dir("generated/manifest")
}

tasks.processResources {
    from(contentManifest)
}

paper {
    name = "Mekanized"
    main = "fr.robie.mekanized.MekanizedPlugin"
    version = project.version.toString()
    description = "Mekanism-style machines for MachineEngine. Inspired by Mekanism; textures, models and recipes " +
            "from Mekanism by Aidan C. Brady (MIT, see LICENSE-MEKANISM.txt). Not affiliated with Mekanism."
    apiVersion = "1.20"
    foliaSupported = true
    serverDependencies {
        register("MachineEngine") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("CraftEngine") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
    permissions {
        register("mekanized.radiation.dose") {
            description = "See your own radiation dose and the radiation where you stand (/mekanized radiation dose|level)"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.TRUE
        }
    }
}

// The plugin jar is the shaded one; the plain jar keeps a classifier so the two never overwrite each other.
tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "Mekanized-${project.version}.jar"
    relocate("fr.robie.paperdispatch", "fr.robie.mekanized.libs.paperdispatch")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

val verifyRelocation = tasks.register<VerifyRelocation>("verifyRelocation") {
    description = "Checks that the Mekanized jar holds no unrelocated bundled library (D35)."
    group = "verification"
    jar = tasks.shadowJar.flatMap { it.archiveFile }
    packages = listOf("fr/robie/paperdispatch/")
    report = layout.buildDirectory.file("reports/relocation.txt")
}

tasks.check {
    dependsOn(verifyRelocation)
}

// ---------------------------------------------------------------------------------------------
// Dev servers: Mekanized with MachineEngine, CraftEngine and zMenu (Paper 1.20.6 and 26.3, Folia
// 1.20.6 and 26.2, like MachineEngine). MachineEngine's plugin jar is built from the checkout next to
// this one (settings.gradle.kts), so run the servers with that checkout present.
// ---------------------------------------------------------------------------------------------

/** MachineEngine's shaded plugin jar, from the local checkout (its shadowRuntimeElements variant). */
val devEngine: Configuration by configurations.creating {
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
}

dependencies {
    devEngine(libs.machineengine.plugin)
}

val craftEngineRuntime = providers.gradleProperty("craftengine_runtime").get()
val craftEngineDevDir = layout.projectDirectory.dir(".craftengine")

val buildCraftEngineDev = tasks.register<BuildCraftEngineDev>("buildCraftEngineDev") {
    group = "run dev server"
    description = "Builds the CraftEngine paper plugin from craftengine_dev_ref (use -PrefreshCraftEngine to rebuild)."
    repositoryUrl = "https://github.com/Xiao-MoMi/craft-engine.git"
    ref = providers.gradleProperty("craftengine_dev_ref")
    refresh = providers.gradleProperty("refreshCraftEngine").isPresent
    buildHeap = providers.gradleProperty("craftengine_build_heap").orElse("4G")
    sourceDirectory = craftEngineDevDir.dir("source")
    outputJar = craftEngineDevDir.file("craftengine-dev.jar")
}

fun registerDevServer(minecraftVersion: String, javaVersion: Int, folia: Boolean = false) {
    val software = if (folia) "Folia" else "Paper"
    tasks.register<RunServer>("run$software-$minecraftVersion") {
        group = "run dev server"
        description = "Runs a $software $minecraftVersion dev server with Mekanized, MachineEngine and CraftEngine."
        devServer(minecraftVersion, javaVersion, folia)
    }
}

/** What every dev server has: its folder in run/, the plugins and the Java version. */
fun RunServer.devServer(minecraftVersion: String, javaVersion: Int, folia: Boolean) {
    val software = if (folia) "Folia" else "Paper"
    minecraftVersion(minecraftVersion)
    runDirectory = layout.projectDirectory.dir("run/${software.lowercase()}-$minecraftVersion")
    if (folia) {
        downloadsApiService.convention(xyz.jpenilla.runtask.service.DownloadsAPIService.folia(project))
        pluginDownloadService.convention(xyz.jpenilla.runtask.pluginsapi.PluginDownloadService.paper(project))
    }
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
    pluginJars.from(devEngine)
    when (craftEngineRuntime) {
        "dev" -> pluginJars.from(buildCraftEngineDev.flatMap { it.outputJar })
        "modrinth" -> downloadPlugins {
            modrinth("craftengine", providers.gradleProperty("craftengine_modrinth_version").get())
        }
        else -> error("Unknown craftengine_runtime '$craftEngineRuntime' (expected dev or modrinth)")
    }
    if (providers.gradleProperty("withZMenu").orElse("true").get() == "true") {
        downloadPlugins { modrinth("zmenu", libs.versions.zmenu.get()) }
    }
    jvmArgs("-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
}

// ---------------------------------------------------------------------------------------------
// ceSnapshot (MachineEngine's docs/CE_TEMPLATES_PLAN.md, Stage 0): what CraftEngine makes of every
// Mekanized block and item, read on the Paper 26.3 dev server and compared with ce-snapshot.json.
// -PacceptCeSnapshot replaces the reference with this run's result.
// ---------------------------------------------------------------------------------------------

/** A dev server that stops by itself once started ([StopWhenDone]) instead of reading the console. */
abstract class SelfStoppingServer : RunServer() {
    override fun setStandardInput(inputStream: java.io.InputStream): JavaExec =
        super.setStandardInput(StopWhenDone(runDirectory.get().asFile.resolve("logs/latest.log")))
}

val snapshotRun = layout.projectDirectory.dir("run/paper-26.3")
val snapshotInstalled = snapshotRun.dir("plugins/CraftEngine/resources/mekanized/configuration")
val snapshotBackup = layout.buildDirectory.dir("ce-snapshot/installed")

val ceSnapshotPrepare = tasks.register<CeSnapshotPrepare>("ceSnapshotPrepare") {
    sourceConfiguration = layout.projectDirectory.dir("src/main/resources/mekanized/pack/configuration")
    runDirectory = snapshotRun
    installedConfiguration = snapshotInstalled
    backupDirectory = snapshotBackup
}

val ceSnapshotCollect = tasks.register<CeSnapshotCollect>("ceSnapshotCollect") {
    runDirectory = snapshotRun
    installedConfiguration = snapshotInstalled
    backupDirectory = snapshotBackup
    output = layout.buildDirectory.file("ce-snapshot/snapshot.json")
    reference = layout.projectDirectory.file("ce-snapshot.json")
    accept = providers.gradleProperty("acceptCeSnapshot").isPresent
}

tasks.register<SelfStoppingServer>("ceSnapshot") {
    group = "run dev server"
    description = "Checks that Mekanized's CraftEngine blocks and items expand as in ce-snapshot.json."
    devServer("26.3", javaVersion = 25, folia = false)
    dependsOn(ceSnapshotPrepare)
    finalizedBy(ceSnapshotCollect)
}

registerDevServer("1.20.6", javaVersion = 21)
registerDevServer("26.3", javaVersion = 25)
registerDevServer("1.20.6", javaVersion = 21, folia = true)
registerDevServer("26.2", javaVersion = 25, folia = true)

// The stock runServer task has no version configured; point people at the explicit ones.
tasks.named<RunServer>("runServer") {
    enabled = false
}
