import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.lint.api)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}

tasks.jar {
    manifest {
        attributes["Lint-Registry-v2"] = "li.songe.gkd.sdp.lint.SdpIssueRegistry"
    }
}
