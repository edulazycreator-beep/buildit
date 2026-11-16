package com.sketchwareai.app.di

import android.content.Context
import com.sketchwareai.core.model.IdGenerator
import com.sketchwareai.core.model.ProjectFactory
import com.sketchwareai.core.model.RandomIdGenerator
import com.sketchwareai.core.storage.FileProjectStore
import com.sketchwareai.core.storage.ProjectStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Scope for work that must outlive a screen, such as flushing an autosave. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideIdGenerator(): IdGenerator = RandomIdGenerator()

    @Provides @Singleton
    fun provideProjectFactory(ids: IdGenerator): ProjectFactory = ProjectFactory(ids)

    /** Projects live in app-private storage: isolated from other apps, included in backups. */
    @Provides @Singleton
    fun provideProjectStore(@ApplicationContext context: Context): ProjectStore =
        FileProjectStore(
            File(context.filesDir, "projects"),
            limits = FileProjectStore.Limits(minSnapshotIntervalMillis = 2 * 60 * 1000L),
        )

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
