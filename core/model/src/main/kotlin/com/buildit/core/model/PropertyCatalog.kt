package com.buildit.core.model

import com.buildit.core.model.ComponentType.BUTTON
import com.buildit.core.model.ComponentType.CARD_VIEW
import com.buildit.core.model.ComponentType.CHECK_BOX
import com.buildit.core.model.ComponentType.CONSTRAINT_LAYOUT
import com.buildit.core.model.ComponentType.CUSTOM_VIEW
import com.buildit.core.model.ComponentType.EDIT_TEXT
import com.buildit.core.model.ComponentType.FRAME_LAYOUT
import com.buildit.core.model.ComponentType.HORIZONTAL_SCROLL_VIEW
import com.buildit.core.model.ComponentType.IMAGE_BUTTON
import com.buildit.core.model.ComponentType.IMAGE_VIEW
import com.buildit.core.model.ComponentType.LINEAR_LAYOUT
import com.buildit.core.model.ComponentType.PROGRESS_BAR
import com.buildit.core.model.ComponentType.RADIO_BUTTON
import com.buildit.core.model.ComponentType.RELATIVE_LAYOUT
import com.buildit.core.model.ComponentType.SCROLL_VIEW
import com.buildit.core.model.ComponentType.SEEK_BAR
import com.buildit.core.model.ComponentType.SWITCH
import com.buildit.core.model.ComponentType.TEXT_VIEW

/** Value kinds a property can hold. Drives the inspector's editor and value validation. */
sealed interface PropertyType {
    /** `match_parent`, `wrap_content` or `<n>dp`. */
    data object SizeDimension : PropertyType
    /** `<n>dp`. */
    data object Dimension : PropertyType
    /** `<n>sp`. */
    data object TextSize : PropertyType
    /** `#RRGGBB` or `#AARRGGBB`. */
    data object Color : PropertyType
    data object Text : PropertyType
    data object Integer : PropertyType
    data object Decimal : PropertyType
    data object Bool : PropertyType
    /** Name of a drawable resource. */
    data object Drawable : PropertyType
    /** Fully-qualified Java class name. */
    data object ClassName : PropertyType
    data class Choice(val options: List<String>) : PropertyType
    /** One or more of [options], joined with `|`. */
    data class Flags(val options: List<String>) : PropertyType
}

enum class PropertyGroup { LAYOUT, APPEARANCE, TEXT, BEHAVIOR }

/**
 * A designer property. [key] is the storage key in [ComponentSchema.properties]; [toXml]
 * maps a valid value to zero or more XML attributes (name with namespace prefix → value).
 */
class PropertyDef(
    val key: String,
    val label: String,
    val type: PropertyType,
    val group: PropertyGroup,
    val toXml: (String) -> List<Pair<String, String>> = { listOf("android:$key" to it) },
) {
    fun validate(value: String): String? = PropertyValues.error(type, value)
    override fun toString() = "PropertyDef($key)"
}

object PropertyValues {
    private val DP = Regex("^\\d{1,4}dp$")
    private val SP = Regex("^\\d{1,3}(\\.\\d{1,2})?sp$")
    private val COLOR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val INT = Regex("^-?\\d{1,9}$")
    private val DECIMAL = Regex("^-?\\d{1,6}(\\.\\d{1,4})?$")
    private val CLASS = Regex("^([a-zA-Z_][a-zA-Z0-9_]*\\.)+[A-Z][a-zA-Z0-9_]*$")
    const val MAX_TEXT_LENGTH = 5_000

    fun error(type: PropertyType, value: String): String? = when (type) {
        PropertyType.SizeDimension ->
            if (value == "match_parent" || value == "wrap_content" || DP.matches(value)) null
            else "Use match_parent, wrap_content or a size like 120dp"
        PropertyType.Dimension -> if (DP.matches(value)) null else "Use a size like 16dp"
        PropertyType.TextSize -> if (SP.matches(value)) null else "Use a text size like 14sp"
        PropertyType.Color -> if (COLOR.matches(value)) null else "Use a color like #FF2196F3"
        PropertyType.Text -> if (value.length <= MAX_TEXT_LENGTH) null else "Text is too long"
        PropertyType.Integer -> if (INT.matches(value)) null else "Enter a whole number"
        PropertyType.Decimal -> if (DECIMAL.matches(value)) null else "Enter a number"
        PropertyType.Bool -> if (value == "true" || value == "false") null else "Must be true or false"
        PropertyType.Drawable -> if (Rules.isResourceName(value)) null else "Not a valid drawable name"
        PropertyType.ClassName -> if (CLASS.matches(value)) null else "Use a full class name like com.example.MyView"
        is PropertyType.Choice -> if (value in type.options) null else "Choose one of ${type.options.joinToString()}"
        is PropertyType.Flags -> {
            val parts = value.split('|')
            when {
                parts.any { it !in type.options } -> "Unknown option in \"$value\""
                parts.toSet().size != parts.size -> "Duplicate option in \"$value\""
                else -> null
            }
        }
    }

