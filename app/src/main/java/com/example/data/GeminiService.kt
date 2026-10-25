package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {

    companion object {
        private const val TAG = "GeminiService"
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun askJarvis(userVoiceQuery: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API key is not configured in BuildConfig")
            return@withContext "सर, कृपया AI Studio के Secrets पैनल में अपनी GEMINI_API_KEY कॉन्फ़िगर करें।"
        }

        try {
            val systemPrompt = """
                You are J.A.R.V.I.S., Tony Stark's ultra-intelligent, respectful, crisp AI assistant.
                You are addressing the user respectfully as 'Sir' or 'सर'.
                The user speaks in Hindi or English (Hinglish).
                
                CRITICAL INSTRUCTIONS FOR NATURAL SPOKEN VOICE OUTPUT:
                1. Always answer in natural, fluent Hindi or conversational Hinglish.
                2. Keep the answer strictly 1 to 2 short sentences (maximum 25-30 words) so it can be spoken aloud immediately within 2 seconds.
                3. Do NOT use any markdown characters: no asterisks (*), no bullet points, no hashes (#), no quotes, no emojis, no links.
                4. Be intelligent, prompt, and witty when appropriate. If asked who you are, state: "मैं जार्विस हूँ सर, आपका पर्सनल एआई असिस्टेंट।"
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val sysInst = JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                }
                put("systemInstruction", sysInst)

                val contentObj = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userVoiceQuery)))
                }
                put("contents", JSONArray().put(contentObj))

                val genConfig = JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 120)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error code: ${response.code}, body: $bodyString")
                    return@withContext "माफ़ कीजिए सर, सर्वर से संपर्क करने में समस्या हुई।"
                }

                val root = JSONObject(bodyString)
                val candidates = root.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text")

                if (!rawText.isNullOrBlank()) {
                    cleanSpokenText(rawText)
                } else {
                    "हाँ सर, मैं आपकी सेवा में तैयार हूँ।"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Gemini API", e)
            "माफ़ कीजिए सर, कनेक्शन में रुकावट आई है।"
        }
    }

    /**
     * Cleans up markdown, emojis, asterisks, brackets, or excessive symbols
     * to ensure fast, smooth Text-To-Speech pronunciation.
     */
    private fun cleanSpokenText(text: String): String {
        return text
            .replace(Regex("[*#_`~>|\\[\\]{}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
