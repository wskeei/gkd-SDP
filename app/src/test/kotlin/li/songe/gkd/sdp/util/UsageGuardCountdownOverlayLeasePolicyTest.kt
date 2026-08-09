package li.songe.gkd.sdp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayLeasePolicyTest {
    @Test
    fun matchingLeaseForegroundAndRuntimeAllowRestore() {
        val session = session()

        assertTrue(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = session.toLease(),
                session = session,
                foregroundAppId = session.appId,
                currentRuntimeGeneration = session.runtimeGeneration,
            ),
        )
    }

    @Test
    fun foregroundSwitchRevokedLeaseAndRuntimeHandoffRejectRestore() {
        val session = session()
        val lease = session.toLease()

        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = lease,
                session = session,
                foregroundAppId = "com.example.other",
                currentRuntimeGeneration = session.runtimeGeneration,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = null,
                session = session,
                foregroundAppId = session.appId,
                currentRuntimeGeneration = session.runtimeGeneration,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = lease,
                session = session,
                foregroundAppId = session.appId,
                currentRuntimeGeneration = session.runtimeGeneration + 1L,
            ),
        )
    }

    @Test
    fun replacementRecordOrLeaseRejectsRestore() {
        val session = session()

        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = session.toLease().copy(recordId = session.recordId + 1L),
                session = session,
                foregroundAppId = session.appId,
                currentRuntimeGeneration = session.runtimeGeneration,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = session.toLease().copy(leaseId = session.leaseId + 1L),
                session = session,
                foregroundAppId = session.appId,
                currentRuntimeGeneration = session.runtimeGeneration,
            ),
        )
    }

    private fun session() = UsageGuardCountdownOverlaySession(
        appId = "com.example.target",
        recordId = 7L,
        expiresAt = 20_000L,
        leaseId = 11L,
        runtimeGeneration = 5L,
    )
}
