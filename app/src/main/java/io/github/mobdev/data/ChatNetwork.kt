package io.github.mobdev.data

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.io.IOException

/** Header that carries the auth token in both directions. */
const val AUTH_TOKEN_HEADER = "X-Auth-Token"

private const val BASE_URL = "https://faerytea.name/"

/**
 * Owns the HTTP stack: OkHttp (token injection + transparent re-login on 401)
 * and Retrofit wired with kotlinx.serialization.
 */
class ChatNetwork(credentials: CredentialsStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // BASIC writes one line per request and one per response to Logcat under
    // tag "okhttp.OkHttpClient" — enough to prove that rotation never triggers
    // a new call while explicit user actions do.
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(credentials))
        .addInterceptor(logging)
        .authenticator(ReloginAuthenticator(credentials, json))
        .build()

    val api: ChatApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ChatApi::class.java)

    fun thumbUrl(link: String): String = BASE_URL + "thumb/" + link
    fun imageUrl(link: String): String = BASE_URL + "img/" + link
}

/** Attaches the current token to every outgoing request. */
private class AuthInterceptor(
    private val credentials: CredentialsStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = credentials.token
        val request = if (token != null && chain.request().header(AUTH_TOKEN_HEADER) == null) {
            chain.request().newBuilder().header(AUTH_TOKEN_HEADER, token).build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/**
 * On a 401, transparently logs in again with the stored credentials and
 * retries the request once. If there are no stored credentials or the
 * re-login fails, the 401 is allowed to propagate so the UI can show the
 * login screen again.
 */
private class ReloginAuthenticator(
    private val credentials: CredentialsStore,
    private val json: Json,
) : Authenticator {

    private val bareClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null
        val name = credentials.username ?: return null
        val password = credentials.password ?: return null

        val freshToken = synchronized(this) {
            val triedToken = response.request.header(AUTH_TOKEN_HEADER)
            val current = credentials.token
            if (current != null && current != triedToken) {
                // Another request already refreshed the token; reuse it.
                current
            } else {
                login(name, password)?.also { credentials.token = it }
            }
        } ?: return null

        return response.request.newBuilder()
            .header(AUTH_TOKEN_HEADER, freshToken)
            .build()
    }

    private fun login(name: String, password: String): String? {
        val body = json.encodeToString(LoginRequest(name, password))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(BASE_URL + "login")
            .post(body)
            .build()
        return try {
            bareClient.newCall(request).execute().use { result ->
                if (!result.isSuccessful) {
                    null
                } else {
                    // The live server returns the token in the response body
                    // (text/plain); the X-Auth-Token header is only a fallback.
                    result.header(AUTH_TOKEN_HEADER)
                        ?: result.body?.string()?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var count = 1
        while (current?.priorResponse != null) {
            count++
            current = current.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
