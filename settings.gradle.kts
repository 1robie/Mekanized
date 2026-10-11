rootProject.name = "mekanized"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.momirealms.net/snapshots/") {
            mavenContent { snapshotsOnly() }
        }
        maven("https://repo.momirealms.net/releases/") {
            mavenContent { releasesOnly() }
        }
        // MachineEngine (com.github.1robie.MachineEngine) and Paper-Dispatch.
        maven("https://jitpack.io") {
            mavenContent {
                includeGroup("com.github.1robie.MachineEngine")
                includeGroup("com.github.1robie.paper-dispatch")
            }
        }
        // MachineEngine's own dependencies, reached through machineengine-test.
        maven("https://repo.groupez.dev/releases") {
            mavenContent { includeGroup("fr.maxlego08.menu") }
        }
        maven("https://repo.tcoded.com/releases") {
            mavenContent { includeGroup("com.tcoded") }
        }
    }
}

// A MachineEngine checkout next to this one (or at -Pmachineengine_dir) replaces the published
// artifacts, so an engine change is picked up without a release. -PlocalEngine=false uses the
// published version (gradle/libs.versions.toml) even when the checkout is there; CI has none.
val engineDir = file(providers.gradleProperty("machineengine_dir").orElse("../MachineEngine").get())
val localEngine = providers.gradleProperty("localEngine").orElse("true").get() == "true"
if (localEngine && engineDir.resolve("settings.gradle.kts").isFile) {
    includeBuild(engineDir) {
        dependencySubstitution {
            substitute(module("com.github.1robie.MachineEngine:machineengine-api")).using(project(":api"))
            substitute(module("com.github.1robie.MachineEngine:machineengine-test")).using(project(":test"))
            substitute(module("com.github.1robie.MachineEngine:machineengine-plugin")).using(project(":plugin"))
        }
    }
}
