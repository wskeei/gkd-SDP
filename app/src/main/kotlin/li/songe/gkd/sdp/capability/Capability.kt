package li.songe.gkd.sdp.capability

import androidx.compose.runtime.Immutable
import li.songe.gkd.sdp.R

/** The capability nodes of the runtime capability center. */
enum class CapabilityId {
    RUNTIME_MODE,
    OVERLAY,
    NOTIFICATION,
    BATTERY_EXEMPTION,
    SHIZUKU,
    A11Y_GUARD,
    APP_LIST_ACCESS,
}

enum class CapabilityStatus {
    UNAVAILABLE,
    ACTION_REQUIRED,
    READY,
    ACTIVE,
    LIMITED,
}

/** The user's chosen runtime mode; null until the first explicit choice. */
enum class RuntimeModeChoice {
    ACCESSIBILITY,
    AUTOMATION,
}

/** What the single primary action of a node does. */
enum class CapabilityActionTarget {
    SET_MODE_ACCESSIBILITY,
    SET_MODE_AUTOMATION,
    OPEN_A11Y_SETTINGS,
    OPEN_OVERLAY_SETTINGS,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_SHIZUKU,
    TOGGLE_A11Y_GUARD,
    OPEN_APP_LIST_SETTINGS,
}

data class CapabilityAction(
    val labelRes: Int,
    val target: CapabilityActionTarget,
)

@Immutable
data class CapabilityNode(
    val id: CapabilityId,
    val status: CapabilityStatus,
    val summaryRes: Int,
    val primaryAction: CapabilityAction? = null,
)

data class CapabilityInput(
    val chosenMode: RuntimeModeChoice?,
    val a11yReady: Boolean,
    val shizukuReady: Boolean,
    val overlayReady: Boolean,
    val notificationReady: Boolean,
    val batteryExempted: Boolean,
    val a11yGuardEnabled: Boolean,
    val appListReady: Boolean,
    val selfControlLocked: Boolean,
    val isGkdFlavor: Boolean,
)

data class CapabilityGraph(
    val nodes: List<CapabilityNode>,
) {
    /** The single next step: the first node needing action, by fixed order. */
    val nextStep: CapabilityNode?
        get() = nodes.firstOrNull { it.status == CapabilityStatus.ACTION_REQUIRED }
}

object CapabilityResolver {
    fun resolve(input: CapabilityInput): CapabilityGraph {
        val nodes = mutableListOf<CapabilityNode>()

        // 1. runtime_mode: one of Accessibility/Automation must be ready.
        val mode = input.chosenMode
        nodes += when {
            mode == null ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTION_REQUIRED,
                    summaryRes = R.string.capability_choose_mode_summary,
                    primaryAction = CapabilityAction(
                        labelRes = R.string.capability_choose_accessibility_action,
                        target = CapabilityActionTarget.SET_MODE_ACCESSIBILITY,
                    ),
                )

            mode == RuntimeModeChoice.ACCESSIBILITY && input.a11yReady ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTIVE,
                    summaryRes = R.string.capability_accessibility_running,
                )

