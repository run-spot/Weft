package run.thespot.weft.map.kakaomap

import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftHealthCheck
import run.thespot.weft.core.WeftPlugin
import run.thespot.weft.core.map.MapContentRenderer
import run.thespot.weft.core.map.MapPlugin
import run.thespot.weft.core.map.rememberMapContent
import kotlin.reflect.KClass

/** Configuration for the Kakao Maps native SDK. */
public class KakaoMapConfig {
    /** Native app key used when a platform map surface initializes the SDK. */
    public var nativeAppKey: String = ""
}

/** Kakao Maps provider for the shared Weft map capability. */
public object KakaoMap : WeftPlugin<KakaoMapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class

    override val pluginName: String = "KakaoMap"

    override fun prepare(block: KakaoMapConfig.() -> Unit): MapPlugin {
        val config = KakaoMapConfig().apply(block)
        require(config.nativeAppKey.isNotBlank()) {
            "Kakao Maps native app key must not be blank."
        }
        return KakaoMapPlugin(config.nativeAppKey)
    }

    override fun install(plugin: MapPlugin, scope: Weft): Unit = Unit

    override fun healthCheck(plugin: MapPlugin): List<WeftHealthCheck> = emptyList()
}

private class KakaoMapPlugin(
    private val nativeAppKey: String,
) : MapPlugin {
    override val renderer: MapContentRenderer = { state, modifier, content, onError ->
        KakaoMapPlatformSurface(
            nativeAppKey = nativeAppKey,
            state = state,
            content = rememberMapContent(content),
            modifier = modifier,
            onError = onError,
        )
    }
}
