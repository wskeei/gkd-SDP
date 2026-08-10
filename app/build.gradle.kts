import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import kotlin.reflect.full.declaredMemberProperties

val sdpVersionFile = rootProject.file("gradle/version.properties")
require(sdpVersionFile.isFile) {
    "Missing GKD-SDP version metadata: ${sdpVersionFile.absolutePath}"
}
val sdpVersionLines = sdpVersionFile.readLines()
val canonicalVersionPropertyRegex = Regex("^[A-Za-z][A-Za-z0-9]*=[^=]*$")
val invalidVersionPropertyLine = sdpVersionLines.firstOrNull { line ->
    line.isNotEmpty() && !line.startsWith('#') && !line.startsWith('!') &&
        !canonicalVersionPropertyRegex.matches(line)
}
require(invalidVersionPropertyLine == null) {
    "GKD-SDP version metadata contains a non-canonical property line"
}
val sdpVersionKeys = sdpVersionLines
    .asSequence()
    .filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith('!') }
    .map { it.substringBefore('=').trim() }
    .toList()
listOf("versionName", "versionCode", "upstreamBase", "upstreamVersionCode").forEach { key ->
    require(sdpVersionKeys.count { it == key } == 1) {
        "GKD-SDP version metadata must contain exactly one $key entry"
    }
}
val sdpVersionProperties = Properties().apply {
    sdpVersionFile.inputStream().use(::load)
}

fun requiredSdpVersionProperty(name: String): String =
    sdpVersionProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing required GKD-SDP version property: $name")

val sdpVersionName = requiredSdpVersionProperty("versionName")
val sdpVersionCodeText = requiredSdpVersionProperty("versionCode")
val sdpUpstreamBase = requiredSdpVersionProperty("upstreamBase")
val sdpUpstreamVersionCodeText = requiredSdpVersionProperty("upstreamVersionCode")
require(sdpVersionName.matches(Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$"))) {
    "Invalid stable GKD-SDP versionName: $sdpVersionName"
}
require(sdpVersionCodeText.matches(Regex("^[1-9][0-9]*$"))) {
    "Invalid GKD-SDP versionCode: $sdpVersionCodeText"
}
val sdpVersionCode = sdpVersionCodeText.toIntOrNull()?.takeIf { it > 0 }
    ?: error("GKD-SDP versionCode is outside the supported integer range: $sdpVersionCodeText")
val sdpUpstreamVersionCode = sdpUpstreamVersionCodeText.toIntOrNull()?.takeIf { it > 0 }
    ?: error("GKD-SDP upstreamVersionCode is outside the supported integer range: $sdpUpstreamVersionCodeText")
require(sdpUpstreamVersionCodeText.matches(Regex("^[1-9][0-9]*$"))) {
    "Invalid GKD-SDP upstreamVersionCode: $sdpUpstreamVersionCodeText"
}
require(sdpVersionCode > sdpUpstreamVersionCode) {
    "GKD-SDP versionCode must be greater than upstreamVersionCode $sdpUpstreamVersionCode"
}
require(sdpUpstreamBase.matches(Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$"))) {
    "Invalid GKD-SDP upstreamBase: $sdpUpstreamBase"
}

fun String.runCommand(): String {
    val process = ProcessBuilder(split(" "))
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("Command failed with exit code $exitCode: $output")
    }
    return output
}

data class GitInfo(
    val commitId: String,
    val commitTime: String,
    val tagName: String?,
) {
    val versionNameSuffix get() = if (tagName == null) ("-" + commitId.take(7)) else null
}

val gitInfo = GitInfo(
    commitId = "git rev-parse HEAD".runCommand(),
    commitTime = "git log -1 --format=%ct".runCommand() + "000",
    tagName = runCatching { "git describe --tags --exact-match".runCommand() }.getOrNull(),
)

val debugSuffixPairList by lazy {
    javax.xml.parsers.DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(file("$projectDir/src/main/res/values/strings.xml"))
        .documentElement.getElementsByTagName("string").run {
            (0 until length).mapNotNull { i ->
                val node = item(i)
                if (node.attributes.getNamedItem("debug_suffix") != null) {
                    val key = node.attributes.getNamedItem("name").nodeValue
                    val value = node.textContent
                    key to value
                } else {
                    null
                }
            }
        }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.remap)
    alias(libs.plugins.loc)
}

