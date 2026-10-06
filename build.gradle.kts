import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("machineengine.platform")
    alias(libs.plugins.plugin.yml.paper)
}

description = "Mekanized: Mekanism-style machines for MachineEngine (textures, models and recipes from Mekanism, MIT)."

dependencies {
    // A content addon: MachineEngine's API (plus the Paper module for item conversion until
    // RESTRUCTURE_PLAN.md R7 moves what it needs into the API).
    compileOnly(project(":machineengine-api"))
    compileOnly(project(":machineengine-paper"))

    // Headless tests of the imported content (machines, recipes, tags), no server.
    testImplementation(project(":machineengine-test"))
    testImplementation(project(":machineengine-paper"))
}

// -- Mekanism import (MEKANIZED_PLAN.md Phase 0, K4) -------------------------------------------------

val fetchMekanism = tasks.register<FetchMekanism>("fetchMekanism") {
    group = "mekanized"
    description = "Checks out the pinned Mekanism commit (only the folders Mekanized imports)."
    repositoryUrl = providers.gradleProperty("mekanism_repository")
    commit = providers.gradleProperty("mekanism_commit")
    paths = listOf(
        "/LICENSE",
        "/src/main/resources/assets/mekanism/",
        "/src/generators/resources/assets/mekanismgenerators/",
        "/src/datagen/generated/mekanism/",
        "/src/datagen/generated/mekanismgenerators/",
        // Java entity models of the gear (jetpack, disassembler...), turned into item models.
        "/src/main/java/mekanism/client/model/",
    )
    sourceDirectory = rootProject.layout.projectDirectory.dir(".mekanism/source")
}

val importMekanism = tasks.register<ImportMekanism>("importMekanism") {
    group = "mekanized"
    description = "Converts the Mekanism checkout into Mekanized assets, recipes and tags."
    dependsOn(fetchMekanism)
    commit = fetchMekanism.flatMap { it.commit }
    sourceDirectory = fetchMekanism.flatMap { it.sourceDirectory }
    // Only the folders fetchMekanism checks out (and the import reads), never the checkout's .git folder.
    sources.from(fetchMekanism.flatMap { fetch ->
        fetch.sourceDirectory.zip(fetch.paths) { directory, paths ->
            objects.fileTree().setDir(directory).include(paths.map { it.removePrefix("/") + if (it.endsWith("/")) "**" else "" })
        }
    })
    outputDirectory = layout.buildDirectory.dir("generated/mekanized")
}

tasks.processResources {
    // Generated content ships under mekanized/ in the jar; the plugin installs it on first start.
    from(importMekanism) {
        into("mekanized")
        exclude("import-report.txt")
    }
}

paper {
    name = "Mekanized"
    main = "fr.robie.machineengine.mekanized.MekanizedPlugin"
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
}

tasks.jar {
    archiveFileName = "Mekanized-${project.version}.jar"
}
