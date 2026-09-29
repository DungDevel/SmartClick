package com.example.smartclick.app.di

import android.content.Context
import androidx.room.Room
import com.example.smartclick.app.data.local.db.ALL_MIGRATIONS
import com.example.smartclick.app.data.local.db.AppDatabase
import com.example.smartclick.app.data.local.db.TvDeviceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartclick.db"
        )
        // FIX-12: migration tường minh thay cho fallbackToDestructiveMigration() để giữ dữ liệu người dùng.
        .addMigrations(*ALL_MIGRATIONS)
        .build()
    }

    @Provides
    fun provideTvDeviceDao(database: AppDatabase): TvDeviceDao {
        return database.tvDeviceDao()
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }
}
