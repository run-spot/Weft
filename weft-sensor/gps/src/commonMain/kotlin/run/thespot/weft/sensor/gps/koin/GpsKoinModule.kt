package run.thespot.weft.sensor.gps.koin

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import run.thespot.weft.core.WeftConfig
import run.thespot.weft.core.WeftContribution
import run.thespot.weft.sensor.gps.Gps

/** Koin contribution for the native GPS location capability. */
@Single
public class GpsWeftContribution : WeftContribution {
    override fun install(config: WeftConfig) {
        config.install(Gps)
    }
}

/** Koin module that exposes this artifact's Weft contribution. */
@Module
@ComponentScan
@Configuration
public class GpsKoinModule
