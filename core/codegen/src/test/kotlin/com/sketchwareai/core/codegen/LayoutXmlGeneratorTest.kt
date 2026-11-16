package com.sketchwareai.core.codegen

import com.sketchwareai.core.model.ComponentFactory
import com.sketchwareai.core.model.NewProjectRequest
import com.sketchwareai.core.model.PaletteItem
import com.sketchwareai.core.model.ProjectEdit
import com.sketchwareai.core.model.ProjectEditor
import com.sketchwareai.core.model.ProjectFactory
import com.sketchwareai.core.model.ProjectSchema
import com.sketchwareai.core.model.ProjectTemplate
import com.sketchwareai.core.model.SequentialIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class LayoutXmlGeneratorTest {
    private val ids = SequentialIdGenerator()
    private val factory = ProjectFactory(ids) { 0L }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))

    @Test fun `golden hello world`() {
        val p = factory.create(NewProjectRequest("App", "com.example.app", ProjectTemplate.HELLO_WORLD))
        val expected = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/linear1"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:gravity="center">
                <TextView
                    android:id="@+id/textview1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello, world!"
                    android:textSize="20sp" />
            </LinearLayout>
        """.trimIndent() + "\n"
        assertEquals(expected, LayoutXmlGenerator.generate(p.screens[0]))
    }

    @Test fun `every palette item in every container produces well formed xml`() {
        var p: ProjectSchema = factory.create(NewProjectRequest("App", "com.example.app"))
        val screen = p.screens[0]
        for (container in PaletteItem.entries.filter { it.type.isContainer && !it.name.startsWith("SCROLL") }) {
            val c = ComponentFactory.create(container, ids, p.screens[0].allComponents().map { it.viewId }.toSet())
            p = ProjectEditor.apply(p, ProjectEdit.AddComponent(screen.id, screen.root.id, 99, c))
            for (item in PaletteItem.entries) {
                val w = ComponentFactory.create(item, ids, p.screens[0].allComponents().map { it.viewId }.toSet())
                p = ProjectEditor.apply(p, ProjectEdit.AddComponent(screen.id, c.id, 99, w))
            }
        }
        val xml = LayoutXmlGenerator.generate(p.screens[0])
        val doc = parse(xml)
        assertEquals(p.screens[0].allComponents().size, doc.getElementsByTagName("*").length)
        assertTrue(xml.contains("xmlns:app=\"http://schemas.android.com/apk/res-auto\""))
        assertTrue(xml.contains("app:cardCornerRadius=\"12dp\""))
        assertTrue(xml.contains("<androidx.recyclerview.widget.RecyclerView"))
        assertTrue(xml.contains("<android.view.View"))
        assertEquals(xml, LayoutXmlGenerator.generate(p.screens[0])) // deterministic
    }

    @Test fun `special properties map to the right attributes`() {
        var p = factory.create(NewProjectRequest("App", "com.example.app"))
        val s = p.screens[0]
        fun addTo(parent: com.sketchwareai.core.model.ComponentId, item: PaletteItem): com.sketchwareai.core.model.ComponentSchema {
            val c = ComponentFactory.create(item, ids, p.screens[0].allComponents().map { it.viewId }.toSet())
            p = ProjectEditor.apply(p, ProjectEdit.AddComponent(s.id, parent, 99, c)); return c
        }
        fun set(c: com.sketchwareai.core.model.ComponentSchema, k: String, v: String) {
            p = ProjectEditor.apply(p, ProjectEdit.SetProperty(s.id, c.id, k, v))
        }
        val rel = addTo(s.root.id, PaletteItem.RELATIVE)
        val btn = addTo(rel.id, PaletteItem.BUTTON)
        set(btn, "layout_centerInParent", "true"); set(btn, "layout_alignParentTop", "false")
        val con = addTo(s.root.id, PaletteItem.CONSTRAINT)
        val txt = addTo(con.id, PaletteItem.TEXT_VIEW)
        set(txt, "constrainTop", "true")
        val bar = addTo(s.root.id, PaletteItem.PROGRESS_BAR)
        set(bar, "progressStyle", "horizontal")
        val img = addTo(s.root.id, PaletteItem.IMAGE_VIEW)
        set(img, "src", "logo")
        val xml = LayoutXmlGenerator.generate(p.screens[0])
        assertTrue(xml.contains("android:layout_centerInParent=\"true\""))
        assertTrue(!xml.contains("layout_alignParentTop"))
        assertTrue(xml.contains("app:layout_constraintTop_toTopOf=\"parent\""))
        assertTrue(xml.contains("style=\"?android:attr/progressBarStyleHorizontal\""))
        assertTrue(xml.contains("android:src=\"@drawable/logo\""))
        assertTrue(!xml.contains("progressStyle"))
    }

    @Test fun `text is escaped for aapt and xml`() {
        var p = factory.create(NewProjectRequest("App", "com.example.app", ProjectTemplate.HELLO_WORLD))
        val s = p.screens[0]
        val tv = s.root.children[0]
        p = ProjectEditor.apply(p, ProjectEdit.SetProperty(s.id, tv.id, "text", "@Tom's \"<b>&</b>\"\nline2 \\ end"))
        val xml = LayoutXmlGenerator.generate(p.screens[0])
        assertTrue(xml, xml.contains("android:text=\"\\@Tom\\'s \\&quot;&lt;b&gt;&amp;&lt;/b&gt;\\&quot;\\nline2 \\\\ end\""))
        val text = parse(xml).getElementsByTagName("TextView").item(0).attributes.getNamedItem("android:text").nodeValue
        assertEquals("\\@Tom\\'s \\\"<b>&</b>\\\"\\nline2 \\\\ end", text)
    }
}
