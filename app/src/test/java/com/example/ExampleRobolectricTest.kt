package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.exif.ExifEntry
import com.example.exif.IfdGroup
import com.example.exif.JpegExifEngine
import com.example.ui.EditorTab
import com.example.ui.EditorViewModel
import com.example.ui.ShamailEditorApp
import com.example.ui.theme.ShamailEditorTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleJpegBytes(): ByteArray {
        val parts = ArrayList<ByteArray>()
        parts.add(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val app1 = JpegExifEngine.buildExifApp1(
            mapOf(
                IfdGroup.IFD0 to listOf(
                    JpegExifEngine.encodeAsciiTag(0x010F, "Fujifilm"),
                    JpegExifEngine.encodeAsciiTag(0x0110, "X-T5"),
                    JpegExifEngine.encodeAsciiTag(0x013B, "Test Photographer")
                ),
                IfdGroup.Exif to listOf(
                    ExifEntry(0x9003, 2, 20, "2025:06:15 09:00:00\u0000".toByteArray(Charsets.UTF_8))
                ),
                IfdGroup.GPS to emptyList(),
                IfdGroup.Interop to emptyList()
            ),
            littleEndian = false
        )!!
        parts.add(app1)
        val sof0 = byteArrayOf(0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08, 0x00, 0x64, 0x00, 0x64, 0x01, 0x01, 0x11, 0x00)
        parts.add(sof0)
        val sos = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00)
        parts.add(sos)
        parts.add(byteArrayOf(0x00, 0xFF.toByte(), 0xD9.toByte()))
        val out = ByteArray(parts.sumOf { it.size })
        var pos = 0
        for (p in parts) { p.copyInto(out, pos); pos += p.size }
        return out
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Shamail Editor", appName)
    }

    @Test
    fun `load jpeg, edit metadata, verify output, and clear session`() {
        val viewModel = EditorViewModel()
        runBlocking {
            viewModel.loadFromBytes(sampleJpegBytes(), "fuji_test.jpg")
        }
        val st = viewModel.uiState.value
        assertEquals(null, st.uploadError)
        assertEquals(true, st.hasPhoto)

        composeTestRule.setContent {
            ShamailEditorTheme(dynamicColor = false) {
                ShamailEditorApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify Overview shows loaded photo details & At a glance
        composeTestRule.onNodeWithText("fuji_test.jpg").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("at_a_glance_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Fujifilm X-T5").performScrollTo().assertIsDisplayed()

        // Switch to Edit tab and clear Artist, then Apply changes
        viewModel.selectTab(EditorTab.EDIT)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_artist").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.changedFieldCount)

        composeTestRule.onNodeWithTag("apply_changes_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            viewModel.uiState.value.buildResult != null
        }
        composeTestRule.waitForIdle()

        // Verified ready banner & Save photo button should be visible
        composeTestRule.onNodeWithTag("verified_ready_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_photo_button").performScrollTo().assertIsDisplayed()

        // Clear session wipes in-memory state back to empty state
        composeTestRule.onNodeWithTag("clear_session_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.hasPhoto)
        composeTestRule.onNodeWithTag("choose_photo_button").performScrollTo().assertIsDisplayed()
    }
}
