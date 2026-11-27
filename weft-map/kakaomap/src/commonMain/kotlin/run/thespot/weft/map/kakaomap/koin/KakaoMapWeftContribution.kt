package run.thespot.weft.map.kakaomap.koin

import org.koin.core.annotation.Property
import org.koin.core.annotation.Single
import run.thespot.weft.core.WeftConfig
import run.thespot.weft.core.WeftContribution
import run.thespot.weft.map.kakaomap.KakaoMap

/**
 * Koin contribution for the Kakao Maps capability.
 *
 * The native app key is supplied by the consuming application's Koin properties under
 * `weft.kakaoMap.nativeAppKey`.
 */
@Single
public class KakaoMapWeftContribution(
    @Property("weft.kakaoMap.nativeAppKey")
    private val nativeAppKey: String,
) : WeftContribution {
    override fun install(config: WeftConfig) {
        config.install(KakaoMap) {
            this.nativeAppKey = this@KakaoMapWeftContribution.nativeAppKey
        }
    }
}
