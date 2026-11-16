package com.sketchwareai.core.storage

import com.sketchwareai.core.model.ENTITY_ID_REGEX
import com.sketchwareai.core.model.IdPrefix
import com.sketchwareai.core.model.ProjectCodec
import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.model.ProjectSchema
import com.sketchwareai.core.model.ProjectValidator
import com.sketchwareai.core.model.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.withLock

/**
 * File-system [ProjectStore]. Each project is a directory, which keeps projects isolated
 * from one another and makes export/import a matter of archiving one folder:
 *
 * ```
 * <root>/<projectId>/
 *   project.json            current model (atomically replaced on save)
 *   state.json              UI state that must not bump the revision (last opened)
 *   history/r<rev>-<ts>.json snapshots taken before each save (bounded)
 *   src/ , res/ ...         user-owned files referenced by the model
 * ```
 */
class FileProjectStore(
    private val root: File,
    private val codec: ProjectCodec = ProjectCodec(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val limits: Limits = Limits(),
) : ProjectStore {

    data class Limits(
        val maxHistory: Int = 30,
        val maxArchiveEntries: Int = 5_000,
        val maxArchiveEntryBytes: Long = 64L * 1024 * 1024,
        val maxArchiveTotalBytes: Long = 256L * 1024 * 1024,
    )

    private val lock = ReentrantLock()

    init {
        root.mkdirs()
        require(root.isDirectory) { "Project root $root is not a directory" }
    }

    // ------------------------------------------------------------------ queries

    override fun list(): List<ProjectSummary> = lock.withLock {
        root.listFiles().orEmpty()
            .filter { it.isDirectory && isProjectId(it.name) && File(it, PROJECT_FILE).exists() }
            .map { dir ->
                val id = ProjectId(dir.name)
                val opened = readState(dir)
                try {
                    val p = codec.decode(File(dir, PROJECT_FILE).readText())
                    ProjectSummary(id, p.config.appName, p.config.packageName, p.config.versionName, p.screens.size, p.updatedAt, opened)
                } catch (e: Exception) {
                    ProjectSummary(id, id.value, "", "", 0, dir.lastModified(), opened, damaged = true)
                }
            }
            .sortedWith(compareByDescending<ProjectSummary> { it.lastOpenedAt ?: it.updatedAt })
    }

    override fun exists(id: ProjectId): Boolean = File(dirOf(id), PROJECT_FILE).exists()

    override fun load(id: ProjectId): ProjectSchema = lock.withLock {
        val file = File(dirOf(id), PROJECT_FILE)
        if (!file.exists()) throw StoreException.NotFound(id)
        val project = try {
            codec.decode(file.readText())
        } catch (e: Exception) {
            throw StoreException.Corrupt(id, historyUnlocked(id), e)
        }
        if (project.id != id) throw StoreException.Corrupt(id, historyUnlocked(id), IllegalStateException("id mismatch"))
        project
    }

    // ------------------------------------------------------------------ mutations

    override fun create(project: ProjectSchema): ProjectSchema = lock.withLock {
        val dir = dirOf(project.id)
        if (dir.exists()) throw IllegalStateException("Project ${project.id} already exists")
        val fresh = project.copy(revision = 0)
        val tmp = tempDir("create")
        try {
            writeAtomically(File(tmp, PROJECT_FILE), codec.encode(fresh))
            moveDir(tmp, dir)
        } finally {
            tmp.deleteRecursively()
        }
        fresh
    }

    override fun save(project: ProjectSchema): ProjectSchema = lock.withLock {
        val current = load(project.id)
        if (current.revision != project.revision) {
            throw StoreException.Conflict(project.id, project.revision, current.revision)
        }
        val dir = dirOf(project.id)
        snapshot(dir, current)
        val saved = project.copy(revision = current.revision + 1, updatedAt = maxOf(clock(), current.updatedAt))
        writeAtomically(File(dir, PROJECT_FILE), codec.encode(saved))
        saved
    }

    override fun delete(id: ProjectId) = lock.withLock {
        val dir = dirOf(id)
        if (!dir.exists()) throw StoreException.NotFound(id)
        // Rename first so a crash mid-delete never leaves a half-deleted project in the list.
        val doomed = File(root, ".deleting-${id.value}-${clock()}")
        moveDir(dir, doomed)
        doomed.deleteRecursively()
        Unit
    }

    override fun markOpened(id: ProjectId) = lock.withLock {
        val dir = dirOf(id)
        if (!dir.exists()) throw StoreException.NotFound(id)
        writeAtomically(File(dir, STATE_FILE), buildJsonObject { put("lastOpenedAt", clock()) }.toString())
    }

    override fun duplicate(id: ProjectId, transform: (ProjectSchema) -> ProjectSchema): ProjectSchema = lock.withLock {
        val source = load(id)
        val copy = transform(source).copy(revision = 0)
        require(copy.id != source.id) { "Duplicate must have a new id" }
        val target = dirOf(copy.id)
        if (target.exists()) throw IllegalStateException("Project ${copy.id} already exists")
        val tmp = tempDir("dup")
        try {
            copyUserFiles(dirOf(id), tmp)
            writeAtomically(File(tmp, PROJECT_FILE), codec.encode(copy))
            moveDir(tmp, target)
        } finally {
            tmp.deleteRecursively()
        }
        copy
    }

    // ------------------------------------------------------------------ history

    override fun history(id: ProjectId): List<ProjectSnapshot> = lock.withLock { historyUnlocked(id) }

    override fun loadSnapshot(id: ProjectId, revision: Long): ProjectSchema = lock.withLock {
        val file = snapshotFile(id, revision) ?: throw StoreException.NotFound(id)
        codec.decode(file.readText())
    }

    override fun restoreSnapshot(id: ProjectId, revision: Long): ProjectSchema = lock.withLock {
        val current = load(id)
        val old = loadSnapshot(id, revision)
        save(old.copy(id = id, revision = current.revision))
    }

    override fun recover(id: ProjectId): ProjectSchema = lock.withLock {
        val dir = dirOf(id)
        val snapshots = historyUnlocked(id)
        for (snap in snapshots) {
            val restored = try {
                codec.decode(snapshotFile(id, snap.revision)!!.readText())
            } catch (e: Exception) {
                continue
            }
            val main = File(dir, PROJECT_FILE)
            if (main.exists()) main.renameTo(File(dir, "$PROJECT_FILE.damaged-${clock()}"))
            val repaired = restored.copy(id = id, revision = snapshots.first().revision + 1)
            writeAtomically(main, codec.encode(repaired))
            return@withLock repaired
        }
        throw StoreException.Corrupt(id, snapshots, IllegalStateException("No readable snapshot to recover from"))
    }

    // ------------------------------------------------------------------ import / export

    override fun export(id: ProjectId, out: OutputStream) = lock.withLock {
        val project = load(id)
        val dir = dirOf(id)
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST_FILE))
        zip.write(buildJsonObject {
            put("format", ARCHIVE_FORMAT)
            put("schemaVersion", project.schemaVersion)
            put("exportedAt", clock())
        }.toString().toByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(PROJECT_FILE))
        zip.write(codec.encode(project).toByteArray())
        zip.closeEntry()
        dir.walkTopDown()
            .filter { it.isFile }
            .map { it to it.relativeTo(dir).invariantSeparatorsPath }
            .filter { (_, rel) -> isUserFile(rel) }
            .sortedBy { it.second }
            .forEach { (file, rel) ->
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        zip.finish()
    }

    override fun import(input: InputStream, newId: () -> ProjectId): ProjectSchema = lock.withLock {
        val tmp = tempDir("import")
        try {
            extractSafely(input, tmp)
            val manifest = File(tmp, MANIFEST_FILE)
            if (!manifest.exists()) throw StoreException.InvalidArchive("Not a Sketchware AI project archive (manifest missing)")
            val format = runCatching {
                Json.parseToJsonElement(manifest.readText()).jsonObject["format"]?.jsonPrimitive?.intOrNull
            }.getOrNull() ?: throw StoreException.InvalidArchive("Unreadable archive manifest")
            if (format > ARCHIVE_FORMAT) throw StoreException.InvalidArchive("Archive was made by a newer version of Sketchware AI")
            manifest.delete()

            val projectFile = File(tmp, PROJECT_FILE)
            if (!projectFile.exists()) throw StoreException.InvalidArchive("Archive has no project.json")
            val decoded = try {
                codec.decode(projectFile.readText())
            } catch (e: Exception) {
                throw StoreException.InvalidArchive("Archive project is unreadable: ${e.message}", e)
            }
            var id = newId()
            while (dirOf(id).exists()) id = newId()
            val now = clock()
            val project = decoded.copy(id = id, revision = 0, createdAt = now, updatedAt = now)
            val errors = ProjectValidator.validate(project).filter { it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) throw StoreException.InvalidArchive("Archive project is invalid: " + errors.joinToString("; ") { it.message })

            writeAtomically(projectFile, codec.encode(project))
            moveDir(tmp, dirOf(id))
            project
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun extractSafely(input: InputStream, target: File) {
        val canonicalTarget = target.canonicalFile
        var entries = 0
        var total = 0L
        val seen = HashSet<String>()
        val zip = ZipInputStream(input)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > limits.maxArchiveEntries) throw StoreException.InvalidArchive("Archive has too many entries")
            val name = entry.name
            if (entry.isDirectory) continue
            if (!ProjectValidator.isSafeRelativePath(name)) throw StoreException.InvalidArchive("Unsafe path in archive: $name")
            if (!seen.add(name)) throw StoreException.InvalidArchive("Duplicate entry in archive: $name")
            if (name != MANIFEST_FILE && name != PROJECT_FILE && !isUserFile(name)) continue // ignore history/state
            val out = File(canonicalTarget, name).canonicalFile
            if (!out.path.startsWith(canonicalTarget.path + File.separator)) {
                throw StoreException.InvalidArchive("Unsafe path in archive: $name")
            }
            out.parentFile.mkdirs()
            var entryBytes = 0L
            FileOutputStream(out).use { fos ->
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    entryBytes += n
                    total += n
                    if (entryBytes > limits.maxArchiveEntryBytes) throw StoreException.InvalidArchive("Archive entry too large: $name")
                    if (total > limits.maxArchiveTotalBytes) throw StoreException.InvalidArchive("Archive is too large")
                    fos.write(buffer, 0, n)
                }
            }
        }
        if (entries == 0) throw StoreException.InvalidArchive("Archive is empty or not a zip file")
    }

    // ------------------------------------------------------------------ helpers

    private fun dirOf(id: ProjectId): File {
        require(isProjectId(id.value)) { "Invalid project id ${id.value}" }
        return File(root, id.value)
    }

    private fun isProjectId(s: String) = ENTITY_ID_REGEX.matches(s) && s.startsWith(IdPrefix.PROJECT + "_")

    /** Files that belong to the user's project content (as opposed to store bookkeeping). */
    private fun isUserFile(rel: String) =
        rel != PROJECT_FILE && rel != STATE_FILE && rel != MANIFEST_FILE &&
            !rel.startsWith("$HISTORY_DIR/") && !rel.startsWith("$PROJECT_FILE.")

    private fun snapshot(dir: File, current: ProjectSchema) {
        val history = File(dir, HISTORY_DIR).apply { mkdirs() }
        writeAtomically(File(history, "r${current.revision}-${current.updatedAt}.json"), codec.encode(current))
        val all = history.listFiles().orEmpty().mapNotNull { f -> parseSnapshotName(f.name)?.let { f to it.first } }
        all.sortedByDescending { it.second }.drop(limits.maxHistory).forEach { it.first.delete() }
    }

    private fun historyUnlocked(id: ProjectId): List<ProjectSnapshot> =
        File(dirOf(id), HISTORY_DIR).listFiles().orEmpty()
            .mapNotNull { f -> parseSnapshotName(f.name)?.let { ProjectSnapshot(id, it.first, it.second) } }
            .sortedByDescending { it.revision }

    private fun snapshotFile(id: ProjectId, revision: Long): File? =
        File(dirOf(id), HISTORY_DIR).listFiles().orEmpty().firstOrNull { parseSnapshotName(it.name)?.first == revision }

    private fun parseSnapshotName(name: String): Pair<Long, Long>? {
        val m = SNAPSHOT_NAME.matchEntire(name) ?: return null
        return m.groupValues[1].toLong() to m.groupValues[2].toLong()
    }

    private fun readState(dir: File): Long? = runCatching {
        val o = Json.parseToJsonElement(File(dir, STATE_FILE).readText()) as JsonObject
        (o["lastOpenedAt"] as? JsonPrimitive)?.longOrNull
    }.getOrNull()

    private fun copyUserFiles(from: File, to: File) {
        from.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(from).invariantSeparatorsPath
            if (isUserFile(rel)) f.copyTo(File(to, rel))
        }
    }

    private fun tempDir(tag: String): File =
        File(root, ".tmp-$tag-${clock()}-${System.nanoTime()}").apply { mkdirs() }

    private fun moveDir(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
    }

    /** Write to a sibling temp file, fsync, then atomically replace the destination. */
    private fun writeAtomically(target: File, content: String) {
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val PROJECT_FILE = "project.json"
        const val STATE_FILE = "state.json"
        const val HISTORY_DIR = "history"
        const val MANIFEST_FILE = "sketchware-ai-export.json"
        const val ARCHIVE_FORMAT = 1
        const val ARCHIVE_EXTENSION = "swai"
        private val SNAPSHOT_NAME = Regex("^r(\\d+)-(\\d+)\\.json$")
    }
}
