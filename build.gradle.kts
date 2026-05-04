import java.net.URI

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.3.10"
    id("io.kotest").version("6.1.5")
    id("com.google.protobuf").version("0.10.0")
}

group = "io.github.tmarsteel.flyingnarrator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = URI("https://central.sonatype.com/repository/maven-snapshots/")
    }
    gradlePluginPortal()
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
    implementation("com.google.protobuf:protobuf-kotlin:4.31.1")
    implementation("club.minnced:opus-java-api:1.1.1")
    implementation("net.java.dev.jna:jna:4.4.0")
    implementation("org.gagravarr:vorbis-java-core:0.8")

    implementation("com.formdev:flatlaf:3.7.1")
    implementation("io.github.fenrur:signal-jvm:3.0.1")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:6.1.5")
    testImplementation("io.kotest:kotest-framework-engine:6.1.5")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.5")
}

sourceSets {
    main {
        proto {
            srcDir("nefsedit-cli/nefsedit-cli/protobuf")
            srcDir("src/main/kotlin/io/github/tmarsteel/flyingnarrator/dirtrally2/gamemodels")
        }
        resources {
            srcDir("build/native-libs")
        }
    }
}

protobuf {
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:4.29.5"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin").apply {
                    option("")
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}