package me.proton.android.lumo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.lumo.analytics.DefaultLumoAnalytics
import me.proton.android.lumo.analytics.LumoAnalytics
import me.proton.android.lumo.initializer.AppStartupInitializer
import me.proton.android.lumo.initializer.AppStartupInitializerImpl
import javax.inject.Singleton

/**
 * gms DI module — Sentry is still wired up here for the gms build variant, but everything else
 * (billing, in-app review, Proton WebView bridge) is gone after the WebView removal.
 */
@Module
@InstallIn(SingletonComponent::class)
object SentryModule {

    @Provides
    @Singleton
    fun mainAnalytics(): LumoAnalytics = DefaultLumoAnalytics()

    @Provides
    @Singleton
    fun appStartupInitializer(): AppStartupInitializer =
        AppStartupInitializerImpl()
}
