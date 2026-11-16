package com.buildit.core.model

import java.security.SecureRandom

/*
 * Stable, typed identifiers for every entity in the project model.
 *
 * IDs are opaque strings of the form `<prefix>_<suffix>`. They never change once
 * assigned (renaming a screen or a view id does not change its entity id), which lets
 * the designer, the block editor, the code generator and the AI refer to the same
 * entity without ambiguity.
 */

@JvmInline value class ProjectId(val value: String) { override fun toString() = value }
@JvmInline value class ScreenId(val value: String) { override fun toString() = value }
@JvmInline value class ComponentId(val value: String) { override fun toString() = value }
@JvmInline value class EventId(val value: String) { override fun toString() = value }
@JvmInline value class BlockId(val value: String) { override fun toString() = value }
@JvmInline value class ResourceId(val value: String) { override fun toString() = value }
@JvmInline value class CodeFileId(val value: String) { override fun toString() = value }

object IdPrefix {
    const val PROJECT = "prj"
    const val SCREEN = "scr"
    const val COMPONENT = "cmp"
    const val EVENT = "evt"
    const val BLOCK = "blk"
    const val RESOURCE = "res"
    const val CODE_FILE = "src"
}

/** Allowed shape of any entity id. Also guarantees ids are safe to use as file names. */
val ENTITY_ID_REGEX = Regex("^[a-z]{3}_[a-z0-9]{1,32}$")

interface IdGenerator {
    fun next(prefix: String): String

    fun projectId() = ProjectId(next(IdPrefix.PROJECT))
    fun screenId() = ScreenId(next(IdPrefix.SCREEN))
    fun componentId() = ComponentId(next(IdPrefix.COMPONENT))
    fun eventId() = EventId(next(IdPrefix.EVENT))
    fun blockId() = BlockId(next(IdPrefix.BLOCK))
    fun resourceId() = ResourceId(next(IdPrefix.RESOURCE))
    fun codeFileId() = CodeFileId(next(IdPrefix.CODE_FILE))
}

/** Production generator: 16 random base-36 characters (~82 bits of entropy). */
class RandomIdGenerator(private val random: SecureRandom = SecureRandom()) : IdGenerator {
    override fun next(prefix: String): String = buildString(prefix.length + 17) {
        append(prefix).append('_')
        repeat(16) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    private companion object {
        const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    }
}

/** Deterministic generator for tests and golden files. Not thread-safe. */
class SequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun next(prefix: String): String = "${prefix}_${++counter}"
}
