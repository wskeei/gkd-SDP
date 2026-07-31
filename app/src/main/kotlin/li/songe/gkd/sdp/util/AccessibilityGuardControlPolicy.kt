package li.songe.gkd.sdp.util

/**
 * Pure decisions for the user-facing accessibility guard controls.
 *
 * The accessibility component being disabled is intentionally not an enable
 * blocker. The feature exists precisely to observe and remind after that
 * permission is lost.
 */
object AccessibilityGuardControlPolicy {
    enum class EnableDecision {
        ALLOW,
        UNAVAILABLE_CHANNEL,
        REQUIRE_A11Y_MODE,
    }

    enum class DisableDecision {
        NO_CHANGE,
        ALLOW,
        BLOCKED_BY_LOCK,
        BLOCKED_BY_QUOTA,
    }

    @Suppress("UNUSED_PARAMETER")
    fun enableDecision(
        strictChannelAvailable: Boolean,
        useA11yMode: Boolean,
        accessibilityComponentEnabled: Boolean,
    ): EnableDecision {
        if (!strictChannelAvailable) return EnableDecision.UNAVAILABLE_CHANNEL
        if (!useA11yMode) return EnableDecision.REQUIRE_A11Y_MODE
        return EnableDecision.ALLOW
    }

    fun disableDecision(
        currentlyEnabled: Boolean,
        anyActiveLock: Boolean,
        quotaAllowed: Boolean,
    ): DisableDecision {
        if (!currentlyEnabled) return DisableDecision.NO_CHANGE
        if (anyActiveLock) return DisableDecision.BLOCKED_BY_LOCK
        if (!quotaAllowed) return DisableDecision.BLOCKED_BY_QUOTA
        return DisableDecision.ALLOW
    }

    fun shouldAutoReenable(
        strictChannelAvailable: Boolean,
        useA11yMode: Boolean,
        armed: Boolean,
        currentlyEnabled: Boolean,
    ): Boolean = strictChannelAvailable &&
        useA11yMode &&
        armed &&
        !currentlyEnabled
}
