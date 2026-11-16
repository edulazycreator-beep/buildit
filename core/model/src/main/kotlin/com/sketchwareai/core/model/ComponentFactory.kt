package com.sketchwareai.core.model

import com.sketchwareai.core.model.ComponentProperty.LAYOUT_HEIGHT
import com.sketchwareai.core.model.ComponentProperty.LAYOUT_WIDTH

enum class PaletteCategory(val label: String) { LAYOUT("Layouts"), WIDGET("Widgets") }

/** Items of the designer palette, with Sketchware-style id prefixes and sensible defaults. */
enum class PaletteItem(
    val label: String,
    val category: PaletteCategory,
    val type: ComponentType,
    val idPrefix: String,
    val defaults: Map<String, String>,
) {
    LINEAR_H("LinearLayout (H)", PaletteCategory.LAYOUT, ComponentType.LINEAR_LAYOUT, "linear",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "orientation" to "horizontal", "padding" to "8dp")),
    LINEAR_V("LinearLayout (V)", PaletteCategory.LAYOUT, ComponentType.LINEAR_LAYOUT, "linear",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "orientation" to "vertical", "padding" to "8dp")),
    SCROLL_V("ScrollView", PaletteCategory.LAYOUT, ComponentType.SCROLL_VIEW, "vscroll",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content")),
    SCROLL_H("HorizontalScrollView", PaletteCategory.LAYOUT, ComponentType.HORIZONTAL_SCROLL_VIEW, "hscroll",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content")),
    FRAME("FrameLayout", PaletteCategory.LAYOUT, ComponentType.FRAME_LAYOUT, "frame",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "padding" to "8dp")),
    RELATIVE("RelativeLayout", PaletteCategory.LAYOUT, ComponentType.RELATIVE_LAYOUT, "relative",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "120dp", "padding" to "8dp")),
    CONSTRAINT("ConstraintLayout", PaletteCategory.LAYOUT, ComponentType.CONSTRAINT_LAYOUT, "constraint",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "120dp", "padding" to "8dp")),
    CARD("CardView", PaletteCategory.LAYOUT, ComponentType.CARD_VIEW, "card",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "layout_margin" to "8dp", "cardCornerRadius" to "12dp", "cardElevation" to "2dp", "padding" to "12dp")),

    TEXT_VIEW("TextView", PaletteCategory.WIDGET, ComponentType.TEXT_VIEW, "textview",
        mapOf(LAYOUT_WIDTH to "wrap_content", LAYOUT_HEIGHT to "wrap_content", "text" to "TextView", "textSize" to "14sp", "textColor" to "#FF000000")),
    BUTTON("Button", PaletteCategory.WIDGET, ComponentType.BUTTON, "button",
        mapOf(LAYOUT_WIDTH to "wrap_content", LAYOUT_HEIGHT to "wrap_content", "text" to "Button")),
    EDIT_TEXT("EditText", PaletteCategory.WIDGET, ComponentType.EDIT_TEXT, "edittext",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "hint" to "Edit Text", "textSize" to "14sp")),
    IMAGE_VIEW("ImageView", PaletteCategory.WIDGET, ComponentType.IMAGE_VIEW, "imageview",
        mapOf(LAYOUT_WIDTH to "100dp", LAYOUT_HEIGHT to "100dp", "scaleType" to "centerCrop")),
    IMAGE_BUTTON("ImageButton", PaletteCategory.WIDGET, ComponentType.IMAGE_BUTTON, "imagebutton",
        mapOf(LAYOUT_WIDTH to "wrap_content", LAYOUT_HEIGHT to "wrap_content")),
    CHECK_BOX("CheckBox", PaletteCategory.WIDGET, ComponentType.CHECK_BOX, "checkbox",
        mapOf(LAYOUT_WIDTH to "wrap_content", LAYOUT_HEIGHT to "wrap_content", "text" to "CheckBox")),
    RADIO_BUTTON("RadioButton", PaletteCategory.WIDGET, ComponentType.RADIO_BUTTON, "radiobutton",
        mapOf(LAYOUT_WIDTH to "wrap_content", LAYOUT_HEIGHT to "wrap_content", "text" to "RadioButton")),
    SWITCH("Switch", PaletteCategory.WIDGET, ComponentType.SWITCH, "switch",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "text" to "Switch")),
    PROGRESS_BAR("ProgressBar", PaletteCategory.WIDGET, ComponentType.PROGRESS_BAR, "progressbar",
        mapOf(LAYOUT_WIDTH to "wrap_content", LAYOUT_HEIGHT to "wrap_content")),
    SEEK_BAR("SeekBar", PaletteCategory.WIDGET, ComponentType.SEEK_BAR, "seekbar",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content", "max" to "100")),
    SPINNER("Spinner", PaletteCategory.WIDGET, ComponentType.SPINNER, "spinner",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content")),
    LIST_VIEW("ListView", PaletteCategory.WIDGET, ComponentType.LIST_VIEW, "listview",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content")),
    RECYCLER_VIEW("RecyclerView", PaletteCategory.WIDGET, ComponentType.RECYCLER_VIEW, "recyclerview",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "wrap_content")),
    WEB_VIEW("WebView", PaletteCategory.WIDGET, ComponentType.WEB_VIEW, "webview",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "200dp")),
    VIDEO_VIEW("VideoView", PaletteCategory.WIDGET, ComponentType.VIDEO_VIEW, "videoview",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "200dp")),
    CUSTOM_VIEW("Custom View", PaletteCategory.WIDGET, ComponentType.CUSTOM_VIEW, "view",
        mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "48dp", "customClass" to "android.view.View")),
}