android {
    namespace = rootProject.ext["android.namespace"].toString()
    compileSdk = rootProject.ext["android.compileSdk"] as Int
    buildToolsVersion = rootProject.ext["android.buildToolsVersion"].toString()

    defaultConfig {
        minSdk = rootProject.ext["android.minSdk"] as Int
        targetSdk = rootProject.ext["android.targetSdk"] as Int

        applicationId = "li.songe.gkd.sdp"
        versionCode = sdpVersionCode
        versionName = sdpVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            localeFilters += listOf("zh", "en")
        }
        ndk {
            // noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        GitInfo::class.declaredMemberProperties.onEach {
            manifestPlaceholders[it.name] = it.get(gitInfo) ?: ""
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        resValues = true
    }

    val gkdSigningConfig = if (project.hasProperty("GKD_STORE_FILE")) {
        signingConfigs.create("gkd") {
            storeFile = file(project.properties["GKD_STORE_FILE"] as String)
            storePassword = project.findProperty("GKD_STORE_PASSWORD")?.toString()
            keyAlias = project.findProperty("GKD_KEY_ALIAS")?.toString()
            keyPassword = project.findProperty("GKD_KEY_PASSWORD")?.toString()
        }
    } else {
        signingConfigs.getByName("debug")
    }

    val playSigningConfig = if (project.hasProperty("PLAY_STORE_FILE")) {
        signingConfigs.create("play") {
            storeFile = file(project.properties["PLAY_STORE_FILE"].toString())
            storePassword = project.properties["PLAY_STORE_PASSWORD"].toString()
            keyAlias = project.properties["PLAY_KEY_ALIAS"].toString()
            keyPassword = project.properties["PLAY_KEY_PASSWORD"].toString()
        }
    } else {
        gkdSigningConfig
    }

    buildTypes {
        all {
            versionNameSuffix = gitInfo.versionNameSuffix
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = gkdSigningConfig
            applicationIdSuffix = ".debug"
            resValue("color", "better_black", "#FF5D92")
            debugSuffixPairList.onEach { (key, value) ->
                resValue("string", key, "$value-debug")
            }
        }
    }
    productFlavors {
        flavorDimensions += "channel"
        create("gkd") {
            isDefault = true
            signingConfig = gkdSigningConfig
            resValue("bool", "is_accessibility_tool", "true")
        }
        create("play") {
            signingConfig = playSigningConfig
            resValue("bool", "is_accessibility_tool", "false")
        }
        all {
            dimension = flavorDimensions.first()
            manifestPlaceholders["channel"] = name
        }
    }
    compileOptions {
        sourceCompatibility = rootProject.ext["android.javaVersion"] as JavaVersion
        targetCompatibility = rootProject.ext["android.javaVersion"] as JavaVersion
    }
    dependenciesInfo.includeInApk = false
    packaging.resources.excludes += setOf(
        // https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        "META-INF/**", "**/attach_hotspot_windows.dll",

        "**.properties", "**.bin", "**/*.proto",
        "**/kotlin-tooling-metadata.json",

        // ktor
        "**/custom.config.conf",
        "**/custom.config.yaml",
    )
}

if (project.hasProperty("GKD_RENAME_APK_FLAG")) {
    androidComponents.onVariants { variant ->
        variant.outputs.onEach { output ->
            output as VariantOutputImpl
            output.outputFileName = "gkd-v${output.versionName.get()}.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(rootProject.ext["kotlin.jvmTarget"] as JvmTarget)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-XXLanguage:+MultiDollarInterpolation",
        )
    }
}

// https://developer.android.com/jetpack/androidx/releases/room?hl=zh-cn#compiler-options
room {
    schemaDirectory("$projectDir/schemas")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(
        rootProject.layout.projectDirectory.file("stability_config.conf"),
    )
}

loc {
    template = "{packageName}.{methodName}({fileName}:{lineNumber})"
}

dependencies {
    lintChecks(project(":quality-lint"))

    implementation(libs.kotlin.stdlib)

    implementation(project(":selector"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)

    implementation(libs.compose.activity)
    implementation(libs.compose.material3)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.navigation)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    compileOnly(project(":hidden_api"))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    // https://github.com/Kotlin/kotlinx-atomicfu/issues/145
    implementation(libs.kotlinx.atomicfu)

    implementation(libs.activityResultLauncher)

    implementation(libs.reorderable)

    implementation(libs.androidx.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.zoomable)

    implementation(libs.exp4j)

    implementation(libs.toaster)
    implementation(libs.permissions)
    implementation(libs.device)

    implementation(libs.json5)
    compileOnly(libs.loc.annotation)


    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
}
