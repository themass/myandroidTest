package com.openapi.ks.myapp.companion

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * her 包「下载 → sha256 校验 → 安装」链（[HerPackSync]，CharacterAssetPackStore 的第一个
 * 生产调用方）。okhttp 用假拦截器替网络：不起服务、不依赖公网。钉五条：
 *
 * 1. 已装版本 ≥ pack_version 就跳过——连网络请求都不发（[herPackAvailability]）；
 * 2. 整包 sha256 ≠ `model_ref.hash`：[HerPackState.VerifyFailed]，**装都不装**、临时文件清掉，
 *    已装版本（如有）原封不动；
 * 3. 成功路径：包落在 filesDir/character-assets/{herId}/v{version}，过程回 Downloading/Installing；
 * 4. download_url 空 / id 不合法：不联网（「暂不可下载」的兑现，路径注入在门口就挡）；
 * 5. renderer 不认识：下载完也不装（包内容端上用不了 → VerifyFailed）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HerPackSyncTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        cacheDir = temp.newFolder("cache")
    }

    private class FakeDownload(private val bytes: ByteArray, var code: Int = 200) : Interceptor {
        val seen = mutableListOf<okhttp3.Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            seen += req
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(bytes.toResponseBody("application/zip".toMediaType())).build()
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun model(hash: String, url: String = "https://cdn.test/pack.zip?sig=ghi", version: Int = 2) =
        HerModelRef(
            asset = "model/scene.model3.json", version = version, hash = hash,
            downloadUrl = url, urlExpiresIn = 3600, sizeBytes = 123,
        )

    private fun detail(model: HerModelRef, renderer: String = "live2d", id: String = HER_ID_TEST) =
        HerDetail(
            id = id, name = "林澈 · Live2D", tagline = "", renderer = renderer,
            status = "published", packVersion = model.version, source = "seed", model = model,
        )

    private fun syncWith(fake: FakeDownload) =
        HerPackSync(filesDir, cacheDir, OkHttpClient.Builder().addInterceptor(fake).build())

    private fun packRoot(herId: String = HER_ID_TEST) = File(filesDir, "character-assets/$herId")

    // ---- 纯决策口径 ----

    @Test
    fun `skip decision compares installed version against pack_version`() {
        val good = model(sha("x".toByteArray()))
        assertEquals(HerPackAvailability.SKIP_INSTALLED, herPackAvailability(2, good))
        assertEquals(HerPackAvailability.SKIP_INSTALLED, herPackAvailability(3, good))   // 本地更新也跳过
        assertEquals(HerPackAvailability.DOWNLOAD, herPackAvailability(1, good))
        assertEquals(HerPackAvailability.DOWNLOAD, herPackAvailability(null, good))
        assertEquals(HerPackAvailability.UNAVAILABLE, herPackAvailability(null, model("deadbeef")))
        assertEquals(HerPackAvailability.UNAVAILABLE, herPackAvailability(null, model(sha("x".toByteArray()), url = "")))
    }

    // ---- 成功路径 ----

    @Test
    fun `downloads sha-verified pack and installs it under character-assets`() = runBlocking {
        val bytes = zipOf("manifest.json" to "{}", "model/scene.model3.json" to "scene")
        val fake = FakeDownload(bytes)
        val sync = syncWith(fake)
        val seen = mutableListOf<HerPackState>()
        val st = sync.sync(detail(model(sha(bytes)))) { seen += it }
        assertEquals(HerPackState.Installed(2), st)
        assertEquals(1, fake.seen.size)
        assertTrue(seen.any { it is HerPackState.Downloading && it.progress != null })
        assertTrue(seen.any { it == HerPackState.Installing })
        val installed = File(packRoot(), "v2")
        assertEquals("scene", File(installed, "model/scene.model3.json").readText())
        assertEquals(2, sync.installedVersion(HER_ID_TEST))
        assertEquals(0, cacheDir.listFiles()!!.size)   // 临时 zip 清干净
        // 签名链接不带登录凭据：对象存储不是我们的 API。
        assertFalse(fake.seen.single().headers.names().any { it.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun `already-installed version skips the network entirely`() = runBlocking {
        val bytes = zipOf("model/scene.model3.json" to "scene")
        val fake = FakeDownload(bytes)
        val sync = syncWith(fake)
        sync.sync(detail(model(sha(bytes))))
        assertEquals(1, fake.seen.size)
        // 第二次同样请求：已装 v2 ≥ v2，一个请求都不该再发。
        val st = sync.sync(detail(model(sha(bytes))))
        assertEquals(HerPackState.Installed(2), st)
        assertEquals(1, fake.seen.size)
        assertEquals(HerPackState.Installed(2), sync.stateFor(detail(model(sha(bytes)))))
    }

    // ---- 校验失败 ----

    @Test
    fun `bytes whose sha256 mismatches model_ref hash are never installed`() = runBlocking {
        val bytes = zipOf("model/scene.model3.json" to "scene")
        val fake = FakeDownload(bytes)
        val sync = syncWith(fake)
        val st = sync.sync(detail(model(sha("totally other bytes".toByteArray()))))
        assertTrue("期望 VerifyFailed，实际 $st", st is HerPackState.VerifyFailed)
        assertEquals(1, fake.seen.size)
        assertFalse("校验不过不该留下任何安装痕迹", packRoot().exists())
        assertEquals(0, cacheDir.listFiles()!!.size)
    }

    @Test
    fun `verify failure keeps an older installed version intact and retryable`() = runBlocking {
        val good = zipOf("model/scene.model3.json" to "scene")
        val sync = syncWith(FakeDownload(good))
        sync.sync(detail(model(sha(good))))                       // 先装 v2
        val bad = zipOf("model/scene.model3.json" to "evil")
        val fakeBad = FakeDownload(bad)
        val sync2 = syncWith(fakeBad)
        // 同一个 version：已装 v2 会先跳过——校验失败的语义要看 version 3。
        // hash 故意对不上发回来的 bad zip：VerifyFailed 且 v2 原封不动。
        val st = sync2.sync(detail(model(sha("some other pack entirely".toByteArray()), version = 3)))
        assertTrue("期望 VerifyFailed，实际 $st", st is HerPackState.VerifyFailed)
        assertEquals("scene", File(packRoot(), "v2/model/scene.model3.json").readText())
        assertFalse(File(packRoot(), "v3").exists())
        assertEquals(HerPackState.Installed(2), sync2.stateFor(detail(model(sha(bad)))))
    }

    // ---- 不可下载 / 不安全输入 ----

    @Test
    fun `empty download url never reaches the network`() = runBlocking {
        val fake = FakeDownload(zipOf("model/scene.model3.json" to "scene"))
        val sync = syncWith(fake)
        val st = sync.sync(detail(model(sha("x".toByteArray()), url = "")))
        assertEquals(HerPackState.NotInstalled, st)
        assertEquals(0, fake.seen.size)
    }

    @Test
    fun `her id outside the pack-id shape touches nothing at all`() = runBlocking {
        val bytes = zipOf("model/scene.model3.json" to "scene")
        val fake = FakeDownload(bytes)
        val sync = syncWith(fake)
        val st = sync.sync(detail(model(sha(bytes)), id = "../evil"))
        assertEquals(HerPackState.NotInstalled, st)
        assertEquals(0, fake.seen.size)
        assertFalse(File(filesDir, "character-assets/evil").exists())
    }

    @Test
    fun `unknown renderer is rejected after download, nothing installed`() = runBlocking {
        val bytes = zipOf("model/scene.model3.json" to "scene")
        val fake = FakeDownload(bytes)
        val sync = syncWith(fake)
        val st = sync.sync(detail(model(sha(bytes)), renderer = "talkinghead"))
        assertTrue("期望 VerifyFailed，实际 $st", st is HerPackState.VerifyFailed)
        assertFalse(packRoot().exists())
    }

    private companion object {
        const val HER_ID_TEST = "live2d-linche-v2"
    }
}
