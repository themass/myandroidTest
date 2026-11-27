package com.openapi.ks.myapp.avatar

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/** This contract is independent of the existing server avatar manifest. */
enum class CharacterEngineType { LAYERED, LIVE2D, RIVE, DUIX, TAO }

data class CharacterAssetManifest(
    val schemaVersion: Int,
    val packId: String,
    val version: Int,
    val engine: CharacterEngineType,
    val entryPoint: String,
    val files: Map<String, String>,
) {
    companion object {
        /** The caller supplies a trusted manifest; checksums detect damaged assets, not malicious manifests. */
        fun parse(json: String): CharacterAssetManifest {
            val obj = JSONObject(json)
            val entries = obj.getJSONObject("files")
            val files = entries.keys().asSequence().associateWith { entries.getString(it) }
            return CharacterAssetManifest(
                obj.getInt("schema_version"), obj.getString("pack_id"), obj.getInt("version"),
                CharacterEngineType.valueOf(obj.getString("engine")), obj.getString("entry_point"), files,
            ).also { require(CharacterAssetPolicy.isValid(it)) { "Invalid character asset manifest" } }
        }
    }
}

/** Pure validation shared by all sources, before any file system operations. */
object CharacterAssetPolicy {
    private val id = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    private val digest = Regex("[0-9a-fA-F]{64}")
    private val component = Regex("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}")
    fun isValid(manifest: CharacterAssetManifest): Boolean =
        manifest.schemaVersion == 1 && id.matches(manifest.packId) && manifest.version > 0 &&
            manifest.files.isNotEmpty() && manifest.files.size <= 200 &&
            manifest.entryPoint in manifest.files && manifest.files.all { (path, sha) ->
                path.length <= 512 && path.split('/').all { component.matches(it) && it != "." && it != ".." } &&
                    digest.matches(sha)
            }
}

fun interface CharacterAssetSource { fun open(path: String): InputStream }

/** filesDir/character-assets/{packId}/v{version}; versions are immutable once installed. */
class CharacterAssetPackStore(private val filesDir: File) {
    companion object {
        fun bundled(context: Context): CharacterAssetPackStore = CharacterAssetPackStore(context.applicationContext.filesDir)
        fun assets(context: Context, prefix: String): CharacterAssetSource {
            require(prefix.isNotEmpty() && prefix.split('/').all { it.matches(Regex("[a-zA-Z0-9_][a-zA-Z0-9_.-]*")) })
            val assets = context.applicationContext.assets
            return CharacterAssetSource { path -> assets.open("$prefix/$path") }
        }
        private const val MAX_BYTES = 100L * 1024 * 1024
    }

    @Synchronized
    fun install(manifest: CharacterAssetManifest, source: CharacterAssetSource): File {
        require(CharacterAssetPolicy.isValid(manifest)) { "Invalid character asset manifest" }
        val privateRoot = filesDir.canonicalFile
        val root = File(privateRoot, "character-assets")
        require(root.canonicalFile == root.absoluteFile) { "Unsafe asset root" }
        val pack = File(root, manifest.packId)
        require(pack.canonicalFile == pack.absoluteFile) { "Unsafe pack directory" }
        check(pack.mkdirs() || pack.isDirectory) { "Cannot create pack directory" }
        val target = File(pack, "v${manifest.version}")
        check(!target.exists()) { "Version already installed" }
        val staging = File(pack, ".staging-${UUID.randomUUID()}")
        check(staging.mkdir()) { "Cannot create staging directory" }
        try {
            var total = 0L
            for ((path, expected) in manifest.files) {
                val output = File(staging, path).canonicalFile
                require(output.path.startsWith(staging.canonicalPath + File.separator)) { "Unsafe asset path" }
                check(output.parentFile!!.mkdirs() || output.parentFile!!.isDirectory)
                val hash = MessageDigest.getInstance("SHA-256")
                source.open(path).use { input ->
                    output.outputStream().use { sink ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= MAX_BYTES) { "Asset pack too large" }
                            hash.update(buffer, 0, count)
                            sink.write(buffer, 0, count)
                        }
                    }
                }
                val actual = hash.digest().joinToString("") { "%02x".format(it) }
                check(actual.equals(expected, ignoreCase = true)) { "Asset checksum mismatch: $path" }
            }
            check(!target.exists() && staging.renameTo(target)) { "Cannot publish asset pack" }
            return target
        } finally {
            staging.deleteRecursively()
        }
    }
}
