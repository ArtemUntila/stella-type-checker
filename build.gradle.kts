plugins {
    kotlin("jvm") version "1.9.22"
    antlr
}

group = "artem.untila"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

tasks {
    generateGrammarSource {
        arguments = arguments + listOf("-visitor")
    }

    compileKotlin {
        dependsOn("generateGrammarSource")
    }

    compileTestKotlin {
        dependsOn("generateTestGrammarSource")
    }
}