import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
  id("com.gradleup.shadow") version "9.3.0"
}

group = "com.solarrabbit"
version = "1.12.0"
description = "LargeRaids"
val mcVersion = "1.21.11"

// Configure paperweight to output Mojang-mapped production artifacts
paperweight {
  reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
  maven("https://repo.helpch.at/releases/")
  maven("https://mvn.lumine.io/repository/maven-public/")
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("")
  configurations = listOf(project.configurations.shadow.get())
  relocate("org.bstats", "com.solarrabbit.largeraids.bstats")

  // Inform Paper/Purpur that this plugin does not require runtime remapping
  manifest {
    attributes("paperweight-mappings-namespace" to "mojang")
  }
}

dependencies {
  paperweight.paperDevBundle(mcVersion + "-R0.1-SNAPSHOT")
  shadow("org.bstats:bstats-bukkit:3.1.0")
  implementation("me.clip:placeholderapi:2.12.2")
  implementation("io.lumine:Mythic-Dist:5.11.2")
}

tasks {
  jar {
    enabled = false
  }

  // Bind the assemble task to shadowJar instead of reobfJar
  assemble {
    dependsOn(shadowJar)
  }

  compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(21)
  }
  javadoc {
    options.encoding = Charsets.UTF_8.name()
  }
  processResources {
    filteringCharset = Charsets.UTF_8.name()
    expand("version" to version)
  }
}

tasks.register("getVersion") {
  doLast {
    println(version)
  }
}

tasks.register("getMCVersion") {
  doLast {
    println(mcVersion)
  }
}