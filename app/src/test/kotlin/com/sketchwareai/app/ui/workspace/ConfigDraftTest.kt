package com.sketchwareai.app.ui.workspace

import com.sketchwareai.core.model.ProjectConfig
import com.sketchwareai.core.model.ScreenId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDraftTest {
    private val config = ProjectConfig(appName = "App", packageName = "com.example.app", mainScreenId = ScreenId("scr_1"))

    @Test fun `round trips unchanged config`() {
        val draft = ConfigDraft.from(config)
        assertTrue(draft.isValid)
        assertEquals(config, draft.applyTo(config))
    }

    @Test fun `invalid fields block saving`() {
        val draft = ConfigDraft.from(config)
        assertFalse(draft.copy(versionCode = "").isValid)
        assertFalse(draft.copy(packageName = "bad").isValid)
        assertFalse(draft.copy(minSdk = 30, targetSdk = 29).isValid)
        assertFalse(draft.copy(appName = " ").isValid)
    }

    @Test fun `applies trimmed edits`() {
        val edited = ConfigDraft.from(config).copy(appName = " New ", versionCode = "42", versionName = " 2.0 ").applyTo(config)
        assertEquals("New", edited.appName)
        assertEquals(42, edited.versionCode)
        assertEquals("2.0", edited.versionName)
        assertEquals(config.mainScreenId, edited.mainScreenId)
    }
}
