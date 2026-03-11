plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.3.10"
    id("io.kotest").version("6.1.5")
}

group = "io.github.tmarsteel.flyingnarrator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("uk.m0nom:javaapiforkml:3.0.11")
    implementation("org.mozilla:rhino:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:6.1.5")
    testImplementation("io.kotest:kotest-framework-engine:6.1.5")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.5")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}