plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

defaultTasks("clean", "shadowJar")

project.group = "com.github.fefo"
project.version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

sourceSets {
    main {
        java.srcDir("src/main/java")
        resources.srcDir("src/main/resources")
    }

    test {
        java.srcDir("src/test/java")
        resources.srcDir("src/test/resources")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(8)
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        from(sourceSets.main.get().resources.srcDirs) {
            expand("pluginVersion" to project.version)
        }
    }

    shadowJar {
        relocate("net.kyori", "com.github.fefo.worldreset.lib.kyori")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://libraries.minecraft.net") }
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
}

dependencies {
    implementation("net.kyori:adventure-api:4.3.0") {
        exclude(group = "org.checkerframework")
    }
    implementation("net.kyori:adventure-platform-bukkit:4.0.0-SNAPSHOT") {
        exclude(group = "org.checkerframework")
    }
    compileOnly("com.mojang:brigadier:1.0.17")
    compileOnly("com.destroystokyo.paper:paper-api:1.16.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:20.1.0")
}
