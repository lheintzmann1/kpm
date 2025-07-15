import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

group = "org.luhe"
version = "0.0.0.19"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.jetbrains.kotlin:kotlin-build-tools-api:2.2.0")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.register("generateVersionProperties") {
    doLast {
        val versionFile = file("src/main/resources/version.properties")
        versionFile.writeText("version=$version\n")
        println("Generated version.properties with version $version")
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

tasks.jar.configure {
    manifest {
        attributes(mapOf("Main-Class" to "kpm.AppKt"))
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+MultiDollarInterpolation"))
}