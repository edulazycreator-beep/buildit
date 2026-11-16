package com.buildit.app.ui.designer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropDownCircle
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buildit.app.ui.common.ConfirmDialog
import com.buildit.app.ui.common.FieldSpec
import com.buildit.app.ui.common.TextFieldsDialog
import com.buildit.app.ui.workspace.EditorUi
import com.buildit.app.ui.workspace.WorkspaceViewModel
import com.buildit.core.model.ComponentId
import com.buildit.core.model.ComponentSchema
import com.buildit.core.model.ComponentType
import com.buildit.core.model.PaletteCategory
import com.buildit.core.model.PaletteItem
import com.buildit.core.model.ProjectSchema
import com.buildit.core.model.ScreenFactory
import com.buildit.core.model.ScreenSchema

/**
 * The "View" tab: screen selector, widget palette, live device preview and properties.
 * Adapts to the window: phones in portrait get a slim palette and a bottom inspector;
 * landscape phones and tablets get a three-pane layout with a side inspector.
 */
@Composable
fun DesignerPane(vm: WorkspaceViewModel, project: ProjectSchema, ui: EditorUi) {
    val screen = ui.screenId?.let(project::screen) ?: return
    var showHierarchy by remember { mutableStateOf(false) }
    var showXml by remember { mutableStateOf(false) }
    var paletteOpen by rememberSaveable { mutableStateOf(true) }
    val selected = ui.selectedId?.let(screen::component)
    // Keep the last selection around so the inspector can animate out with content.
    var lastSelected by remember { mutableStateOf<ComponentSchema?>(null) }
    if (selected != null) lastSelected = selected

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val roomy = maxWidth >= 480.dp
        Column(Modifier.fillMaxSize()) {
            ScreenBar(
                project = project,
                screen = screen,
                vm = vm,
                paletteOpen = paletteOpen,
                onTogglePalette = { paletteOpen = !paletteOpen },
                onShowHierarchy = { showHierarchy = true },
                onShowXml = { showXml = true },
            )
            HorizontalDivider()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedVisibility(
                    visible = paletteOpen,
                    enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkHorizontally(tween(200)) + fadeOut(tween(150)),
                ) {
                    Palette(
                        compact = !roomy,
                        onAdd = { vm.addComponent(it) },
                        modifier = Modifier.width(if (roomy) 104.dp else 72.dp).fillMaxHeight(),
                    )
                }
                DevicePreview(
                    appName = project.config.appName,
                    screen = screen,
                    selected = ui.selectedId,
                    maxFrameWidth = if (wide) 420.dp else Dp.Unspecified,
                    onSelect = vm::select,
                    onDrop = { payload, parent, index ->
                        when (payload) {
                            is DragPayload.NewItem -> vm.addComponent(payload.item, parent, index)
                            is DragPayload.Existing -> vm.moveComponent(payload.id, parent, index)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (wide) {
                    AnimatedVisibility(
                        visible = selected != null,
                        enter = slideInHorizontally(tween(240)) { it } + fadeIn(tween(240)),
                        exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(150)),
                    ) {
                        lastSelected?.let { c ->
                            PropertiesPanel(vm, screen, selected ?: c, PanelLayout.SIDE, Modifier.width(320.dp).fillMaxHeight())
                        }
                    }
                }
            }
            if (!wide) {
                AnimatedVisibility(
                    visible = selected != null,
                    enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(240)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
                ) {
                    lastSelected?.let { c -> PropertiesPanel(vm, screen, selected ?: c, PanelLayout.BOTTOM) }
                }
            }
        }
    }

    if (showHierarchy) HierarchyDialog(screen, ui.selectedId, onSelect = { vm.select(it); showHierarchy = false }, onDismiss = { showHierarchy = false })
    if (showXml) XmlDialog(screen, vm.generatedXml().orEmpty(), onDismiss = { showXml = false })
}

// ------------------------------------------------------------------------ screen bar

private enum class ScreenDialog { ADD, RENAME, DELETE }

