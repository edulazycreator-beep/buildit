package com.sketchwareai.core.storage

import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.model.ProjectSchema
import java.io.InputStream
import java.io.OutputStream

data class ProjectSummary(
    val id: ProjectId,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val screenCount: Int,
    val updatedAt: Long,
    /** Last time the project was opened in the editor; null if never. Drives "Recent projects". */
    val lastOpenedAt: Long?,
    /** True when project.json cannot be read; the UI offers [ProjectStore.recover]. */
    val damaged: Boolean = false,
)

/** An automatic snapshot taken before each save. Forms the project's version history. */
data class ProjectSnapshot(val projectId: ProjectId, val revision: Long, val savedAt: Long)

sealed class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val id: ProjectId) : StoreException("Project $id not found")
    class Conflict(val id: ProjectId, val expected: Long, val actual: Long) :
        StoreException("Project $id was modified elsewhere (expected revision $expected, found $actual)")
    /** The main project file is unreadable. [snapshots] lists what [ProjectStore.recover] can restore. */
    class Corrupt(val id: ProjectId, val snapshots: List<ProjectSnapshot>, cause: Throwable) :
        StoreException("Project $id is damaged: ${cause.message}", cause)
    class InvalidArchive(message: String, cause: Throwable? = null) : StoreException(message, cause)
}

/**
 * Persistence for projects. Implementations must be safe for concurrent use and must never
 * leave a half-written project file behind (atomic replace).
 */
interface ProjectStore {
    fun list(): List<ProjectSummary>
    fun exists(id: ProjectId): Boolean
    fun load(id: ProjectId): ProjectSchema

    /**
     * Saves [project] if the stored revision equals `project.revision` (optimistic locking).
     * Returns the saved project with `revision + 1` and an updated timestamp.
     */
    fun save(project: ProjectSchema): ProjectSchema

    /** Writes a brand-new project (revision 0). Fails if the id already exists. */
    fun create(project: ProjectSchema): ProjectSchema

    fun delete(id: ProjectId)
    fun markOpened(id: ProjectId)

    /** Copies the project's files (sources, resources) as well as its model. */
    fun duplicate(id: ProjectId, transform: (ProjectSchema) -> ProjectSchema): ProjectSchema

    fun history(id: ProjectId): List<ProjectSnapshot>
    fun loadSnapshot(id: ProjectId, revision: Long): ProjectSchema

    /** Restores a snapshot as a new revision; the current state is itself snapshotted first. */
    fun restoreSnapshot(id: ProjectId, revision: Long): ProjectSchema

    /** Repairs a [StoreException.Corrupt] project from its newest readable snapshot. */
    fun recover(id: ProjectId): ProjectSchema

    fun export(id: ProjectId, out: OutputStream)

    /** Imports an archive produced by [export]. Always assigns a new project id. */
    fun import(input: InputStream, newId: () -> ProjectId): ProjectSchema
}
