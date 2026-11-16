package com.sketchwareai.app.ui.projects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sketchwareai.app.data.ProjectRepository
import com.sketchwareai.app.data.toUserMessage
import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.storage.ProjectSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Project list + project-level actions. Shared by the Dashboard and the Project Manager. */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>?> = repository.projects

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _openRequests = Channel<ProjectId>(Channel.BUFFERED)
    /** Emits when an action (import, duplicate) produced a project that should be opened. */
    val openRequests: Flow<ProjectId> = _openRequests.receiveAsFlow()

    init { refresh() }

    fun refresh() = launchAction(null) { repository.refresh() }

    fun rename(id: ProjectId, name: String) = launchAction("Project renamed") { repository.rename(id, name) }

    fun duplicate(id: ProjectId, name: String, packageName: String) = launchAction("Project duplicated") {
        repository.duplicate(id, name, packageName)
    }

    fun delete(id: ProjectId) = launchAction("Project deleted") { repository.delete(id) }

    fun recover(id: ProjectId) = launchAction("Project recovered from history") { repository.recover(id) }

    fun export(id: ProjectId, destination: Uri) = launchAction("Project exported") { repository.export(id, destination) }

    fun import(source: Uri) = launchAction("Project imported") {
        val project = repository.import(source)
        _openRequests.send(project.id)
    }

    fun suggestDuplicatePackage(packageName: String) = repository.suggestDuplicatePackage(packageName)

    private fun launchAction(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
                success?.let { _messages.send(it) }
            } catch (e: Exception) {
                _messages.send(e.toUserMessage())
            } finally {
                _busy.value = false
            }
        }
    }
}
