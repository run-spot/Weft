package run.thespot.weft.map.navermap

/** Error reported by a NAVER Maps native platform surface. */
public class NaverMapException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
