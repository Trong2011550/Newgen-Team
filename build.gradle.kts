plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "me.newgen"
version = "1.1.0"

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
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Vault (soft dependency) - team bank
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude("org.bukkit", "bukkit")
    }
    // PlayerPoints (soft dependency) - team upgrade
    compileOnly("org.black_ixx:playerpoints:3.2.6")
    // PacketEvents (provided at runtime by the standalone plugin; load: BEFORE)
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("org.postgresql:postgresql:42.7.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("org.bstats", "me.newgen.team.libs.bstats")
        relocate("com.zaxxer.hikari", "me.newgen.team.libs.hikari")
        relocate("org.sqlite", "me.newgen.team.libs.sqlite")
        relocate("org.mariadb.jdbc", "me.newgen.team.libs.mariadb")
        relocate("org.postgresql", "me.newgen.team.libs.postgresql")
        minimize {
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
            exclude(dependency("org.postgresql:postgresql:.*"))
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
