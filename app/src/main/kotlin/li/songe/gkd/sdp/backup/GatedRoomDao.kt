package li.songe.gkd.sdp.backup

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Room exposes DAO interfaces directly, so calls which do not use DbSet.withTransaction would
 * otherwise bypass the backup/recovery exclusion. The proxy only changes suspend DAO methods;
 * Flow/Paging accessors keep their normal cold-query behavior and are not writes themselves.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> gateRoomDao(daoType: Class<T>, delegate: T): T {
    synchronized(daoProxyLock) {
        daoProxyCache[delegate]?.let { return it as T }
    }
    require(daoType.isInterface)
    val proxy = Proxy.newProxyInstance(
        daoType.classLoader,
        arrayOf(daoType),
    ) { _, method, args ->
        val callArgs = args ?: emptyArray()
        if (callArgs.lastOrNull() !is Continuation<*>) {
            return@newProxyInstance invokeRoomDao(delegate, method, callArgs)
        }
        invokeGatedSuspend(delegate, method, callArgs)
    } as T
    synchronized(daoProxyLock) {
        daoProxyCache[delegate] = proxy
    }
    return proxy
}

private val daoProxyLock = Any()
private val daoProxyCache = IdentityHashMap<Any, Any>()

private fun invokeRoomDao(
    delegate: Any,
    method: java.lang.reflect.Method,
    args: Array<Any?>,
): Any? = try {
    method.invoke(delegate, *args)
} catch (error: InvocationTargetException) {
    throw (error.cause ?: error)
}

private fun invokeGatedSuspend(
    delegate: Any,
    method: java.lang.reflect.Method,
    args: Array<Any?>,
): Any? {
    val completion = args.last() as Continuation<Any?>
    val methodArgs = args.copyOf(args.size - 1)
    val operation: suspend () -> Any? = {
        withBackupDataMutationGate {
            suspendCoroutineUninterceptedOrReturn { delegateContinuation ->
                invokeRoomDao(
                    delegate = delegate,
                    method = method,
                    args = methodArgs + delegateContinuation,
                )
            }
        }
    }
    return operation.startCoroutineUninterceptedOrReturn(completion)
}
