package run.thespot.weft.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import run.thespot.weft.core.LocalWeft
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftApp
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.Circle
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPlugin
import run.thespot.weft.core.map.Marker
import run.thespot.weft.core.map.Polygon
import run.thespot.weft.core.map.Polyline
import run.thespot.weft.core.map.WeftMap
import run.thespot.weft.core.map.WeftMapState
import run.thespot.weft.map.kakaomap.KakaoMap

private val Ink = Color(0xFF172033)
private val MutedInk = Color(0xFF647087)
private val Canvas = Color(0xFFF3F5F9)
private val PanelColor = Color.White
private val BorderColor = Color(0xFFE0E5EF)
private val Accent = Color(0xFF3859D8)
private val Success = Color(0xFF167A52)
private val Notice = Color(0xFF9A6200)

private enum class DemoPage(val label: String) {
    Overview("Overview"),
    Map("Map"),
    Login("Login"),
    Push("Push"),
}

/** Shared demo UI. The native entry points call this composable from Android and iOS. */
@Composable
public fun WeftDemoApp() {
    var selectedPage by remember { mutableStateOf(DemoPage.Overview) }
    var enteredNativeAppKey by remember { mutableStateOf("") }
    var activeNativeAppKey by remember { mutableStateOf("") }
    var mapError by remember { mutableStateOf<String?>(null) }

    val weft = remember(activeNativeAppKey) {
        Weft {
            if (activeNativeAppKey.isNotBlank()) {
                install(KakaoMap) {
                    nativeAppKey = activeNativeAppKey
                }
            }
        }
    }

    // A new runtime starts a new WeftApp composition, matching the app-level runtime lifecycle.
    key(weft) {
        WeftApp(weft) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Canvas)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Header()
                Spacer(Modifier.height(18.dp))
                PageNavigation(
                    selectedPage = selectedPage,
                    onSelect = { selectedPage = it },
                )
                Spacer(Modifier.height(18.dp))

                when (selectedPage) {
                    DemoPage.Overview -> OverviewPage(onOpen = { selectedPage = it })
                    DemoPage.Map -> MapPage(
                        enteredNativeAppKey = enteredNativeAppKey,
                        activeNativeAppKey = activeNativeAppKey,
                        mapError = mapError,
                        onKeyChange = { enteredNativeAppKey = it },
                        onInstallMap = {
                            val key = enteredNativeAppKey.trim()
                            if (key.isNotEmpty()) {
                                mapError = null
                                activeNativeAppKey = key
                            }
                        },
                        onRemoveMap = {
                            mapError = null
                            activeNativeAppKey = ""
                            enteredNativeAppKey = ""
                        },
                        onMapError = { throwable ->
                            mapError = throwable.message ?: throwable::class.simpleName ?: "Map error"
                        },
                    )
                    DemoPage.Login -> CapabilityStatusPage(
                        title = "Login",
                        artifact = "run.thespot.weft:weft-login",
                        milestone = "M0 · build and publication scaffold",
                        message = "This module does not expose a sign-in API yet, so there is no login flow to run in the demo.",
                    )
                    DemoPage.Push -> CapabilityStatusPage(
                        title = "Push",
                        artifact = "run.thespot.weft:weft-push",
                        milestone = "M0 · build and publication scaffold",
                        message = "This module does not expose a push registration or notification API yet, so there is no push flow to run in the demo.",
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column {
        BasicText(
            text = "Weft Demo",
            style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "A Kotlin-only consumer for Weft capability modules",
            style = TextStyle(color = MutedInk, fontSize = 14.sp),
        )
    }
}

@Composable
private fun PageNavigation(
    selectedPage: DemoPage,
    onSelect: (DemoPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DemoPage.values().forEach { page ->
            val selected = page == selectedPage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) Accent else PanelColor, RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(1.dp, if (selected) Accent else BorderColor),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(page) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = page.label,
                    style = TextStyle(
                        color = if (selected) Color.White else MutedInk,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OverviewPage(onOpen: (DemoPage) -> Unit) {
    val weft = LocalWeft.current
    val mapInstalled = weft.getOrNull<MapPlugin>() != null
    val healthChecks = weft.healthChecks()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            BasicText("Runtime", style = titleStyle())
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "Weft is created once and provided with WeftApp. The map page resolves MapPlugin through LocalWeft.",
                style = bodyStyle(),
            )
            Spacer(Modifier.height(14.dp))
            StatusLine("weft-core", "Installed", Success)
            Spacer(Modifier.height(8.dp))
            StatusLine(
                name = "weft-kakaomap",
                status = if (mapInstalled) "Installed" else "Add a native app key",
                color = if (mapInstalled) Success else Notice,
            )
            Spacer(Modifier.height(8.dp))
            StatusLine("weft-login", "M0 · no public API", Notice)
            Spacer(Modifier.height(8.dp))
            StatusLine("weft-push", "M0 · no public API", Notice)
        }

        Panel {
            BasicText("Reported health checks", style = titleStyle())
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = if (healthChecks.isEmpty()) {
                    "No installed plugin reported a probe. Weft collects checks but does not send network requests."
                } else {
                    healthChecks.joinToString(separator = "\n") { "${it.name} · ${it.method} ${it.url}" }
                },
                style = bodyStyle(),
            )
        }

        Panel {
            BasicText("Open a module", style = titleStyle())
            Spacer(Modifier.height(12.dp))
            ModuleRow(
                name = "Map",
                detail = "Kakao Maps · markers and overlays",
                status = if (mapInstalled) "Ready" else "Key needed",
                statusColor = if (mapInstalled) Success else Notice,
                onClick = { onOpen(DemoPage.Map) },
            )
            Spacer(Modifier.height(10.dp))
            ModuleRow(
                name = "Login",
                detail = "OAuth / PKCE API is not implemented",
                status = "M0",
                statusColor = Notice,
                onClick = { onOpen(DemoPage.Login) },
            )
            Spacer(Modifier.height(10.dp))
            ModuleRow(
                name = "Push",
                detail = "Registration API is not implemented",
                status = "M0",
                statusColor = Notice,
                onClick = { onOpen(DemoPage.Push) },
            )
        }
    }
}

@Composable
private fun MapPage(
    enteredNativeAppKey: String,
    activeNativeAppKey: String,
    mapError: String?,
    onKeyChange: (String) -> Unit,
    onInstallMap: () -> Unit,
    onRemoveMap: () -> Unit,
    onMapError: (Throwable) -> Unit,
) {
    val mapPluginInstalled = LocalWeft.current.getOrNull<MapPlugin>() != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            BasicText("Kakao map provider", style = titleStyle())
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "This page installs KakaoMap as the MapPlugin capability, then renders the same map contract on Android and iOS.",
                style = bodyStyle(),
            )
            if (!mapPluginInstalled) {
                Spacer(Modifier.height(16.dp))
                BasicText("Kakao native app key", style = labelStyle())
                Spacer(Modifier.height(7.dp))
                BasicTextField(
                    value = enteredNativeAppKey,
                    onValueChange = onKeyChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelColor, RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    label = "Install map provider",
                    enabled = enteredNativeAppKey.isNotBlank(),
                    onClick = onInstallMap,
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "The key stays in memory and is not written to a file.",
                    style = captionStyle(),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill("Provider installed", Success)
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = "${activeNativeAppKey.length} characters",
                        style = captionStyle(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                ActionButton("Remove provider", onClick = onRemoveMap)
            }
        }

        if (mapPluginInstalled) {
            mapError?.let { message ->
                Panel {
                    BasicText("Map reported an error", style = titleStyle())
                    Spacer(Modifier.height(6.dp))
                    BasicText(message, style = bodyStyle())
                }
            }

            val mapState = remember {
                WeftMapState(
                    CameraPosition(
                        target = LatLng(37.5665, 126.9780),
                        zoom = 15f,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(PanelColor, RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(16.dp)),
            ) {
                WeftMap(
                    state = mapState,
                    modifier = Modifier.fillMaxSize(),
                    onError = onMapError,
                    content = {
                        Marker(
                            MapMarker(
                                id = "weft-demo-city-hall",
                                position = LatLng(37.5665, 126.9780),
                                title = "Weft Demo",
                                snippet = "Seoul City Hall",
                            ),
                        )
                        Polyline(
                            id = "weft-demo-route",
                            points = listOf(
                                LatLng(37.5665, 126.9780),
                                LatLng(37.5680, 126.9810),
                                LatLng(37.5700, 126.9820),
                            ),
                            color = Accent,
                            widthDp = 6f,
                        )
                        Polygon(
                            id = "weft-demo-area",
                            points = listOf(
                                LatLng(37.5658, 126.9755),
                                LatLng(37.5678, 126.9765),
                                LatLng(37.5684, 126.9795),
                                LatLng(37.5662, 126.9802),
                            ),
                            fillColor = Color(0x333859D8),
                            strokeColor = Color(0xCC3859D8),
                        )
                        Circle(
                            id = "weft-demo-pickup-radius",
                            center = LatLng(37.5665, 126.9780),
                            radiusMeters = 250.0,
                            fillColor = Color(0x223B9B72),
                            strokeColor = Color(0xCC167A52),
                        )
                    },
                )
            }

            BasicText(
                text = "The sample renders one marker, a polyline, a polygon, and a circle through WeftMap.",
                style = captionStyle(),
            )
        }
    }
}

@Composable
private fun CapabilityStatusPage(
    title: String,
    artifact: String,
    milestone: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            StatusPill(milestone, Notice)
            Spacer(Modifier.height(14.dp))
            BasicText(title, style = TextStyle(color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            BasicText(artifact, style = captionStyle())
            Spacer(Modifier.height(14.dp))
            BasicText(message, style = bodyStyle())
        }
        Panel {
            BasicText("How to exercise it later", style = titleStyle())
            Spacer(Modifier.height(7.dp))
            BasicText(
                text = "When this module gains a public capability API, add its WeftPlugin to the runtime and expose an interactive flow on this page.",
                style = bodyStyle(),
            )
        }
    }
}

@Composable
private fun ModuleRow(
    name: String,
    detail: String,
    status: String,
    statusColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F9FC), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(name, style = labelStyle())
            Spacer(Modifier.height(3.dp))
            BasicText(detail, style = captionStyle())
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(status, statusColor)
    }
}

@Composable
private fun StatusLine(name: String, status: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(name, style = labelStyle())
        StatusPill(status, color)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) Accent else Color(0xFFAFB7C6)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelColor, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

private fun titleStyle() = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

private fun labelStyle() = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

private fun bodyStyle() = TextStyle(color = MutedInk, fontSize = 13.sp, lineHeight = 19.sp)

private fun captionStyle() = TextStyle(color = MutedInk, fontSize = 11.sp, lineHeight = 16.sp)
