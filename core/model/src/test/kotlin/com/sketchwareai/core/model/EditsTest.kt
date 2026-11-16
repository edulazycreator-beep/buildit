package com.sketchwareai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EditsTest {
    private val ids = SequentialIdGenerator()
    private val project = ProjectFactory(ids) { 0L }.create(NewProjectRequest("App", "com.example.app", ProjectTemplate.EMPTY))
    private val screen get() = project.screens[0]
    private val root get() = screen.root

    private fun add(p: ProjectSchema, item: PaletteItem, parent: ComponentId = p.screens[0].root.id, index: Int = Int.MAX_VALUE): Pair<ProjectSchema, ComponentSchema> {
        val c = ComponentFactory.create(item, ids, p.screens[0].allComponents().map { it.viewId }.toSet())
        return ProjectEditor.apply(p, ProjectEdit.AddComponent(p.screens[0].id, parent, index, c)) to c
    }

    private fun expectEditError(block: () -> Unit) {
        try { block(); fail("expected EditException") } catch (_: EditException) {}
    }

    @Test fun `palette defaults are valid for every item in every container`() {
        for (container in PaletteItem.entries.filter { it.type.isContainer }) {
            for (item in PaletteItem.entries) {
                var (p, parent) = add(project, container)
                if (container.type == ComponentType.SCROLL_VIEW || container.type == ComponentType.HORIZONTAL_SCROLL_VIEW) {
                    p = add(p, item, parent.id).first
                } else {
                    p = add(p, item, parent.id).first
                }
                assertTrue("$container/$item", ProjectValidator.validate(p).none { it.severity == Severity.ERROR })
            }
        }
    }

    @Test fun `add assigns sequential view ids and respects index`() {
        var (p, a) = add(project, PaletteItem.TEXT_VIEW)
        val (p2, b) = add(p, PaletteItem.TEXT_VIEW, index = 0)
        assertEquals("textview1", a.viewId)
        assertEquals("textview2", b.viewId)
        assertEquals(listOf(b.id, a.id), p2.screens[0].root.children.map { it.id })
    }

    @Test fun `cannot add into non container`() {
        val (p, button) = add(project, PaletteItem.BUTTON)
        expectEditError { add(p, PaletteItem.TEXT_VIEW, button.id) }
    }

    @Test fun `scroll view accepts only one child`() {
        val (p, scroll) = add(project, PaletteItem.SCROLL_V)
        val (p2, _) = add(p, PaletteItem.LINEAR_V, scroll.id)
        expectEditError { add(p2, PaletteItem.TEXT_VIEW, scroll.id) }
    }

    @Test fun `remove deletes subtree and its events`() {
        val (p1, layout) = add(project, PaletteItem.LINEAR_V)
        val (p2, button) = add(p1, PaletteItem.BUTTON, layout.id)
        val withEvent = p2.copy(screens = listOf(p2.screens[0].copy(events = listOf(EventSchema(EventId("evt_1"), "onClick", button.id)))))
        val p3 = ProjectEditor.apply(withEvent, ProjectEdit.RemoveComponent(screen.id, layout.id))
        assertTrue(p3.screens[0].root.children.isEmpty())
        assertTrue(p3.screens[0].events.isEmpty())
        expectEditError { ProjectEditor.apply(p3, ProjectEdit.RemoveComponent(screen.id, root.id)) }
    }

    @Test fun `move reorders within parent using visible index`() {
        var p = project
        val items = (1..3).map { add(p, PaletteItem.TEXT_VIEW).also { p = it.first }.second }
        // Drag the first item to the slot after the third (visible index 3).
        val moved = ProjectEditor.apply(p, ProjectEdit.MoveComponent(screen.id, items[0].id, root.id, 3))
        assertEquals(listOf(items[1].id, items[2].id, items[0].id), moved.screens[0].root.children.map { it.id })
        val back = ProjectEditor.apply(moved, ProjectEdit.MoveComponent(screen.id, items[0].id, root.id, 0))
        assertEquals(items.map { it.id }, back.screens[0].root.children.map { it.id })
    }

    @Test fun `move into another container prunes parent specific properties`() {
        val (p1, frame) = add(project, PaletteItem.FRAME)
        val (p2, text) = add(p1, PaletteItem.TEXT_VIEW)
        val weighted = ProjectEditor.apply(p2, ProjectEdit.SetProperty(screen.id, text.id, "layout_weight", "1"))
        val moved = ProjectEditor.apply(weighted, ProjectEdit.MoveComponent(screen.id, text.id, frame.id, 0))
        val after = moved.screens[0].component(text.id)!!
        assertNull(after.properties["layout_weight"])
        assertEquals("TextView", after.properties["text"])
    }

    @Test fun `cannot move layout into its own descendant`() {
        val (p1, outer) = add(project, PaletteItem.LINEAR_V)
        val (p2, inner) = add(p1, PaletteItem.LINEAR_H, outer.id)
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.MoveComponent(screen.id, outer.id, inner.id, 0)) }
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.MoveComponent(screen.id, outer.id, outer.id, 0)) }
    }

    @Test fun `set property validates against the catalog`() {
        val (p, text) = add(project, PaletteItem.TEXT_VIEW)
        fun set(key: String, value: String?) = ProjectEditor.apply(p, ProjectEdit.SetProperty(screen.id, text.id, key, value))
        assertEquals("Hi", set("text", "Hi").screens[0].component(text.id)!!.properties["text"])
        assertFalse(set("textColor", null).screens[0].component(text.id)!!.properties.containsKey("textColor"))
        expectEditError { set("textSize", "big") }
        expectEditError { set("textColor", "red") }
        expectEditError { set("orientation", "vertical") } // not a TextView property
        expectEditError { set("layout_width", null) }
        assertNotNull(set("gravity", "center_vertical|end"))
        expectEditError { set("gravity", "center|center") }
    }

    @Test fun `view ids must be valid and unique`() {
        val (p1, a) = add(project, PaletteItem.BUTTON)
        val (p2, _) = add(p1, PaletteItem.BUTTON)
        assertEquals("ok", ProjectEditor.apply(p2, ProjectEdit.SetViewId(screen.id, a.id, "ok")).screens[0].component(a.id)!!.viewId)
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.SetViewId(screen.id, a.id, "button2")) }
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.SetViewId(screen.id, a.id, "1abc")) }
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.SetViewId(screen.id, a.id, "class")) }
    }

    @Test fun `screens can be added renamed and removed but not the main one`() {
        assertEquals("UserProfileActivity", ScreenFactory.activityNameFor("user_profile"))
        assertNotNull(ScreenFactory.layoutNameError("main", project.screens))
        assertNotNull(ScreenFactory.layoutNameError("Bad", project.screens))
        assertNull(ScreenFactory.layoutNameError("second", project.screens))
        val second = ScreenFactory.create("second", ids)
        val p1 = ProjectEditor.apply(project, ProjectEdit.AddScreen(second))
        assertEquals(2, p1.screens.size)
        val p2 = ProjectEditor.apply(p1, ProjectEdit.RenameScreen(second.id, "DetailActivity", "detail"))
        assertEquals("detail", p2.screen(second.id)!!.layoutName)
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.AddScreen(ScreenFactory.create("detail", ids))) }
        expectEditError { ProjectEditor.apply(p2, ProjectEdit.RemoveScreen(project.config.mainScreenId)) }
        val p3 = ProjectEditor.apply(p2, ProjectEdit.SetMainScreen(second.id), ProjectEdit.RemoveScreen(project.config.mainScreenId))
        assertEquals(listOf(second.id), p3.screens.map { it.id })
    }

    @Test fun `failed batch leaves project unchanged`() {
        val (p, text) = add(project, PaletteItem.TEXT_VIEW)
        try {
            ProjectEditor.apply(p, ProjectEdit.SetProperty(screen.id, text.id, "text", "changed"), ProjectEdit.RemoveComponent(screen.id, root.id))
            fail()
        } catch (_: EditException) {}
        assertEquals("TextView", p.screens[0].component(text.id)!!.properties["text"])
    }

    @Test fun `duplicate gives fresh ids recursively`() {
        val (p1, layout) = add(project, PaletteItem.LINEAR_V)
        val (p2, _) = add(p1, PaletteItem.BUTTON, layout.id)
        val source = p2.screens[0].component(layout.id)!!
        val taken = p2.screens[0].allComponents().map { it.viewId }.toSet()
        val copy = ComponentFactory.duplicate(source, ids, taken)
        val p3 = ProjectEditor.apply(p2, ProjectEdit.AddComponent(screen.id, root.id, 9, copy))
        assertEquals(listOf("linear3", "button2"), copy.flatten().map { it.viewId })
        assertTrue(ProjectValidator.validate(p3).isEmpty())
    }

    @Test fun `history undo redo`() {
        val h = EditHistory(limit = 2)
        val (p1, _) = add(project, PaletteItem.BUTTON)
        val (p2, _) = add(p1, PaletteItem.BUTTON)
        val (p3, _) = add(p2, PaletteItem.BUTTON)
        h.record("a", project); h.record("b", p1); h.record("c", p2) // limit drops "a"
        assertEquals("c", h.undoLabel)
        assertEquals(p2, h.undo(p3)!!.project)
        assertEquals(p1, h.undo(p2)!!.project)
        assertNull(h.undo(p1))
        assertEquals(p2, h.redo(p1)!!.project)
        h.record("d", p2)
        assertFalse(h.canRedo)
    }

    @Test fun `property catalog depends on parent`() {
        val keys = { parent: ComponentType? -> PropertyCatalog.propertiesFor(ComponentType.BUTTON, parent).map { it.key } }
        assertTrue("layout_weight" in keys(ComponentType.LINEAR_LAYOUT))
        assertFalse("layout_weight" in keys(ComponentType.FRAME_LAYOUT))
        assertTrue("layout_centerInParent" in keys(ComponentType.RELATIVE_LAYOUT))
        assertTrue("constrainTop" in keys(ComponentType.CONSTRAINT_LAYOUT))
        assertTrue("text" in keys(null))
        assertEquals(keys(null).size, keys(null).toSet().size)
    }

    @Test fun `validator flags bad property values`() {
        val bad = project.copy(screens = listOf(screen.copy(root = root.copy(properties = root.properties + ("padding" to "lots")))))
        assertTrue(ProjectValidator.validate(bad).any { it.code == "property.value" })
        val unknown = project.copy(screens = listOf(screen.copy(root = root.copy(properties = root.properties + ("bogus" to "1")))))
        assertTrue(ProjectValidator.validate(unknown).single().severity == Severity.WARNING)
    }
}
