// Shared Java setup (copied from MachineEngine's buildSrc).
plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
    // Paper 26.x is published for Java 25 only, while our bytecode stays Java 21 (D25): JDK 25's javac
    // reads the Java 25 API classes fine, so do not let Gradle reject them by JVM version.
    disableAutoTargetJvm()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Bytecode floor: Java 21 (Minecraft 1.20.6, CraftEngine). nms:v26_* raise it to 25.
    options.release = 21
    // -classfile: warnings about attributes inside third-party jars (JOML via Paper), nothing to fix here.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Xlint:-classfile"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    // javadoc fails on modules that only contain package-info.java so far.
    onlyIf("module has classes to document") { task ->
        !(task as Javadoc).source.filter { it.name != "package-info.java" }.isEmpty
    }
}

dependencies {
    compileOnlyApi(libs.findLibrary("jetbrains-annotations").get())
    testCompileOnly(libs.findLibrary("jetbrains-annotations").get())

    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
