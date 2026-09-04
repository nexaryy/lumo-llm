package me.proton.android.lumo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.android.lumo.ActivityProvider
import me.proton.android.lumo.DefaultActivityProvider
import me.proton.android.lumo.data.db.LumoDatabase
import me.proton.android.lumo.data.db.dao.ConversationDao
import me.proton.android.lumo.data.db.dao.LumoDao
import me.proton.android.lumo.data.db.dao.MessageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun appPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(
            "lumo_prefs",
            Context.MODE_PRIVATE
        )

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("lumo_prefs") }
        )

    @Provides
    @Singleton
    fun lumoDatabase(@ApplicationContext context: Context): LumoDatabase =
        Room.databaseBuilder(context, LumoDatabase::class.java, LumoDatabase.NAME)
            .build()

    @Provides
    fun lumoDao(db: LumoDatabase): LumoDao = db.lumoDao()

    @Provides
    fun conversationDao(db: LumoDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun messageDao(db: LumoDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun activityProvider(): ActivityProvider =
        DefaultActivityProvider()
}
