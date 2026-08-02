import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.Action
import org.gradle.api.Project
import java.util.Properties

val securityFloors = Properties().apply {
    rootDir.resolve("gradle/security-dependency-floors.properties").inputStream().use(::load)
}

fun requiredFloor(key: String): String =
    securityFloors.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing security dependency floor: $key")

val nettyFloor = requiredFloor("netty4_1")
val commonsLang3Floor = requiredFloor("commonsLang3")
val httpcomponentsFloor = requiredFloor("httpcomponents4_5")
val jose4jFloor = requiredFloor("jose4j")
val bouncycastleFloor = requiredFloor("bouncycastleJdk18on")
val jdom2Floor = requiredFloor("jdom2")
val numericVersionPattern = "\\d+".toRegex()
val netty41Pattern = "^4\\.1\\.(\\d+)\\.Final$".toRegex()

fun versionParts(version: String): List<Int> =
    numericVersionPattern.findAll(version).map { it.value.toInt() }.toList()

fun isBelowFloor(version: String, floor: String): Boolean {
    val actual = versionParts(version)
    val expected = versionParts(floor)
    if (actual.isEmpty() || expected.isEmpty()) return false
    val width = maxOf(actual.size, expected.size)
    for (index in 0 until width) {
        val actualPart = actual.getOrElse(index) { 0 }
        val expectedPart = expected.getOrElse(index) { 0 }
        if (actualPart < expectedPart) return true
        if (actualPart > expectedPart) return false
    }
    return false
}

fun securityFloor(group: String?, name: String, version: String): Pair<String, String>? {
    return when {
        group == "io.netty" && netty41Pattern.matches(version) -> nettyFloor to "Netty 4.1 family security floor"
        group == "org.apache.commons" && name == "commons-lang3" ->
            commonsLang3Floor to "Apache Commons Lang security floor"
        group == "org.apache.httpcomponents" && name in setOf("httpclient", "httpmime") ->
            httpcomponentsFloor to "Apache HttpClient 4.5 security floor"
        group == "org.bitbucket.b_c" && name == "jose4j" -> jose4jFloor to "jose4j security floor"
        group == "org.bouncycastle" && name in setOf("bcpkix-jdk18on", "bcprov-jdk18on", "bcutil-jdk18on") ->
            bouncycastleFloor to "Bouncy Castle JDK 18 security floor"
        group == "org.jdom" && name == "jdom2" -> jdom2Floor to "JDOM 2 security floor"
        else -> null
    }
}

fun applySecurityFloor(details: DependencyResolveDetails) {
    val group = details.requested.group
    val name = details.requested.name
    val version = details.requested.version ?: return
    val floor = securityFloor(group, name, version) ?: return
    if (isBelowFloor(version, floor.first)) {
        details.useVersion(floor.first)
        details.because(floor.second)
    }
}

fun configureSecurityResolution(configuration: Configuration) {
    configuration.resolutionStrategy.eachDependency(::applySecurityFloor)
}

gradle.beforeProject(object : Action<Project> {
    override fun execute(project: Project) {
        // Buildscript classpaths are resolved separately from normal project configurations.
        project.buildscript.configurations.configureEach {
            configureSecurityResolution(this)
        }
        project.configurations.configureEach {
            configureSecurityResolution(this)
        }
    }
})
