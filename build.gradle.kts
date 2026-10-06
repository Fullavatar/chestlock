plugins {
    java
}

group = "com.github.fullavatar"
version = "1.0.0"

val hytaleSharedSourceDir = providers.gradleProperty("hytaleSharedSourceDir")
    .orElse("C:/hytale-shared-source")
val hytaleServerJar = hytaleSharedSourceDir.map {
    file("$it/HytaleServer/dist/HytaleServer/HytaleServer.jar")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    compileOnly(files(hytaleServerJar))
}

tasks.jar {
    archiveBaseName.set("ChestLock")
}
