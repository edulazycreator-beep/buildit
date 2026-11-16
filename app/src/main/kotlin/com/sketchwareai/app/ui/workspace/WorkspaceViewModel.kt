package com.sketchwareai.app.ui.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sketchwareai.app.data.ProjectRepository
import com.sketchwareai.app.data.toUserMessage
import com.sketchwareai.app.di.ApplicationScope
import com.sketchwareai.app.ui.navigation.WorkspaceRoute
import com.sketchwareai.core.codegen.LayoutXmlGenerator
import com.sketchwareai.core.model.ComponentFactory
import com.sketchwareai.core.model.ComponentId
import com.sketchwareai.core.model.ComponentSchema
import com.sketchwareai.core.model.EditHistory
import com.sketchwareai.core.model.IdGenerator
import com.sketchwareai.core.model.PaletteItem
import com.sketchwareai.core.model.ProjectConfig
import com.sketchwareai.core.model.ProjectEdit
import com.sketchwareai.core.model.ProjectEditor
import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.model.ProjectSchema
import com.sketchwareai.core.model.Rules
import com.sketchwareai.core.model.ScreenFactory
import com.sketchwareai.core.model.ScreenId
import com.sketchwareai.core.model.ScreenSchema
import com.sketchwareai.core.model.parentOf
import com.sketchwareai.core.storage.ProjectSnapshot
import com.sketchwareai.core.storage.StoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface WorkspaceState {
    data object Loading : WorkspaceState
    data class Loaded(val project: ProjectSchema) : WorkspaceState
    data class Damaged(val snapshotCount: Int) : WorkspaceState
    data class Failed(val message: String) : WorkspaceState
}

enum class SaveStatus { SAVED, PENDING, SAVING, FAILED }

data class EditorUi(
    val screenId: ScreenId? = null,
    val selectedId: ComponentId? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
)

/** Editable copy of [ProjectConfig]; numeric fields are strings so partial input is allowed. */
data class ConfigDraft(
    val appName: String,
    val packageName: String,
    val versionCode: String,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
) {
    val appNameError get() = Rules.appNameError(appName)
    val packageError get() = Rules.packageNameError(packageName)
    val versionError get() = Rules.versionError(versionCode.toIntOrNull() ?: 0, versionName)
    val sdkError get() = Rules.sdkError(minSdk, targetSdk)
    val isValid get() = listOf(appNameError, packageError, versionError, sdkError).all { it == null }

    fun applyTo(config: ProjectConfig) = config.copy(
        appName = appName.trim(), packageName = packageName.trim(),
        versionCode = versionCode.toInt(), versionName = versionName.trim(),
        minSdk = minSdk, targetSdk = targetSdk,
    )

    companion object {
        fun from(c: ProjectConfig) = ConfigDraft(c.appName, c.packageName, c.versionCode.toString(), c.versionName, c.minSdk, c.targetSdk)
    }
}

