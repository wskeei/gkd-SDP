package li.songe.gkd.sdp.capability

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
    val label: String,
    val target: CapabilityActionTarget,
)

data class CapabilityNode(
    val id: CapabilityId,
    val status: CapabilityStatus,
    val summary: String,
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
                    summary = "请选择运行模式",
                    primaryAction = CapabilityAction(
                        label = "选择无障碍模式",
                        target = CapabilityActionTarget.SET_MODE_ACCESSIBILITY,
                    ),
                )

            mode == RuntimeModeChoice.ACCESSIBILITY && input.a11yReady ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTIVE,
                    summary = "无障碍模式运行中",
                )

            mode == RuntimeModeChoice.ACCESSIBILITY ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTION_REQUIRED,
                    summary = "需要开启无障碍服务",
                    primaryAction = CapabilityAction(
                        label = "前往系统设置开启",
                        target = CapabilityActionTarget.OPEN_A11Y_SETTINGS,
                    ),
                )

            mode == RuntimeModeChoice.AUTOMATION && input.shizukuReady ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTIVE,
                    summary = "自动化模式（Shizuku）运行中",
                )

            else ->
                CapabilityNode(
                    id = CapabilityId.RUNTIME_MODE,
                    status = CapabilityStatus.ACTION_REQUIRED,
                    summary = "需要授权 Shizuku",
                    primaryAction = CapabilityAction(
                        label = "打开 Shizuku 授权",
                        target = CapabilityActionTarget.OPEN_SHIZUKU,
                    ),
                )
        }

        // 2. overlay
        nodes += if (input.overlayReady) {
            CapabilityNode(CapabilityId.OVERLAY, CapabilityStatus.READY, "悬浮窗权限已就绪")
        } else {
            CapabilityNode(
                CapabilityId.OVERLAY,
                CapabilityStatus.ACTION_REQUIRED,
                "使用申请、拦截与倒计时需要悬浮窗权限",
                CapabilityAction("前往系统设置开启", CapabilityActionTarget.OPEN_OVERLAY_SETTINGS),
            )
        }

        // 3. notification
        nodes += if (input.notificationReady) {
            CapabilityNode(CapabilityId.NOTIFICATION, CapabilityStatus.READY, "通知权限已就绪")
        } else {
            CapabilityNode(
                CapabilityId.NOTIFICATION,
                CapabilityStatus.ACTION_REQUIRED,
                "前台服务与守护提醒需要通知权限",
                CapabilityAction("前往系统设置开启", CapabilityActionTarget.OPEN_NOTIFICATION_SETTINGS),
            )
        }

        // 4. battery exemption: recommended, not blocking
        nodes += if (input.batteryExempted) {
            CapabilityNode(CapabilityId.BATTERY_EXEMPTION, CapabilityStatus.ACTIVE, "已加入电池优化白名单")
        } else {
            CapabilityNode(
                CapabilityId.BATTERY_EXEMPTION,
                CapabilityStatus.LIMITED,
                "未加入电池优化白名单（推荐，不阻断使用）",
                CapabilityAction("前往系统设置", CapabilityActionTarget.OPEN_BATTERY_SETTINGS),
            )
        }

        // 5. shizuku: only needed in Automation mode
        nodes += when {
            mode == null ->
                CapabilityNode(CapabilityId.SHIZUKU, CapabilityStatus.READY, "选择自动化模式后需要 Shizuku")

            mode == RuntimeModeChoice.ACCESSIBILITY ->
                CapabilityNode(CapabilityId.SHIZUKU, CapabilityStatus.UNAVAILABLE, "无障碍模式不需要 Shizuku")

            input.shizukuReady ->
                CapabilityNode(CapabilityId.SHIZUKU, CapabilityStatus.ACTIVE, "Shizuku 已授权")

            else ->
                CapabilityNode(
                    CapabilityId.SHIZUKU,
                    CapabilityStatus.ACTION_REQUIRED,
                    "自动化模式需要 Shizuku 授权",
                    CapabilityAction("打开 Shizuku 授权", CapabilityActionTarget.OPEN_SHIZUKU),
                )
        }

        // 6. a11y guard: gkd/A11y scenario only; locked guard cannot be disabled
        nodes += when {
            !input.isGkdFlavor ->
                CapabilityNode(CapabilityId.A11Y_GUARD, CapabilityStatus.UNAVAILABLE, "此版本不适用")

            input.selfControlLocked && input.a11yGuardEnabled ->
                CapabilityNode(CapabilityId.A11Y_GUARD, CapabilityStatus.ACTIVE, "守护已开启（锁定保护生效中，不可关闭）")

            input.a11yGuardEnabled ->
                CapabilityNode(CapabilityId.A11Y_GUARD, CapabilityStatus.ACTIVE, "无障碍守护已开启")

            else ->
                CapabilityNode(
                    CapabilityId.A11Y_GUARD,
                    CapabilityStatus.READY,
                    "无障碍守护可在无障碍服务关闭时先行开启",
                    CapabilityAction("开启守护", CapabilityActionTarget.TOGGLE_A11Y_GUARD),
                )
        }

        // 7. app list access
        nodes += if (input.appListReady) {
            CapabilityNode(CapabilityId.APP_LIST_ACCESS, CapabilityStatus.READY, "应用列表权限已就绪")
        } else {
            CapabilityNode(
                CapabilityId.APP_LIST_ACCESS,
                CapabilityStatus.ACTION_REQUIRED,
                "应用选择与应用拦截需要应用列表权限",
                CapabilityAction("前往系统设置开启", CapabilityActionTarget.OPEN_APP_LIST_SETTINGS),
            )
        }

        return CapabilityGraph(nodes = nodes)
    }
}
