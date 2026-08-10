package li.songe.gkd.sdp.performance

/** Startup completion hook; kept injectable so debug/release builds differ. */
object AppDrawReporter {
    fun reportFullyDrawn() = Unit
}
