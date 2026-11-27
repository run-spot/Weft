package run.thespot.weft.demo.ios

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectBase
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow
import run.thespot.weft.demo.WeftDemoApp

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun main() {
    memScoped {
        val argv = arrayOf("WeftDemo").map { it.cstr.ptr }.toCValues()
        autoreleasepool {
            UIApplicationMain(1, argv, null, NSStringFromClass(WeftDemoAppDelegate))
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class WeftDemoAppDelegate @ObjCObjectBase.OverrideInit constructor() :
    UIResponder(),
    UIApplicationDelegateProtocol {
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta

    private var appWindow: UIWindow? = null

    override fun window(): UIWindow? = appWindow

    override fun setWindow(window: UIWindow?) {
        appWindow = window
    }

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?,
    ): Boolean {
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = ComposeUIViewController { WeftDemoApp() }
        appWindow = window
        window.makeKeyAndVisible()
        return true
    }
}
