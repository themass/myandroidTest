package com.openapi.ks.dh

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads engine packs on demand into app-private storage and reports whether an engine is
 * ready. A pack counts as ready when its completion marker file exists (see [markerRelativePath]);
 * the marker is written last, so an interrupted download or extraction is never mistaken for a
 * finished one.
 *
 * @param baseUrlOverride when set, spec URLs that are not absolute (`http://` or `https://`) are
 *   resolved against it, which lets a mirror or test server host the packs.
 */
class AssetStore internal constructor(
    private val rootDir: File,
    private val client: OkHttpClient,
    private val baseUrlOverride: String?,
    private val manifest: (EngineAssets) -> List<AssetSpec>,
) {

    constructor(
        context: Context,
        client: OkHttpClient = OkHttpClient(),
        baseUrlOverride: String? = null,
    ) : this(
        rootDir = File(context.applicationContext.filesDir, DIR_NAME),
        client = client,
        baseUrlOverride = baseUrlOverride,
        manifest = AssetManifest::assetsFor,
    )

    private val lock = Mutex()

    fun cacheRoot(engine: EngineAssets): File = File(rootDir, engine.name.lowercase())

    fun isReady(engine: EngineAssets): Boolean {
        val specs = manifest(engine)
        return specs.isNotEmpty() && specs.all { isPackReady(engine, it) }
    }

    /**
     * Makes sure every pack of [engine] is downloaded and extracted. Packs that already have a
     * marker are skipped. [onProgress] receives 0..1 across all packs and ends with 1 on success.
     * Failures are returned, never thrown (cancellation excepted), and leave no partial content.
     */
    suspend fun ensure(engine: EngineAssets, onProgress: (Float) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val specs = manifest(engine)
                    if (specs.isEmpty()) {
                        throw IOException("No downloadable assets are configured for $engine")
                    }
                    val root = cacheRoot(engine)
                    specs.forEachIndexed { index, spec ->
                        if (!isPackReady(engine, spec)) {
                            installPack(root, spec) { fraction ->
                                onProgress((index + fraction) / specs.size)
                            }
                        }
                    }
                    onProgress(1f)
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    private fun isPackReady(engine: EngineAssets, spec: AssetSpec): Boolean =
        File(cacheRoot(engine), markerRelativePath(spec)).isFile

    private suspend fun installPack(root: File, spec: AssetSpec, onFraction: (Float) -> Unit) {
        root.mkdirs()
        val download = File(root, "${spec.id}.download.tmp")
        val staging = File(root, ".staging-${spec.id}")
        try {
            download(resolveUrl(spec.url), download, onFraction)
            spec.sha256?.let { verifySha256(download, it, spec.id) }

            val target = File(root, spec.relativePath)
            staging.deleteRecursively()
            when (spec.kind) {
                AssetSpec.Kind.ZIP -> {
                    staging.mkdirs()
                    extractZip(download, staging, target.name)
                    replace(staging, target)
                }
                AssetSpec.Kind.FILE -> {
                    download.copyTo(staging, overwrite = true)
                    replace(staging, target)
                }
            }

            val marker = File(root, markerRelativePath(spec))
            marker.parentFile?.mkdirs()
            if (!marker.createNewFile() && !marker.isFile) {
                throw IOException("Cannot write marker ${marker.path}")
            }
            onFraction(1f)
        } finally {
            download.delete()
            staging.deleteRecursively()
        }
    }

    private fun resolveUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = baseUrlOverride
            ?: throw IOException("Relative asset URL '$url' needs a baseUrlOverride")
        return base.trimEnd('/') + "/" + url.trimStart('/')
    }

    private suspend fun download(url: String, dest: File, onFraction: (Float) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onFraction(read.toFloat() / total)
                    }
                }
            }
        }
    }

    private fun verifySha256(file: File, expected: String, id: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch for $id: expected $expected, got $actual")
        }
    }

    /**
     * Extracts [zip] into [destDir], rejecting entries that would land outside it. When every
     * entry lives under one top-level directory named [flattenName] (the target's own name —
     * e.g. gj_dh_res.zip wraps `gj_dh_res/wenet.o`), that wrapper is stripped so the payload
     * lands directly in [destDir] instead of double-nested beyond the target path.
     */
    private fun extractZip(zip: File, destDir: File, flattenName: String) {
        val destRoot = destDir.canonicalFile
        ZipFile(zip).use { zipFile ->
            val entries = zipFile.entries().toList()
            val prefix = singleTopLevelDir(entries.map { it.name }, flattenName)
            for (entry in entries) {
                var name = entry.name
                if (prefix != null && name.startsWith(prefix)) {
                    name = name.drop(prefix.length)
                    if (name.isEmpty()) continue // the wrapper dir entry itself
                }
                if (name.startsWith("/") || name.startsWith("\\") || DRIVE_PREFIX.containsMatchIn(name)) {
                    throw IOException("Unsafe absolute zip entry: $name")
                }
                val out = File(destRoot, name).canonicalFile
                if (out != destRoot && !out.path.startsWith(destRoot.path + File.separator)) {
                    throw IOException("Unsafe zip entry escapes target dir: $name")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    /** The `"<name>/"` prefix to strip when every entry sits under exactly one top-level dir
     *  named [flattenName]; null keeps the tree verbatim. */
    private fun singleTopLevelDir(names: List<String>, flattenName: String): String? {
        if (names.isEmpty()) return null
        val tops = names.map { it.substringBefore('/', missingDelimiterValue = "") }.toSet()
        return if (tops == setOf(flattenName)) "$flattenName/" else null
    }

    /** Moves [from] to [to], replacing any previous (possibly stale) content at [to]. */
    private fun replace(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (to.exists() && !to.deleteRecursively()) {
            throw IOException("Cannot replace ${to.path}")
        }
        if (!from.renameTo(to)) {
            throw IOException("Cannot move ${from.path} to ${to.path}")
        }
    }

    companion object {
        private const val DIR_NAME = "dh-engines"
        private const val BUFFER_SIZE = 64 * 1024
        private val DRIVE_PREFIX = Regex("^[A-Za-z]:")

        /**
         * Completion marker for [spec], relative to the engine cache root: a `tmp` directory
         * next to the pack's own directory, e.g. `model/Oliver` -> `model/tmp/Oliver`.
         */
        fun markerRelativePath(spec: AssetSpec): String {
            val path = spec.relativePath.trim('/')
            val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
            val name = path.substringAfterLast('/')
            return if (parent.isEmpty()) "tmp/$name" else "$parent/tmp/$name"
        }
    }
}
