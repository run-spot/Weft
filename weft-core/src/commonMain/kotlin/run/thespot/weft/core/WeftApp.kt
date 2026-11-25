package run.thespot.weft.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The [Weft] runtime available to the current composition.
 *
 * Screens read the runtime from here instead of receiving it as a parameter, so a composable that
 * needs a capability depends only on that capability:
 *
 * ```kotlin
 * WeftApp(weft) {
 *     WeftMap(WeftMapState(CameraPosition(LatLng(37.5665, 126.9780)))) {
 *         Marker(MapMarker(id = "office", position = LatLng(37.5665, 126.9780)))
 *     }
 * }
 * ```
 *
 * A preview or a UI test provides a fake runtime the same way the app does,
 * `WeftApp(fakeWeft) { ... }`, and composables below it stay unchanged.
 *
 * Reading the local outside [WeftApp] is a programming error and fails during composition, because
 * a missing runtime would otherwise surface much later, at the first capability lookup. The value is
 * static: replacing the runtime requires a new composition rather than a recomposition, which
 * matches the lifecycle of an app-level runtime.
 */
public val LocalWeft: ProvidableCompositionLocal<Weft> = staticCompositionLocalOf {
    error("Weft is not provided. Wrap your UI with WeftApp(weft) { ... }.")
}

/**
 * Provides [weft] to [content] through [LocalWeft], the single place an app or a test hands the
 * runtime to its UI:
 *
 * ```kotlin
 * setContent {
 *     WeftApp(weft) {
 *         App()
 *     }
 * }
 * ```
 *
 * @param weft runtime created with [Weft]; capabilities are resolved from it with
 *   [Weft.get] and [Weft.getOrNull].
 * @param content composable tree that may read [LocalWeft].
 */
@Composable
public fun WeftApp(
    weft: Weft,
    content: @Composable () -> Unit,
): Unit {
    CompositionLocalProvider(LocalWeft provides weft, content = content)
}
