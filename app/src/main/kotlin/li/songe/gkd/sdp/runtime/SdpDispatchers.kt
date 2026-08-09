package li.songe.gkd.sdp.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatcher boundary shared by runtime code and tests. */
data class SdpDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val a11yEvent: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    val a11yQuery: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    val a11yAction: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
)
