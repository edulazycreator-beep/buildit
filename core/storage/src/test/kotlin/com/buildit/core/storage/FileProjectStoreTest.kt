package com.buildit.core.storage

import com.buildit.core.model.CodeFileId
import com.buildit.core.model.CodeFileSchema
import com.buildit.core.model.CodeLanguage
import com.buildit.core.model.NewProjectRequest
import com.buildit.core.model.ProjectFactory
import com.buildit.core.model.ProjectId
import com.buildit.core.model.ProjectSchema
import com.buildit.core.model.ProjectTemplate
import com.buildit.core.model.RandomIdGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileProjectStoreTest {

    private lateinit var root: File
    private var now = 1_000L
    private val ids = RandomIdGenerator()
    private val factory = ProjectFactory(ids) { now }
    private lateinit var store: FileProjectStore

    @Before fun setUp() {
        root = Files.createTempDirectory("buildit-store").toFile()
        store = FileProjectStore(root, clock = { now }, limits = FileProjectStore.Limits(maxHistory = 3))
    }

    @After fun tearDown() { root.deleteRecursively() }

    private fun created(name: String = "App", template: ProjectTemplate = ProjectTemplate.HELLO_WORLD): ProjectSchema =
        store.create(factory.create(NewProjectRequest(name, "com.example.${name.lowercase()}", template)))

    @Test fun `create load and list`() {
        val p = created("Alpha")
        assertEquals(p, store.load(p.id))
        val list = store.list()
        assertEquals(1, list.size)
        assertEquals("Alpha", list[0].appName)
        assertEquals(1, list[0].screenCount)
        assertFalse(list[0].damaged)
    }

    @Test fun `save bumps revision and detects conflicts`() {
        val p = created()
        now = 2_000L
        val saved = store.save(factory.rename(p, "Renamed"))
        assertEquals(1L, saved.revision)
        assertEquals(2_000L, saved.updatedAt)
        assertEquals("Renamed", store.load(p.id).config.appName)
        try {
            store.save(factory.rename(p, "Stale write")) // still revision 0
            fail("expected conflict")
        } catch (e: StoreException.Conflict) {
            assertEquals(1L, e.actual)
        }
        assertEquals("Renamed", store.load(p.id).config.appName)
    }

    @Test fun `history is bounded and restorable`() {
        var p = created()
        for (i in 1..5) { now += 1; p = store.save(factory.rename(p, "Name$i")) }
        val history = store.history(p.id)
        assertEquals(listOf(4L, 3L, 2L), history.map { it.revision }) // maxHistory = 3
        assertEquals("Name3", store.loadSnapshot(p.id, 3).config.appName)

        val restored = store.restoreSnapshot(p.id, 3)
        assertEquals("Name3", restored.config.appName)
        assertEquals(6L, restored.revision)
        assertEquals(5L, store.history(p.id).first().revision) // pre-restore state preserved
    }

    @Test fun `recent projects ordered by last opened`() {
        val a = created("Aaa"); now += 10
        val b = created("Bbb"); now += 10
        assertEquals(listOf(b.id, a.id), store.list().map { it.id })
        store.markOpened(a.id)
        assertEquals(listOf(a.id, b.id), store.list().map { it.id })
        assertEquals(a, store.load(a.id)) // opening does not change the model
    }

    @Test fun `corrupt project is reported and recoverable`() {
        var p = created()
        now += 1; p = store.save(factory.rename(p, "Good"))
        File(root, "${p.id}/project.json").writeText("{ truncated")
        try {
            store.load(p.id); fail()
        } catch (e: StoreException.Corrupt) {
            assertEquals(listOf(0L), e.snapshots.map { it.revision })
        }
        assertTrue(store.list().single().damaged)
        val recovered = store.recover(p.id)
        assertEquals("App", recovered.config.appName) // snapshot r0
        assertEquals(recovered, store.load(p.id))
        assertTrue(root.resolve(p.id.value).list()!!.any { it.startsWith("project.json.damaged-") })
    }

    @Test fun `delete removes project`() {
        val p = created()
        store.delete(p.id)
        assertFalse(store.exists(p.id))
        assertTrue(store.list().isEmpty())
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun `duplicate copies user files but not history`() {
        var p = created()
        File(root, "${p.id}/src/java/Util.java").apply { parentFile.mkdirs(); writeText("class Util {}") }
        now += 1; p = store.save(p.copy(sourceFiles = listOf(CodeFileSchema(CodeFileId("src_1"), "src/java/Util.java", CodeLanguage.JAVA))))
        val copy = store.duplicate(p.id) { factory.duplicate(it, "Copy", "com.example.copy") }
        assertNotEquals(p.id, copy.id)
        assertEquals("class Util {}", File(root, "${copy.id}/src/java/Util.java").readText())
        assertTrue(store.history(copy.id).isEmpty())
        assertEquals(2, store.list().size)
    }

    @Test fun `export then import round trips with a new id`() {
        var p = created("Exported", ProjectTemplate.BUTTON_AND_TEXT)
        File(root, "${p.id}/res/drawable/logo.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        now += 1; p = store.save(factory.rename(p, "Exported2"))
        val bytes = ByteArrayOutputStream().also { store.export(p.id, it) }.toByteArray()

        val imported = store.import(ByteArrayInputStream(bytes)) { ids.projectId() }
        assertNotEquals(p.id, imported.id)
        assertEquals(0L, imported.revision)
        assertEquals(p.screens, imported.screens)
        assertEquals(p.config, imported.config)
        assertTrue(File(root, "${imported.id}/res/drawable/logo.png").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(File(root, "${imported.id}/history").exists())
        assertEquals(imported, store.load(imported.id))
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bos ->
        ZipOutputStream(bos).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }.toByteArray()

    private fun validArchiveEntries(): Array<Pair<String, ByteArray>> {
        val p = created("Src")
        val bytes = ByteArrayOutputStream().also { store.export(p.id, it) }.toByteArray()
        store.delete(p.id)
        val entries = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries += e.name to z.readBytes() }
        }
        return entries.toTypedArray()
    }

    private fun expectInvalid(bytes: ByteArray) {
        try {
            store.import(ByteArrayInputStream(bytes)) { ids.projectId() }
            fail("expected InvalidArchive")
        } catch (_: StoreException.InvalidArchive) {}
        assertTrue("store must stay clean", store.list().isEmpty())
        assertTrue("no temp dirs left", root.listFiles()!!.none { it.name.startsWith(".tmp") })
    }

    @Test fun `import rejects zip slip`() {
        expectInvalid(zip(*validArchiveEntries(), "../evil.txt" to "x".toByteArray()))
        assertFalse(File(root.parentFile, "evil.txt").exists())
        expectInvalid(zip(*validArchiveEntries(), "src/../../evil.txt" to "x".toByteArray()))
        expectInvalid(zip(*validArchiveEntries(), "/abs.txt" to "x".toByteArray()))
    }

    @Test fun `import rejects non archives and oversized content`() {
        expectInvalid("not a zip".toByteArray())
        expectInvalid(zip("project.json" to "{}".toByteArray())) // no manifest
        val entries = validArchiveEntries()
        expectInvalid(zip(*entries.map { if (it.first == "project.json") it.first to "{oops".toByteArray() else it }.toTypedArray()))

        val small = FileProjectStore(root, clock = { now }, limits = FileProjectStore.Limits(maxArchiveEntryBytes = 10))
        try {
            small.import(ByteArrayInputStream(zip(*entries, "res/big.bin" to ByteArray(100)))) { ids.projectId() }
            fail()
        } catch (_: StoreException.InvalidArchive) {}
    }

    @Test fun `archives with the pre-rename manifest name still import`() {
        val entries = validArchiveEntries().map {
            if (it.first == FileProjectStore.MANIFEST_FILE) FileProjectStore.LEGACY_MANIFEST_FILE to it.second else it
        }
        val p = store.import(ByteArrayInputStream(zip(*entries.toTypedArray()))) { ids.projectId() }
        assertTrue(store.exists(p.id))
        assertFalse(File(root, "${p.id}/${FileProjectStore.LEGACY_MANIFEST_FILE}").exists())
    }

    @Test fun `import rejects archive with invalid project`() {
        val entries = validArchiveEntries().map {
            if (it.first == "project.json") it.first to String(it.second).replace("com.example.src", "nopackage").toByteArray() else it
        }
        expectInvalid(zip(*entries.toTypedArray()))
    }

    @Test fun `ids cannot escape the root`() {
        try { store.load(ProjectId("../etc")); fail() } catch (_: IllegalArgumentException) {}
        try { store.delete(ProjectId("prj_ok/../../x")); fail() } catch (_: IllegalArgumentException) {}
    }
}

class SnapshotThrottleTest {
    @Test fun `autosaves are snapshotted at most once per interval but restores always are`() {
        val root = Files.createTempDirectory("buildit-throttle").toFile()
        try {
            var now = 0L
            val store = FileProjectStore(root, clock = { now }, limits = FileProjectStore.Limits(minSnapshotIntervalMillis = 60_000))
            val factory = ProjectFactory(RandomIdGenerator()) { now }
            var p = store.create(factory.create(NewProjectRequest("App", "com.example.app")))
            for (i in 1..10) { now += 1_000; p = store.save(factory.rename(p, "N$i")) }
            assertEquals(listOf(0L), store.history(p.id).map { it.revision })
            now += 60_000
            p = store.save(factory.rename(p, "Later"))
            assertEquals(listOf(10L, 0L), store.history(p.id).map { it.revision })
            now += 1_000
            store.restoreSnapshot(p.id, 0)
            assertEquals(11L, store.history(p.id).first().revision)
        } finally {
            root.deleteRecursively()
        }
    }
}
