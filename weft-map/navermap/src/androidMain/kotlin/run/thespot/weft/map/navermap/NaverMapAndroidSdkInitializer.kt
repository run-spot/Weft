package run.thespot.weft.map.navermap

internal object NaverMapAndroidSdkInitializer {
    private var initializedNcpKeyId: String? = null

    @Synchronized
    fun initialize(ncpKeyId: String, initializeSdk: (String) -> Unit) {
        when (val existingKey = initializedNcpKeyId) {
            null -> {
                initializeSdk(ncpKeyId)
                initializedNcpKeyId = ncpKeyId
            }

            ncpKeyId -> Unit

            else -> throw NaverMapException(
                "Naver Maps SDK is already initialized with a different NCP key ID.",
            )
        }
    }

    @Synchronized
    internal fun resetForTest() {
        initializedNcpKeyId = null
    }
}
