plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // verifyPaperBaseline (D25): reads compiled classes and the 1.20.6 Paper API.
    implementation(libs.asm)
}
