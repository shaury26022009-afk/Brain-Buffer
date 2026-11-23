package com.example.cloud

import com.example.BuildConfig

/**
 * Runtime Supabase configuration injected via AI Studio Secrets -> BuildConfig.
 * Never hardcodes real credentials or exposes any service-role key on the client.
 */
object SupabaseConfig {
    const val AUTH_REDIRECT_SCHEME = "com.aistudio.brainbuffer.vkrqmx"
    const val AUTH_REDIRECT_HOST = "auth-callback"
    const val AUTH_REDIRECT_URI = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"

    private val rawUrl: String
        get() = runCatching { BuildConfig.SUPABASE_URL }.getOrDefault("").trim().trimEnd('/')

    private val rawAnonKey: String
        get() = runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrDefault("").trim()

    val isConfigured: Boolean
        get() {
            val url = rawUrl
            val key = rawAnonKey
            if (url.isBlank() || key.isBlank()) return false
            if (!url.startsWith("https://")) return false
            if (url.contains("YOUR_SUPABASE", ignoreCase = true)) return false
            if (key.contains("YOUR_SUPABASE", ignoreCase = true)) return false
            return true
        }

    val projectUrl: String get() = if (isConfigured) rawUrl else ""
    val anonKey: String get() = if (isConfigured) rawAnonKey else ""

    fun authUrl(path: String): String = "$projectUrl/auth/v1/${path.trimStart('/')}"
    fun restUrl(tableAndQuery: String): String = "$projectUrl/rest/v1/${tableAndQuery.trimStart('/')}"
    fun functionUrl(functionName: String): String = "$projectUrl/functions/v1/${functionName.trimStart('/')}"
}
