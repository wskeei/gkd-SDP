package li.songe.gkd.sdp.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class SupportBundleManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val flavor: String,
    val androidApi: Int,
    val generatedAtMinute: Long,
    val files: List<SupportBundleFileDigest>,
)

@Serializable
data class SupportBundleFileDigest(
    val name: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
data class SupportBundleMetadata(
    val appVersionName: String,
    val appVersionCode: Int,
    val flavor: String,
    val androidApi: Int,
)

@Serializable
data class SupportAppSummary(
    val installSourceCategory: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val primaryAbi: String,
    val androidApi: Int,
    val featureFlags: Map<String, Boolean>,
)

@Serializable
data class SupportCapabilitySummary(
    val capabilities: Map<String, Boolean>,
)

@Serializable
data class SupportDiagnosticEvent(
    val occurredAtMillis: Long,
    val event: DiagnosticEvent,
)

@Serializable
data class SupportDiagnosticEventEntry(
    val occurredAtMinute: Long,
    val event: DiagnosticEvent,
)

@Serializable
data class SupportCrashSummary(
    val errorCode: String,
    val errorCategory: DiagnosticErrorCategory,
    val occurredAtMillis: Long,
    val appFrames: List<String>,
    val count: Int,
)

@Serializable
data class SupportCrashSummaryEntry(
    val errorCode: String,
    val errorCategory: DiagnosticErrorCategory,
    val occurredAtMinute: Long,
    val appFrames: List<String>,
    val count: Int,
)

data class SupportBundleRequest(
    val generatedAtMillis: Long,
    val metadata: SupportBundleMetadata,
    val appSummary: SupportAppSummary,
    val capabilitySummary: SupportCapabilitySummary,
    val diagnosticEvents: List<SupportDiagnosticEvent>,
    val crashSummaries: List<SupportCrashSummary>,
)
