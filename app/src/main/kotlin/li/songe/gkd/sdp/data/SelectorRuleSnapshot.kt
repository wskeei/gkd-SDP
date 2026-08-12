package li.songe.gkd.sdp.data

/**
 * A privacy-safe, exact attribution snapshot captured at the moment a selector rule matches.
 *
 * Raw selectors, node text, URLs and interception messages intentionally never cross this
 * boundary. The snapshot survives subscription updates/removal as long as the subscription row
 * itself remains available.
 */
data class SelectorRuleSnapshot(
    val subsId: Long,
    val subsVersion: Int,
    val appId: String,
    val activityId: String? = null,
    val groupType: Int,
    val groupKey: Int,
    val ruleIndex: Int,
    val ruleKey: Int?,
    val ruleName: String?,
    val groupName: String?,
    val subscriptionName: String?,
    val matchedAt: Long,
) {
    fun eventKey(): String {
        val identity = ruleKey?.let { "key:$it" }
            ?: "version:$subsVersion:index:${ruleIndex.coerceAtLeast(0)}"
        return "selector_intercept:v2:$subsId:$appId:$groupType:$groupKey:$identity"
    }

    fun displayRuleIdentity(): String {
        normalizeLabel(ruleName)?.let { return it }
        // i18n-ignore: legacy fallback or non-display heuristic data
        ruleKey?.let { return "规则 key=$it" }
        // i18n-ignore: legacy fallback or non-display heuristic data
        return "规则 #${ruleIndex.coerceAtLeast(0) + 1}"
    }

    fun toActionLog(
        outcome: Int,
        ctime: Long = matchedAt,
    ): ActionLog = ActionLog(
        ctime = ctime,
        appId = appId,
        activityId = activityId,
        subsId = subsId,
        subsVersion = subsVersion,
        groupKey = groupKey,
        groupType = groupType,
        ruleIndex = ruleIndex,
        ruleKey = ruleKey,
        outcome = outcome,
        matchedAt = matchedAt,
        subsNameSnapshot = normalizeLabel(subscriptionName),
        groupNameSnapshot = normalizeLabel(groupName),
        ruleNameSnapshot = displayRuleIdentity(),
    )

    companion object {
        private const val MAX_LABEL_CODE_POINTS = 80

        fun fromResolvedRule(
            rule: ResolvedRule,
            appId: String,
            activityId: String?,
            matchedAt: Long,
        ): SelectorRuleSnapshot = SelectorRuleSnapshot(
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            appId = appId,
            activityId = activityId,
            groupType = rule.g.group.groupType,
            groupKey = rule.g.group.key,
            ruleIndex = rule.index,
            ruleKey = rule.key,
            ruleName = normalizeLabel(rule.rule.name),
            groupName = normalizeLabel(rule.g.group.name),
            subscriptionName = normalizeLabel(rule.rawSubs.name),
            matchedAt = matchedAt,
        )

        fun normalizeLabel(value: String?): String? {
            val normalized = value
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val codePointCount = normalized.codePointCount(0, normalized.length)
            return if (codePointCount <= MAX_LABEL_CODE_POINTS) {
                normalized
            } else {
                normalized.substring(0, normalized.offsetByCodePoints(0, MAX_LABEL_CODE_POINTS))
            }
        }

        fun shortActivityId(value: String?): String? {
            val normalized = normalizeLabel(value) ?: return null
            val maxCodePoints = 48
            if (normalized.codePointCount(0, normalized.length) <= maxCodePoints) return normalized
            val leftCodePoints = (maxCodePoints - 1) / 2
            val rightCodePoints = maxCodePoints - 1 - leftCodePoints
            val leftEnd = normalized.offsetByCodePoints(0, leftCodePoints)
            val rightStart = normalized.offsetByCodePoints(0, normalized.codePointCount(0, normalized.length) - rightCodePoints)
            return normalized.substring(0, leftEnd) + "…" + normalized.substring(rightStart)
        }
    }
}

fun ResolvedRule.toSelectorRuleSnapshot(
    appId: String,
    activityId: String?,
    matchedAt: Long,
): SelectorRuleSnapshot = SelectorRuleSnapshot.fromResolvedRule(
    rule = this,
    appId = appId,
    activityId = activityId,
    matchedAt = matchedAt,
)