@Composable
private fun ScreenBar(
    project: ProjectSchema,
    screen: ScreenSchema,
    vm: WorkspaceViewModel,
    paletteOpen: Boolean,
    onTogglePalette: () -> Unit,
    onShowHierarchy: () -> Unit,
    onShowXml: () -> Unit,
) {
    var screensMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ScreenDialog?>(null) }
    val isMain = screen.id == project.config.mainScreenId

    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onTogglePalette) {
            Icon(
                if (paletteOpen) Icons.AutoMirrored.Outlined.MenuOpen else Icons.Outlined.Widgets,
                contentDescription = if (paletteOpen) "Hide palette" else "Show palette",
            )
        }
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { screensMenu = true }) {
                Text("${screen.layoutName}.xml", fontWeight = FontWeight.SemiBold)
                Text("  ${screen.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose screen")
            }
            DropdownMenu(expanded = screensMenu, onDismissRequest = { screensMenu = false }) {
                project.screens.forEach { s ->
                    DropdownMenuItem(
                        text = { Text("${s.layoutName}.xml" + if (s.id == project.config.mainScreenId) "  (main)" else "") },
                        onClick = { vm.selectScreen(s.id); screensMenu = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Add screen…") }, onClick = { screensMenu = false; dialog = ScreenDialog.ADD })
            }
        }
        IconButton(onClick = onShowHierarchy) { Icon(Icons.Outlined.AccountTree, contentDescription = "View hierarchy") }
        IconButton(onClick = onShowXml) { Icon(Icons.Outlined.Code, contentDescription = "Show XML") }
        Box {
            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Screen actions") }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { Text("Rename screen") }, onClick = { moreMenu = false; dialog = ScreenDialog.RENAME })
                if (!isMain) {
                    DropdownMenuItem(text = { Text("Set as main screen") }, onClick = { moreMenu = false; vm.setMainScreen(screen.id) })
                    DropdownMenuItem(text = { Text("Delete screen") }, onClick = { moreMenu = false; dialog = ScreenDialog.DELETE })
                }
            }
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        ScreenDialog.ADD -> TextFieldsDialog(
            title = "Add screen",
            confirmLabel = "Add",
            fields = listOf(FieldSpec("Layout name (e.g. second)", "", { ScreenFactory.layoutNameError(it.trim(), project.screens) })),
            onConfirm = { vm.addScreen(it[0]) },
            onDismiss = dismiss,
        )
        ScreenDialog.RENAME -> TextFieldsDialog(
            title = "Rename screen",
            confirmLabel = "Rename",
            fields = listOf(FieldSpec("Layout name", screen.layoutName, { ScreenFactory.layoutNameError(it.trim(), project.screens, screen.id) })),
            onConfirm = { vm.renameScreen(screen.id, it[0]) },
            onDismiss = dismiss,
        )
        ScreenDialog.DELETE -> ConfirmDialog(
            title = "Delete ${screen.layoutName}.xml?",
            message = "${screen.name} and all its views will be removed. You can undo this.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteScreen(screen.id) },
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

// ------------------------------------------------------------------------ palette

fun iconFor(item: PaletteItem): ImageVector = when (item) {
    PaletteItem.LINEAR_H -> Icons.Outlined.ViewColumn
    PaletteItem.LINEAR_V -> Icons.Outlined.ViewAgenda
    PaletteItem.SCROLL_V -> Icons.Outlined.SwapVert
    PaletteItem.SCROLL_H -> Icons.Outlined.SwapHoriz
    PaletteItem.FRAME -> Icons.Outlined.CropFree
    PaletteItem.RELATIVE -> Icons.Outlined.GridView
    PaletteItem.CONSTRAINT -> Icons.Outlined.Layers
    PaletteItem.CARD -> Icons.Outlined.CreditCard
    PaletteItem.TEXT_VIEW -> Icons.Outlined.TextFields
    PaletteItem.BUTTON -> Icons.Outlined.SmartButton
    PaletteItem.EDIT_TEXT -> Icons.Outlined.Edit
    PaletteItem.IMAGE_VIEW -> Icons.Outlined.Image
    PaletteItem.IMAGE_BUTTON -> Icons.Outlined.AddPhotoAlternate
    PaletteItem.CHECK_BOX -> Icons.Outlined.CheckBox
    PaletteItem.RADIO_BUTTON -> Icons.Outlined.RadioButtonChecked
    PaletteItem.SWITCH -> Icons.Outlined.ToggleOn
    PaletteItem.PROGRESS_BAR -> Icons.Outlined.HourglassEmpty
    PaletteItem.SEEK_BAR -> Icons.Outlined.LinearScale
    PaletteItem.SPINNER -> Icons.Outlined.ArrowDropDownCircle
    PaletteItem.LIST_VIEW -> Icons.AutoMirrored.Outlined.List
    PaletteItem.RECYCLER_VIEW -> Icons.Outlined.ViewStream
    PaletteItem.WEB_VIEW -> Icons.Outlined.Language
    PaletteItem.VIDEO_VIEW -> Icons.Outlined.OndemandVideo
    PaletteItem.CUSTOM_VIEW -> Icons.Outlined.Extension
}

fun iconFor(component: ComponentSchema): ImageVector {
    if (component.type == ComponentType.LINEAR_LAYOUT) {
        return if (component.properties["orientation"] == "horizontal") Icons.Outlined.ViewColumn else Icons.Outlined.ViewAgenda
    }
    return iconFor(PaletteItem.entries.first { it.type == component.type })
}

fun labelFor(component: ComponentSchema): String = when (component.type) {
    ComponentType.LINEAR_LAYOUT -> if (component.properties["orientation"] == "horizontal") "LinearLayout (H)" else "LinearLayout (V)"
    else -> PaletteItem.entries.first { it.type == component.type }.label
}

@Composable
private fun Palette(compact: Boolean, onAdd: (PaletteItem) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, tonalElevation = 1.dp) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            item {
                if (!compact) Text(
                    "Tap to add\nHold to drag",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                )
            }
            PaletteCategory.entries.forEach { category ->
                item(key = category.name) {
                    Text(
                        category.label,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(PaletteItem.entries.filter { it.category == category }, key = { it.name }) { item ->
                    PaletteEntry(item, compact, onAdd)
                }
            }
        }
    }
}

private fun compactLabel(item: PaletteItem): String =
    item.label.replace("LinearLayout ", "Linear").replace("Horizontal", "H-")
        .removeSuffix("Layout").removeSuffix("View").trim().ifEmpty { item.label }

@Composable
private fun PaletteEntry(item: PaletteItem, compact: Boolean, onAdd: (PaletteItem) -> Unit) {
    val hostView = LocalView.current
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        hostView.startDragAndDrop(
                            dragClipData(item.name),
                            LabelShadowBuilder(context, item.label),
                            DragPayload.NewItem(item),
                            0,
                        )
                    },
                    onDrag = { change, _ -> change.consume() },
                )
            }
            .clickable { onAdd(item) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(iconFor(item), contentDescription = null, modifier = Modifier.size(22.dp))
        Text(
            if (compact) compactLabel(item) else item.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (compact) 9.sp else 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 12.sp,
        )
    }
}

