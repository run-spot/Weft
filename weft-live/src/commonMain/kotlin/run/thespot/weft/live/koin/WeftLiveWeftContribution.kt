package run.thespot.weft.live.koin

import org.koin.core.annotation.Single
import run.thespot.weft.core.WeftConfig
import run.thespot.weft.core.WeftContribution
import run.thespot.weft.live.LiveActivityPlatform
import run.thespot.weft.live.WeftLive

/** Koin contribution for live activities; the app supplies a platform implementation. */
@Single
public class WeftLiveWeftContribution(
    private val platform: LiveActivityPlatform,
) : WeftContribution {
    override fun install(config: WeftConfig) {
        config.install(WeftLive) {
            this.platform = this@WeftLiveWeftContribution.platform
        }
    }
}
