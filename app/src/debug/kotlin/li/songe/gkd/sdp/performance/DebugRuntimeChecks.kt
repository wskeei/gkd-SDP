package li.songe.gkd.sdp.performance

import android.os.Build
import android.os.StrictMode
import li.songe.gkd.sdp.diagnostics.DiagnosticErrorCategory
import li.songe.gkd.sdp.diagnostics.DiagnosticEvent
import li.songe.gkd.sdp.diagnostics.DiagnosticEventCode
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.diagnostics.DiagnosticResult
import li.songe.gkd.sdp.diagnostics.DiagnosticStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

object DebugRuntimeChecks {
    private val listeners = CopyOnWriteArrayList<DebugRuntimeViolationListener>()
    private val executor = Executors.newSingleThreadExecutor()

    fun enable(
        listener: DebugRuntimeViolationListener = DebugRuntimeViolationListener { event ->
            DiagnosticLogger.record(
                DiagnosticEvent(
                    eventCode = DiagnosticEventCode.RUNTIME_FAILURE,
                    stage = DiagnosticStage.USER_INTERFACE,
                    result = DiagnosticResult.FAILED,
                    entityHash = DiagnosticLogger.stableEntityHash(event.violation.name),
                    count = 1,
                    errorCategory = when (event.violation) {
                        DebugRuntimeViolation.CLEARTEXT_NETWORK -> DiagnosticErrorCategory.SECURITY
                        DebugRuntimeViolation.MAIN_THREAD_NETWORK -> DiagnosticErrorCategory.NETWORK
                        else -> DiagnosticErrorCategory.IO
                    },
                ),
            )
        },
        installPolicies: Boolean = true,
    ) {
        if (listeners.isEmpty()) {
            listeners.add(listener)
            if (installPolicies) {
                installPolicies()
            }
        } else if (listener !in listeners) {
            listeners.add(listener)
        }
    }

    fun report(event: DebugRuntimeEvent) {
        listeners.forEach { it.onViolation(event) }
    }

    private fun installPolicies() {
        val threadListener = StrictMode.OnThreadViolationListener {
            report(DebugRuntimeEvent(DebugRuntimeViolation.MAIN_THREAD_DISK, "THREAD_POLICY"))
        }
        val threadBuilder = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            threadBuilder.penaltyListener(executor, threadListener)
        }
        StrictMode.setThreadPolicy(threadBuilder.build())

        val vmListener = StrictMode.OnVmViolationListener {
            report(DebugRuntimeEvent(DebugRuntimeViolation.LEAKED_CLOSABLE, "VM_POLICY"))
        }
        val vmBuilder = StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectActivityLeaks()
            .detectCleartextNetwork()
            .penaltyLog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vmBuilder.penaltyListener(executor, vmListener)
        }
        StrictMode.setVmPolicy(vmBuilder.build())
    }

}
