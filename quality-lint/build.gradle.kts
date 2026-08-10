import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    compileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}
