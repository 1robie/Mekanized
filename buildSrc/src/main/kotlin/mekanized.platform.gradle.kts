// Paper and CraftEngine on the compile classpath, newest Paper API, and a check that the bytecode only
// uses what the oldest supported Paper version has (MachineEngine's D25, D26). Copied from MachineEngine.
plugins {
    id("mekanized.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val paperApi = libs.findLibrary("paper-api").get()
val craftEngineVersion = providers.gradleProperty("craftengine_version").get()

/** The oldest supported Paper API, with everything it brings (Adventure, Guava...). */
val paperBaseline: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

/**
 * Libraries shaded into a plugin jar and relocated there (D35). They are `implementation` dependencies, and
 * their bytecode is checked against the baseline like ours (verifyBundledPaperBaseline).
 */
val bundledLibraries: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}
configurations.named("implementation") {
    extendsFrom(bundledLibraries)
}

dependencies {
    compileOnlyApi(paperApi)
    // CraftEngine's published POMs pull in its whole runtime; take only the classes we compile against.
    compileOnlyApi("net.momirealms:craft-engine-core:$craftEngineVersion") { isTransitive = false }
    compileOnlyApi("net.momirealms:craft-engine-bukkit:$craftEngineVersion") { isTransitive = false }
    // Headless tests run without a server but still need Adventure Key and friends.
    testImplementation(paperApi)
    paperBaseline(libs.findLibrary("paper-api-baseline").get())
}

val serverPackages = listOf(
    "org/bukkit/", "io/papermc/paper/", "com/destroystokyo/paper/", "org/spigotmc/", "co/aikar/",
    "net/kyori/", "net/md_5/bungee/", "com/mojang/brigadier/",
    "com/google/common/", "com/google/gson/", "org/joml/", "org/yaml/snakeyaml/",
)

val verifyPaperBaseline = tasks.register<VerifyPaperBaseline>("verifyPaperBaseline") {
    description = "Checks that the compiled classes only use API present in the oldest supported Paper version."
    group = "verification"
    classes.from(sourceSets.main.map { it.output.classesDirs })
    baseline.from(paperBaseline)
    checkedPackages.set(serverPackages)
    maxClassVersion.set(65) // Java 21
    report.set(layout.buildDirectory.file("reports/paper-baseline.txt"))
}

// Problems in bundled libraries that our code never reaches, one per line as the report prints them.
val bundledBaselineAllowList = rootProject.layout.projectDirectory.file("gradle/paper-baseline-allowed.txt")

val verifyBundledPaperBaseline = tasks.register<VerifyPaperBaseline>("verifyBundledPaperBaseline") {
    description = "Checks that the bundled libraries only use API present in the oldest supported Paper version, " +
            "except for the problems listed in gradle/paper-baseline-allowed.txt."
    group = "verification"
    classes.from(bundledLibraries)
    baseline.from(paperBaseline)
    checkedPackages.set(serverPackages)
    maxClassVersion.set(65)
    allowed.set(providers.fileContents(bundledBaselineAllowList).asText.map { text ->
        text.lines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
    })
    report.set(layout.buildDirectory.file("reports/paper-baseline-bundled.txt"))
}

tasks.named("check") {
    dependsOn(verifyPaperBaseline, verifyBundledPaperBaseline)
}
