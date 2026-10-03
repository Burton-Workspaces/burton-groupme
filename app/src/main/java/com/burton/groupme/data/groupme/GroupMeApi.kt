package com.burton.groupme.data.groupme

import com.burton.groupme.data.parse.TinyJson
import com.burton.groupme.data.parse.TinyJson.int
import com.burton.groupme.data.parse.TinyJson.obj
import com.burton.groupme.data.parse.TinyJson.str
import com.burton.groupme.data.parse.TinyJson.strList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupMeApi @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun get(
        token: String,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): Map<String, Any?> = request(token, path, params, bodyJson = null)

    suspend fun post(
        token: String,
        path: String,
        body: Map<String, Any?> = emptyMap(),
        params: Map<String, String> = emptyMap(),
    ): Map<String, Any?> = request(token, path, params, bodyJson = TinyJson.stringify(body))

    private suspend fun request(
        token: String,
        path: String,
        params: Map<String, String>,
        bodyJson: String?,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val url = "$HOST/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val builder = Request.Builder()
            .url(url)
            .header("X-Access-Token", token)
            .header("Accept", "application/json")
        if (bodyJson == null) {
            builder.get()
        } else {
            builder.post(bodyJson.toRequestBody(JSON))
                .header("Content-Type", "application/json")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 304) return@use emptyMap()
            if (!response.isSuccessful && text.isBlank()) {
                throw GroupMeApiException(path, "http_${response.code}")
            }
            if (text.isBlank()) return@use emptyMap()
            val parsed = TinyJson.parseObject(text)
            val meta = parsed.obj("meta")
            val code = meta.int("code", response.code)
            if (code == 304) return@use emptyMap()
            if (code !in 200..299) {
                val error = meta.strList("errors").firstOrNull()
                    ?: meta.str("errors").ifBlank { parsed.str("error") }
                        .ifBlank { "http_$code" }
                throw GroupMeApiException(path, error)
            }
            parsed
        }
    }

    companion object {
        const val HOST = "https://api.groupme.com/v3"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
