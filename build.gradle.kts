plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
}

group = "com.denizenscript.denizenenchantmentfix"
version = "${project.version}-SNAPSHOT"

repositories {
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "denizen-repo"
        url = uri("https://maven.citizensnpcs.co/repo")
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.7-R0.1-SNAPSHOT")
    compileOnly("com.denizenscript:denizen:1.3.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to version)
        }
    }
}
