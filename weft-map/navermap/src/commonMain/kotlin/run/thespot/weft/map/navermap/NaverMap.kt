package run.thespot.weft.map.navermap

import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftHealthCheck
import run.thespot.weft.core.WeftPlugin
import run.thespot.weft.core.map.MapContentRenderer
import run.thespot.weft.core.map.MapPlugin
import run.thespot.weft.core.map.rememberMapContent
import kotlin.reflect.KClass

/** Configuration for the NAVER Maps native SDK. */
public class NaverMapConfig {
    /** NAVER Cloud Platform key ID registered for the Android package/iOS bundle ID. */
    public var ncpKeyId: String = ""
}

/** NAVER Maps provider for the shared Weft map capability. */
public object NaverMap : WeftPlugin<NaverMapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class

    override val pluginName: String = "NaverMap"

    override fun prepare(block: NaverMapConfig.() -> Unit): MapPlugin {
        val config = NaverMapConfig().apply(block)
        require(config.ncpKeyId.isNotBlank()) {
            "NAVER Maps NCP key ID must not be blank."
        }
        return NaverMapPlugin(config.ncpKeyId)
    }

    override fun install(plugin: MapPlugin, scope: Weft): Unit = Unit

    override fun healthCheck(plugin: MapPlugin): List<WeftHealthCheck> = emptyList()
}

private class NaverMapPlugin(
    private val ncpKeyId: String,
) : MapPlugin {
    override val renderer: MapContentRenderer = { state, modifier, content, onError ->
        NaverMapPlatformSurface(
            ncpKeyId = ncpKeyId,
            state = state,
            content = rememberMapContent(content),
            modifier = modifier,
            onError = onError,
        )
    }
}
