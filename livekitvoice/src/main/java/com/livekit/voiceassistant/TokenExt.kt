package com.livekit.voiceassistant

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.Keep
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// TODO: Add your Sandbox ID here
private const val sandboxID = ""

// NOTE: If you prefer not to use LiveKit Sandboxes for testing, you can generate your
// tokens manually by visiting https://cloud.livekit.io/projects/p_/settings/keys
// and using one of your API Keys to generate a token with custom TTL and permissions.
private const val hardcodedUrl = "wss://testvoice-b8y35yvd.livekit.cloud"
// 使用本地测试 Token，避免网络连接问题
private const val hardcodedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUElTQU5ObUVHQlF4ZlkiLCJleHAiOjE3NzM0ODQ2OTMsInN1YiI6InRoZW1hc3MiLCJqdGkiOiJ0aGVtYXNzIiwibmFtZSI6InRoZW1hc3MiLCJtZXRhZGF0YSI6Im1ldGFkYXRhIiwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJ0aGVtYXNzX3Jvb20ifSwic2lwIjp7fX0.8cuvwjvfoeh69h3X2XFiI0Q_IQoOs3JNedfI2FkNGPE"

private val okHttpClient by lazy { OkHttpClient() }

/**
 * Retrieves a LiveKit from the token server.
 *
 * Currently configured to use LiveKit's Sandbox token server.
 * When building an app for production, you should use your own token server.
 */
fun ComponentActivity.requireToken(onTokenGenerated: (url: String, token: String) -> Unit) {
    if (sandboxID.isEmpty()) {
        runOnUiThread {
            // NOTE: If you prefer not to use LiveKit Sandboxes for testing, you can generate your
            // tokens manually by visiting https://cloud.livekit.io/projects/p_/settings/keys
            // and using one of your API Keys to generate a token with custom TTL and permissions.
            onTokenGenerated("wss://testvoice-b8y35yvd.livekit.cloud", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUElTQU5ObUVHQlF4ZlkiLCJleHAiOjE3NTc3MTY2OTMsInN1YiI6InRoZW1hc3MiLCJqdGkiOiJ0aGVtYXNzIiwibmFtZSI6InRoZW1hc3MiLCJtZXRhZGF0YSI6Im1ldGFkYXRhIiwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJ0aGVtYXNzX3Jvb20ifSwic2lwIjp7fX0.8cuvwjvfoeh69h3X2XFiI0Q_IQoOs3JNedfI2FkNGPE")
            Timber.w { "sandboxID not populated, using default URL and token." }
        }
        return
    }

    val activity = this
    val tokenEndpoint = "https://cloud-api.livekit.io/api/sandbox/connection-details"
    val client = OkHttpClient()
    val request = Request.Builder()
        .url(tokenEndpoint)
        .header("X-Sandbox-ID", sandboxID)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(
                    activity,
                    "Failed to fetch connection details",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

        override fun onResponse(call: Call, response: Response) {
            response.body.let { responseBody ->
                if (response.isSuccessful) {
                    data class ConnectionDetails(
                        val serverUrl: String,
                        val participantToken: String,
                    )
                    val json = responseBody.string()
                    val cd = Gson().fromJson(json, ConnectionDetails::class.java)
                    runOnUiThread {
                        onTokenGenerated(cd.serverUrl, cd.participantToken)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Failed to parse connection details",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }
        }
    })
}

/**
 * Retrieves a token using the requireToken function.
 * This is a convenience function for easier usage in Compose screens.
 */
suspend fun ComponentActivity.retrieveToken(): Pair<String, String> = suspendCancellableCoroutine { continuation ->
    requireToken { url, token ->
        continuation.resume(Pair(url, token))
    }
}