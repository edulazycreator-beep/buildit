package com.buildit.app.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildit.app.ui.common.FieldSpec
import com.buildit.app.ui.common.TextFieldsDialog
import com.buildit.app.ui.workspace.WorkspaceViewModel
import com.buildit.core.model.ComponentSchema
import com.buildit.core.model.PropertyCatalog
import com.buildit.core.model.PropertyDef
import com.buildit.core.model.PropertyGroup
import com.buildit.core.model.PropertyType
import com.buildit.core.model.Rules
import com.buildit.core.model.ScreenSchema
import com.buildit.core.model.parentOf

enum class PanelLayout { BOTTOM, SIDE }

/**
 * Inspector for the selected view. [PanelLayout.BOTTOM] is a compact strip of property
 * cards for phones in portrait; [PanelLayout.SIDE] is a full grouped list for wide windows.
 */
@Composable
fun PropertiesPanel(
    vm: WorkspaceViewModel,
    screen: ScreenSchema,
    component: ComponentSchema,
    layout: PanelLayout,
    modifier: Modifier = Modifier,
) {
    val parent = screen.root.parentOf(component.id)
    val isRoot = parent == null
    val defs = PropertyCatalog.propertiesFor(component.type, parent?.type)
    var editing by remember(component.id) { mutableStateOf<PropertyDef?>(null) }
    var renaming by remember(component.id) { mutableStateOf(false) }
    val onPick: (PropertyDef) -> Unit = { def ->
        val value = component.properties[def.key]
        if (def.type == PropertyType.Bool) vm.setProperty(component.id, def.key, if (value == "true") "false" else "true")
        else editing = def
    }

    Surface(modifier, tonalElevation = 3.dp, shadowElevation = if (layout == PanelLayout.BOTTOM) 8.dp else 0.dp) {
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(iconFor(component), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { renaming = true }.padding(4.dp)) {
                    Text(component.viewId, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(labelFor(component), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (layout == PanelLayout.BOTTOM) {
                    ActionButtons(vm, isRoot)
                }
            }
            if (layout == PanelLayout.SIDE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) { ActionButtons(vm, isRoot) }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth()) {
                    PropertyGroup.entries.forEach { group ->
                        val inGroup = defs.filter { it.group == group }
                        if (inGroup.isNotEmpty()) {
                            item(key = "h-" + group.name) {
                                Text(
                                    group.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(inGroup, key = { it.key }) { def -> PropertyRow(def, component.properties[def.key]) { onPick(def) } }
                        }
                    }
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(defs, key = { it.key }) { def -> PropertyCard(def, component.properties[def.key]) { onPick(def) } }
                }
            }
        }
    }

    editing?.let { def ->
        PropertyEditorDialog(
            def = def,
            current = component.properties[def.key],
            onSave = { vm.setProperty(component.id, def.key, it) },
            onDismiss = { editing = null },
        )
    }
    if (renaming) {
        val taken = screen.allComponents().filter { it.id != component.id }.map { it.viewId }.toSet()
        TextFieldsDialog(
            title = "Rename view id",
            confirmLabel = "Rename",
            fields = listOf(FieldSpec("Id", component.viewId, { v ->
                val t = v.trim()
                when {
                    !Rules.isJavaIdentifier(t) -> "Letters, digits and _; must not start with a digit"
                    t in taken -> "Already used on this screen"
                    else -> null
                }
            })),
            onConfirm = { vm.setViewId(component.id, it[0]) },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun ActionButtons(vm: WorkspaceViewModel, isRoot: Boolean) {
    IconButton(onClick = vm::selectParent, enabled = !isRoot) { Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Select parent") }
    IconButton(onClick = { vm.shiftSelected(-1) }, enabled = !isRoot) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up") }
    IconButton(onClick = { vm.shiftSelected(1) }, enabled = !isRoot) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down") }
    IconButton(onClick = vm::duplicateSelected, enabled = !isRoot) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate") }
    IconButton(onClick = vm::deleteSelected, enabled = !isRoot) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
}

@Composable
private fun PropertyRow(def: PropertyDef, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(def.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (def.type == PropertyType.Color && value != null) {
            ColorSwatch(value, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (def.type == PropertyType.Bool) {
            androidx.compose.material3.Switch(checked = value == "true", onCheckedChange = { onClick() }, modifier = Modifier.heightIn(max = 32.dp))
        } else {
            Text(
                displayValue(def, value),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

@Composable
private fun PropertyCard(def: PropertyDef, value: String?, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.width(118.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(def.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (def.type == PropertyType.Color && value != null) {
                    ColorSwatch(value, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    displayValue(def, value),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun displayValue(def: PropertyDef, value: String?): String = when {
    value == null && def.type == PropertyType.Bool -> "false"
    value == null -> "—"
    def.type == PropertyType.Text -> value.replace('\n', ' ')
    else -> value
}

@Composable
private fun ColorSwatch(hex: String, modifier: Modifier = Modifier) {
    val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Transparent)
    Box(modifier.clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

// ------------------------------------------------------------------------ editors

private val PRESET_COLORS = listOf(
    "#FFFFFFFF", "#FF000000", "#FF757575", "#FFE0E0E0", "#FFF44336", "#FFE91E63", "#FF9C27B0", "#FF673AB7",
    "#FF3F51B5", "#FF1A5CC4", "#FF2196F3", "#FF03A9F4", "#FF00BCD4", "#FF009688", "#FF4CAF50", "#FF8BC34A",
    "#FFCDDC39", "#FFFFEB3B", "#FFFFC107", "#FFFF9800", "#FFFF5722", "#FF795548", "#00000000",
)

/** Editor for one property. `onSave(null)` clears the property (falls back to the default). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertyEditorDialog(def: PropertyDef, current: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current.orEmpty()) }
    val type = def.type
    val error = value.takeIf { it.isNotEmpty() }?.let(def::validate)
    val clearable = def !== PropertyCatalog.LAYOUT_WIDTH && def !== PropertyCatalog.LAYOUT_HEIGHT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(def.label) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (type) {
                    is PropertyType.Choice -> type.options.forEach { option ->
                        OptionRow(option, selected = value == option) { value = option }
                    }
                    is PropertyType.Flags -> {
                        val selected = value.split('|').filter { it.isNotEmpty() }.toSet()
                        type.options.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().toggleable(value = option in selected, role = Role.Checkbox) { on ->
                                    val next = if (on) selected + option else selected - option
                                    value = type.options.filter { it in next }.joinToString("|")
                                }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = option in selected, onCheckedChange = null)
                                Text(option, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    PropertyType.SizeDimension -> {
                        OptionRow("match_parent", value == "match_parent") { value = "match_parent" }
                        OptionRow("wrap_content", value == "wrap_content") { value = "wrap_content" }
                        val custom = value.endsWith("dp")
                        OutlinedTextField(
                            value = if (custom) value.removeSuffix("dp") else "",
                            onValueChange = { v -> value = v.filter(Char::isDigit).take(4).let { if (it.isEmpty()) "" else it + "dp" } },
                            label = { Text("Custom size (dp)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    PropertyType.Dimension, PropertyType.TextSize -> {
                        val unit = if (type == PropertyType.TextSize) "sp" else "dp"
                        OutlinedTextField(
                            value = value.removeSuffix(unit),
                            onValueChange = { v -> value = v.filter { it.isDigit() || (unit == "sp" && it == '.') }.take(6).let { if (it.isEmpty()) "" else it + unit } },
                            label = { Text("Value ($unit)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    PropertyType.Color -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PRESET_COLORS.forEach { hex ->
                                ColorSwatch(
                                    hex,
                                    Modifier.size(32.dp).border(
                                        if (value.equals(hex, true)) 3.dp else 0.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    ).clickable { value = hex },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it.trim().take(9) },
                            label = { Text("Hex (#AARRGGBB)") },
                            singleLine = true,
                            isError = error != null,
                        )
                    }
                    PropertyType.Integer, PropertyType.Decimal -> OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.trim() },
                        label = { Text("Value") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (type == PropertyType.Integer) KeyboardType.Number else KeyboardType.Decimal),
                    )
                    PropertyType.Text -> OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(def.label) },
                        minLines = 2,
                    )
                    PropertyType.Drawable, PropertyType.ClassName -> OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.trim() },
                        label = { Text(if (type == PropertyType.Drawable) "Drawable name (e.g. logo)" else "Class (e.g. com.example.MyView)") },
                        singleLine = true,
                    )
                    PropertyType.Bool -> {
                        OptionRow("true", value == "true") { value = "true" }
                        OptionRow("false", value == "false") { value = "false" }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = error == null && (value.isNotEmpty() || clearable),
                onClick = { onSave(value.ifEmpty { null }); onDismiss() },
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                if (clearable && current != null) TextButton(onClick = { onSave(null); onDismiss() }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
