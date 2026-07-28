package com.djapp.api

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

object ApiClient {

    private const val PREFS_NAME = "dj_api_prefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_USER_EMAIL = "user_email"

    private var baseUrl = ""
    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                var request = chain.request()
                val token = _authToken
                if (!token.isNullOrBlank()) {
                    request = request.newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .build()
    }

    private var _authToken: String? = null
    private var _userEmail: String? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""
        _authToken = prefs.getString(KEY_AUTH_TOKEN, null)
        _userEmail = prefs.getString(KEY_USER_EMAIL, null)
    }

    fun setAuthToken(token: String?) {
        _authToken = token
    }

    fun setBaseUrl(url: String) {
        baseUrl = url
    }

    fun getUserEmail(): String? = _userEmail

    fun isLoggedIn(): Boolean = !_authToken.isNullOrBlank()

    fun saveSession(context: Context, token: String?, email: String?) {
        _authToken = token
        _userEmail = email
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun clearSession(context: Context) {
        _authToken = null
        _userEmail = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw ApiException(parseErrorMessage(body, response.code))
        }
        body
    }

    suspend fun post(path: String, body: Any? = null): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val jsonBody = if (body != null) gson.toJson(body) else "{}"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw ApiException(parseErrorMessage(responseBody, response.code))
        }
        responseBody
    }

    suspend fun put(path: String, body: Any? = null): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val jsonBody = if (body != null) gson.toJson(body) else "{}"
        val request = Request.Builder()
            .url(url)
            .put(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw ApiException(parseErrorMessage(responseBody, response.code))
        }
        responseBody
    }

    suspend fun delete(path: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw ApiException(parseErrorMessage(responseBody, response.code))
        }
        responseBody
    }

    inline fun <reified T> parse(json: String): T {
        return gson.fromJson(json, T::class.java)
    }

    fun <T> parseList(json: String, clazz: Class<T>): List<T> {
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, clazz).type
        return gson.fromJson(json, type)
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val map = gson.fromJson(body, Map::class.java)
            map?.get("message") as? String
                ?: map?.get("error") as? String
                ?: "Fehler $code"
        } catch (_: Exception) {
            when (code) {
                401 -> "Nicht autorisiert"
                403 -> "Zugriff verweigert"
                404 -> "Nicht gefunden"
                500 -> "Serverfehler"
                else -> "Fehler $code"
            }
        }
    }
}
