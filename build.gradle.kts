import java.net.URI

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.3.10"
    id("io.kotest").version("6.1.5")
}

group = "io.github.tmarsteel.flyingnarrator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = URI("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencies {
    implementation("uk.m0nom:javaapiforkml:3.0.11")
    implementation("org.mozilla:rhino:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.0")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml:3.1.1")
    implementation("tools.jackson.module:jackson-module-jaxb-annotations:3.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.22.1")

    implementation("org.bytedeco:javacv-platform:1.5.11")

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