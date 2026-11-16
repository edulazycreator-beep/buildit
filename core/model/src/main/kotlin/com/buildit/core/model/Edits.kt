package com.buildit.core.model

/*
 * Every modification of a project is a ProjectEdit applied by ProjectEditor. The designer,
 * (later) the block editor and the AI all produce edits, so they share validation and the
 * same undo/redo history.
 */

class EditException(message: String) : IllegalArgumentException(message)

sealed interface ProjectEdit {
    /** Inserts [component] into [parentId] at [index] (clamped to the child count). */
    data class AddComponent(val screenId: ScreenId, val parentId: ComponentId, val index: Int, val component: ComponentSchema) : ProjectEdit

    /** Removes a component, its descendants and any events that targeted them. */
    data class RemoveComponent(val screenId: ScreenId, val componentId: ComponentId) : ProjectEdit

    /**
     * Moves a component under [newParentId]. [index] is a position in the new parent's
     * *current* child list (i.e. as the user sees it while dragging).
     */
    data class MoveComponent(val screenId: ScreenId, val componentId: ComponentId, val newParentId: ComponentId, val index: Int) : ProjectEdit

    /** Sets a property; a null or empty [value] removes it. */
    data class SetProperty(val screenId: ScreenId, val componentId: ComponentId, val key: String, val value: String?) : ProjectEdit

    data class SetViewId(val screenId: ScreenId, val componentId: ComponentId, val viewId: String) : ProjectEdit
    data class AddScreen(val screen: ScreenSchema) : ProjectEdit
    data class RemoveScreen(val screenId: ScreenId) : ProjectEdit
    data class RenameScreen(val screenId: ScreenId, val name: String, val layoutName: String) : ProjectEdit
    data class SetMainScreen(val screenId: ScreenId) : ProjectEdit
    data class UpdateConfig(val config: ProjectConfig) : ProjectEdit
}

object ProjectEditor {

    /** Applies [edits] atomically. The result must pass [ProjectValidator] or nothing changes. */
    fun apply(project: ProjectSchema, vararg edits: ProjectEdit): ProjectSchema = applyAll(project, edits.toList())

