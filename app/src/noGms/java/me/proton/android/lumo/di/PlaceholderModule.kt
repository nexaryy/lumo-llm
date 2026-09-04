package me.proton.android.lumo.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.lumo.analytics.DefaultLumoAnalytics
import me.proton.android.lumo.analytics.LumoAnalytics
import me.proton.android.lumo.initializer.AppStartupInitializer
import javax.inject.Singleton

/**
 * noGms DI module — provides no-op implementations for analytics, initialization, etc.
 * The Proton WebView bridge, billing, in-app review and feature flag dependencies have been
 * removed — this module now only provides the things the rest of the app actually needs.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaceholderModule {

    @Provides
    @Singleton
    fun mainAnalytics(): LumoAnalytics = DefaultLumoAnalytics()

    @Provides
    @Singleton
    fun appStartupInitializer(): AppStartupInitializer =
        object : AppStartupInitializer {
            @Suppress("EmptyFunctionBlock")
            override fun initialize(context: Context) {
            }
        }
}
