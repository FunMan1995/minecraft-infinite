plugins {
    java
}

group = "dev.funman.infinite"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.processResources {
    val implVersion = version.toString()
    inputs.property("version", implVersion)
    filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
        expand("version" to implVersion)
    }
}

tasks.jar {
    archiveBaseName.set("MinecraftInfinite")
    archiveVersion.set(project.version.toString())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
