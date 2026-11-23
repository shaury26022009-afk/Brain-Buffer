package com.example.cloud

import com.example.model.SupabaseAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed interface SupabaseAuthResult<out T> {
    data class Success<T>(val data: T, val notice: String? = null) : SupabaseAuthResult<T>
    data class Error(val message: String, val isUnauthorized: Boolean = false) : SupabaseAuthResult<Nothing>
}

/**
 * Production Supabase GoTrue REST authentication client using OkHttp.
 *
 * Supports:
 * - Email/password sign-up (with deep-link verification redirect), login, resend verification, and recovery.
 * - Authenticated user hydration (`GET /auth/v1/user`).
 * - Session refresh, logout, and server-side Edge Function account deletion.
 *
 * Never logs passwords or access/refresh tokens; requires HTTPS + publishable anon key only.
 */
class SupabaseAuthClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): SupabaseAuthResult<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext SupabaseAuthResult.Error(
                "Cloud authentication isn't configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets."
            )
        }
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@") || cleanEmail.length < 5) {
            return@withContext SupabaseAuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 8) {
            return@withContext SupabaseAuthResult.Error("Use at least 8 characters for your password.")
        }

        val bodyJson = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("data", JSONObject().apply {
                put("display_name", displayName.trim())
            })
        }
        val redirectEncoded = URLEncoder.encode(SupabaseConfig.AUTH_REDIRECT_URI, "UTF-8")

        executeAuthRequest(
            url = SupabaseConfig.authUrl("signup?redirect_to=$redirectEncoded"),
            bodyJson = bodyJson,
            isSignUp = true,
            fallbackProvider = "email"
        )
    }

    suspend fun signInWithPassword(
        email: String,
        password: String
    ): SupabaseAuthResult<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext SupabaseAuthResult.Error(
                "Cloud authentication isn't configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets."
            )
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            return@withContext SupabaseAuthResult.Error("Enter both your email and password.")
        }

        val bodyJson = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
        }

        executeAuthRequest(
            url = SupabaseConfig.authUrl("token?grant_type=password"),
            bodyJson = bodyJson,
            isSignUp = false,
            fallbackProvider = "email"
        )
    }

    /**
     * Fetches the authenticated user object (`GET /auth/v1/user`) to hydrate user ID,
     * email, linked providers, and display name metadata.
     */
    suspend fun fetchUser(
        accessToken: String,
        refreshToken: String = "",
        expiresAtEpochSec: Long = 0L
    ): SupabaseAuthResult<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || accessToken.isBlank()) {
            return@withContext SupabaseAuthResult.Error("Not signed in.", isUnauthorized = true)
        }
        val request = Request.Builder()
            .url(SupabaseConfig.authUrl("user"))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SupabaseAuthResult.Error(
                        message = parseSupabaseError(rawBody, response.code),
                        isUnauthorized = response.code == 401
                    )
                }
                val userObj = runCatching { JSONObject(rawBody) }.getOrNull()
                    ?: return@use SupabaseAuthResult.Error("Unexpected user payload from authentication server.")
                val userId = userObj.optString("id", "")
                val email = userObj.optString("email", "")
                if (userId.isBlank()) {
                    return@use SupabaseAuthResult.Error("Could not read authenticated user ID.")
                }
                SupabaseAuthResult.Success(
                    SupabaseAuthSession(
                        userId = userId,
                        email = email,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtEpochSec = expiresAtEpochSec,
                        providers = extractProviders(userObj, fallbackProvider = ""),
                        displayNameHint = extractDisplayNameHint(userObj)
                    )
                )
            }
        }.getOrElse { err ->
            SupabaseAuthResult.Error(networkErrorMessage(err))
        }
    }

    suspend fun resendVerificationEmail(email: String): SupabaseAuthResult<Unit> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext SupabaseAuthResult.Error(
                    "Cloud authentication isn't configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets."
                )
            }
            val cleanEmail = email.trim()
            if (!cleanEmail.contains("@")) {
                return@withContext SupabaseAuthResult.Error("Enter your email address to resend verification.")
            }

            val payload = JSONObject().apply {
                put("type", "signup")
                put("email", cleanEmail)
                put("options", JSONObject().apply {
                    put("emailRedirectTo", SupabaseConfig.AUTH_REDIRECT_URI)
                })
            }.toString()

            val request = Request.Builder()
                .url(SupabaseConfig.authUrl("resend"))
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        SupabaseAuthResult.Success(
                            Unit,
                            notice = "Verification email resent to $cleanEmail. Check your inbox and spam folder."
                        )
                    } else {
                        SupabaseAuthResult.Error(parseSupabaseError(response.body?.string(), response.code))
                    }
                }
            }.getOrElse { err ->
                SupabaseAuthResult.Error(networkErrorMessage(err))
            }
        }

    suspend fun refreshSession(refreshToken: String): SupabaseAuthResult<SupabaseAuthSession> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext SupabaseAuthResult.Error("Supabase is not configured.")
            }
            if (refreshToken.isBlank()) {
                return@withContext SupabaseAuthResult.Error("Session expired. Please sign in again.", isUnauthorized = true)
            }

            val bodyJson = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            executeAuthRequest(
                url = SupabaseConfig.authUrl("token?grant_type=refresh_token"),
                bodyJson = bodyJson,
                isSignUp = false,
                fallbackProvider = ""
            )
        }

    suspend fun resetPassword(email: String): SupabaseAuthResult<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext SupabaseAuthResult.Error(
                "Cloud authentication isn't configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets."
            )
        }
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@")) {
            return@withContext SupabaseAuthResult.Error("Enter a valid email to receive a reset link.")
        }

        val payload = JSONObject().apply { put("email", cleanEmail) }.toString()
        val redirectEncoded = URLEncoder.encode(SupabaseConfig.AUTH_REDIRECT_URI, "UTF-8")
        val request = Request.Builder()
            .url(SupabaseConfig.authUrl("recover?redirect_to=$redirectEncoded"))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SupabaseAuthResult.Success(
                        Unit,
                        notice = "Password reset link sent to $cleanEmail if an account exists."
                    )
                } else {
                    val errorMsg = parseSupabaseError(response.body?.string(), response.code)
                    SupabaseAuthResult.Error(errorMsg)
                }
            }
        }.getOrElse { err ->
            SupabaseAuthResult.Error(networkErrorMessage(err))
        }
    }

    suspend fun signOut(accessToken: String): SupabaseAuthResult<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || accessToken.isBlank()) {
            return@withContext SupabaseAuthResult.Success(Unit)
        }
        val request = Request.Builder()
            .url(SupabaseConfig.authUrl("logout"))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 401) {
                    SupabaseAuthResult.Success(Unit)
                } else {
                    SupabaseAuthResult.Error(parseSupabaseError(response.body?.string(), response.code))
                }
            }
        }.getOrElse {
            // Even when offline, caller can safely clear local auth session after best-effort revoke
            SupabaseAuthResult.Success(Unit)
        }
    }

    /**
     * Calls the deployed Supabase Edge Function `delete-account`, which validates the caller JWT
     * and deletes the account server-side without embedding any service-role key on the device.
     */
    suspend fun requestAccountDeletion(accessToken: String): SupabaseAuthResult<Unit> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext SupabaseAuthResult.Error("Cloud backend is not configured.")
            }
            if (accessToken.isBlank()) {
                return@withContext SupabaseAuthResult.Error("Sign in first before deleting a cloud account.", isUnauthorized = true)
            }

            val request = Request.Builder()
                .url(SupabaseConfig.functionUrl("delete-account"))
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        SupabaseAuthResult.Success(Unit, notice = "Your cloud account and synced data have been permanently deleted.")
                    } else if (response.code == 404) {
                        SupabaseAuthResult.Error(
                            "Deploy the `delete-account` Supabase Edge Function from supabase/functions/delete-account/index.ts to enable one-tap cloud account deletion."
                        )
                    } else {
                        SupabaseAuthResult.Error(
                            parseSupabaseError(raw, response.code),
                            isUnauthorized = response.code == 401
                        )
                    }
                }
            }.getOrElse { err ->
                SupabaseAuthResult.Error(networkErrorMessage(err))
            }
        }

    private fun executeAuthRequest(
        url: String,
        bodyJson: JSONObject,
        isSignUp: Boolean,
        fallbackProvider: String
    ): SupabaseAuthResult<SupabaseAuthSession> {
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SupabaseAuthResult.Error(
                        message = parseSupabaseError(rawBody, response.code),
                        isUnauthorized = response.code == 401
                    )
                }

                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                    ?: return@use SupabaseAuthResult.Error("Unexpected response from authentication server.")

                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.optJSONObject("user") ?: json
                val userId = userObj.optString("id", "")
                val userEmail = userObj.optString("email", "")
                val providers = extractProviders(userObj, fallbackProvider)
                val displayNameHint = extractDisplayNameHint(userObj)

                if (accessToken.isBlank()) {
                    // Email confirmation enabled on Supabase project: signup succeeded, verification required
                    return@use if (isSignUp && userId.isNotBlank()) {
                        SupabaseAuthResult.Success(
                            data = SupabaseAuthSession(
                                userId = userId,
                                email = userEmail,
                                providers = providers,
                                displayNameHint = displayNameHint
                            ),
                            notice = "Account created! Verify your email via the link in your inbox, then sign in to start cloud sync."
                        )
                    } else {
                        SupabaseAuthResult.Error("Email confirmation required. Check your inbox and try signing in.")
                    }
                }

                val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
                SupabaseAuthResult.Success(
                    data = SupabaseAuthSession(
                        userId = userId,
                        email = userEmail,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtEpochSec = expiresAt,
                        providers = providers,
                        displayNameHint = displayNameHint
                    )
                )
            }
        }.getOrElse { err ->
            SupabaseAuthResult.Error(networkErrorMessage(err))
        }
    }

    private fun extractProviders(userObj: JSONObject, fallbackProvider: String): List<String> {
        val result = linkedSetOf<String>()
        val identities: JSONArray? = userObj.optJSONArray("identities")
        if (identities != null) {
            for (i in 0 until identities.length()) {
                val p = identities.optJSONObject(i)?.optString("provider", "").orEmpty().trim()
                if (p.isNotEmpty()) result.add(p)
            }
        }
        val appMeta = userObj.optJSONObject("app_metadata")
        val providersArr = appMeta?.optJSONArray("providers")
        if (providersArr != null) {
            for (i in 0 until providersArr.length()) {
                val p = providersArr.optString(i, "").trim()
                if (p.isNotEmpty()) result.add(p)
            }
        }
        val single = appMeta?.optString("provider", "").orEmpty().trim()
        if (single.isNotEmpty()) result.add(single)
        if (result.isEmpty() && fallbackProvider.isNotBlank()) {
            result.add(fallbackProvider)
        }
        return result.toList()
    }

    private fun extractDisplayNameHint(userObj: JSONObject): String {
        val meta = userObj.optJSONObject("user_metadata") ?: return ""
        return meta.optString("display_name", "").trim()
            .ifBlank { meta.optString("full_name", "").trim() }
            .ifBlank { meta.optString("name", "").trim() }
    }

    private fun parseSupabaseError(rawBody: String?, statusCode: Int): String {
        val json = rawBody?.let { runCatching { JSONObject(it) }.getOrNull() }
        val detail = json?.optString("msg").orEmpty()
            .ifBlank { json?.optString("error_description").orEmpty() }
            .ifBlank { json?.optString("message").orEmpty() }
            .ifBlank { json?.optString("error").orEmpty() }

        val lower = detail.lowercase()
        return when {
            lower.contains("already registered") || lower.contains("already exists") ->
                "An account with this email already exists. Try signing in instead."
            lower.contains("invalid login") || lower.contains("invalid credentials") ->
                "Incorrect email or password. Please double-check and try again."
            lower.contains("email not confirmed") ->
                "Please confirm your email via the link in your inbox before signing in."
            statusCode == 429 ->
                "Too many attempts right now. Please wait a moment and try again."
            statusCode == 401 ->
                "Your session expired. Please sign in again."
            detail.isNotBlank() -> detail
            else -> "Authentication request failed (HTTP $statusCode)."
        }
    }

    private fun networkErrorMessage(err: Throwable): String = when (err) {
        is IOException -> "No internet connection. Your local Brain Buffer data remains available offline."
        else -> "Could not reach Supabase right now. Please try again shortly."
    }
}
