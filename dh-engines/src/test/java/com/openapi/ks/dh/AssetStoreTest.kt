package com.openapi.ks.dh

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Plain-JVM tests: the cache root is a [TemporaryFolder] and HTTP is served by an
 * interceptor on a real [OkHttpClient], so no Android runtime is needed.
 */
class AssetStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val requestedUrls = mutableListOf<String>()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, body) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun fakeClient(code: Int = 200, body: ByteArray = ByteArray(0)): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls += chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "ERR")
                    .body(body.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

    private val zipSpec = AssetSpec(
        id = "fixture",
        url = "https://example.test/fixture.zip",
        relativePath = "model/fixture",
    )

    private fun store(
        client: OkHttpClient,
        specs: List<AssetSpec> = listOf(zipSpec),
        baseUrlOverride: String? = null,
    ) = AssetStore(
        rootDir = tmp.root,
        client = client,
        baseUrlOverride = baseUrlOverride,
        manifest = { specs },
    )

    @Test
    fun isReady_false_on_empty_cache() {
        assertFalse(store(fakeClient()).isReady(EngineAssets.DUIX))
    }

    @Test
    fun ensure_downloads_and_extracts_zip_into_relative_path() {
        val store = store(fakeClient(body = zipOf("a.txt" to "hello", "sub/b.txt" to "world")))
        val progress = mutableListOf<Float>()

        val result = runBlocking { store.ensure(EngineAssets.DUIX) { progress += it } }

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val root = store.cacheRoot(EngineAssets.DUIX)
        assertEquals("hello", File(root, "model/fixture/a.txt").readText())
        assertEquals("world", File(root, "model/fixture/sub/b.txt").readText())
        // Completion marker touched next to the extracted pack: model/tmp/fixture.
        assertTrue(File(root, "model/tmp/fixture").isFile)
        assertTrue(store.isReady(EngineAssets.DUIX))
        assertEquals(1f, progress.last(), 0f)
    }

    @Test
    fun ensure_skips_when_marker_present() {
        val store = store(fakeClient(body = zipOf("a.txt" to "hello")))
        assertTrue(runBlocking { store.ensure(EngineAssets.DUIX) }.isSuccess)
        assertEquals(1, requestedUrls.size)

        assertTrue(runBlocking { store.ensure(EngineAssets.DUIX) }.isSuccess)

        assertEquals("no second download expected", 1, requestedUrls.size)
    }

    @Test
    fun ensure_fails_cleanly_on_http_error() {
        val store = store(fakeClient(code = 404))

        val result = runBlocking { store.ensure(EngineAssets.DUIX) }

        assertTrue(result.isFailure)
        assertFalse(store.isReady(EngineAssets.DUIX))
        val root = store.cacheRoot(EngineAssets.DUIX)
        assertFalse(File(root, "model/fixture").exists())
        assertFalse(File(root, "model/tmp/fixture").exists())
        assertTrue(
            "no leftover temp files",
            root.walkTopDown().none { it.isFile && it.name.endsWith(".tmp") },
        )
    }

    @Test
    fun baseUrlOverride_prefixes_relative_urls() {
        val relative = zipSpec.copy(url = "packs/fixture.zip")
        val store = store(
            fakeClient(body = zipOf("a.txt" to "hello")),
            specs = listOf(relative),
            baseUrlOverride = "https://mirror.test/dh/",
        )

        assertTrue(runBlocking { store.ensure(EngineAssets.DUIX) }.isSuccess)

        assertEquals(listOf("https://mirror.test/dh/packs/fixture.zip"), requestedUrls)
    }

    @Test
    fun ensure_rejects_zip_path_traversal() {
        val store = store(fakeClient(body = zipOf("../evil.txt" to "x")))

        val result = runBlocking { store.ensure(EngineAssets.DUIX) }

        assertTrue(result.isFailure)
        assertFalse(store.isReady(EngineAssets.DUIX))
        assertFalse(File(store.cacheRoot(EngineAssets.DUIX), "model/evil.txt").exists())
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test
    fun ensure_rejects_absolute_zip_entry() {
        val store = store(fakeClient(body = zipOf("/abs.txt" to "x")))

        val result = runBlocking { store.ensure(EngineAssets.DUIX) }

        assertTrue(result.isFailure)
        assertFalse(store.isReady(EngineAssets.DUIX))
        assertFalse(File(store.cacheRoot(EngineAssets.DUIX), "model/fixture").exists())
    }

    @Test
    fun ensure_fails_and_is_not_ready_when_engine_has_no_assets() {
        val store = store(fakeClient(), specs = emptyList())

        assertFalse(store.isReady(EngineAssets.LIVE2D))
        assertTrue(runBlocking { store.ensure(EngineAssets.LIVE2D) }.isFailure)
        assertEquals("no request expected", 0, requestedUrls.size)
    }

    @Test
    fun ensure_fails_on_sha256_mismatch() {
        val spec = zipSpec.copy(sha256 = "0".repeat(64))
        val store = store(fakeClient(body = zipOf("a.txt" to "hello")), specs = listOf(spec))

        val result = runBlocking { store.ensure(EngineAssets.DUIX) }

        assertTrue(result.isFailure)
        assertFalse(store.isReady(EngineAssets.DUIX))
    }

    @Test
    fun manifest_duix_pins_match_the_plan_urls() {
        val duix = AssetManifest.assetsFor(EngineAssets.DUIX)

        assertEquals(
            listOf(
                "https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip",
                "https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip",
            ),
            duix.map { it.url },
        )
        assertEquals(
            listOf("model/gj_dh_res", "model/Oliver"),
            duix.map { it.relativePath },
        )
        assertEquals(
            listOf("model/tmp/gj_dh_res", "model/tmp/Oliver"),
            duix.map { AssetStore.markerRelativePath(it) },
        )
    }
}
