import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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
version = "connect-mock-2"

val arrowVersion = "0.11.0"
val hopliteVersion = "1.3.8"
val ktorVersion = "1.4.1"

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

    compile("ch.qos.logback:logback-classic:1.2.3")

    testImplementation(kotlin("test-junit"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.arrow-kt:arrow-fx")
    implementation ("io.arrow-kt:arrow-syntax")
    implementation("io.arrow-kt:arrow-fx-coroutines")
    implementation("io.arrow-kt:arrow-fx-kotlinx-coroutines")
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-arrow:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-hocon:$hopliteVersion")
    kapt ("io.arrow-kt:arrow-meta:$arrowVersion")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks {
    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to application.mainClassName))
        }
    }
}
