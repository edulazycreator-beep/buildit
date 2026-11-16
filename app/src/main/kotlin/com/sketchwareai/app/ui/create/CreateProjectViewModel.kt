package com.sketchwareai.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sketchwareai.app.data.AppSettings
import com.sketchwareai.app.data.ProjectRepository
import com.sketchwareai.app.data.SettingsRepository
import com.sketchwareai.app.data.toUserMessage
import com.sketchwareai.core.model.NewProjectRequest
import com.sketchwareai.core.model.ProjectConfig
import com.sketchwareai.core.model.ProjectId
import com.sketchwareai.core.model.ProjectTemplate
import com.sketchwareai.core.model.Rules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateProjectForm(
    val appName: String = "",
    val packageName: String = "",
    /** While false the package follows the app name; editing the package by hand detaches it. */
    val packageEdited: Boolean = false,
    val template: ProjectTemplate = ProjectTemplate.HELLO_WORLD,
    val minSdk: Int = ProjectConfig.DEFAULT_MIN_SDK,
    val targetSdk: Int = ProjectConfig.DEFAULT_TARGET_SDK,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val appNameError get() = if (appName.isEmpty()) null else Rules.appNameError(appName)
    val packageError get() = if (packageName.isEmpty()) null else Rules.packageNameError(packageName)
    val sdkError get() = Rules.sdkError(minSdk, targetSdk)
    val canSubmit get() = !submitting && Rules.appNameError(appName) == null &&
        Rules.packageNameError(packageName) == null && sdkError == null
}

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(CreateProjectForm())
    val form: StateFlow<CreateProjectForm> = _form.asStateFlow()

    private val _created = Channel<ProjectId>(Channel.BUFFERED)
    val created = _created.receiveAsFlow()

    private var packagePrefix = AppSettings.DEFAULT_PACKAGE_PREFIX

    init {
        viewModelScope.launch { packagePrefix = settings.settings.first().packagePrefix }
    }

    fun onAppNameChange(value: String) = _form.update {
        it.copy(appName = value, packageName = if (it.packageEdited) it.packageName else derivePackage(value), error = null)
    }

    fun onPackageChange(value: String) = _form.update { it.copy(packageName = value.trim(), packageEdited = true, error = null) }
    fun onTemplateChange(value: ProjectTemplate) = _form.update { it.copy(template = value) }
    fun onMinSdkChange(value: Int) = _form.update { it.copy(minSdk = value) }
    fun onTargetSdkChange(value: Int) = _form.update { it.copy(targetSdk = value) }

    fun submit() {
        val f = _form.value
        if (!f.canSubmit) return
        _form.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val project = projects.create(NewProjectRequest(f.appName, f.packageName, f.template, f.minSdk, f.targetSdk))
                _created.send(project.id)
            } catch (e: Exception) {
                _form.update { it.copy(submitting = false, error = e.toUserMessage()) }
            }
        }
    }

    private fun derivePackage(appName: String) = Rules.derivePackageName(packagePrefix, appName)
}
