package run.thespot.weft.map.kakaomap

/** Runs all host shutdown steps even when native shape cleanup or engine finish fails. */
internal fun runKakaoMapHostTeardown(
    cleanupShapes: () -> Unit,
    finishEngine: () -> Unit,
    clearReferences: () -> Unit,
) {
    var firstFailure: Throwable? = null

    fun attempt(action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure
        }
    }

    attempt(cleanupShapes)
    attempt(finishEngine)
    attempt(clearReferences)
    firstFailure?.let { throw it }
}
