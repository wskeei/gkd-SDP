package li.songe.gkd.sdp.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import li.songe.gkd.sdp.data.RpcError
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun CoroutineScope.launchTry(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    silent: Boolean = false,
    block: suspend CoroutineScope.() -> Unit,
) = launch(context, start) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: InterruptRuleMatchException) {
    } catch (e: Throwable) {
        LogUtils.d(e)
        if (!silent) {
            toast(stableErrorMessage(e), loc = "", forced = e is RpcError)
        }
    }
}

@Composable
fun CoroutineScope.launchAsFn(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): () -> Unit = {
    launch(context, start) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LogUtils.d(e)
            toast(stableErrorMessage(e), loc = "")
        }
    }
}

@Composable
fun <T> CoroutineScope.launchAsFn(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.(T) -> Unit,
): (T) -> Unit = {
    launch(context, start) {
        try {
            block(it)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LogUtils.d(e)
            toast(stableErrorMessage(e), loc = "")
        }
    }
}

private fun stableErrorMessage(error: Throwable): String =
    DiagnosticLogger.userMessage(error)

suspend fun stopCoroutine(): Nothing {
    currentCoroutineContext()[Job]?.cancel()
    yield()
    // the following code will not be run
    throw CancellationException("Coroutine stopped")
}