// ------------------------------------------------------------------------ device preview

@Composable
private fun DevicePreview(
    appName: String,
    screen: ScreenSchema,
    selected: ComponentId?,
    maxFrameWidth: Dp,
    onSelect: (ComponentId?) -> Unit,
    onDrop: (DragPayload, ComponentId, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val selectCallback = onSelect
    val dropCallback = onDrop
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), contentAlignment = Alignment.TopCenter) {
        Card(
            Modifier.fillMaxHeight().then(if (maxFrameWidth == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxFrameWidth).fillMaxWidth()),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                // Mock app bar, like the default theme of a generated app.
                Box(
                    Modifier.fillMaxWidth().height(44.dp).background(androidx.compose.ui.graphics.Color(0xFF1A5CC4)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(appName, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(start = 14.dp), fontWeight = FontWeight.Medium)
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ScrollView(ctx).apply {
                            isFillViewport = true
                            setBackgroundColor(Color.WHITE)
                            addView(DesignCanvas(ctx), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        }
                    },
                    update = { scroll ->
                        val canvas = scroll.getChildAt(0) as DesignCanvas
                        canvas.accentColor = accent
                        canvas.listener = object : DesignCanvas.Listener {
                            override fun onSelect(id: ComponentId?) = selectCallback(id)
                            override fun onDrop(payload: DragPayload, parentId: ComponentId, index: Int) = dropCallback(payload, parentId, index)
                        }
                        canvas.render(screen.root, selected)
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------------ dialogs

@Composable
private fun HierarchyDialog(screen: ScreenSchema, selected: ComponentId?, onSelect: (ComponentId) -> Unit, onDismiss: () -> Unit) {
    val rows = remember(screen.root) {
        buildList {
            fun walk(c: ComponentSchema, depth: Int) { add(c to depth); c.children.forEach { walk(it, depth + 1) } }
            walk(screen.root, 0)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${screen.layoutName}.xml") },
        text = {
            LazyColumn {
                items(rows, key = { it.first.id.value }) { (c, depth) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (c.id == selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onSelect(c.id) }
                            .padding(start = (depth * 16).dp + 4.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(iconFor(c), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(c.viewId, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(labelFor(c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XmlDialog(screen: ScreenSchema, xml: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text("${screen.layoutName}.xml") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") } },
                    actions = {
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("${screen.layoutName}.xml", xml))
                        }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
                    },
                )
                Text(
                    xml,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}
