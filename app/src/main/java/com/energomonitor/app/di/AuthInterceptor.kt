package com.energomonitor.app.di

import android.util.Base64
import com.energomonitor.app.data.local.UserPreferences
import com.energomonitor.app.data.remote.AuthorizationRequest
import com.energomonitor.app.data.remote.AuthorizationResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val userPreferences: UserPreferences
) : Interceptor {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Ignore if we are already doing an authorization request to avoid loops
        if (request.url.encodedPath.contains("authorizations")) {
            return chain.proceed(request)
        }

        var token = runBlocking { userPreferences.token.first() }

        // If no token exists, try to get one synchronously
        if (token.isNullOrBlank()) {
            token = runBlocking {
                mutex.withLock {
                    var currentToken = userPreferences.token.first()
                    if (currentToken.isNullOrBlank()) {
                        currentToken = refreshAccessToken(chain)
                    }
                    currentToken
                }
            }
            if (token.isNullOrBlank()) {
                // Failed to get token (e.g. wrong credentials), proceed anyway to surface 401
                return chain.proceed(request)
            }
        }

        var response = proceedWithToken(chain, request, token)

        // If unauthorized, token might be expired
        if (response.code == 401) {
            response.close() // Close the previous body
            token = runBlocking {
                mutex.withLock {
                    // Check if another thread already refreshed the token
                    val newToken = userPreferences.token.first()
                    if (newToken != null && newToken != token && newToken.isNotBlank()) {
                        newToken
                    } else {
                        refreshAccessToken(chain)
                    }
                }
            }
            if (!token.isNullOrBlank()) {
                response = proceedWithToken(chain, request, token)
            } else {
                // Token refresh failed, maybe user changed password or network issue. 
                // Return original 401 so the app knows it's unauthenticated.
                return proceedWithToken(chain, request, null)
            }
        }

        return response
    }

    private fun proceedWithToken(chain: Interceptor.Chain, request: Request, token: String?): Response {
        val builder = request.newBuilder()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        // Fix for first login: if the original request was built with an empty user_id, it will have "users//feeds".
        // We patch it with the newly obtained user_id so it doesn't fail with 404.
        val urlString = request.url.toString()
        if (urlString.contains("users//feeds")) {
            val userId = runBlocking { userPreferences.userId.first() }
            if (!userId.isNullOrBlank()) {
                builder.url(urlString.replace("users//feeds", "users/$userId/feeds"))
            }
        }

        return chain.proceed(builder.build())
    }

    private suspend fun refreshAccessToken(chain: Interceptor.Chain): String? {
        val username = userPreferences.username.first()
        val password = userPreferences.password.first()

        if (username.isNullOrBlank() || password.isNullOrBlank()) return null

        val basicAuth = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        
        val authRequestDto = AuthorizationRequest()
        val body = json.encodeToString(authRequestDto).toRequestBody("application/json".toMediaType())

        // Build the request using the same okhttp client but to the auth endpoint
        val authRequest = Request.Builder()
            .url("https://api.energomonitor.com/v1/authorizations")
            .post(body)
            .header("Authorization", "Basic $basicAuth")
            .build()

        try {
            // Need a new client without this interceptor to avoid infinite loops,
            // or we can just proceed the chain since we check for "authorizations" at the top.
            val response = chain.proceed(authRequest)
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string()
                if (responseBodyStr != null) {
                    val authResponse = json.decodeFromString<AuthorizationResponse>(responseBodyStr)
                    userPreferences.saveAuthData(
                        userId = authResponse.user_id,
                        token = authResponse.token,
                        expiresAt = authResponse.expires_at
                    )
                    return authResponse.token
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
