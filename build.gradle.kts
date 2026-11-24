import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.flyphantomfly"
version = "0.2.0"

repositories {
    mavenCentral()
}

dependencies {
    // Vendored upstream `main` @ b3fd141 (unreleased: 20bc/40sc reader, smali printer) —
    // see vendor/README.md; revert to Maven Central pins when upstream tags v1.8.0+.
    implementation(files("vendor/DexFile-b3fd141.jar", "vendor/Smali-b3fd141.jar"))
    // DexFile's runtime dependency — with files() deps the vendored POM isn't read,
    // so AndroidMisc must be declared explicitly. The vendored Smali module's generated
    // parser needs the ANTLR runtime the same way (upstream pins 4.13.2).
    implementation("io.github.vova7878:AndroidMisc:v1.0.1")
    implementation("org.antlr:antlr4-runtime:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// DexFile upstream pins a Java 17 *toolchain* with no auto-provisioning, which
// breaks builds on machines without a provisioned JDK 17 (see the integration
// review, §2.2). This CLI instead compiles with whatever JDK runs Gradle and
// targets release 17 bytecode — same result, no toolchain resolution.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar>().configureEach {
    archiveBaseName = "dexfile-cli"
    archiveClassifier = ""
    archiveVersion = ""
    manifest {
        attributes["Main-Class"] = "io.github.flyphantomfly.dexfilecli.Main"
    }
    mergeServiceFiles()
}
