package com.buildit.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildit.app.ui.common.ConfirmDialog
import com.buildit.app.ui.common.relativeTime
import com.buildit.app.ui.create.SdkPicker
import com.buildit.app.ui.designer.DesignerPane
import com.buildit.core.model.ComponentSchema
import com.buildit.core.model.ProjectConfig
import com.buildit.core.model.ProjectSchema
import com.buildit.core.storage.ProjectSnapshot

private enum class WorkspaceTab(val label: String) { VIEW("View"), SETTINGS("Settings"), HISTORY("History") }

/**
 * Project workspace, Sketchware-style: the "View" tab is the visual designer. Settings and
 * History manage the project itself. Logic (Event) and Component tabs arrive in later phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(onBack: () -> Unit, vm: WorkspaceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }

    val title = (state as? WorkspaceState.Loaded)?.project?.config?.appName ?: "Project"
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1)
                            Text(
                                when (ui.saveStatus) {
                                    SaveStatus.SAVED -> "All changes saved"
                                    SaveStatus.PENDING, SaveStatus.SAVING -> "Saving…"
                                    SaveStatus.FAILED -> "Not saved"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ui.saveStatus == SaveStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        if (state is WorkspaceState.Loaded) {
                            IconButton(onClick = vm::undo, enabled = ui.canUndo) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = ui.undoLabel?.let { "Undo $it" } ?: "Undo")
                            }
                            IconButton(onClick = vm::redo, enabled = ui.canRedo) {
                                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = ui.redoLabel?.let { "Redo $it" } ?: "Redo")
                            }
                        }
                    },
                )
                if (state is WorkspaceState.Loaded) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        WorkspaceTab.entries.forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) })
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                WorkspaceState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is WorkspaceState.Failed -> Centered(s.message)
                is WorkspaceState.Damaged -> DamagedPane(s.snapshotCount) { vm.recover() }
                is WorkspaceState.Loaded -> when (WorkspaceTab.entries[tab]) {
                    WorkspaceTab.VIEW -> DesignerPane(vm, s.project, ui)
                    WorkspaceTab.SETTINGS -> SettingsPane(vm)
                    WorkspaceTab.HISTORY -> HistoryPane(s.project, history) { vm.restore(it) }
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DamagedPane(snapshotCount: Int, onRecover: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This project's file is damaged", style = MaterialTheme.typography.titleLarge)
        Text(
            if (snapshotCount > 0) "$snapshotCount earlier version(s) are available in history."
            else "No earlier versions are available, so it cannot be recovered automatically.",
            modifier = Modifier.padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
        )
        if (snapshotCount > 0) Button(onClick = { confirm = true }) { Text("Recover latest version") }
    }
    if (confirm) ConfirmDialog(
        title = "Recover project?",
        message = "The damaged file is kept aside and the newest readable version is restored.",
        confirmLabel = "Recover",
        onConfirm = onRecover,
        onDismiss = { confirm = false },
    )
}

// ------------------------------------------------------------------------- settings

@Composable
private fun SettingsPane(vm: WorkspaceViewModel) {
    val draft = vm.draft.collectAsStateWithLifecycle().value ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.appName, onValueChange = { v -> vm.editDraft { it.copy(appName = v) } },
            label = { Text("App name") }, singleLine = true, isError = draft.appNameError != null,
            supportingText = draft.appNameError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.packageName, onValueChange = { v -> vm.editDraft { it.copy(packageName = v.trim()) } },
            label = { Text("Package name") }, singleLine = true, isError = draft.packageError != null,
            supportingText = draft.packageError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.versionCode, onValueChange = { v -> vm.editDraft { it.copy(versionCode = v.filter(Char::isDigit).take(10)) } },
                label = { Text("Version code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.versionName, onValueChange = { v -> vm.editDraft { it.copy(versionName = v) } },
                label = { Text("Version name") }, singleLine = true, modifier = Modifier.weight(1f),
            )
        }
        draft.versionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val range = ProjectConfig.LOWEST_MIN_SDK..ProjectConfig.HIGHEST_TARGET_SDK
            SdkPicker("Minimum SDK", draft.minSdk, range, { v -> vm.editDraft { it.copy(minSdk = v) } }, Modifier.weight(1f))
            SdkPicker("Target SDK", draft.targetSdk, range, { v -> vm.editDraft { it.copy(targetSdk = v) } }, Modifier.weight(1f))
        }
        draft.sdkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = vm::resetDraft, modifier = Modifier.weight(1f)) { Text("Reset") }
            Button(onClick = vm::saveConfig, enabled = draft.isValid, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
    }
}

// ------------------------------------------------------------------------- history

@Composable
private fun HistoryPane(project: ProjectSchema, history: List<ProjectSnapshot>, onRestore: (Long) -> Unit) {
    var confirm by remember { mutableStateOf<ProjectSnapshot?>(null) }
    if (history.isEmpty()) {
        Centered("No earlier versions yet. A snapshot is kept automatically every time the project is saved.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            ListItem(
                headlineContent = { Text("Current version") },
                supportingContent = { Text("What you see in the editor. Older versions are kept automatically.") },
            )
            HorizontalDivider()
        }
        items(history, key = { it.revision }) { snap ->
            ListItem(
                headlineContent = { Text("Revision ${snap.revision}") },
                supportingContent = { Text("Saved ${relativeTime(snap.savedAt)}") },
                trailingContent = { TextButton(onClick = { confirm = snap }) { Text("Restore") } },
            )
        }
    }
    confirm?.let { snap ->
        ConfirmDialog(
            title = "Restore revision ${snap.revision}?",
            message = "The current state is saved to history first, so you can undo this restore.",
            confirmLabel = "Restore",
            onConfirm = { onRestore(snap.revision) },
            onDismiss = { confirm = null },
        )
    }
}
