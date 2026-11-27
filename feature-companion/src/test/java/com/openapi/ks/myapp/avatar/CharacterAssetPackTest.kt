package com.openapi.ks.myapp.avatar

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class CharacterAssetPackTest {
    @get:Rule val temp = TemporaryFolder()
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun manifest(version: Int = 1, path: String = "model/main.bin", sha: String = hash("model")) =
        CharacterAssetManifest(1, "hero", version, CharacterEngineType.LIVE2D, path, mapOf(path to sha))
    private fun source(text: String = "model") = CharacterAssetSource { ByteArrayInputStream(text.toByteArray()) }

    @Test fun `decision rejects unsupported schema unsafe paths and invalid metadata`() {
        assertFalse(CharacterAssetPolicy.isValid(manifest().copy(schemaVersion = 2)))
        assertFalse(CharacterAssetPolicy.isValid(manifest().copy(packId = "../hero")))
        assertFalse(CharacterAssetPolicy.isValid(manifest().copy(version = 0)))
        assertFalse(CharacterAssetPolicy.isValid(manifest(path = "../escape")))
        assertFalse(CharacterAssetPolicy.isValid(manifest(path = "/absolute")))
        assertFalse(CharacterAssetPolicy.isValid(manifest(path = "a\\b")))
        assertFalse(CharacterAssetPolicy.isValid(manifest().copy(entryPoint = "missing")))
        assertFalse(CharacterAssetPolicy.isValid(manifest(sha = "bad")))
    }

    @Test fun `copies verified files into fixed private version directory`() {
        val store = CharacterAssetPackStore(temp.root)
        val result = store.install(manifest(), source())
        assertEquals("model", result.resolve("model/main.bin").readText())
        assertEquals(temp.root.resolve("character-assets/hero/v1").canonicalFile, result.canonicalFile)
    }

    @Test fun `failed update and same version leave installed bytes intact`() {
        val store = CharacterAssetPackStore(temp.root)
        val old = store.install(manifest(), source())
        assertThrows(Exception::class.java) { store.install(manifest(2), source("corrupt")) }
        assertEquals("model", old.resolve("model/main.bin").readText())
        assertFalse(temp.root.resolve("character-assets/hero/v2").exists())
        assertThrows(Exception::class.java) { store.install(manifest(), source("different")) }
        assertEquals("model", old.resolve("model/main.bin").readText())
    }

    @Test fun `rejects symlink escape in target parents`() {
        val outside = temp.newFolder("outside")
        val root = temp.newFolder("private")
        val packs = root.resolve("character-assets").apply { mkdirs() }
        java.nio.file.Files.createSymbolicLink(packs.resolve("hero").toPath(), outside.toPath())
        assertThrows(Exception::class.java) { CharacterAssetPackStore(root).install(manifest(), source()) }
        assertEquals(0, outside.listFiles()!!.size)
    }
}
