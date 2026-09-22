plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    constraints {
        implementation("com.fasterxml.jackson:jackson-bom:2.21.2")
        implementation("org.apache.commons:commons-lang3:3.20.0")
        implementation("org.apache.commons:commons-text:1.15.0")
    }
}
