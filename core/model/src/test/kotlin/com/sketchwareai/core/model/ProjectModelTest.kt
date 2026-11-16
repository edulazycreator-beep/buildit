package com.sketchwareai.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectModelTest {

    private val factory = ProjectFactory(SequentialIdGenerator()) { 1_000L }
    private fun newProject(template: ProjectTemplate = ProjectTemplate.BUTTON_AND_TEXT) =
        factory.create(NewProjectRequest("My App", "com.example.myapp", template))

    // ------------------------------------------------------------- creation

    @Test fun `every template creates a valid project`() {
        for (t in ProjectTemplate.values()) {
            val p = ProjectFactory().create(NewProjectRequest("App ${t.name}", "com.example.app", t))
            assertEquals(emptyList<ValidationIssue>(), ProjectValidator.validate(p))
            assertEquals("MainActivity", p.mainScreen!!.name)
        }
    }

    @Test fun `create trims input and stamps time`() {
        val p = factory.create(NewProjectRequest("  Trimmed  ", " com.example.t "))
        assertEquals("Trimmed", p.config.appName)
        assertEquals("com.example.t", p.config.packageName)
        assertEquals(1_000L, p.createdAt)
        assertEquals(0L, p.revision)
    }

    @Test fun `create rejects invalid package`() {
        try {
            factory.create(NewProjectRequest("App", "example"))
            fail("expected InvalidProjectException")
        } catch (e: InvalidProjectException) {
            assertTrue(e.issues.any { it.code == "config.packageName" })
        }
    }

    @Test fun `random ids are well formed and distinct`() {
        val gen = RandomIdGenerator()
        val ids = (1..1000).map { gen.componentId().value }
        assertTrue(ids.all { ENTITY_ID_REGEX.matches(it) })
        assertEquals(1000, ids.toSet().size)
    }

    // ------------------------------------------------------------- field rules

    @Test fun `package name rules`() {
        assertNull(Rules.packageNameError("com.example.app"))
        assertNull(Rules.packageNameError("com.Example_1.app2"))
        assertNotNull(Rules.packageNameError(""))
        assertNotNull(Rules.packageNameError("app"))
        assertNotNull(Rules.packageNameError("com..app"))
        assertNotNull(Rules.packageNameError("com.1app"))
        assertNotNull(Rules.packageNameError("com.class.app"))
        assertNotNull(Rules.packageNameError("com.ex-ample"))
    }

    @Test fun `package derivation from app name`() {
        assertEquals("com.my.mycoolapp", Rules.derivePackageName("com.my", "My Cool App!"))
        assertEquals("com.my.app2", Rules.derivePackageName("com.my", "2 App 2"))
        assertEquals("com.my.newproject", Rules.derivePackageName("com.my", "日本語"))
        assertEquals("com.my.appclass", Rules.derivePackageName("com.my", "Class"))
        for (name in listOf("x", "Hello World", "123", "for", "Ünïcødé app", "a-b_c")) {
            assertNull(name, Rules.packageNameError(Rules.derivePackageName("com.my", name)))
        }
    }

    @Test fun `sdk rules`() {
        assertNull(Rules.sdkError(24, 35))
        assertNotNull(Rules.sdkError(19, 35))
        assertNotNull(Rules.sdkError(30, 29))
        assertNotNull(Rules.sdkError(24, 99))
    }

    @Test fun `app name rules`() {
        assertNull(Rules.appNameError("Calculator"))
        assertNotNull(Rules.appNameError("  "))
        assertNotNull(Rules.appNameError("a".repeat(51)))
        assertNotNull(Rules.appNameError("two\nlines"))
    }

    // ------------------------------------------------------------- structural validation

    @Test fun `detects duplicate view ids and ids`() {
        val p = newProject()
        val screen = p.screens[0]
        val dupChild = screen.root.children[0].copy(id = screen.root.children[1].id, viewId = screen.root.children[1].viewId)
        val broken = p.copy(screens = listOf(screen.copy(root = screen.root.copy(children = screen.root.children + dupChild))))
        val codes = ProjectValidator.validate(broken).map { it.code }
        assertTrue(codes.contains("id.duplicate"))
        assertTrue(codes.contains("component.viewId.duplicate"))
    }

    @Test fun `non container cannot have children and root must be container`() {
        val p = newProject()
        val screen = p.screens[0]
        val button = screen.root.children.first { it.type == ComponentType.BUTTON }
        val bad = button.copy(children = listOf(ComponentSchema(ComponentId("cmp_99"), ComponentType.TEXT_VIEW, "tv99")))
        val broken = p.copy(screens = listOf(screen.copy(root = button.copy(children = emptyList()).let { screen.root.copy(children = listOf(bad)) })))
        assertTrue(ProjectValidator.validate(broken).any { it.code == "component.children" })

        val leafRoot = p.copy(screens = listOf(screen.copy(root = button)))
        assertTrue(ProjectValidator.validate(leafRoot).any { it.code == "screen.root" })
    }

    @Test fun `event must target existing component`() {
        val p = newProject()
        val screen = p.screens[0]
        val event = EventSchema(EventId("evt_1"), "onClick", ComponentId("cmp_missing"))
        val broken = p.copy(screens = listOf(screen.copy(events = listOf(event))))
        assertTrue(ProjectValidator.validate(broken).any { it.code == "event.target" })
    }

    @Test fun `missing main screen is an error`() {
        val p = newProject()
        val broken = p.copy(config = p.config.copy(mainScreenId = ScreenId("scr_nope")))
        assertTrue(ProjectValidator.hasErrors(broken))
    }

    @Test fun `unsafe paths are rejected`() {
        assertTrue(ProjectValidator.isSafeRelativePath("src/java/A.java"))
        assertFalse(ProjectValidator.isSafeRelativePath("../etc/passwd"))
        assertFalse(ProjectValidator.isSafeRelativePath("/abs"))
        assertFalse(ProjectValidator.isSafeRelativePath("src/../../x"))
        assertFalse(ProjectValidator.isSafeRelativePath("C:/x"))
        assertFalse(ProjectValidator.isSafeRelativePath("a\\b"))
        assertFalse(ProjectValidator.isSafeRelativePath("a//b"))
        val p = newProject().copy(sourceFiles = listOf(CodeFileSchema(CodeFileId("src_1"), "src/../x.java", CodeLanguage.JAVA)))
        assertTrue(ProjectValidator.validate(p).any { it.code == "source.path" })
    }

    // ------------------------------------------------------------- operations

    @Test fun `duplicate gets new id and keeps content`() {
        val p = newProject().copy(revision = 7)
        val d = factory.duplicate(p, "Copy", "com.example.myapp.copy")
        assertNotEquals(p.id, d.id)
        assertEquals(0L, d.revision)
        assertEquals(p.screens, d.screens)
        assertEquals("Copy", d.config.appName)
    }

    @Test fun `rename validates`() {
        val p = newProject()
        assertEquals("New", factory.rename(p, " New ").config.appName)
        try { factory.rename(p, ""); fail() } catch (_: InvalidProjectException) {}
    }

    @Test fun `duplicate package suggestion avoids collisions`() {
        assertEquals("a.b.copy", factory.suggestDuplicatePackage("a.b", emptySet()))
        assertEquals("a.b.copy3", factory.suggestDuplicatePackage("a.b", setOf("a.b.copy", "a.b.copy2")))
    }

    // ------------------------------------------------------------- serialization

    private fun richProject(): ProjectSchema {
        val p = newProject()
        val screen = p.screens[0]
        val button = screen.root.children.first { it.type == ComponentType.BUTTON }
        val block = BlockSchema(
            BlockId("blk_1"), "component_set_text",
            fields = mapOf("COMPONENT" to "textview1"),
            inputs = mapOf("VALUE" to BlockSchema(BlockId("blk_2"), "text_literal", mapOf("TEXT" to "Clicked \"quoted\" ünicode"))),
            next = BlockSchema(BlockId("blk_3"), "toast", mapOf("MESSAGE" to "Hi")),
        )
        return p.copy(
            screens = listOf(screen.copy(
                events = listOf(EventSchema(EventId("evt_1"), "onClick", button.id, listOf(block))),
                variables = listOf(VariableSchema("count", VariableType.INTEGER, "0"), VariableSchema("name", VariableType.STRING)),
            )),
            resources = listOf(
                ResourceSchema(ResourceId("res_1"), ResourceType.COLOR, "primary", value = "#FF6200EE"),
                ResourceSchema(ResourceId("res_2"), ResourceType.DRAWABLE, "logo", path = "res/drawable/logo.png"),
            ),
            sourceFiles = listOf(CodeFileSchema(CodeFileId("src_1"), "src/java/com/example/Util.java", CodeLanguage.JAVA)),
        )
    }

    @Test fun `codec round trips a rich project`() {
        val codec = ProjectCodec()
        val p = richProject()
        assertTrue(ProjectValidator.validate(p).isEmpty())
        assertEquals(p, codec.decode(codec.encode(p)))
    }

    @Test fun `codec output is deterministic regardless of map insertion order`() {
        val codec = ProjectCodec()
        val p = richProject()
        val screen = p.screens[0]
        val reordered = p.copy(screens = listOf(screen.copy(root = screen.root.copy(properties = screen.root.properties.entries.reversed().associate { it.toPair() }))))
        assertEquals(codec.encode(p), codec.encode(reordered))
    }

    @Test fun `codec rejects garbage newer versions and missing fields`() {
        val codec = ProjectCodec()
        expectFormatError { codec.decode("not json") }
        expectFormatError { codec.decode("{}") }
        val newer = codec.encode(newProject()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        expectFormatError { codec.decode(newer) }
        val noName = codec.encode(newProject()).replace("\"appName\"", "\"appNameX\"")
        expectFormatError { codec.decode(noName) }
        val badEnum = codec.encode(newProject()).replace("\"LINEAR_LAYOUT\"", "\"BOGUS\"")
        expectFormatError { codec.decode(badEnum) }
    }

    @Test fun `migrations run in sequence on the json tree`() {
        // A test-only v0 → v1 migration: v0 stored the app name at the top level as "title".
        val v0to1 = object : Migration {
            override val fromVersion = 0
            override fun migrate(document: JsonObject): JsonObject {
                val config = document["config"]!!.jsonObject.with("appName", document["title"]!!)
                return JsonObject(document - "title").with("config", config).with("schemaVersion", JsonPrimitive(1))
            }
        }
        val codec = ProjectCodec(migrations = listOf(v0to1))
        val v1 = codec.encode(newProject())
        val v0 = v1.replace("\"schemaVersion\": 1", "\"schemaVersion\": 0")
            .replace("\"appName\": \"My App\",", "")
            .replaceFirst("{", "{\n  \"title\": \"Old Name\",")
        val migrated = codec.decode(v0)
        assertEquals("Old Name", migrated.config.appName)
        assertEquals(CURRENT_SCHEMA_VERSION, migrated.schemaVersion)

        expectFormatError { ProjectCodec(migrations = emptyList()).decode(v0) }
    }

    private fun expectFormatError(block: () -> Unit) {
        try { block(); fail("expected ProjectFormatException") } catch (_: ProjectFormatException) {}
    }
}
