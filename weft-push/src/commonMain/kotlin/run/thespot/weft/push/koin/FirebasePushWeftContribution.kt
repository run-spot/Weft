package run.thespot.weft.push.koin

import org.koin.core.annotation.Single
import run.thespot.weft.core.WeftConfig
import run.thespot.weft.core.WeftContribution
import run.thespot.weft.push.DefaultPushMessageProcessor
import run.thespot.weft.push.FirebasePush
import run.thespot.weft.push.PushMessageProcessor

/** Koin contribution for the Firebase Cloud Messaging capability. */
@Single
public class FirebasePushWeftContribution(
    private val messageProcessor: PushMessageProcessor = DefaultPushMessageProcessor,
) : WeftContribution {
    override fun install(config: WeftConfig) {
        config.install(FirebasePush) {
            this.messageProcessor = this@FirebasePushWeftContribution.messageProcessor
        }
    }
}
