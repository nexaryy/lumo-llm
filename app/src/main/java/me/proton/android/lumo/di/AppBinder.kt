package me.proton.android.lumo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.lumo.data.repository.ThemeRepository
import me.proton.android.lumo.data.repository.ThemeRepositoryImpl
import javax.inject.Singleton

/**
 * App binder — slimmed down after the Proton WebView removal.
 *
 * The legacy WebAppRepository / FeatureGatekeeper / LegacyFeatureFlagDataSource were all
 * tied to the Proton web app bridge and are no longer needed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBinder {

    @Binds
    @Singleton
    abstract fun themeRepository(impl: ThemeRepositoryImpl): ThemeRepository
}
