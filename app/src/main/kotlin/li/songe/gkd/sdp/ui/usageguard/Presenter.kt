@file:JvmName("UsageGuardPresenter")

package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.UsageGuardRecord

internal fun UsageGuardRecord.endStateText(): String {
    return when (endReason) {
        UsageGuardRecord.END_REASON_ACTIVE -> "进行中"
        UsageGuardRecord.END_REASON_EXPIRED -> "已到时"
        UsageGuardRecord.END_REASON_LEFT_APP -> "离开应用结束"
        UsageGuardRecord.END_REASON_REPLACED -> "已被新的申请替换"
        UsageGuardRecord.END_REASON_HOME_BUTTON -> "已回到桌面"
        UsageGuardRecord.END_REASON_USER_TERMINATED -> "已主动终止"
        else -> "未知状态"
    }
}
