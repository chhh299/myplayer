package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class CustomAiModel(
  val id: String,
  val pricing: JsonObject? = null,
)

@Serializable
private data class CustomAiModelListResponse(
  val data: List<CustomAiModel> = emptyList(),
)

@Serializable
private data class CustomAiMessage(
  val role: String,
  val content: String,
)

@Serializable
private data class CustomAiErrorBody(val error: CustomAiErrorDetail? = null)

@Serializable
private data class CustomAiErrorDetail(val message: String? = null)

@Serializable
private data class CustomAiChatRequest(
  val model: String,
  val messages: List<CustomAiMessage>,
  val temperature: Double? = null,
  @SerialName("max_tokens") val maxTokens: Int = 2048,
)

class CustomAiClient(
  private val client: OkHttpClient,
  private val json: Json,
  private val baseUrlProvider: () -> String,
) : AiClient {

  companion object {
    private const val TAG = "CustomAiClient"
    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
  }

  fun normalizedBaseUrl(): String {
    var raw = baseUrlProvider().trim()
    if (raw.isBlank()) return DEFAULT_BASE_URL
    raw = raw.trimEnd('/')
    if (raw.endsWith("/chat/completions")) {
      raw = raw.removeSuffix("/chat/completions").trimEnd('/')
    }
    if (raw.endsWith("/models")) {
      raw = raw.removeSuffix("/models").trimEnd('/')
    }
    val uri = runCatching { java.net.URI(raw) }.getOrNull()
    val path = uri?.path.orEmpty()
    if (path.isEmpty() || path == "/") {
      raw = "$raw/v1"
    }
    return raw
  }

  private val apiClient: OkHttpClient =
    client.newBuilder()
      .connectTimeout(60, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    runCatching {
      val baseUrl = normalizedBaseUrl()
      val url = "$baseUrl/models"
      Log.i(TAG, "Fetching models from: $url")
      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("API error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<CustomAiModelListResponse>(body)
      parsed.data.map { model ->
        AiModelInfo(
          id = model.id,
          displayName = model.id,
          isFree = AiModelPricing.isZeroCost(model.pricing),
        )
      }
    }
  }

  override suspend fun verifyKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val baseUrl = normalizedBaseUrl()
      val url = "$baseUrl/models"
      Log.i(TAG, "Verifying key at: $url")
      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      if (!response.isSuccessful) {
        val body = response.body.string()
        throw Exception("HTTP ${response.code}: ${parseError(body)}")
      }
      "Connected to $baseUrl successfully"
    }
  }

  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions,
  ): Result<AiGeneratedContent> = withContext(Dispatchers.IO) {
    runCatching {
      val baseUrl = normalizedBaseUrl()
      val url = "$baseUrl/chat/completions"
      Log.i(TAG, "Sending chat completion to: $url with model: $model")

      val requestBody = json.encodeToString(
        CustomAiChatRequest.serializer(),
        CustomAiChatRequest(
          model = model,
          messages = listOf(
            CustomAiMessage(role = "system", content = instruction),
            CustomAiMessage(role = "user", content = userInput),
          ),
          temperature = options.temperature.takeIf { it > 0 },
          maxTokens = options.maxTokens,
        ),
      )

      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) {
        val errMsg = parseError(body)
        Log.e(TAG, "Chat completion failed HTTP ${response.code}: $errMsg")
        throw Exception("API error ${response.code}: $errMsg")
      }

      AiResponseParser.openAiCompatible(json, body, "Custom")
    }
  }

  private fun parseError(body: String): String = try {
    val error = json.decodeFromString<CustomAiErrorBody>(body)
    error.error?.message ?: body
  } catch (_: Exception) {
    body.take(200)
  }
}
