package com.buildit.core.model

/** Starter templates offered by "Create project". Each produces a valid, buildable project. */
enum class ProjectTemplate(val displayName: String, val description: String) {
    EMPTY("Empty Activity", "One screen with an empty vertical layout"),
    HELLO_WORLD("Hello World", "One screen with a centered greeting"),
    BUTTON_AND_TEXT("Text and Button", "A label, a text field and a button, ready for logic"),
}

data class NewProjectRequest(
    val appName: String,
    val packageName: String,
    val template: ProjectTemplate = ProjectTemplate.EMPTY,
    val minSdk: Int = ProjectConfig.DEFAULT_MIN_SDK,
    val targetSdk: Int = ProjectConfig.DEFAULT_TARGET_SDK,
    val language: SourceLanguage = SourceLanguage.JAVA,
)

class InvalidProjectException(val issues: List<ValidationIssue>) :
    Exception(issues.filter { it.severity == Severity.ERROR }.joinToString("; ") { it.message })

/** Pure operations that create or transform whole projects. Persistence is the store's job. */
class ProjectFactory(
    private val ids: IdGenerator = RandomIdGenerator(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun create(request: NewProjectRequest): ProjectSchema {
        val now = clock()
        val screen = mainScreen(request.template)
        val project = ProjectSchema(
            id = ids.projectId(),
            config = ProjectConfig(
                appName = request.appName.trim(),
                packageName = request.packageName.trim(),
                minSdk = request.minSdk,
                targetSdk = request.targetSdk,
                mainScreenId = screen.id,
                language = request.language,
            ),
            screens = listOf(screen),
            createdAt = now,
            updatedAt = now,
        )
        return requireValid(project)
    }

    /** Copy with a fresh project id and identity. Entity ids are project-scoped and are kept. */
    fun duplicate(source: ProjectSchema, appName: String, packageName: String): ProjectSchema {
        val now = clock()
        return requireValid(
            source.copy(
                id = ids.projectId(),
                config = source.config.copy(appName = appName.trim(), packageName = packageName.trim()),
                createdAt = now,
                updatedAt = now,
                revision = 0,
            )
        )
    }

    fun rename(project: ProjectSchema, appName: String): ProjectSchema =
        requireValid(project.copy(config = project.config.copy(appName = appName.trim())))

    fun updateConfig(project: ProjectSchema, config: ProjectConfig): ProjectSchema =
        requireValid(project.copy(config = config))

    /** Suggests a package for a duplicate, e.g. `com.example.app` → `com.example.app.copy2`. */
    fun suggestDuplicatePackage(packageName: String, taken: Set<String>): String {
        var candidate = "$packageName.copy"
        var n = 2
        while (candidate in taken) candidate = "$packageName.copy${n++}"
        return candidate
    }

    private fun requireValid(project: ProjectSchema): ProjectSchema {
        val issues = ProjectValidator.validate(project)
        if (issues.any { it.severity == Severity.ERROR }) throw InvalidProjectException(issues)
        return project
    }

    private fun mainScreen(template: ProjectTemplate): ScreenSchema {
        val matchParent = mapOf(
            ComponentProperty.LAYOUT_WIDTH to "match_parent",
            ComponentProperty.LAYOUT_HEIGHT to "match_parent",
            ComponentProperty.ORIENTATION to "vertical",
        )
        val children = when (template) {
            ProjectTemplate.EMPTY -> emptyList()
            ProjectTemplate.HELLO_WORLD -> listOf(
                ComponentSchema(
                    ids.componentId(), ComponentType.TEXT_VIEW, "textview1",
                    mapOf(
                        ComponentProperty.LAYOUT_WIDTH to "wrap_content",
                        ComponentProperty.LAYOUT_HEIGHT to "wrap_content",
                        ComponentProperty.TEXT to "Hello, world!",
                        ComponentProperty.TEXT_SIZE to "20sp",
                    ),
                )
            )
            ProjectTemplate.BUTTON_AND_TEXT -> listOf(
                ComponentSchema(
                    ids.componentId(), ComponentType.TEXT_VIEW, "textview1",
                    mapOf(
                        ComponentProperty.LAYOUT_WIDTH to "match_parent",
                        ComponentProperty.LAYOUT_HEIGHT to "wrap_content",
                        ComponentProperty.TEXT to "Type something",
                    ),
                ),
                ComponentSchema(
                    ids.componentId(), ComponentType.EDIT_TEXT, "edittext1",
                    mapOf(
                        ComponentProperty.LAYOUT_WIDTH to "match_parent",
                        ComponentProperty.LAYOUT_HEIGHT to "wrap_content",
                        ComponentProperty.HINT to "Your text",
                    ),
                ),
                ComponentSchema(
                    ids.componentId(), ComponentType.BUTTON, "button1",
                    mapOf(
                        ComponentProperty.LAYOUT_WIDTH to "wrap_content",
                        ComponentProperty.LAYOUT_HEIGHT to "wrap_content",
                        ComponentProperty.TEXT to "Submit",
                    ),
                ),
            )
        }
        val rootProps = if (template == ProjectTemplate.HELLO_WORLD) matchParent + (ComponentProperty.GRAVITY to "center")
        else matchParent + (ComponentProperty.PADDING to "16dp")
        return ScreenSchema(
            id = ids.screenId(),
            name = "MainActivity",
            layoutName = "main",
            root = ComponentSchema(ids.componentId(), ComponentType.LINEAR_LAYOUT, "linear1", rootProps, children),
        )
    }
}
