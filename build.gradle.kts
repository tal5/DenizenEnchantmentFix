plugins {
    java
}

group = "com.denizenscript.denizenenchantmentfix"
version = "1.0-SNAPSHOT"

repositories {
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
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
