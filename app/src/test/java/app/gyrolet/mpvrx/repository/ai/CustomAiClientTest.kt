package app.gyrolet.mpvrx.repository.ai

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomAiClientTest {

  @Test
  fun testBaseUrlNormalization() {
    val client = CustomAiClient(
      client = OkHttpClient(),
      json = Json { ignoreUnknownKeys = true },
      baseUrlProvider = { "https://api.deepseek.com/v1///" },
    )
    assertEquals("https://api.deepseek.com/v1", client.normalizedBaseUrl())
  }

  @Test
  fun testOpenAiCompatibleResponseParsing() {
    val json = Json { ignoreUnknownKeys = true }
    val responseBody = """
      {
        "id": "chatcmpl-123",
        "choices": [
          {
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "1\n00:00:01,000 --> 00:00:04,000\nHello\n你好"
            }
          }
        ]
      }
    """.trimIndent()

    val result = AiResponseParser.openAiCompatible(json, responseBody, "Custom")
    assertTrue(result.isSuccess)
    assertEquals("1\n00:00:01,000 --> 00:00:04,000\nHello\n你好", result.getOrNull()?.text)
  }
}
