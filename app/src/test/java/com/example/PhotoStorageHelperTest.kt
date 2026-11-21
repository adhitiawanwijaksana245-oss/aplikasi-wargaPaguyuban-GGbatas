package com.example

import com.example.util.PhotoStorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStorageHelperTest {

    @Test
    fun formatAsImageFormula_wrapsPublicHttpsUrl() {
        val url = "https://drive.google.com/uc?id=abc123"
        assertEquals("=IMAGE(\"https://drive.google.com/uc?id=abc123\")", PhotoStorageHelper.formatAsImageFormula(url))
    }

    @Test
    fun formatAsImageFormula_keepsExistingFormulaIdempotent() {
        val formula = "=IMAGE(\"https://iili.io/sample.jpg\")"
        assertEquals("=IMAGE(\"https://iili.io/sample.jpg\")", PhotoStorageHelper.formatAsImageFormula(formula))
    }

    @Test
    fun formatAsImageFormula_blocksLocalPaths() {
        assertEquals("", PhotoStorageHelper.formatAsImageFormula("file:///data/user/0/com.aistudio.aplikasiwarga.rtmlya/files/warga_photos/img.jpg"))
        assertEquals("", PhotoStorageHelper.formatAsImageFormula("content://media/picker/0/media/1000425431"))
    }

    @Test
    fun extractUrlFromFormula_extractsCleanUrl() {
        val formula = "=IMAGE(\"https://drive.google.com/uc?id=xyz789\")"
        assertEquals("https://drive.google.com/uc?id=xyz789", PhotoStorageHelper.extractUrlFromFormula(formula))
        assertEquals("", PhotoStorageHelper.extractUrlFromFormula("{}"))
        assertEquals("", PhotoStorageHelper.extractUrlFromFormula("CellImage"))
        assertEquals("", PhotoStorageHelper.extractUrlFromFormula("{\"valueType\":\"IMAGE\"}"))
    }

    @Test
    fun getDisplayModel_handlesFormulaAndInvalidLocalFile() {
        val formula = "=IMAGE(\"https://drive.google.com/uc?id=xyz789\")"
        assertEquals("https://drive.google.com/uc?id=xyz789", PhotoStorageHelper.getDisplayModel(formula))
        assertEquals("", PhotoStorageHelper.getDisplayModel("file:///data/user/0/nonexistent/photo.jpg"))
        assertEquals("", PhotoStorageHelper.getDisplayModel("{\"valueType\":\"IMAGE\"}"))
    }

    @Test
    fun isLocalUri_and_isPublicHttpUrl_detectCorrectly() {
        assertTrue(PhotoStorageHelper.isLocalUri("file:///data/user/0/test.jpg"))
        assertTrue(PhotoStorageHelper.isLocalUri("content://media/123"))
        assertFalse(PhotoStorageHelper.isLocalUri("https://drive.google.com/uc?id=123"))
        assertTrue(PhotoStorageHelper.isPublicHttpUrl("=IMAGE(\"https://drive.google.com/uc?id=123\")"))
        assertFalse(PhotoStorageHelper.isPublicHttpUrl("{\"valueType\":\"IMAGE\"}"))
    }

    @Test
    fun normalizeGoogleDriveUrl_convertsShareLinksToDirectViewUrl() {
        assertEquals(
            "https://drive.google.com/uc?export=view&id=1AbCDefGhiJK_123",
            PhotoStorageHelper.normalizeGoogleDriveUrl("https://drive.google.com/file/d/1AbCDefGhiJK_123/view?usp=sharing")
        )
        assertEquals(
            "https://drive.google.com/uc?export=view&id=9ZyXwvUtsRq_456",
            PhotoStorageHelper.normalizeGoogleDriveUrl("https://drive.google.com/open?id=9ZyXwvUtsRq_456")
        )
        assertEquals(
            "https://drive.google.com/uc?export=view&id=1AbCDefGhiJK_123",
            PhotoStorageHelper.getDisplayModel("=IMAGE(\"https://drive.google.com/file/d/1AbCDefGhiJK_123/view?usp=drive_link\")")
        )
    }
}
