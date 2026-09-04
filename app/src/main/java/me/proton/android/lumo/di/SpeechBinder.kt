package me.proton.android.lumo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.lumo.speech.SpeechRepository
import me.proton.android.lumo.speech.SpeechRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechBinder {

    @Binds
    @Singleton
    abstract fun speechRepository(speechRepository: SpeechRepositoryImpl): SpeechRepository
}
