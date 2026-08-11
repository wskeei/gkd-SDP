plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "li.songe.gkd.sdp.baselineprofile"
    compileSdk = rootProject.ext["android.compileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.ext["android.minSdk"] as Int
        targetSdk = rootProject.ext["android.targetSdk"] as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    flavorDimensions += "channel"
    productFlavors {
        create("gkd") {
            dimension = "channel"
        }
        create("play") {
            dimension = "channel"
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "google"
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

baselineProfile {
    managedDevices.clear()
    managedDevices += "pixel6Api35"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}
