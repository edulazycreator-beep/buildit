package com.buildit.core.model

enum class Severity { ERROR, WARNING }

data class ValidationIssue(
    val severity: Severity,
    val code: String,
    val message: String,
    /** Dotted path to the offending entity, e.g. `screens[scr_1].root`. */
    val path: String,
)

/** Field-level rules, also used directly by the UI for inline form validation. */
object Rules {
    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits",
    )
    private val PACKAGE_SEGMENT = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
    private val JAVA_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val CLASS_NAME = Regex("^[A-Z][A-Za-z0-9_]*$")
    private val RESOURCE_NAME = Regex("^[a-z][a-z0-9_]*$")

    const val MAX_APP_NAME_LENGTH = 50
    const val MAX_PACKAGE_NAME_LENGTH = 150

    fun appNameError(name: String): String? = when {
        name.isBlank() -> "App name is required"
        name.length > MAX_APP_NAME_LENGTH -> "App name must be at most $MAX_APP_NAME_LENGTH characters"
        name.any { it == '\n' || it == '\r' } -> "App name must be a single line"
        else -> null
    }

    fun packageNameError(pkg: String): String? {
        if (pkg.isBlank()) return "Package name is required"
        if (pkg.length > MAX_PACKAGE_NAME_LENGTH) return "Package name is too long"
        val segments = pkg.split('.')
        if (segments.size < 2) return "Package name needs at least two segments, e.g. com.example.app"
        segments.forEach { s ->
            if (!PACKAGE_SEGMENT.matches(s)) return "\"$s\" is not a valid package segment"
            if (s in JAVA_KEYWORDS) return "\"$s\" is a reserved Java keyword"
        }
        return null
    }

    fun sdkError(minSdk: Int, targetSdk: Int): String? = when {
        minSdk < ProjectConfig.LOWEST_MIN_SDK -> "Minimum SDK must be at least ${ProjectConfig.LOWEST_MIN_SDK}"
        targetSdk > ProjectConfig.HIGHEST_TARGET_SDK -> "Target SDK must be at most ${ProjectConfig.HIGHEST_TARGET_SDK}"
        targetSdk < minSdk -> "Target SDK cannot be lower than minimum SDK"
        else -> null
    }

    fun versionError(versionCode: Int, versionName: String): String? = when {
        versionCode !in 1..2_100_000_000 -> "Version code must be between 1 and 2100000000"
        versionName.isBlank() -> "Version name is required"
        else -> null
    }

    /**
     * Suggests a package from an app name, Sketchware-style:
     * `("com.my", "My Cool App!")` → `com.my.mycoolapp`. Always returns a valid package
     * when [prefix] is valid.
     */
    fun derivePackageName(prefix: String, appName: String): String {
        var segment = appName.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.dropWhile { it.isDigit() }
        if (segment.isEmpty()) segment = "newproject"
        if (segment in JAVA_KEYWORDS) segment = "app$segment"
        return "$prefix.$segment"
    }

    fun isJavaIdentifier(s: String) = JAVA_IDENTIFIER.matches(s) && s !in JAVA_KEYWORDS
    fun isClassName(s: String) = CLASS_NAME.matches(s) && s !in JAVA_KEYWORDS
    fun isResourceName(s: String) = RESOURCE_NAME.matches(s) && s !in JAVA_KEYWORDS
}

/** Whole-project structural validation. A project with ERROR issues must not be built. */
object ProjectValidator {

    fun validate(project: ProjectSchema): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        fun error(code: String, msg: String, path: String) = issues.add(ValidationIssue(Severity.ERROR, code, msg, path))
        fun warn(code: String, msg: String, path: String) = issues.add(ValidationIssue(Severity.WARNING, code, msg, path))

        val c = project.config
        Rules.appNameError(c.appName)?.let { error("config.appName", it, "config.appName") }
        Rules.packageNameError(c.packageName)?.let { error("config.packageName", it, "config.packageName") }
        Rules.sdkError(c.minSdk, c.targetSdk)?.let { error("config.sdk", it, "config.sdk") }
        Rules.versionError(c.versionCode, c.versionName)?.let { error("config.version", it, "config.version") }

        if (project.screens.isEmpty()) error("screens.empty", "Project must have at least one screen", "screens")
        if (project.mainScreen == null) error("config.mainScreen", "Main screen ${c.mainScreenId} does not exist", "config.mainScreenId")

        // Entity ids: well-formed and globally unique.
        val seen = HashSet<String>()
        fun checkId(id: String, path: String) {
            if (!ENTITY_ID_REGEX.matches(id)) error("id.format", "Malformed id \"$id\"", path)
            if (!seen.add(id)) error("id.duplicate", "Duplicate id \"$id\"", path)
        }
        checkId(project.id.value, "id")

