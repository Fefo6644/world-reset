plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.github.hierynomus.license-base") version "0.15.0"
}

defaultTasks("clean", "licenseMain", "shadowJar")

project.group = "com.github.fefo"
project.version = "1.2.3"

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

license {
    header = rootProject.file("license-header.txt")
    encoding = "UTF-8"

    mapping("java", "DOUBLESLASH_STYLE")

    ext["year"] = 2021
    ext["name"] = "Fefo6644"
    ext["email"] = "federico.lopez.1999@outlook.com"

    include("**/*.java")
}

repositories {
    mavenCentral()
    maven { url = uri("https://libraries.minecraft.net") }
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
}

dependencies {
    implementation("net.kyori:adventure-api:4.5.0") {
        exclude(group = "org.checkerframework")
    }
    implementation("net.kyori:adventure-platform-bukkit:4.0.0-SNAPSHOT") {
        exclude(group = "org.checkerframework")
    }
    compileOnly("com.mojang:brigadier:1.0.17")
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:20.1.0")
}
