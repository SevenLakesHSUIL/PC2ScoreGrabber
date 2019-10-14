import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    id("com.diffplug.gradle.spotless") version "3.25.0"
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

version = "1.0"

repositories {
    mavenCentral()
    maven {
        setUrl("https://dl.bintray.com/kotlin/exposed")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    // PC2 Deps
    implementation(files("lib/pc2.jar"))
    implementation("com.fasterxml.jackson.core:jackson-core:2.5.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.5.4")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.5.4")

    // DB Deps
    implementation("com.h2database:h2:1.4.199")
    implementation("com.vladsch.kotlin-jdbc:kotlin-jdbc:0.4.4")
}

spotless {
    kotlin {
        ktlint("0.33.0")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    kotlinGradle {
        ktlint("0.33.0")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes.apply {
            put("Main-Class", "ProblemScorerKt")
        }
    }
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "5.5.1"
}
