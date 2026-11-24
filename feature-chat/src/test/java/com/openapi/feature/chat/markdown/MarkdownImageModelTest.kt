package com.openapi.feature.chat.markdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownImageModelTest {

    @Test
    fun existingLocalPathStaysAFileModel() {
        val source = "/tmp/chat-image.jpg"
        assertEquals(File(source), MarkdownImageModel.resolve(File("/cache"), source))
    }

    @Test
    fun httpUrlUsesExistingDownloadedAttachmentFile() {
        val source = "https://example.test/image.jpg?sig=secret"
        val cacheDir = File("/cache")
        val expected = MarkdownImageModel.cachedAttachmentFile(cacheDir, source)

        assertEquals(expected, MarkdownImageModel.resolve(cacheDir, source) { it == expected })
    }

    @Test
    fun httpUrlFallsBackToRemoteWhenNoDownloadedFileExists() {
        val source = "https://example.test/image.jpg"
        assertEquals(source, MarkdownImageModel.resolve(File("/cache"), source) { false })
    }

    @Test
    fun contentAndFileUrisRemainUris() {
        val cacheDir = File("/cache")
        assertEquals("content://media/image/7", MarkdownImageModel.resolve(cacheDir, "content://media/image/7"))
        assertEquals("file:///tmp/image.jpg", MarkdownImageModel.resolve(cacheDir, "file:///tmp/image.jpg"))
    }
}
