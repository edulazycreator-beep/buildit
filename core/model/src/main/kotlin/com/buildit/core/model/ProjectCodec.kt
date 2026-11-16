package com.buildit.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class ProjectFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Upgrades a raw project document from schema version [fromVersion] to `fromVersion + 1`.
 * Migrations operate on the JSON tree, before decoding, so old shapes never need Kotlin classes.
 */
interface Migration {
    val fromVersion: Int
    fun migrate(document: JsonObject): JsonObject
}

/** Registry of production migrations. Append a migration whenever [CURRENT_SCHEMA_VERSION] is bumped. */
object Migrations {
    val ALL: List<Migration> = emptyList()
}

/**
 * Explicit, deterministic JSON codec for [ProjectSchema].
 *
 * Hand-written (instead of reflection or a compiler plugin) so that: the on-disk format is
 * an explicit contract; map keys are emitted in sorted order (stable diffs, stable hashes);
 * and documents written by newer app versions are rejected instead of silently truncated.
 */
class ProjectCodec(
    private val migrations: List<Migration> = Migrations.ALL,
    private val currentVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    private val json = Json { prettyPrint = true }

    fun encode(project: ProjectSchema): String = json.encodeToString(JsonElement.serializer(), encodeProject(project))

    fun decode(text: String): ProjectSchema {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ProjectFormatException("Project file is not valid JSON", e)
        }
        return decodeTree(root)
    }

    fun decodeTree(document: JsonObject): ProjectSchema {
        val migrated = migrate(document)
        return try {
            decodeProject(migrated)
        } catch (e: ProjectFormatException) {
            throw e
        } catch (e: Exception) {
            throw ProjectFormatException("Project file is malformed: ${e.message}", e)
        }
    }

    /** Reads the schema version without decoding the whole document. */
    fun peekVersion(document: JsonObject): Int =
        document["schemaVersion"]?.jsonPrimitive?.intOrNull ?: throw ProjectFormatException("Missing schemaVersion")

    private fun migrate(document: JsonObject): JsonObject {
        var doc = document
        var version = peekVersion(doc)
        if (version > currentVersion) {
            throw ProjectFormatException("Project was created by a newer version of Build It (schema $version > $currentVersion)")
        }
        while (version < currentVersion) {
            val step = migrations.firstOrNull { it.fromVersion == version }
                ?: throw ProjectFormatException("No migration from schema version $version")
            doc = step.migrate(doc)
            val next = peekVersion(doc)
            if (next != version + 1) throw ProjectFormatException("Migration from $version produced version $next")
            version = next
        }
        return doc
    }

    // ---------------------------------------------------------------- encode

    private fun encodeProject(p: ProjectSchema) = buildJsonObject {
        put("schemaVersion", p.schemaVersion)
        put("id", p.id.value)
        put("revision", p.revision)
        put("createdAt", p.createdAt)
        put("updatedAt", p.updatedAt)
        put("config", buildJsonObject {
            put("appName", p.config.appName)
            put("packageName", p.config.packageName)
            put("versionCode", p.config.versionCode)
            put("versionName", p.config.versionName)
            put("minSdk", p.config.minSdk)
            put("targetSdk", p.config.targetSdk)
            put("mainScreenId", p.config.mainScreenId.value)
            put("language", p.config.language.name)
        })
        put("screens", JsonArray(p.screens.map(::encodeScreen)))
        put("resources", JsonArray(p.resources.map(::encodeResource)))
        put("sourceFiles", JsonArray(p.sourceFiles.map(::encodeCodeFile)))
    }

    private fun encodeScreen(s: ScreenSchema) = buildJsonObject {
        put("id", s.id.value)
        put("name", s.name)
        put("layoutName", s.layoutName)
        put("root", encodeComponent(s.root))
        put("events", JsonArray(s.events.map(::encodeEvent)))
        put("variables", JsonArray(s.variables.map { v ->
            buildJsonObject {
                put("name", v.name)
                put("type", v.type.name)
                v.initialValue?.let { put("initialValue", it) }
            }
        }))
    }

    private fun encodeComponent(c: ComponentSchema): JsonObject = buildJsonObject {
        put("id", c.id.value)
        put("type", c.type.name)
        put("viewId", c.viewId)
        put("properties", sortedStringMap(c.properties))
        put("children", JsonArray(c.children.map(::encodeComponent)))
    }

    private fun encodeEvent(e: EventSchema) = buildJsonObject {
        put("id", e.id.value)
        put("type", e.type)
        put("target", e.targetComponentId?.value?.let(::JsonPrimitive) ?: JsonNull)
        put("body", JsonArray(e.body.map(::encodeBlock)))
    }

    private fun encodeBlock(b: BlockSchema): JsonObject = buildJsonObject {
        put("id", b.id.value)
        put("type", b.type)
        put("fields", sortedStringMap(b.fields))
        put("inputs", JsonObject(b.inputs.toSortedMap().mapValues { encodeBlock(it.value) }))
        put("next", b.next?.let(::encodeBlock) ?: JsonNull)
    }

    private fun encodeResource(r: ResourceSchema) = buildJsonObject {
        put("id", r.id.value)
        put("type", r.type.name)
        put("name", r.name)
        r.value?.let { put("value", it) }
        r.path?.let { put("path", it) }
    }

    private fun encodeCodeFile(f: CodeFileSchema) = buildJsonObject {
        put("id", f.id.value)
        put("path", f.path)
        put("language", f.language.name)
    }

    private fun sortedStringMap(map: Map<String, String>) =
        JsonObject(map.toSortedMap().mapValues { JsonPrimitive(it.value) })

    // ---------------------------------------------------------------- decode

    private fun decodeProject(o: JsonObject): ProjectSchema {
        val config = o.obj("config")
        return ProjectSchema(
            schemaVersion = o.req("schemaVersion").int,
            id = ProjectId(o.str("id")),
            revision = o.req("revision").long,
            createdAt = o.req("createdAt").long,
            updatedAt = o.req("updatedAt").long,
            config = ProjectConfig(
                appName = config.str("appName"),
                packageName = config.str("packageName"),
                versionCode = config.req("versionCode").int,
                versionName = config.str("versionName"),
                minSdk = config.req("minSdk").int,
                targetSdk = config.req("targetSdk").int,
                mainScreenId = ScreenId(config.str("mainScreenId")),
                language = enumOf(config.str("language")),
            ),
            screens = o.arr("screens").map { decodeScreen(it.jsonObject) },
            resources = o.arr("resources").map { decodeResource(it.jsonObject) },
            sourceFiles = o.arr("sourceFiles").map { decodeCodeFile(it.jsonObject) },
        )
    }

    private fun decodeScreen(o: JsonObject) = ScreenSchema(
        id = ScreenId(o.str("id")),
        name = o.str("name"),
        layoutName = o.str("layoutName"),
        root = decodeComponent(o.obj("root")),
        events = o.arr("events").map { decodeEvent(it.jsonObject) },
        variables = o.arr("variables").map {
            val v = it.jsonObject
            VariableSchema(v.str("name"), enumOf(v.str("type")), v.optStr("initialValue"))
        },
    )

    private fun decodeComponent(o: JsonObject): ComponentSchema = ComponentSchema(
        id = ComponentId(o.str("id")),
        type = enumOf(o.str("type")),
        viewId = o.str("viewId"),
        properties = o.stringMap("properties"),
        children = o.arr("children").map { decodeComponent(it.jsonObject) },
    )

    private fun decodeEvent(o: JsonObject) = EventSchema(
        id = EventId(o.str("id")),
        type = o.str("type"),
        targetComponentId = o.optStr("target")?.let(::ComponentId),
        body = o.arr("body").map { decodeBlock(it.jsonObject) },
    )

    private fun decodeBlock(o: JsonObject): BlockSchema = BlockSchema(
        id = BlockId(o.str("id")),
        type = o.str("type"),
        fields = o.stringMap("fields"),
        inputs = o.obj("inputs").mapValues { decodeBlock(it.value.jsonObject) },
        next = (o["next"] as? JsonObject)?.let(::decodeBlock),
    )

    private fun decodeResource(o: JsonObject) = ResourceSchema(
        id = ResourceId(o.str("id")),
        type = enumOf(o.str("type")),
        name = o.str("name"),
        value = o.optStr("value"),
        path = o.optStr("path"),
    )

    private fun decodeCodeFile(o: JsonObject) = CodeFileSchema(
        id = CodeFileId(o.str("id")),
        path = o.str("path"),
        language = enumOf(o.str("language")),
    )

    private fun JsonObject.req(key: String): JsonPrimitive =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull } ?: throw ProjectFormatException("Missing field \"$key\"")
    private fun JsonObject.str(key: String): String = req(key).let {
        if (!it.isString) throw ProjectFormatException("Field \"$key\" must be a string")
        it.content
    }
    private fun JsonObject.optStr(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: throw ProjectFormatException("Missing object \"$key\"")
    private fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: throw ProjectFormatException("Missing array \"$key\"")
    private fun JsonObject.stringMap(key: String): Map<String, String> = obj(key).mapValues { it.value.jsonPrimitive.content }

    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name == name } ?: throw ProjectFormatException("Unknown ${E::class.simpleName} \"$name\"")
}

/** Convenience used by migrations: copy an object with a replaced/added key. */
fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