    /** `"16dp"` → 16, null for non-dp values. */
    fun dp(value: String?): Int? = value?.takeIf { DP.matches(it) }?.removeSuffix("dp")?.toInt()
    fun sp(value: String?): Float? = value?.takeIf { SP.matches(it) }?.removeSuffix("sp")?.toFloat()
}

/** Which properties a component exposes, given its own type and its parent's type. */
object PropertyCatalog {

    private val GRAVITY = PropertyType.Flags(
        listOf("left", "right", "top", "bottom", "center", "center_horizontal", "center_vertical", "start", "end")
    )
    private fun bool(key: String, label: String, group: PropertyGroup = PropertyGroup.BEHAVIOR) =
        PropertyDef(key, label, PropertyType.Bool, group)
    /** Booleans that are only meaningful when true (RelativeLayout rules). */
    private fun rule(key: String, label: String) =
        PropertyDef(key, label, PropertyType.Bool, PropertyGroup.LAYOUT) { if (it == "true") listOf("android:$key" to "true") else emptyList() }
    private fun constraint(key: String, label: String, attr: String) =
        PropertyDef(key, label, PropertyType.Bool, PropertyGroup.LAYOUT) { if (it == "true") listOf("app:$attr" to "parent") else emptyList() }
    private fun app(key: String, label: String, type: PropertyType, group: PropertyGroup) =
        PropertyDef(key, label, type, group) { listOf("app:$key" to it) }

    val LAYOUT_WIDTH = PropertyDef(ComponentProperty.LAYOUT_WIDTH, "Width", PropertyType.SizeDimension, PropertyGroup.LAYOUT)
    val LAYOUT_HEIGHT = PropertyDef(ComponentProperty.LAYOUT_HEIGHT, "Height", PropertyType.SizeDimension, PropertyGroup.LAYOUT)

    private val COMMON = listOf(
        LAYOUT_WIDTH,
        LAYOUT_HEIGHT,
        PropertyDef(ComponentProperty.LAYOUT_MARGIN, "Margin", PropertyType.Dimension, PropertyGroup.LAYOUT),
        PropertyDef(ComponentProperty.PADDING, "Padding", PropertyType.Dimension, PropertyGroup.LAYOUT),
        PropertyDef(ComponentProperty.BACKGROUND, "Background", PropertyType.Color, PropertyGroup.APPEARANCE),
        PropertyDef(ComponentProperty.VISIBILITY, "Visibility", PropertyType.Choice(listOf("visible", "invisible", "gone")), PropertyGroup.BEHAVIOR),
        bool("enabled", "Enabled"),
    )

    private val IN_LINEAR = listOf(
        PropertyDef("layout_weight", "Weight", PropertyType.Decimal, PropertyGroup.LAYOUT),
        PropertyDef(ComponentProperty.LAYOUT_GRAVITY, "Layout gravity", GRAVITY, PropertyGroup.LAYOUT),
    )
    private val IN_FRAME = listOf(PropertyDef(ComponentProperty.LAYOUT_GRAVITY, "Layout gravity", GRAVITY, PropertyGroup.LAYOUT))
    private val IN_RELATIVE = listOf(
        rule("layout_centerInParent", "Center in parent"),
        rule("layout_centerHorizontal", "Center horizontally"),
        rule("layout_centerVertical", "Center vertically"),
        rule("layout_alignParentTop", "Align parent top"),
        rule("layout_alignParentBottom", "Align parent bottom"),
        rule("layout_alignParentStart", "Align parent start"),
        rule("layout_alignParentEnd", "Align parent end"),
    )
    private val IN_CONSTRAINT = listOf(
        constraint("constrainTop", "Pin top to parent", "layout_constraintTop_toTopOf"),
        constraint("constrainBottom", "Pin bottom to parent", "layout_constraintBottom_toBottomOf"),
        constraint("constrainStart", "Pin start to parent", "layout_constraintStart_toStartOf"),
        constraint("constrainEnd", "Pin end to parent", "layout_constraintEnd_toEndOf"),
    )

