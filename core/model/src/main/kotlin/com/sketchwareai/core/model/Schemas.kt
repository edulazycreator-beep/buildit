package com.sketchwareai.core.model

/*
 * The central project model: the single source of truth shared by the Visual Designer,
 * the Logic (block) Editor, the code generators and the AI subsystems.
 *
 * All types are immutable. Edits produce new instances; persistence assigns revisions.
 * Any change to the shape of these classes requires bumping [CURRENT_SCHEMA_VERSION]
 * and registering a [Migration] in [Migrations].
 */

const val CURRENT_SCHEMA_VERSION = 1

data class ProjectSchema(
    val id: ProjectId,
    val config: ProjectConfig,
    val screens: List<ScreenSchema>,
    val resources: List<ResourceSchema> = emptyList(),
    val sourceFiles: List<CodeFileSchema> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    /** Incremented by the store on every successful save; used for conflict detection. */
    val revision: Long = 0,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    val mainScreen: ScreenSchema? get() = screens.firstOrNull { it.id == config.mainScreenId }
    fun screen(id: ScreenId): ScreenSchema? = screens.firstOrNull { it.id == id }
}

data class ProjectConfig(
    val appName: String,
    val packageName: String,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val minSdk: Int = DEFAULT_MIN_SDK,
    val targetSdk: Int = DEFAULT_TARGET_SDK,
    val mainScreenId: ScreenId,
    val language: SourceLanguage = SourceLanguage.JAVA,
) {
    companion object {
        const val LOWEST_MIN_SDK = 21
        const val DEFAULT_MIN_SDK = 24
        const val DEFAULT_TARGET_SDK = 35
        const val HIGHEST_TARGET_SDK = 36
    }
}

enum class SourceLanguage { JAVA, KOTLIN }

/** One screen = one Activity + one layout file. */
data class ScreenSchema(
    val id: ScreenId,
    /** Activity class name, e.g. `MainActivity`. */
    val name: String,
    /** Layout resource name without extension, e.g. `main` → `res/layout/main.xml`. */
    val layoutName: String,
    /** Root of the view hierarchy. Must be a container. */
    val root: ComponentSchema,
    val events: List<EventSchema> = emptyList(),
    val variables: List<VariableSchema> = emptyList(),
) {
    fun allComponents(): List<ComponentSchema> = root.flatten()
    fun component(id: ComponentId): ComponentSchema? = allComponents().firstOrNull { it.id == id }
}

data class ComponentSchema(
    val id: ComponentId,
    val type: ComponentType,
    /** The `android:id` name (`@+id/<viewId>`); also the generated Java field name. */
    val viewId: String,
    /** Attribute name → value, e.g. `"text" to "Hello"`. Keys are [ComponentProperty] names. */
    val properties: Map<String, String> = emptyMap(),
    val children: List<ComponentSchema> = emptyList(),
) {
    fun flatten(): List<ComponentSchema> = listOf(this) + children.flatMap { it.flatten() }
}

enum class ComponentType(val xmlTag: String, val isContainer: Boolean) {
    LINEAR_LAYOUT("LinearLayout", true),
    RELATIVE_LAYOUT("RelativeLayout", true),
    FRAME_LAYOUT("FrameLayout", true),
    CONSTRAINT_LAYOUT("androidx.constraintlayout.widget.ConstraintLayout", true),
    SCROLL_VIEW("ScrollView", true),
    HORIZONTAL_SCROLL_VIEW("HorizontalScrollView", true),
    CARD_VIEW("androidx.cardview.widget.CardView", true),
    TEXT_VIEW("TextView", false),
    EDIT_TEXT("EditText", false),
    BUTTON("Button", false),
    IMAGE_VIEW("ImageView", false),
    IMAGE_BUTTON("ImageButton", false),
    CHECK_BOX("CheckBox", false),
    RADIO_BUTTON("RadioButton", false),
    SWITCH("Switch", false),
    PROGRESS_BAR("ProgressBar", false),
    RECYCLER_VIEW("androidx.recyclerview.widget.RecyclerView", false),
    WEB_VIEW("WebView", false),
    VIDEO_VIEW("VideoView", false),
    CUSTOM_VIEW("View", false),
}

/** Property keys understood by the designer and the XML generator (Phase 3 extends this). */
object ComponentProperty {
    const val LAYOUT_WIDTH = "layout_width"
    const val LAYOUT_HEIGHT = "layout_height"
    const val ORIENTATION = "orientation"
    const val TEXT = "text"
    const val HINT = "hint"
    const val TEXT_SIZE = "textSize"
    const val TEXT_COLOR = "textColor"
    const val BACKGROUND = "background"
    const val PADDING = "padding"
    const val LAYOUT_MARGIN = "layout_margin"
    const val GRAVITY = "gravity"
    const val LAYOUT_GRAVITY = "layout_gravity"
    const val VISIBILITY = "visibility"
    const val CUSTOM_CLASS = "customClass"
}

/** An event handler (e.g. `onClick` of `button1`) whose body is a block workspace. */
data class EventSchema(
    val id: EventId,
    /** Event type key, e.g. `onCreate`, `onClick`. Defined by the block system (Phase 4). */
    val type: String,
    /** Null for screen-level events such as `onCreate`. */
    val targetComponentId: ComponentId? = null,
    val body: List<BlockSchema> = emptyList(),
)

/**
 * A block instance, structured like Blockly's JSON serialization so the WebView workspace
 * can round-trip it: value/statement inputs hold child blocks, `next` chains statements.
 */
data class BlockSchema(
    val id: BlockId,
    val type: String,
    val fields: Map<String, String> = emptyMap(),
    val inputs: Map<String, BlockSchema> = emptyMap(),
    val next: BlockSchema? = null,
) {
    fun flatten(): List<BlockSchema> =
        listOf(this) + inputs.values.flatMap { it.flatten() } + (next?.flatten() ?: emptyList())
}

data class VariableSchema(val name: String, val type: VariableType, val initialValue: String? = null)

enum class VariableType { STRING, INTEGER, BOOLEAN, DOUBLE, OBJECT, LIST_STRING, LIST_INTEGER, LIST_MAP, MAP }

data class ResourceSchema(
    val id: ResourceId,
    val type: ResourceType,
    /** Resource name, e.g. `primary_color` → `@color/primary_color`. */
    val name: String,
    /** Literal value for value resources (string, color, dimen). */
    val value: String? = null,
    /** Project-relative file path for file resources (drawable, font, raw). */
    val path: String? = null,
)

enum class ResourceType(val isFile: Boolean) {
    STRING(false), COLOR(false), DIMEN(false), DRAWABLE(true), FONT(true), RAW(true)
}

/** A source file the user (not a generator) owns. Generators must never overwrite these. */
data class CodeFileSchema(
    val id: CodeFileId,
    /** Project-relative path under `src/`, e.g. `src/java/com/example/Util.java`. */
    val path: String,
    val language: CodeLanguage,
)

enum class CodeLanguage { JAVA, KOTLIN, XML, GRADLE }
