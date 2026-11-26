package run.thespot.weft.login.koin

import org.koin.core.annotation.Property
import org.koin.core.annotation.Single
import run.thespot.weft.core.WeftConfig
import run.thespot.weft.core.WeftContribution
import run.thespot.weft.login.WeftLogin

/** Koin contribution for the Supabase Auth login capability. */
@Single
public class SupabaseLoginWeftContribution(
    @Property("weft.login.supabaseUrl") private val supabaseUrl: String,
    @Property("weft.login.publishableKey") private val publishableKey: String,
    @Property("weft.login.redirectUri") private val redirectUri: String,
) : WeftContribution {
    override fun install(config: WeftConfig) {
        config.install(WeftLogin) {
            this.supabaseUrl = this@SupabaseLoginWeftContribution.supabaseUrl
            this.publishableKey = this@SupabaseLoginWeftContribution.publishableKey
            this.redirectUri = this@SupabaseLoginWeftContribution.redirectUri
        }
    }
}
