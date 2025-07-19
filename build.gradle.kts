plugins {
    `maven-publish`
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    id("org.springframework.boot") version "3.4.0" apply false
    id("net.researchgate.release") version "3.0.2"
    id("io.spring.dependency-management") version "1.1.6"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/bible-game/common")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/bible-game/config")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(rootProject.libs.bundles.common)
        implementation(rootProject.libs.bundles.config)
        implementation(rootProject.libs.bundles.core)
        implementation(rootProject.libs.bundles.data)
        implementation(rootProject.libs.bundles.database)
        implementation(rootProject.libs.bundles.integration)
        implementation(rootProject.libs.bundles.jwt)
        implementation(rootProject.libs.bundles.kotlin)
        implementation(rootProject.libs.bundles.spring)
        implementation(rootProject.libs.bundles.security)
        implementation(rootProject.libs.bundles.test)
    }
}

tasks.register("printTagVersion") {
    doLast {
        println(project.version.toString().split("-")[0])
    }
}