    private val TEXT = listOf(
        PropertyDef(ComponentProperty.TEXT, "Text", PropertyType.Text, PropertyGroup.TEXT),
        PropertyDef(ComponentProperty.TEXT_SIZE, "Text size", PropertyType.TextSize, PropertyGroup.TEXT),
        PropertyDef(ComponentProperty.TEXT_COLOR, "Text color", PropertyType.Color, PropertyGroup.TEXT),
        PropertyDef("textStyle", "Text style", PropertyType.Choice(listOf("normal", "bold", "italic", "bold|italic")), PropertyGroup.TEXT),
        PropertyDef(ComponentProperty.GRAVITY, "Gravity", GRAVITY, PropertyGroup.TEXT),
    )
    private val EDIT = listOf(
        PropertyDef(ComponentProperty.HINT, "Hint", PropertyType.Text, PropertyGroup.TEXT),
        PropertyDef("textColorHint", "Hint color", PropertyType.Color, PropertyGroup.TEXT),
        PropertyDef(
            "inputType", "Input type",
            PropertyType.Choice(listOf("text", "textPassword", "textEmailAddress", "number", "numberDecimal", "phone", "textMultiLine")),
            PropertyGroup.BEHAVIOR,
        ),
    )
    private val CHECKABLE = listOf(bool("checked", "Checked"))
    private val IMAGE = listOf(
        PropertyDef("src", "Image", PropertyType.Drawable, PropertyGroup.APPEARANCE) { listOf("android:src" to "@drawable/$it") },
        PropertyDef("scaleType", "Scale type", PropertyType.Choice(listOf("fitCenter", "centerCrop", "center", "centerInside", "fitXY")), PropertyGroup.APPEARANCE),
    )
    private val RANGE = listOf(
        PropertyDef("max", "Max", PropertyType.Integer, PropertyGroup.BEHAVIOR),
        PropertyDef("progress", "Progress", PropertyType.Integer, PropertyGroup.BEHAVIOR),
    )
    private val PROGRESS = listOf(
        PropertyDef("progressStyle", "Style", PropertyType.Choice(listOf("circular", "horizontal")), PropertyGroup.APPEARANCE) {
            if (it == "horizontal") listOf("style" to "?android:attr/progressBarStyleHorizontal") else emptyList()
        },
        bool("indeterminate", "Indeterminate"),
    ) + RANGE
    private val LINEAR = listOf(
        PropertyDef(ComponentProperty.ORIENTATION, "Orientation", PropertyType.Choice(listOf("vertical", "horizontal")), PropertyGroup.LAYOUT),
        PropertyDef(ComponentProperty.GRAVITY, "Gravity", GRAVITY, PropertyGroup.LAYOUT),
    )
    private val CARD = listOf(
        app("cardCornerRadius", "Corner radius", PropertyType.Dimension, PropertyGroup.APPEARANCE),
        app("cardElevation", "Elevation", PropertyType.Dimension, PropertyGroup.APPEARANCE),
        app("cardBackgroundColor", "Card color", PropertyType.Color, PropertyGroup.APPEARANCE),
    )
    private val CUSTOM = listOf(
        PropertyDef(ComponentProperty.CUSTOM_CLASS, "Class", PropertyType.ClassName, PropertyGroup.BEHAVIOR) { emptyList() },
    )

    fun propertiesFor(type: ComponentType, parentType: ComponentType?): List<PropertyDef> {
        val parent = when (parentType) {
            LINEAR_LAYOUT -> IN_LINEAR
            FRAME_LAYOUT, SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW, CARD_VIEW -> IN_FRAME
            RELATIVE_LAYOUT -> IN_RELATIVE
            CONSTRAINT_LAYOUT -> IN_CONSTRAINT
            else -> emptyList()
        }
        val own = when (type) {
            LINEAR_LAYOUT -> LINEAR
            CARD_VIEW -> CARD
            TEXT_VIEW, BUTTON -> TEXT
            EDIT_TEXT -> TEXT + EDIT
            CHECK_BOX, RADIO_BUTTON, SWITCH -> TEXT + CHECKABLE
            IMAGE_VIEW, IMAGE_BUTTON -> IMAGE
            PROGRESS_BAR -> PROGRESS
            SEEK_BAR -> RANGE
            CUSTOM_VIEW -> CUSTOM
            else -> emptyList()
        }
        return (COMMON + parent + own).distinctBy { it.key }
    }

    fun find(type: ComponentType, parentType: ComponentType?, key: String): PropertyDef? =
        propertiesFor(type, parentType).firstOrNull { it.key == key }
}