    fun applyAll(project: ProjectSchema, edits: List<ProjectEdit>): ProjectSchema {
        val result = edits.fold(project, ::applyOne)
        val errors = ProjectValidator.validate(result).filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) throw EditException(errors.first().message)
        return result
    }

    private fun applyOne(p: ProjectSchema, edit: ProjectEdit): ProjectSchema = when (edit) {
        is ProjectEdit.AddComponent -> p.updateScreen(edit.screenId) { s ->
            val parent = s.require(edit.parentId)
            if (!parent.type.isContainer) throw EditException("${parent.type.xmlTag} cannot contain other views")
            if (s.allComponents().any { it.id == edit.component.id }) throw EditException("Component already exists")
            val parentType = parent.type
            val child = edit.component.withApplicableProperties(parentType)
            s.copy(root = s.root.updateNode(edit.parentId) { it.copy(children = it.children.insertAt(edit.index, child)) })
        }

        is ProjectEdit.RemoveComponent -> p.updateScreen(edit.screenId) { s ->
            if (s.root.id == edit.componentId) throw EditException("The root layout cannot be removed")
            val removed = s.require(edit.componentId).flatten().map { it.id }.toSet()
            s.copy(
                root = s.root.removeNode(edit.componentId),
                events = s.events.filter { it.targetComponentId == null || it.targetComponentId !in removed },
            )
        }

        is ProjectEdit.MoveComponent -> p.updateScreen(edit.screenId) { s ->
            if (s.root.id == edit.componentId) throw EditException("The root layout cannot be moved")
            val moving = s.require(edit.componentId)
            val newParent = s.require(edit.newParentId)
            if (!newParent.type.isContainer) throw EditException("${newParent.type.xmlTag} cannot contain other views")
            if (moving.flatten().any { it.id == edit.newParentId }) throw EditException("A layout cannot be moved into itself")
            val oldParent = s.root.parentOf(edit.componentId)!!
            var index = edit.index
            if (oldParent.id == newParent.id) {
                val oldIndex = oldParent.children.indexOfFirst { it.id == edit.componentId }
                if (oldIndex < index) index -= 1
            }
            val child = if (oldParent.type == newParent.type) moving else moving.withApplicableProperties(newParent.type)
            val without = s.root.removeNode(edit.componentId)
            s.copy(root = without.updateNode(edit.newParentId) { it.copy(children = it.children.insertAt(index, child)) })
        }

        is ProjectEdit.SetProperty -> p.updateScreen(edit.screenId) { s ->
            val component = s.require(edit.componentId)
            val parentType = s.root.parentOf(edit.componentId)?.type
            val def = PropertyCatalog.find(component.type, parentType, edit.key)
                ?: throw EditException("${component.type.xmlTag} has no property \"${edit.key}\"")
            val value = edit.value?.takeIf { it.isNotEmpty() }
            if (value != null) def.validate(value)?.let { throw EditException("${def.label}: $it") }
            if (value == null && (def === PropertyCatalog.LAYOUT_WIDTH || def === PropertyCatalog.LAYOUT_HEIGHT)) {
                throw EditException("${def.label} is required")
            }
            s.copy(root = s.root.updateNode(edit.componentId) {
                it.copy(properties = if (value == null) it.properties - edit.key else it.properties + (edit.key to value))
            })
        }

        is ProjectEdit.SetViewId -> p.updateScreen(edit.screenId) { s ->
            if (!Rules.isJavaIdentifier(edit.viewId)) throw EditException("\"${edit.viewId}\" is not a valid id")
            if (s.allComponents().any { it.viewId == edit.viewId && it.id != edit.componentId }) {
                throw EditException("Id \"${edit.viewId}\" is already used on this screen")
            }
            s.require(edit.componentId)
            s.copy(root = s.root.updateNode(edit.componentId) { it.copy(viewId = edit.viewId) })
        }

        is ProjectEdit.AddScreen -> {
            if (p.screens.any { it.name == edit.screen.name || it.layoutName == edit.screen.layoutName }) {
                throw EditException("A screen named ${edit.screen.name} already exists")
            }
            p.copy(screens = p.screens + edit.screen)
        }

        is ProjectEdit.RemoveScreen -> {
            if (p.screen(edit.screenId) == null) throw EditException("Screen not found")
            if (p.config.mainScreenId == edit.screenId) throw EditException("The main screen cannot be deleted")
            p.copy(screens = p.screens.filter { it.id != edit.screenId })
        }

        is ProjectEdit.RenameScreen -> {
            if (p.screens.any { it.id != edit.screenId && (it.name == edit.name || it.layoutName == edit.layoutName) }) {
                throw EditException("A screen named ${edit.name} already exists")
            }
            p.updateScreen(edit.screenId) { it.copy(name = edit.name, layoutName = edit.layoutName) }
        }

        is ProjectEdit.SetMainScreen -> {
            if (p.screen(edit.screenId) == null) throw EditException("Screen not found")
            p.copy(config = p.config.copy(mainScreenId = edit.screenId))
        }

        is ProjectEdit.UpdateConfig -> {
            if (edit.config.mainScreenId != p.config.mainScreenId && p.screen(edit.config.mainScreenId) == null) {
                throw EditException("Screen not found")
            }
            p.copy(config = edit.config)
        }
    }

    // ------------------------------------------------------------------ tree helpers

    private fun ProjectSchema.updateScreen(id: ScreenId, transform: (ScreenSchema) -> ScreenSchema): ProjectSchema {
        if (screen(id) == null) throw EditException("Screen not found")
        return copy(screens = screens.map { if (it.id == id) transform(it) else it })
    }

    private fun ScreenSchema.require(id: ComponentId): ComponentSchema = component(id) ?: throw EditException("Component not found")

    private fun <T> List<T>.insertAt(index: Int, item: T): List<T> =
        toMutableList().apply { add(index.coerceIn(0, size), item) }
}

fun ComponentSchema.parentOf(id: ComponentId): ComponentSchema? {
    if (children.any { it.id == id }) return this
    return children.firstNotNullOfOrNull { it.parentOf(id) }
}

fun ComponentSchema.updateNode(id: ComponentId, transform: (ComponentSchema) -> ComponentSchema): ComponentSchema =
    if (this.id == id) transform(this) else copy(children = children.map { it.updateNode(id, transform) })

fun ComponentSchema.removeNode(id: ComponentId): ComponentSchema =
    copy(children = children.filter { it.id != id }.map { it.removeNode(id) })

/** Drops properties that do not apply under [parentType] (e.g. `layout_weight` outside a LinearLayout). */
fun ComponentSchema.withApplicableProperties(parentType: ComponentType?): ComponentSchema {
    val keys = PropertyCatalog.propertiesFor(type, parentType).map { it.key }.toSet()
    return copy(properties = properties.filterKeys { it in keys })
}

/** Bounded, snapshot-based undo/redo. States are immutable, so storing them is cheap. */
class EditHistory(private val limit: Int = 100) {
    data class Entry(val label: String, val project: ProjectSchema)

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val undoLabel get() = undoStack.lastOrNull()?.label
    val redoLabel get() = redoStack.lastOrNull()?.label

    /** Records the state *before* an edit labelled [label]. Clears redo. */
    fun record(label: String, before: ProjectSchema) {
        undoStack.addLast(Entry(label, before))
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: ProjectSchema): Entry? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(Entry(entry.label, current))
        return entry
    }

    fun redo(current: ProjectSchema): Entry? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(Entry(entry.label, current))
        return entry
    }

    fun clear() { undoStack.clear(); redoStack.clear() }
}
