package li.songe.gkd.sdp.diagnostics

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object RedactionPolicy {
    private const val MAX_TEXT_LENGTH = 80

    private val credentialRegex = Regex(
        pattern = "(?i)\\b(authorization|cookie)\\s*[:=]\\s*(?:bearer\\s+)?[^\\s,;}|]+",
    )
    private val uriRegex = Regex(
        pattern = "(?i)\\b(?:https?|content|file)://[^\\s,;}|]+",
    )
    private val emailRegex = Regex(
        pattern = "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
    )
    private val phoneRegex = Regex(
        pattern = "(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)",
    )
    private val absolutePathRegex = Regex(
        pattern = "(?<![A-Za-z0-9])/(?:Users|home|data|storage|sdcard|private|var|tmp)/[^\\s,;}|]+",
        option = RegexOption.IGNORE_CASE,
    )
    private val sensitiveFieldRegex = Regex(
        pattern = "(?i)\\b(reason|text|message|contact|extras|notification(?:text|body)?|node(?:text)?)\\s*[:=]\\s*[^,;}|]+",
    )

    fun redact(value: String): String {
        var result = credentialRegex.replace(value) { "${it.groupValues[1]}=[redacted]" }
        result = uriRegex.replace(result, "[uri]")
        result = emailRegex.replace(result, "[email]")
        result = phoneRegex.replace(result, "[phone]")
        result = absolutePathRegex.replace(result, "[path]")
        result = sensitiveFieldRegex.replace(result) { "${it.groupValues[1]}=[redacted]" }
        return if (result.length <= MAX_TEXT_LENGTH) {
            result
        } else {
            result.take(MAX_TEXT_LENGTH - 1) + "…"
        }
    }

    fun stableIdHash(value: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(value.toByteArray(StandardCharsets.UTF_8))
        return digest.digest().take(6).joinToString("") { byte -> "%02x".format(byte) }
    }
}