/**
 * Owns one open project: the in-memory model, the undo/redo history and autosave.
 * Every editor (designer, settings, later blocks and AI) changes the project only through
 * [edit], so all changes are validated, undoable and persisted the same way.
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val ids: IdGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val projectId = ProjectId(savedStateHandle.toRoute<WorkspaceRoute>().projectId)

    private val _state = MutableStateFlow<WorkspaceState>(WorkspaceState.Loading)
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    private val _history = MutableStateFlow<List<ProjectSnapshot>>(emptyList())
    val history: StateFlow<List<ProjectSnapshot>> = _history.asStateFlow()

    private val _draft = MutableStateFlow<ConfigDraft?>(null)
    val draft: StateFlow<ConfigDraft?> = _draft.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val edits = EditHistory()
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    /** Revision of the copy on disk; the in-memory project's own revision is not trusted. */
    private var storedRevision = 0L
    private var savedProject: ProjectSchema? = null
    /** Draft matching the last applied config; lets external changes refresh an untouched form. */
    private var savedDraft: ConfigDraft? = null

    private val project: ProjectSchema? get() = (state.value as? WorkspaceState.Loaded)?.project

    init { viewModelScope.launch { reload() } }

    // ------------------------------------------------------------------ editing

    /** Applies [changes] as one undoable step. Returns false (and shows why) if rejected. */
    fun edit(label: String, vararg changes: ProjectEdit): Boolean {
        val before = project ?: return false
        val after = try {
            ProjectEditor.applyAll(before, changes.toList())
        } catch (e: Exception) {
            _messages.trySend(e.toUserMessage())
            return false
        }
        edits.record(label, before)
        setProject(after)
        scheduleSave()
        return true
    }

    fun undo() {
        val current = project ?: return
        val entry = edits.undo(current) ?: return
        setProject(entry.project)
        scheduleSave()
        _messages.trySend("Undid: ${entry.label}")
    }

    fun redo() {
        val current = project ?: return
        val entry = edits.redo(current) ?: return
        setProject(entry.project)
        scheduleSave()
    }

    private fun setProject(p: ProjectSchema) {
        _state.value = WorkspaceState.Loaded(p)
        _ui.update { ui ->
            val screen = p.screen(ui.screenId ?: p.config.mainScreenId) ?: p.mainScreen ?: p.screens.first()
            ui.copy(
                screenId = screen.id,
                selectedId = ui.selectedId?.takeIf { screen.component(it) != null },
                canUndo = edits.canUndo, canRedo = edits.canRedo,
                undoLabel = edits.undoLabel, redoLabel = edits.redoLabel,
            )
        }
        if (_draft.value == null || _draft.value == savedDraft) _draft.value = ConfigDraft.from(p.config)
        savedDraft = ConfigDraft.from(p.config)
    }

    // ------------------------------------------------------------------ designer helpers

    val currentScreen: ScreenSchema? get() = project?.let { p -> ui.value.screenId?.let(p::screen) }

    fun selectScreen(id: ScreenId) = _ui.update { it.copy(screenId = id, selectedId = null) }
    fun select(id: ComponentId?) = _ui.update { it.copy(selectedId = id) }

    fun selectParent() {
        val screen = currentScreen ?: return
        val id = ui.value.selectedId ?: return
        screen.root.parentOf(id)?.let { select(it.id) }
    }

    /** Adds a palette item to [parentId] (default: selected container, else root). */
    fun addComponent(item: PaletteItem, parentId: ComponentId? = null, index: Int = Int.MAX_VALUE) {
        val screen = currentScreen ?: return
        val target = parentId ?: ui.value.selectedId?.let(screen::component)?.let { sel ->
            if (sel.type.isContainer) sel.id else screen.root.parentOf(sel.id)?.id
        } ?: screen.root.id
        val component = ComponentFactory.create(item, ids, screen.allComponents().map { it.viewId }.toSet())
        if (edit("Add ${item.label}", ProjectEdit.AddComponent(screen.id, target, index, component))) select(component.id)
    }

    fun moveComponent(id: ComponentId, newParentId: ComponentId, index: Int) {
        val screen = currentScreen ?: return
        val c = screen.component(id) ?: return
        if (edit("Move ${c.viewId}", ProjectEdit.MoveComponent(screen.id, id, newParentId, index))) select(id)
    }

    /** Moves the selected component one step up (-1) or down (+1) among its siblings. */
    fun shiftSelected(delta: Int) {
        val screen = currentScreen ?: return
        val id = ui.value.selectedId ?: return
        val parent = screen.root.parentOf(id) ?: return
        val pos = parent.children.indexOfFirst { it.id == id }
        val target = pos + delta
        if (target < 0 || target >= parent.children.size) return
        // MoveComponent takes a visible index: moving down by one means "before the slot after next".
        moveComponent(id, parent.id, if (delta > 0) target + 1 else target)
    }

    fun duplicateSelected() {
        val screen = currentScreen ?: return
        val id = ui.value.selectedId ?: return
        val source = screen.component(id) ?: return
        val parent = screen.root.parentOf(id) ?: return
        val copy = ComponentFactory.duplicate(source, ids, screen.allComponents().map { it.viewId }.toSet())
        val index = parent.children.indexOfFirst { it.id == id } + 1
        if (edit("Duplicate ${source.viewId}", ProjectEdit.AddComponent(screen.id, parent.id, index, copy))) select(copy.id)
    }

    fun deleteSelected() {
        val screen = currentScreen ?: return
        val id = ui.value.selectedId ?: return
        val c = screen.component(id) ?: return
        val parent = screen.root.parentOf(id)
        if (edit("Delete ${c.viewId}", ProjectEdit.RemoveComponent(screen.id, id))) select(parent?.id)
    }

    fun setProperty(id: ComponentId, key: String, value: String?) {
        val screen = currentScreen ?: return
        val c = screen.component(id) ?: return
        edit("Change ${c.viewId}.$key", ProjectEdit.SetProperty(screen.id, id, key, value))
    }

    fun setViewId(id: ComponentId, viewId: String) {
        val screen = currentScreen ?: return
        edit("Rename to $viewId", ProjectEdit.SetViewId(screen.id, id, viewId.trim()))
    }

    fun addScreen(layoutName: String) {
        val screen = ScreenFactory.create(layoutName.trim(), ids)
        if (edit("Add screen ${screen.name}", ProjectEdit.AddScreen(screen))) selectScreen(screen.id)
    }

    fun renameScreen(id: ScreenId, layoutName: String) {
        val name = layoutName.trim()
        edit("Rename screen", ProjectEdit.RenameScreen(id, ScreenFactory.activityNameFor(name), name))
    }

    fun deleteScreen(id: ScreenId) {
        if (edit("Delete screen", ProjectEdit.RemoveScreen(id))) project?.let { selectScreen(it.config.mainScreenId) }
    }

    fun setMainScreen(id: ScreenId) { edit("Set main screen", ProjectEdit.SetMainScreen(id)) }

    fun generatedXml(): String? = currentScreen?.let(LayoutXmlGenerator::generate)

    fun findComponent(id: ComponentId): ComponentSchema? = currentScreen?.component(id)

    // ------------------------------------------------------------------ settings tab

    fun editDraft(transform: (ConfigDraft) -> ConfigDraft) = _draft.update { it?.let(transform) }

    fun resetDraft() { project?.let { _draft.value = ConfigDraft.from(it.config) } }

    fun saveConfig() {
        val p = project ?: return
        val d = _draft.value?.takeIf { it.isValid } ?: return
        if (edit("Project settings", ProjectEdit.UpdateConfig(d.applyTo(p.config)))) _messages.trySend("Project settings saved")
    }

    // ------------------------------------------------------------------ history tab

    fun restore(revision: Long) = viewModelScope.launch {
        try {
            flushSave()
            repository.restore(projectId, revision)
            edits.clear()
            reload()
            _messages.send("Restored revision $revision")
        } catch (e: Exception) {
            _messages.send(e.toUserMessage())
        }
    }

    fun recover() = viewModelScope.launch {
        try {
            repository.recover(projectId)
            reload()
            _messages.send("Project recovered")
        } catch (e: Exception) {
            _messages.send(e.toUserMessage())
        }
    }

    // ------------------------------------------------------------------ persistence

    private suspend fun reload() {
        try {
            val p = repository.open(projectId)
            storedRevision = p.revision
            savedProject = p
            _draft.value = null
            setProject(p)
            refreshHistory()
        } catch (e: StoreException.Corrupt) {
            _state.value = WorkspaceState.Damaged(e.snapshots.size)
        } catch (e: Exception) {
            _state.value = WorkspaceState.Failed(e.toUserMessage())
        }
    }

    private suspend fun refreshHistory() {
        _history.value = runCatching { repository.history(projectId) }.getOrDefault(emptyList())
    }

    private fun scheduleSave() {
        _ui.update { it.copy(saveStatus = SaveStatus.PENDING) }
        saveJob?.cancel()
        saveJob = appScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    private suspend fun flushSave() {
        saveJob?.cancel()
        saveNow()
    }

    private suspend fun saveNow() = saveMutex.withLock {
        val p = project ?: return@withLock
        if (p == savedProject?.copy(revision = p.revision, updatedAt = p.updatedAt)) {
            _ui.update { it.copy(saveStatus = SaveStatus.SAVED) }
            return@withLock
        }
        _ui.update { it.copy(saveStatus = SaveStatus.SAVING) }
        try {
            val saved = repository.save(p.copy(revision = storedRevision))
            storedRevision = saved.revision
            savedProject = p
            _ui.update { it.copy(saveStatus = if (project == p) SaveStatus.SAVED else SaveStatus.PENDING) }
            refreshHistory()
        } catch (e: StoreException.Conflict) {
            _ui.update { it.copy(saveStatus = SaveStatus.FAILED) }
            _messages.send("This project was changed elsewhere; your latest edits were not saved.")
        } catch (e: Exception) {
            _ui.update { it.copy(saveStatus = SaveStatus.FAILED) }
            _messages.send(e.toUserMessage())
        }
    }

    override fun onCleared() {
        // Flush outstanding edits in a scope that survives this ViewModel.
        if (ui.value.saveStatus != SaveStatus.SAVED) {
            saveJob?.cancel()
            appScope.launch { saveNow() }
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 600L
    }
}
