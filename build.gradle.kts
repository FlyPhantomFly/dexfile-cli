import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.flyphantomfly"
version = "0.1.7"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.vova7878:DexFile:v1.7.0")
    implementation("io.github.vova7878:Smali:v1.7.0")
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