        duplicates(project.screens.map { it.name }).forEach { error("screen.name.duplicate", "Duplicate screen name \"$it\"", "screens") }
        duplicates(project.screens.map { it.layoutName }).forEach { error("screen.layout.duplicate", "Duplicate layout name \"$it\"", "screens") }

        for (screen in project.screens) {
            val sp = "screens[${screen.id}]"
            checkId(screen.id.value, sp)
            if (!Rules.isClassName(screen.name)) error("screen.name", "\"${screen.name}\" is not a valid Activity class name", "$sp.name")
            if (!Rules.isResourceName(screen.layoutName)) error("screen.layout", "\"${screen.layoutName}\" is not a valid layout name", "$sp.layoutName")
            if (!screen.root.type.isContainer) error("screen.root", "Screen root must be a layout container", "$sp.root")

            val components = screen.allComponents()
            for (comp in components) {
                val cp = "$sp.components[${comp.id}]"
                checkId(comp.id.value, cp)
                if (!Rules.isJavaIdentifier(comp.viewId)) error("component.viewId", "\"${comp.viewId}\" is not a valid view id", "$cp.viewId")
                if (!comp.type.isContainer && comp.children.isNotEmpty()) error("component.children", "${comp.type.xmlTag} cannot contain children", cp)
                if (comp.type == ComponentType.SCROLL_VIEW || comp.type == ComponentType.HORIZONTAL_SCROLL_VIEW) {
                    if (comp.children.size > 1) error("component.scrollChildren", "A scroll view can host only one direct child", cp)
                }
            }
            fun checkProperties(comp: ComponentSchema, parentType: ComponentType?) {
                val defs = PropertyCatalog.propertiesFor(comp.type, parentType).associateBy { it.key }
                for ((key, value) in comp.properties) {
                    val def = defs[key]
                    val cp = "$sp.components[${comp.id}].properties.$key"
                    if (def == null) warn("property.unknown", "${comp.viewId}: property \"$key\" is ignored here", cp)
                    else def.validate(value)?.let { error("property.value", "${comp.viewId}: ${def.label}: $it", cp) }
                }
                comp.children.forEach { checkProperties(it, comp.type) }
            }
            checkProperties(screen.root, null)
            duplicates(components.map { it.viewId }).forEach { error("component.viewId.duplicate", "Duplicate view id \"$it\"", sp) }

            val componentIds = components.map { it.id }.toSet()
            for (event in screen.events) {
                val ep = "$sp.events[${event.id}]"
                checkId(event.id.value, ep)
                if (event.type.isBlank()) error("event.type", "Event type is required", ep)
                if (event.targetComponentId != null && event.targetComponentId !in componentIds) {
                    error("event.target", "Event targets missing component ${event.targetComponentId}", ep)
                }
                event.body.flatMap { it.flatten() }.forEach { checkId(it.id.value, "$ep.blocks[${it.id}]") }
            }
            duplicates(screen.events.map { "${it.type}:${it.targetComponentId}" }).forEach {
                warn("event.duplicate", "Event $it is defined more than once", sp)
            }

            duplicates(screen.variables.map { it.name }).forEach { error("variable.duplicate", "Duplicate variable \"$it\"", sp) }
            screen.variables.forEach {
                if (!Rules.isJavaIdentifier(it.name)) error("variable.name", "\"${it.name}\" is not a valid variable name", "$sp.variables")
            }
        }

        for (res in project.resources) {
            val rp = "resources[${res.id}]"
            checkId(res.id.value, rp)
            if (!Rules.isResourceName(res.name)) error("resource.name", "\"${res.name}\" is not a valid resource name", rp)
            if (res.type.isFile && res.path.isNullOrBlank()) error("resource.path", "File resource needs a path", rp)
            if (!res.type.isFile && res.value == null) error("resource.value", "Value resource needs a value", rp)
            res.path?.let { if (!isSafeRelativePath(it)) error("resource.path.unsafe", "Unsafe resource path \"$it\"", rp) }
        }
        duplicates(project.resources.map { "${it.type}/${it.name}" }).forEach { error("resource.duplicate", "Duplicate resource $it", "resources") }

        for (file in project.sourceFiles) {
            val fp = "sourceFiles[${file.id}]"
            checkId(file.id.value, fp)
            if (!isSafeRelativePath(file.path) || !file.path.startsWith("src/")) error("source.path", "Source path must be a safe path under src/", fp)
        }
        duplicates(project.sourceFiles.map { it.path }).forEach { error("source.duplicate", "Duplicate source file $it", "sourceFiles") }

        return issues
    }

    fun hasErrors(project: ProjectSchema) = validate(project).any { it.severity == Severity.ERROR }

    /** Relative, normalized, no `..`, no absolute roots, no backslashes. Shared with archive import. */
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains('\u0000')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun <T> duplicates(items: List<T>): Set<T> = items.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
