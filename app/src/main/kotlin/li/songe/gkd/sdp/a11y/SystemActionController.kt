package li.songe.gkd.sdp.a11y

/**
 * System navigation actions with a privileged-input-first fallback.
 * A nullable input result means that Shizuku is unavailable; false means the
 * service explicitly rejected the injection. Both cases use accessibility.
 */
class SystemActionController(
    private val inputHome: () -> Boolean?,
    private val accessibilityHome: () -> Boolean,
) {
    fun performHome(): Boolean {
        val inputAccepted = runCatching { inputHome() }.getOrNull() == true
        return inputAccepted || runCatching { accessibilityHome() }.getOrDefault(false)
    }
}
