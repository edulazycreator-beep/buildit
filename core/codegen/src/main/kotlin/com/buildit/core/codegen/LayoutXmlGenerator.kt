package com.buildit.core.codegen

import com.buildit.core.model.ComponentProperty
import com.buildit.core.model.ComponentSchema
import com.buildit.core.model.ComponentType
import com.buildit.core.model.PropertyCatalog
import com.buildit.core.model.PropertyType
import com.buildit.core.model.ScreenSchema

/**
 * Screen → `res/layout/<layoutName>.xml`.
 *
 * Deterministic: attribute order is `id`, width, height, then catalog order; unknown or
 * invalid properties are skipped (the validator reports them), so output always parses.
 */
object LayoutXmlGenerator {
    const val VERSION = 1
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"
    private const val INDENT = "    "

    fun path(screen: ScreenSchema) = "app/src/main/res/layout/${screen.layoutName}.xml"

    fun generate(screen: ScreenSchema): String {
        val body = StringBuilder()
        var usesApp = false
        fun emit(c: ComponentSchema, parentType: ComponentType?, depth: Int, isRoot: Boolean) {
            val pad = INDENT.repeat(depth)
            val attrs = attributes(c, parentType)
            if (attrs.any { it.first.startsWith("app:") }) usesApp = true
            body.append(pad).append('<').append(tagFor(c))
            if (isRoot) body.append(NS_PLACEHOLDER)
            attrs.forEach { (name, value) -> body.append('\n').append(pad).append(INDENT).append(name).append("=\"").append(xmlEscape(value)).append('"') }
            if (c.children.isEmpty()) {
                body.append(" />\n")
            } else {
                body.append(">\n")
                c.children.forEach { emit(it, c.type, depth + 1, false) }
                body.append(pad).append("</").append(tagFor(c)).append(">\n")
            }
        }
        emit(screen.root, null, 0, true)
        val ns = buildString {
            append("\n").append(INDENT).append("xmlns:android=\"").append(ANDROID_NS).append('"')
            if (usesApp) append("\n").append(INDENT).append("xmlns:app=\"").append(APP_NS).append('"')
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + body.toString().replaceFirst(NS_PLACEHOLDER, ns)
    }

    private const val NS_PLACEHOLDER = "\u0000NS\u0000"

    fun tagFor(c: ComponentSchema): String =
        if (c.type == ComponentType.CUSTOM_VIEW) {
            c.properties[ComponentProperty.CUSTOM_CLASS]
                ?.takeIf { PropertyCatalog.find(c.type, null, ComponentProperty.CUSTOM_CLASS)?.validate(it) == null }
                ?: "View"
        } else c.type.xmlTag

    private fun attributes(c: ComponentSchema, parentType: ComponentType?): List<Pair<String, String>> {
        val out = mutableListOf("android:id" to "@+id/${c.viewId}")
        val defs = PropertyCatalog.propertiesFor(c.type, parentType)
        for (def in defs) {
            val raw = c.properties[def.key]
            val value = when {
                raw != null && def.validate(raw) == null -> raw
                def === PropertyCatalog.LAYOUT_WIDTH || def === PropertyCatalog.LAYOUT_HEIGHT -> "wrap_content"
                else -> continue
            }
            val escaped = if (def.type == PropertyType.Text) androidEscape(value) else value
            out += def.toXml(escaped)
        }
        return out
    }

    /** aapt2 string escaping for literal text in attributes. */
    fun androidEscape(s: String): String {
        val b = StringBuilder()
        s.forEachIndexed { i, ch ->
            when {
                ch == '\\' -> b.append("\\\\")
                ch == '\'' -> b.append("\\'")
                ch == '"' -> b.append("\\\"")
                ch == '\n' -> b.append("\\n")
                ch == '\t' -> b.append("\\t")
                i == 0 && (ch == '@' || ch == '?') -> b.append('\\').append(ch)
                else -> b.append(ch)
            }
        }
        return b.toString()
    }

    fun xmlEscape(s: String): String = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (ch < ' ' && ch != '\n' && ch != '\t') Unit else append(ch)
        }
    }
}
