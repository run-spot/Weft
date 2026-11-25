package run.thespot.weft.map.kakaomap

/** Error reported by a Kakao Maps native platform surface. */
public class KakaoMapException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
