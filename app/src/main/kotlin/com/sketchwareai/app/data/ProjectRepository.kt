package com.sketchwareai.app.data

import android.content.Context
import android.net.Uri
import com.sketchwareai.app.di.IoDispatcher
import com.sketchwareai.core.model.IdGenerator
import com.sketchwareai.core.model.NewProjectRequest
import com.sketchwareai.core.model.ProjectConfig
import com.sketchwareai.core.model.ProjectFactory
import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.model.ProjectSchema
import com.sketchwareai.core.storage.ProjectSnapshot
import com.sketchwareai.core.storage.ProjectStore
import com.sketchwareai.core.storage.ProjectSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-facing entry point to projects. Moves all disk work off the main thread and keeps a
 * observable project list that every screen shares, refreshed after each mutation.
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val store: ProjectStore,
    private val factory: ProjectFactory,
    private val ids: IdGenerator,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _projects = MutableStateFlow<List<ProjectSummary>?>(null)

    /** `null` until the first load completes. Ordered most-recently-used first. */
    val projects: StateFlow<List<ProjectSummary>?> = _projects.asStateFlow()

    suspend fun refresh() {
        _projects.value = withContext(io) { store.list() }
    }

    suspend fun create(request: NewProjectRequest): ProjectSchema =
        mutate { store.create(factory.create(request)) }

    /** Loads a project for editing and records it as recently opened. */
    suspend fun open(id: ProjectId): ProjectSchema =
        mutate { store.load(id).also { store.markOpened(id) } }

    suspend fun save(project: ProjectSchema): ProjectSchema = mutate { store.save(project) }

    /** Plain load without touching "recently opened". */
    suspend fun load(id: ProjectId): ProjectSchema = withContext(io) { store.load(id) }

    suspend fun updateConfig(project: ProjectSchema, config: ProjectConfig): ProjectSchema =
        mutate { store.save(factory.updateConfig(project, config)) }

    suspend fun rename(id: ProjectId, appName: String): ProjectSchema =
        mutate { store.save(factory.rename(store.load(id), appName)) }

    suspend fun duplicate(id: ProjectId, appName: String, packageName: String): ProjectSchema =
        mutate { store.duplicate(id) { factory.duplicate(it, appName, packageName) } }

    suspend fun delete(id: ProjectId) = mutate { store.delete(id) }

    suspend fun history(id: ProjectId): List<ProjectSnapshot> = withContext(io) { store.history(id) }

    suspend fun restore(id: ProjectId, revision: Long): ProjectSchema = mutate { store.restoreSnapshot(id, revision) }

    suspend fun recover(id: ProjectId): ProjectSchema = mutate { store.recover(id) }

    suspend fun export(id: ProjectId, destination: Uri) = withContext(io) {
        val out = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Cannot write to the selected file")
        out.use { store.export(id, it) }
    }

    suspend fun import(source: Uri): ProjectSchema = mutate {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Cannot read the selected file")
        input.use { store.import(it) { ids.projectId() } }
    }

    fun suggestDuplicatePackage(packageName: String): String =
        factory.suggestDuplicatePackage(packageName, _projects.value.orEmpty().map { it.packageName }.toSet())

    private suspend fun <T> mutate(block: () -> T): T {
        val result = withContext(io) { block() }
        refresh()
        return result
    }
}
