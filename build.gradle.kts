plugins {
    kotlin("jvm") version "2.5.0-Beta2"
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenLocal() // net.azisaba:CrackShot (./gradlew publishToMavenLocal in ../CrackShot)
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.lumine.io/repository/maven-public/") // MythicMobs
    maven("https://jitpack.io") // VaultAPI
    maven("https://maven.azisaba.net/release") // MMLuck
    maven("https://repo.azisaba.net/repository/maven-public/") // Rarity API
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.14.0") { isTransitive = false }
    compileOnly("net.azisaba:CrackShot:1.0.3") { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("com.github.Mori01231:MMLuck:1.8.1+1.21.11") { isTransitive = false }
    compileOnly("net.azisaba.rarity:api:2.1.1") { isTransitive = false }
    compileOnly("xyz.acrylicstyle:StorageBox:1.6.5+1.21.11") { isTransitive = false }
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
