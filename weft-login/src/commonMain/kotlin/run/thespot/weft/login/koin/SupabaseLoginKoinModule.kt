package run.thespot.weft.login.koin

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/** Koin module that exposes the Supabase login contribution from this artifact. */
@Module
@ComponentScan
@Configuration
public class SupabaseLoginKoinModule
