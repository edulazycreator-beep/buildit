package com.buildit.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildit.app.ui.common.relativeTime
import com.buildit.core.storage.ProjectSummary

enum class ProjectAction { RENAME, DUPLICATE, EXPORT, DELETE, RECOVER }

/** Sketchware-style project row: app icon tile, name, package, version, overflow actions. */
@Composable
fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onAction: (ProjectAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(
                    if (project.damaged) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (project.damaged) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = "Damaged", tint = MaterialTheme.colorScheme.onErrorContainer)
                } else {
                    Text(
                        project.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(project.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (project.damaged) {
                    Text("Project file is damaged — tap ⋮ to recover", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(project.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "v${project.versionName} · ${project.screenCount} screen${if (project.screenCount == 1) "" else "s"} · edited ${relativeTime(project.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ProjectMenu(project.damaged, onAction)
        }
    }
}

@Composable
private fun ProjectMenu(damaged: Boolean, onAction: (ProjectAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val select: (ProjectAction) -> Unit = { open = false; onAction(it) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Project actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (damaged) {
                MenuItem("Recover from history", Icons.Outlined.Healing) { select(ProjectAction.RECOVER) }
            } else {
                MenuItem("Rename", Icons.Outlined.Edit) { select(ProjectAction.RENAME) }
                MenuItem("Duplicate", Icons.Outlined.ContentCopy) { select(ProjectAction.DUPLICATE) }
                MenuItem("Export", Icons.Outlined.IosShare) { select(ProjectAction.EXPORT) }
            }
            MenuItem("Delete", Icons.Outlined.Delete) { select(ProjectAction.DELETE) }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}