object ComponentFactory {

    fun create(item: PaletteItem, ids: IdGenerator, takenViewIds: Set<String>): ComponentSchema =
        ComponentSchema(ids.componentId(), item.type, nextViewId(item.idPrefix, takenViewIds), item.defaults)

    /** `textview` + {textview1, textview2} → `textview3`. */
    fun nextViewId(prefix: String, taken: Set<String>): String {
        var n = 1
        while ("$prefix$n" in taken) n++
        return "$prefix$n"
    }

    /** Deep copy with fresh entity ids and unused view ids (for "duplicate"). */
    fun duplicate(source: ComponentSchema, ids: IdGenerator, takenViewIds: Set<String>): ComponentSchema {
        val taken = takenViewIds.toMutableSet()
        fun copy(c: ComponentSchema): ComponentSchema {
            val prefix = c.viewId.trimEnd { it.isDigit() }.ifEmpty { "view" }
            val viewId = nextViewId(prefix, taken).also { taken += it }
            return c.copy(id = ids.componentId(), viewId = viewId, children = c.children.map(::copy))
        }
        return copy(source)
    }
}

object ScreenFactory {
    /** `second` → `SecondActivity`; `user_profile` → `UserProfileActivity`. */
    fun activityNameFor(layoutName: String): String =
        layoutName.split('_').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } + "Activity"

    fun layoutNameError(layoutName: String, existing: List<ScreenSchema>, ignore: ScreenId? = null): String? = when {
        layoutName.isBlank() -> "Name is required"
        !Rules.isResourceName(layoutName) -> "Use lowercase letters, digits and _ (start with a letter)"
        existing.any { it.id != ignore && it.layoutName == layoutName } -> "\"$layoutName\" already exists"
        existing.any { it.id != ignore && it.name == activityNameFor(layoutName) } -> "${activityNameFor(layoutName)} already exists"
        else -> null
    }

    fun create(layoutName: String, ids: IdGenerator): ScreenSchema = ScreenSchema(
        id = ids.screenId(),
        name = activityNameFor(layoutName),
        layoutName = layoutName,
        root = ComponentSchema(
            ids.componentId(), ComponentType.LINEAR_LAYOUT, "linear1",
            mapOf(LAYOUT_WIDTH to "match_parent", LAYOUT_HEIGHT to "match_parent", "orientation" to "vertical"),
        ),
    )
}
