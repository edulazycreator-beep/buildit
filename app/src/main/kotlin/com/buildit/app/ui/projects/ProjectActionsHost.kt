package com.buildit.app.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.buildit.app.ui.common.ConfirmDialog
import com.buildit.app.ui.common.FieldSpec
import com.buildit.app.ui.common.TextFieldsDialog
import com.buildit.core.model.Rules
import com.buildit.core.storage.FileProjectStore
import com.buildit.core.storage.ProjectSummary

/** Stateful helper that turns [ProjectAction]s into dialogs, file pickers and ViewModel calls. */
class ProjectActionsState internal constructor(
    internal val onActionImpl: (ProjectSummary, ProjectAction) -> Unit,
    val launchImport: () -> Unit,
) {
    fun onAction(project: ProjectSummary, action: ProjectAction) = onActionImpl(project, action)
}

@Composable
fun rememberProjectActions(vm: ProjectsViewModel): ProjectActionsState {
    var pending by remember { mutableStateOf<Pair<ProjectSummary, ProjectAction>?>(null) }
    var exporting by remember { mutableStateOf<ProjectSummary?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val project = exporting
        exporting = null
        if (uri != null && project != null) vm.export(project.id, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    when (val p = pending) {
        null -> Unit
        else -> {
            val (project, action) = p
            val dismiss = { pending = null }
            when (action) {
                ProjectAction.RENAME -> TextFieldsDialog(
                    title = "Rename project",
                    confirmLabel = "Rename",
                    fields = listOf(FieldSpec("App name", project.appName, Rules::appNameError, KeyboardCapitalization.Words)),
                    onConfirm = { vm.rename(project.id, it[0]) },
                    onDismiss = dismiss,
                )
                ProjectAction.DUPLICATE -> TextFieldsDialog(
                    title = "Duplicate project",
                    confirmLabel = "Duplicate",
                    fields = listOf(
                        FieldSpec("App name", "${project.appName} copy", Rules::appNameError, KeyboardCapitalization.Words),
                        FieldSpec("Package name", vm.suggestDuplicatePackage(project.packageName), Rules::packageNameError),
                    ),
                    onConfirm = { vm.duplicate(project.id, it[0], it[1]) },
                    onDismiss = dismiss,
                )
                ProjectAction.DELETE -> ConfirmDialog(
                    title = "Delete \"${project.appName}\"?",
                    message = "The project, its history and all its files will be permanently removed from this device. Export it first if you may need it again.",
                    confirmLabel = "Delete",
                    destructive = true,
                    onConfirm = { vm.delete(project.id) },
                    onDismiss = dismiss,
                )
                ProjectAction.RECOVER -> ConfirmDialog(
                    title = "Recover project?",
                    message = "The damaged file will be kept aside and the project restored from its most recent readable snapshot. Changes after that snapshot are lost.",
                    confirmLabel = "Recover",
                    onConfirm = { vm.recover(project.id) },
                    onDismiss = dismiss,
                )
                ProjectAction.EXPORT -> Unit // handled immediately below
            }
        }
    }

    return remember(vm) {
        ProjectActionsState(
            onActionImpl = { project, action ->
                if (action == ProjectAction.EXPORT) {
                    exporting = project
                    val safeName = project.appName.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "project" }
                    exportLauncher.launch("$safeName.${FileProjectStore.ARCHIVE_EXTENSION}")
                } else {
                    pending = project to action
                }
            },
            launchImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        )
    }
}
