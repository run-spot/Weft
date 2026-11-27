package run.thespot.weft.map.navermap

/** Runs all host shutdown steps even when native shape cleanup or engine finish fails. */
internal fun runNaverMapHostTeardown(
    cleanupShapes: () -> Unit,
    finishEngine: () -> Unit,
    clearReferences: () -> Unit,
) {
    var firstFailure: Throwable? = null

    fun attempt(action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            val primary = firstFailure
            if (primary == null) {
                firstFailure = failure
            } else if (primary !== failure) {
                primary.addSuppressed(failure)
            }
        }
    }

    attempt(cleanupShapes)
    attempt(finishEngine)
    attempt(clearReferences)
    firstFailure?.let { throw it }
}
