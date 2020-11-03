import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("kapt") version "1.4.10"

    id("java")
    id("com.github.johnrengelman.shadow") version "5.0.0"

    application
}
application {
    mainClassName = "io.fperezp.connect.mock.ServerKt"
}

group = "io.fperezp"
version = "1.0-SNAPSHOT"

val arrowVersion = "0.11.0"
val ktorVersion = "1.4.0"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/kotlin/ktor")
    }
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlinx")
    }
    maven { url = uri("https://dl.bintray.com/arrow-kt/arrow-kt/") }
    maven { url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local/") } // for SNAPSHOT builds

}
dependencies {
    implementation(platform("io.arrow-kt:arrow-stack:$arrowVersion"))

    testImplementation(kotlin("test-junit"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.arrow-kt:arrow-fx")
    implementation ("io.arrow-kt:arrow-syntax")
    kapt ("io.arrow-kt:arrow-meta:$arrowVersion")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf("Main-Class" to application.mainClassName))
    }
}
