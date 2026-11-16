package com.buildit.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildit.app.ui.projects.ProjectAction
import com.buildit.app.ui.projects.ProjectCard
import com.buildit.app.ui.projects.ProjectsViewModel
import com.buildit.app.ui.projects.rememberProjectActions
import com.buildit.core.model.ProjectId

private const val RECENT_COUNT = 5

/**
 * Home screen. Mirrors Sketchware's main screen (project list + big "+" button) while adding
 * quick actions. The contextual AI entry point is added here in Phase 6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onCreateProject: () -> Unit,
    onOpenProject: (ProjectId) -> Unit,
    onShowAllProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ProjectsViewModel = hiltViewModel(),
) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberProjectActions(vm)

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.openRequests.collect { onOpenProject(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build It") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProject,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New project") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val list = projects
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyState(onCreateProject, actions.launchImport)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = actions.launchImport) {
                                Icon(Icons.Outlined.FileOpen, contentDescription = null)
                                Text("  Import")
                            }
                            FilledTonalButton(onClick = onShowAllProjects) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                Text("  All projects (${list.size})")
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent projects", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (list.size > RECENT_COUNT) TextButton(onClick = onShowAllProjects) { Text("See all") }
                        }
                    }
                    items(list.take(RECENT_COUNT), key = { it.id.value }) { p ->
                        ProjectCard(
                            modifier = Modifier.animateItem(),
                            project = p,
                            onOpen = { if (p.damaged) actions.onAction(p, ProjectAction.RECOVER) else onOpenProject(p.id) },
                            onAction = { actions.onAction(p, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Build your first app", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Create a project from a template, design its screens and wire up logic with blocks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onImport) { Text("Import project") }
            Button(onClick = onCreate) { Text("Create project") }
        }
    }
}