            mode == RuntimeModeChoice.ACCESSIBILITY ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTION_REQUIRED,
                    summaryRes = R.string.capability_accessibility_required,
                    primaryAction = CapabilityAction(
                        labelRes = R.string.capability_open_system_settings,
                        target = CapabilityActionTarget.OPEN_A11Y_SETTINGS,
                    ),
                )

            mode == RuntimeModeChoice.AUTOMATION && input.shizukuReady ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTIVE,
                    summaryRes = R.string.capability_automation_running,
                )

            else ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTION_REQUIRED,
                    summaryRes = R.string.capability_shizuku_required,
                    primaryAction = CapabilityAction(
                        labelRes = R.string.capability_open_shizuku,
                        target = CapabilityActionTarget.OPEN_SHIZUKU,
                    ),
                )
        }

        // 2. overlay
        nodes += if (input.overlayReady) {
            CapabilityNode(
                CapabilityId.OVERLAY,
                CapabilityStatus.READY,
                R.string.capability_overlay_ready,
            )
        } else {
            CapabilityNode(
                CapabilityId.OVERLAY,
                CapabilityStatus.ACTION_REQUIRED,
                R.string.capability_overlay_required,
                CapabilityAction(
                    R.string.capability_open_overlay_settings,
                    CapabilityActionTarget.OPEN_OVERLAY_SETTINGS,
                ),
            )
        }

        // 3. notification
        nodes += if (input.notificationReady) {
            CapabilityNode(
                CapabilityId.NOTIFICATION,
                CapabilityStatus.READY,
                R.string.capability_notification_ready,
            )
        } else {
            CapabilityNode(
                CapabilityId.NOTIFICATION,
                CapabilityStatus.ACTION_REQUIRED,
                R.string.capability_notification_required,
                CapabilityAction(
                    R.string.capability_open_notification_settings,
                    CapabilityActionTarget.OPEN_NOTIFICATION_SETTINGS,
                ),
            )
        }

        // 4. battery exemption: recommended, not blocking
        nodes += if (input.batteryExempted) {
            CapabilityNode(
                CapabilityId.BATTERY_EXEMPTION,
                CapabilityStatus.ACTIVE,
                R.string.capability_battery_active,
            )
        } else {
            CapabilityNode(
                CapabilityId.BATTERY_EXEMPTION,
                CapabilityStatus.LIMITED,
                R.string.capability_battery_limited,
                CapabilityAction(
                    R.string.capability_open_battery_settings,
                    CapabilityActionTarget.OPEN_BATTERY_SETTINGS,
                ),
            )
        }

        // 5. shizuku: only needed in Automation mode
        nodes += when {
            mode == null ->
                CapabilityNode(
                    CapabilityId.SHIZUKU,
                    CapabilityStatus.READY,
                    R.string.capability_shizuku_after_mode,
                )

            mode == RuntimeModeChoice.ACCESSIBILITY ->
                CapabilityNode(
                    CapabilityId.SHIZUKU,
                    CapabilityStatus.UNAVAILABLE,
                    R.string.capability_shizuku_not_needed_accessibility,
                )

            input.shizukuReady ->
                CapabilityNode(
                    CapabilityId.SHIZUKU,
                    CapabilityStatus.ACTIVE,
                    R.string.capability_shizuku_authorized,
                )

            else ->
                CapabilityNode(
                    CapabilityId.SHIZUKU,
                    CapabilityStatus.ACTION_REQUIRED,
                    R.string.capability_shizuku_automation_required,
                    CapabilityAction(
                        R.string.capability_open_shizuku,
                        CapabilityActionTarget.OPEN_SHIZUKU,
                    ),
                )
        }

        // 6. a11y guard: gkd/A11y scenario only; locked guard cannot be disabled
        nodes += when {
            !input.isGkdFlavor ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.UNAVAILABLE,
                    R.string.capability_guard_not_applicable,
                )

            mode == null ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.UNAVAILABLE,
                    R.string.capability_guard_after_mode,
                )

            mode != RuntimeModeChoice.ACCESSIBILITY ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.UNAVAILABLE,
                    R.string.capability_guard_automation_not_applicable,
                )

            input.selfControlLocked && input.a11yGuardEnabled ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.ACTIVE,
                    R.string.capability_guard_locked,
                )

            input.a11yGuardEnabled ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.ACTIVE,
                    R.string.capability_guard_active,
                )

            else ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.READY,
                    R.string.capability_guard_ready,
                    CapabilityAction(
                        R.string.capability_guard_enable,
                        CapabilityActionTarget.TOGGLE_A11Y_GUARD,
                    ),
                )
        }

        // 7. app list access
        nodes += if (input.appListReady) {
            CapabilityNode(
                CapabilityId.APP_LIST_ACCESS,
                CapabilityStatus.READY,
                R.string.capability_app_list_ready,
            )
        } else {
            CapabilityNode(
                CapabilityId.APP_LIST_ACCESS,
                CapabilityStatus.ACTION_REQUIRED,
                R.string.capability_app_list_required,
                CapabilityAction(
                    R.string.capability_open_app_list_settings,
                    CapabilityActionTarget.OPEN_APP_LIST_SETTINGS,
                ),
            )
        }

        return CapabilityGraph(nodes = nodes)
    }
}
