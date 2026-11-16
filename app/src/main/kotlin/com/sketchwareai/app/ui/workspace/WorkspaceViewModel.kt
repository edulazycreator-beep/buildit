package com.sketchwareai.app.ui.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sketchwareai.app.data.ProjectRepository
import com.sketchwareai.app.data.toUserMessage
import com.sketchwareai.app.ui.navigation.WorkspaceRoute
import com.sketchwareai.core.model.ProjectConfig
import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.model.ProjectSchema
import com.sketchwareai.core.model.Rules
import com.sketchwareai.core.storage.ProjectSnapshot
import com.sketchwareai.core.storage.StoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WorkspaceState {
    data object Loading : WorkspaceState
    data class Loaded(val project: ProjectSchema, val history: List<ProjectSnapshot>) : WorkspaceState
    data class Damaged(val snapshotCount: Int) : WorkspaceState
    data class Failed(val message: String) : WorkspaceState
}

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

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
) : ViewModel() {

    val projectId = ProjectId(savedStateHandle.toRoute<WorkspaceRoute>().projectId)

    private val _state = MutableStateFlow<WorkspaceState>(WorkspaceState.Loading)
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private val _draft = MutableStateFlow<ConfigDraft?>(null)
    val draft: StateFlow<ConfigDraft?> = _draft.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init { viewModelScope.launch { reload() } }

    private suspend fun show(project: ProjectSchema) {
        _state.value = WorkspaceState.Loaded(project, repository.history(project.id))
        _draft.value = ConfigDraft.from(project.config)
    }

    fun editDraft(transform: (ConfigDraft) -> ConfigDraft) = _draft.update { it?.let(transform) }

    fun resetDraft() {
        (state.value as? WorkspaceState.Loaded)?.let { _draft.value = ConfigDraft.from(it.project.config) }
    }

    fun saveConfig() {
        val loaded = state.value as? WorkspaceState.Loaded ?: return
        val draft = _draft.value?.takeIf { it.isValid } ?: return
        mutate("Project settings saved") { repository.updateConfig(loaded.project, draft.applyTo(loaded.project.config)) }
    }

    fun restore(revision: Long) = mutate("Restored revision $revision") { repository.restore(projectId, revision) }

    fun recover() = mutate("Project recovered") { repository.recover(projectId) }

    private fun mutate(success: String, block: suspend () -> ProjectSchema) {
        viewModelScope.launch {
            try {
                show(block())
                _messages.send(success)
            } catch (e: StoreException.Conflict) {
                _messages.send(e.toUserMessage())
                reload()
            } catch (e: Exception) {
                _messages.send(e.toUserMessage())
            }
        }
    }

    /** Loads the latest stored state and records the project as recently opened. */
    private suspend fun reload() {
        try {
            show(repository.open(projectId))
        } catch (e: StoreException.Corrupt) {
            _state.value = WorkspaceState.Damaged(e.snapshots.size)
        } catch (e: Exception) {
            _state.value = WorkspaceState.Failed(e.toUserMessage())
        }
    }
}
