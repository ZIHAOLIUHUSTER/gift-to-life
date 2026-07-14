package com.gift.tolife.core.database.di

import android.content.Context
import androidx.room.Room
import com.gift.tolife.core.database.AppDatabase
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.EntryTransactions
import com.gift.tolife.core.database.Migrations
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gift_tolife.db"
        ).addMigrations(
            Migrations.MIGRATION_1_3,
            Migrations.MIGRATION_3_4
        ).build()
    }

    @Provides
    fun provideEntryDao(database: AppDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideEntryTagDao(database: AppDatabase): EntryTagDao = database.entryTagDao()

    @Provides
    @Singleton
    fun provideEntryRepository(
        entryDao: EntryDao,
        entryTagDao: EntryTagDao,
        entryTransactions: EntryTransactions
    ): EntryRepository {
        return EntryRepository(entryDao, entryTagDao, entryTransactions)
    }
}
