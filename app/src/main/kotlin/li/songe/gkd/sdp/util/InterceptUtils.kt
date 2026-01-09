package li.songe.gkd.sdp.util

import java.util.concurrent.ConcurrentHashMap

object InterceptUtils {
    // Map<SubsId_GroupKey, ValidUntilTime>
    private val allowedGroups = ConcurrentHashMap<String, Long>()

    fun isAllowed(subsId: Long, groupKey: Int): Boolean {
        val key = "${subsId}_${groupKey}"
        val validUntil = allowedGroups[key] ?: return false
        return System.currentTimeMillis() < validUntil
    }

    fun setAllowed(subsId: Long, groupKey: Int, cooldownSeconds: Int) {
        val key = "${subsId}_${groupKey}"
        allowedGroups[key] = System.currentTimeMillis() + cooldownSeconds * 1000L
    }
}
