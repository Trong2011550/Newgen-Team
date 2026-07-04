plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "me.newgen"
version = "1.0.0"

base {
    archivesName.set("NewGenTeam")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Vault (soft dependency) - team bank
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude("org.bukkit", "bukkit")
    }
    // PlayerPoints (soft dependency) - team upgrade
    compileOnly("org.black_ixx:playerpoints:3.2.6")
    // PacketEvents (provided at runtime by the standalone plugin; load: BEFORE)
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "me.newgen.team.libs.hikari")
        relocate("org.sqlite", "me.newgen.team.libs.sqlite")
        minimize {
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        }
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
