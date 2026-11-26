package run.thespot.weft.push.koin

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/** Koin module that exposes the Firebase push contribution from this artifact. */
@Module
@ComponentScan
@Configuration
public class FirebasePushKoinModule
