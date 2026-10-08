import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("machineengine.platform")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml.paper)
}

description = "Mekanized: Mekanism-style machines for MachineEngine (textures, models and recipes from Mekanism, MIT)."

dependencies {
    // A content addon: MachineEngine's public API only (D27).
    compileOnly(project(":api"))
    // Its own relocated copy (D35): command classes are generic in the plugin, so not shared through api.
    bundledLibraries(libs.paper.dispatch)

    // Headless tests of the content (machines, recipes, tags), no server. The harness exposes
    // engine internals; that is allowed in tests (D28).
    testImplementation(project(":test"))
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

// The plugin jar is the shaded one; the plain jar keeps a classifier so the two never overwrite each other.
tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "Mekanized-${project.version}.jar"
    relocate("fr.robie.paperdispatch", "fr.robie.machineengine.mekanized.libs.paperdispatch")
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
