package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import java.time.LocalDateTime
import java.time.LocalTime

sealed interface AppBlockerDecision {
    data class Block(
        val ruleId: Long,
        val message: String,
        /** Immutable rule data captured in the same snapshot as the decision. */
        val ruleSnapshot: BlockTimeRule? = null,
    ) : AppBlockerDecision
    data object EngineDisabled : AppBlockerDecision
    data object NoMatchingTarget : AppBlockerDecision
    data object GroupDisabled : AppBlockerDecision
    data object NoEnabledRule : AppBlockerDecision
    data object OutsideSchedule : AppBlockerDecision
    data class InvalidRule(val ruleIds: List<Long>) : AppBlockerDecision
}

/** Pure, deterministic application blocker decision logic. */
object AppBlockerDecisionPolicy {
    data class Snapshot(
        val rules: List<BlockTimeRule> = emptyList(),
        val groups: List<AppGroup> = emptyList(),
    )

    fun decide(
        packageName: String,
        snapshot: Snapshot,
        now: LocalDateTime,
        enabled: Boolean = true,
    ): AppBlockerDecision {
        if (!enabled) return AppBlockerDecision.EngineDisabled
        if (packageName.isBlank()) return AppBlockerDecision.NoMatchingTarget

        val matchingGroups = snapshot.groups.filter { group ->
            group.containsApp(packageName)
        }
        val appRules = snapshot.rules.filter { rule ->
            rule.targetType == BlockTimeRule.TARGET_TYPE_APP &&
                rule.targetId == packageName
        }
        val groupIds = matchingGroups
            .filter { it.enabled }
            .map { it.id.toString() }
            .toSet()
        val groupRules = snapshot.rules.filter { rule ->
            rule.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
                rule.targetId in groupIds
        }
        val allTargetRules = appRules + groupRules
        if (allTargetRules.isEmpty()) {
            return when {
                matchingGroups.any { !it.enabled } -> AppBlockerDecision.GroupDisabled
                appRules.isEmpty() && matchingGroups.isEmpty() -> AppBlockerDecision.NoMatchingTarget
                else -> AppBlockerDecision.NoEnabledRule
            }
        }

        val enabledRules = allTargetRules.filter { it.enabled }
        if (enabledRules.isEmpty()) return AppBlockerDecision.NoEnabledRule

        val invalidIds = enabledRules
            .filterNot(::hasValidSchedule)
            .map { it.id }
        val validRules = enabledRules.filter(::hasValidSchedule)
        val blockingRule = validRules
            .asSequence()
            .filter { rule -> rule.isBlockingAt(now) }
            .sortedByDescending { it.createdAt }
            .firstOrNull()
        if (blockingRule != null) {
            return AppBlockerDecision.Block(
                ruleId = blockingRule.id,
                message = blockingRule.interceptMessage,
                ruleSnapshot = blockingRule,
            )
        }
        if (invalidIds.isNotEmpty()) return AppBlockerDecision.InvalidRule(invalidIds)
        return AppBlockerDecision.OutsideSchedule
    }

    private fun hasValidSchedule(rule: BlockTimeRule): Boolean =
        parseTimeOrNull(rule.startTime) != null &&
            parseTimeOrNull(rule.endTime) != null &&
            hasValidDays(rule.daysOfWeek)

    private fun hasValidDays(value: String): Boolean {
        if (value.isBlank()) return false
        return value.split(',').all { token ->
            val day = token.trim().toIntOrNull()
            day != null && day in 1..7
        }
    }

    fun isValidTime(value: String): Boolean = parseTimeOrNull(value) != null

    fun isValidDays(value: String): Boolean = hasValidDays(value)

    private fun parseTimeOrNull(value: String): LocalTime? = runCatching {
        require(Regex("\\d{2}:\\d{2}").matches(value))
        val parts = value.split(":")
        require(parts.size == 2)
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

    private fun BlockTimeRule.isBlockingAt(now: LocalDateTime): Boolean {
        val start = parseTimeOrNull(startTime) ?: return false
        val end = parseTimeOrNull(endTime) ?: return false
        val days = getDaysOfWeekList().toSet()
        val currentDay = now.dayOfWeek.value
        val currentTime = now.toLocalTime()

        val inWindow = when {
            start == end -> currentDay in days
            end.isAfter(start) -> {
                if (currentDay !in days) {
                    false
                } else if (end == LocalTime.of(23, 59)) {
                    // Existing templates use 23:59 as the inclusive last
                    // minute of the day, not as an exclusive boundary.
                    !currentTime.isBefore(start)
                } else {
                    !currentTime.isBefore(start) && currentTime.isBefore(end)
                }
            }
            else -> {
                val previousDay = now.toLocalDate().minusDays(1).dayOfWeek.value
                (currentDay in days && !currentTime.isBefore(start)) ||
                    (previousDay in days && currentTime.isBefore(end))
            }
        }
        return if (isAllowMode) !inWindow else inWindow
    }
}
