package li.songe.gkd.sdp.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.notif.exposeNotif
import li.songe.gkd.sdp.remote.ExposeAction
import li.songe.gkd.sdp.remote.ExposeChannel
import li.songe.gkd.sdp.remote.ExposeCommandIssuer
import li.songe.gkd.sdp.remote.ExposeConsumeResult
import li.songe.gkd.sdp.remote.FileExposeCommandStore
import li.songe.gkd.sdp.syncFixState
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.SnapshotExt
import li.songe.gkd.sdp.util.componentName
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.privateStoreFolder
import li.songe.gkd.sdp.util.runMainPost
import li.songe.gkd.sdp.util.shFolder
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.store.writeTextAtomically
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import li.songe.gkd.sdp.R

class ExposeService : Service() {
    override fun onBind(intent: Intent?): Binder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appScope.launchTry(Dispatchers.IO) {
            try {
                handleIntent(intent)
            } finally {
                runMainPost(1_000) { stopSelf(startId) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleIntent(intent: Intent?) {
        val parsed = parseCommand(intent)
        if (parsed == null) {
            LogUtils.d("Expose command rejected")
            return
        }
        val (action, token, channel) = parsed
        when (issuer.consume(token, action, channel)) {
            is ExposeConsumeResult.Denied -> {
                LogUtils.d("Expose command authorization rejected")
                return
            }
            ExposeConsumeResult.Allowed -> Unit
        }
        when (action) {
            ExposeAction.STATUS_AUTOSTART -> StatusService.autoStart()
            ExposeAction.CAPTURE -> SnapshotExt.captureSnapshot()
            ExposeAction.SYNC_FIX -> {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_6c189aad4d), forced = true)
                syncFixState()
            }
        }
        if (channel == ExposeChannel.EXTERNAL) {
            deleteCommandScripts()
        }
    }

    private fun parseCommand(intent: Intent?): Triple<ExposeAction, String, ExposeChannel>? {
        if (
            intent == null ||
            intent.component != ComponentName(this, ExposeService::class.java) ||
            intent.action != ACTION_COMMAND ||
            intent.data != null ||
            intent.selector != null ||
            !intent.categories.isNullOrEmpty()
        ) return null
        val expectedExtras = setOf(EXTRA_ACTION, EXTRA_TOKEN, EXTRA_CHANNEL)
        if (intent.extras?.keySet().orEmpty() != expectedExtras) return null
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { value -> ExposeAction.entries.firstOrNull { it.name == value } }
            ?: return null
        val channel = intent.getStringExtra(EXTRA_CHANNEL)
            ?.let { value -> ExposeChannel.entries.firstOrNull { it.name == value } }
            ?: return null
        val token = intent.getStringExtra(EXTRA_TOKEN)
            ?.takeIf { it.length in 43..64 && it.all(::isTokenCharacter) }
            ?: return null
        if (channel == ExposeChannel.EXTERNAL && action != ExposeAction.SYNC_FIX) return null
        return Triple(action, token, channel)
    }

    override fun onCreate() {
        super.onCreate()
        exposeNotif.notifyService()
    }

    companion object {
        const val ACTION_COMMAND = "li.songe.gkd.sdp.action.EXPOSE_COMMAND"
        const val EXTRA_ACTION = "li.songe.gkd.sdp.extra.EXPOSE_ACTION"
        const val EXTRA_TOKEN = "li.songe.gkd.sdp.extra.EXPOSE_TOKEN"
        const val EXTRA_CHANNEL = "li.songe.gkd.sdp.extra.EXPOSE_CHANNEL"

        private val issuer by lazy {
            ExposeCommandIssuer(
                store = FileExposeCommandStore(
                    privateStoreFolder.resolve("expose-command-records.json"),
                ),
            )
        }
        private val externalGeneration = AtomicLong()

        suspend fun issueInternalIntent(action: ExposeAction): Intent {
            val issued = issuer.issue(action, ExposeChannel.INTERNAL)
            return commandIntent(action, issued.token, ExposeChannel.INTERNAL)
        }

        suspend fun refreshExternalCommandFile(): File {
            val generation = externalGeneration.incrementAndGet()
            val issued = issuer.issue(ExposeAction.SYNC_FIX, ExposeChannel.EXTERNAL)
            val file = shFolder.resolve("expose.sh")
            writeTextAtomically(
                file,
                buildExternalScript(
                    component = ExposeService::class.componentName.flattenToShortString(),
                    token = issued.token,
                ),
            )
            restrictToOwner(file)
            appScope.launch(Dispatchers.IO) {
                delay(ExposeCommandIssuer.EXTERNAL_TTL_MILLIS)
                if (externalGeneration.get() == generation) {
                    issuer.revoke(ExposeChannel.EXTERNAL)
                    deleteCommandScripts()
                }
            }
            return file
        }

        suspend fun clearCommandFiles() {
            externalGeneration.incrementAndGet()
            issuer.revoke(ExposeChannel.EXTERNAL)
            deleteCommandScripts()
        }

        private fun commandIntent(
            action: ExposeAction,
            token: String,
            channel: ExposeChannel,
        ): Intent = Intent(app, ExposeService::class.java).apply {
            this.action = ACTION_COMMAND
            putExtra(EXTRA_ACTION, action.name)
            putExtra(EXTRA_TOKEN, token)
            putExtra(EXTRA_CHANNEL, channel.name)
        }

        private fun buildExternalScript(component: String, token: String): String = """
            set -euo pipefail
            am start-foreground-service \
              -n $component \
              -a $ACTION_COMMAND \
              --es $EXTRA_ACTION ${ExposeAction.SYNC_FIX.name} \
              --es $EXTRA_TOKEN $token \
              --es $EXTRA_CHANNEL ${ExposeChannel.EXTERNAL.name}
        """.trimIndent()

        private fun deleteCommandScripts() {
            shFolder.resolve("expose.sh").delete()
            shFolder.resolve("start.sh").delete()
        }

        fun restrictToOwner(file: File) {
            runCatching { Os.chmod(file.absolutePath, 0x180) }.onFailure {
                file.setReadable(false, false)
                file.setWritable(false, false)
                file.setExecutable(false, false)
                file.setReadable(true, true)
                file.setWritable(true, true)
            }
        }

        private fun isTokenCharacter(character: Char): Boolean =
            character.isLetterOrDigit() || character == '-' || character == '_'
    }
}
